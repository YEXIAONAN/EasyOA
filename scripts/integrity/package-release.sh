#!/usr/bin/env bash
# Assemble and sign an offline deployment bundle from already-built images.
set -Eeuo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
VERSION="" OUTPUT="" PRIVATE_KEY="" PUBLIC_KEY=""
while [ $# -gt 0 ]; do
  case "$1" in
    --version) VERSION="${2:?}"; shift 2 ;;
    --output) OUTPUT="${2:?}"; shift 2 ;;
    --signing-key) PRIVATE_KEY="${2:?}"; shift 2 ;;
    --public-key) PUBLIC_KEY="${2:?}"; shift 2 ;;
    -h|--help) echo 'Usage: package-release.sh --version vX.Y.Z --output NEW_DIRECTORY --signing-key PRIVATE_FILE --public-key PUBLIC_FILE'; exit 0 ;;
    *) echo "Unknown option: $1" >&2; exit 2 ;;
  esac
done
source "$ROOT/scripts/bootstrap/common.sh"
require_command docker Docker
require_command python3 'Python 3 (release assembly only)'
printf '%s' "$VERSION" | grep -Eq '^v[0-9]+\.[0-9]+\.[0-9]+([.-][A-Za-z0-9][A-Za-z0-9.-]*)?$' || DIE 'A valid release version is required.'
[ -n "$OUTPUT" ] && [ ! -e "$OUTPUT" ] || DIE 'Output must be a new directory; existing files will not be overwritten.'
[ -s "$PRIVATE_KEY" ] && [ -s "$PUBLIC_KEY" ] || DIE 'Provide the release signing private key and trusted public key as files.'
OPENSSL_BIN="$(find_openssl_ed25519 || true)"
[ -n "$OPENSSL_BIN" ] || DIE 'An Ed25519-capable OpenSSL is required.'
temp="$(mktemp -d)"
trap 'rm -rf "$temp"' EXIT
umask 077
"$OPENSSL_BIN" pkey -in "$PRIVATE_KEY" -pubout -outform DER -out "$temp/derived.der"
"$OPENSSL_BIN" pkey -pubin -in "$PUBLIC_KEY" -outform DER -out "$temp/trusted.der"
cmp -s "$temp/derived.der" "$temp/trusted.der" || DIE 'Signing key does not match the trusted release public key.'

docker tag easyoa-easyoa-api "easyoa-api:$VERSION"
docker tag easyoa-easyoa-web "easyoa-web:$VERSION"
mkdir -p "$OUTPUT/backend" "$OUTPUT/frontend/dist" "$OUTPUT/scripts/release" "$OUTPUT/scripts/bootstrap" "$OUTPUT/scripts/integrity" "$OUTPUT/infra/nginx/conf.d" "$OUTPUT/infra/nginx/certs" "$OUTPUT/integrity" "$OUTPUT/docs"
OUTPUT="$(cd "$OUTPUT" && pwd)"
python3 - "$ROOT/docker-compose.yml" "$OUTPUT/docker-compose.yml" "$VERSION" <<'PYCODE'
import re, sys
from pathlib import Path
images = {'easyoa-api':'easyoa-api', 'easyoa-web':'easyoa-web'}
seen = {name:0 for name in images}
result, service, skip = [], None, False
for line in Path(sys.argv[1]).read_text().splitlines(keepends=True):
    match = re.match(r'^  ([A-Za-z0-9_-]+):\s*$', line)
    if match: service = match.group(1)
    if skip and line.startswith('      '): continue
    skip = False
    if service in images and line.rstrip() == '    build:':
        seen[service] += 1
        result.append(f'    image: {images[service]}:{sys.argv[3]}\n')
        skip = True
    else: result.append(line)
if any(count != 1 for count in seen.values()):
    raise SystemExit('Expected exactly one build block for each application service')
Path(sys.argv[2]).write_text(''.join(result))
PYCODE
cat > "$OUTPUT/README.md" <<'RELEASEDOC'
# EasyOA Community Edition

EasyOA Community Edition is licensed under AGPL-3.0-only. Historical MIT
releases retain their original license. See LICENSE and NOTICE.

Run `./easyoactl install`, then `./easyoactl start` on Linux/macOS.
Windows service/development commands use `.\easyoactl.ps1`.
No command defaults to starting services. Production requires a valid official
signature, strong configuration and TLS. Use `install --self-signed-tls` only
for local testing. Existing .env configuration and data are preserved.

This package includes the API jar, frontend dist, required runtime scripts,
and all four Docker service images. See [deployment guide](docs/deployment.md).
The application's About page links to source for its build. Official source:
https://github.com/YEXIAONAN/EasyOA

Use the source repository and `./easyoactl dev` for local development.
Use `./easyoactl help` for backup, restore, diagnostics and upgrade commands.
RELEASEDOC

cp "$ROOT/easyoactl" "$ROOT/easyoactl.ps1" "$ROOT/.env.example" "$ROOT/LICENSE" "$ROOT/NOTICE" "$OUTPUT/"
cp "$ROOT/scripts/bootstrap/"*.sh "$ROOT/scripts/bootstrap/"*.ps1 "$OUTPUT/scripts/bootstrap/"
cp "$ROOT/scripts/integrity/"*.sh "$ROOT/scripts/integrity/"*.ps1 "$ROOT/scripts/integrity/README.md" "$ROOT/scripts/integrity/protected-files.txt" "$OUTPUT/scripts/integrity/"
cp "$ROOT/scripts/release/archive.py" "$ROOT/scripts/release/backup.py" "$ROOT/scripts/release/version.py" "$OUTPUT/scripts/release/"
cp "$ROOT/scripts/quick_start.sh" "$ROOT/scripts/generate-self-signed-cert.sh" "$OUTPUT/scripts/"
cp "$ROOT/infra/nginx/nginx.conf" "$OUTPUT/infra/nginx/"
cp "$ROOT/infra/nginx/conf.d/"*.conf "$OUTPUT/infra/nginx/conf.d/"
cp "$ROOT/integrity/README.md" "$OUTPUT/integrity/"
cp "$ROOT/docs/deployment.md" "$OUTPUT/docs/"
cp "$PUBLIC_KEY" "$OUTPUT/integrity/release-public-key.pem"
touch "$OUTPUT/infra/nginx/certs/.gitkeep"
cp "$ROOT/VERSION" "$OUTPUT/VERSION"
[ "v$(tr -d '[:space:]' < "$ROOT/VERSION")" = "$VERSION" ] || DIE 'Package version does not match repository VERSION.'
# Extract exactly the application payload carried by the signed images, never target caches.
api_container="$(docker create "easyoa-api:$VERSION")"
web_container=""
package_cleanup() {
  [ -z "$api_container" ] || docker rm "$api_container" >/dev/null 2>&1 || true
  [ -z "$web_container" ] || docker rm "$web_container" >/dev/null 2>&1 || true
  rm -rf "$temp"
}
trap package_cleanup EXIT
docker cp "$api_container:/app/app.jar" "$OUTPUT/backend/app.jar"
# Refuse stale images: the jar must carry this version and the same trust anchor.
python3 - "$OUTPUT/backend/app.jar" "$OUTPUT/VERSION" "$temp/trusted.der" "$VERSION" <<'PYBUILD'
import hashlib,sys,zipfile
from pathlib import Path
with zipfile.ZipFile(sys.argv[1]) as jar:
    text=jar.read('BOOT-INF/classes/easyoa-build.properties').decode('utf-8')
values=dict(line.split('=',1) for line in text.splitlines() if '=' in line and not line.startswith('#'))
expected={'version':Path(sys.argv[2]).read_text().strip(),
          'release-key-fingerprint':hashlib.sha256(Path(sys.argv[3]).read_bytes()).hexdigest(),
          'source-ref':sys.argv[4]}
if any(values.get(key)!=value for key,value in expected.items()):
    raise SystemExit('API image build metadata does not match version, trusted key or source tag; rebuild with build-release.sh.')
PYBUILD
web_container="$(docker create "easyoa-web:$VERSION")"
docker cp "$web_container:/usr/share/nginx/html/." "$OUTPUT/frontend/dist/"
# Public release payload must be readable by the non-root API container.
find "$OUTPUT" -type d -exec chmod 755 {} +
find "$OUTPUT" -type f -exec chmod 644 {} +
chmod 755 "$OUTPUT/easyoactl" "$OUTPUT/scripts/"*.sh "$OUTPUT/scripts/bootstrap/"*.sh "$OUTPUT/scripts/integrity/"*.sh
images=()
while IFS= read -r image; do [ -z "$image" ] || images+=("$image"); done < <(
  docker compose --project-directory "$OUTPUT" --env-file "$OUTPUT/.env.example" -f "$OUTPUT/docker-compose.yml" config --images
)
[ "${#images[@]}" -eq 4 ] || DIE 'Release Compose must contain all four service images.'
docker save "${images[@]}" | gzip > "$OUTPUT/release-images.tar.gz"
bash "$OUTPUT/scripts/integrity/generate-manifest.sh" --root "$OUTPUT"
"$OPENSSL_BIN" pkeyutl -sign -inkey "$PRIVATE_KEY" -rawin -in "$OUTPUT/integrity/manifest.sha256" -out "$OUTPUT/integrity/manifest.sig"
bash "$OUTPUT/scripts/integrity/verify-integrity.sh" --root "$OUTPUT"
chmod 644 "$OUTPUT/integrity/manifest.sha256" "$OUTPUT/integrity/manifest.sig" "$OUTPUT/release-images.tar.gz"
SUCCESS "Signed deployment bundle assembled: $OUTPUT"
