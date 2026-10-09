#!/usr/bin/env bash
# The same core build/sign/archive pipeline is used locally and in GitHub Actions.
set -Eeuo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TAG="" OUTPUT="" PRIVATE_KEY="" PUBLIC_KEY=""
while [ $# -gt 0 ]; do
  case "$1" in
    --tag) TAG="${2:?}"; shift 2 ;;
    --output) OUTPUT="${2:?}"; shift 2 ;;
    --signing-key) PRIVATE_KEY="${2:?}"; shift 2 ;;
    --public-key) PUBLIC_KEY="${2:?}"; shift 2 ;;
    *) printf 'Unknown option: %s\n' "$1" >&2; exit 2 ;;
  esac
done
source "$ROOT/scripts/bootstrap/common.sh"
require_command python3 'Python 3'; require_command docker Docker
python3 "$ROOT/scripts/release/version.py" --tag "$TAG"
[ -s "$PRIVATE_KEY" ] && [ -s "$PUBLIC_KEY" ] || DIE 'Release signing key and trusted public key are required.'
[ -n "$OUTPUT" ] && [ ! -e "$OUTPUT" ] || DIE 'Release output must be a new directory.'
OPENSSL_BIN="$(find_openssl_ed25519 || true)"
[ -n "$OPENSSL_BIN" ] || DIE 'Ed25519-capable OpenSSL is required.'
key_check="$(mktemp -d)"; trap 'rm -rf "$key_check"' EXIT
umask 077
"$OPENSSL_BIN" pkey -in "$PRIVATE_KEY" -pubout -outform DER -out "$key_check/private-public.der"
"$OPENSSL_BIN" pkey -pubin -in "$PUBLIC_KEY" -outform DER -out "$key_check/public.der"
cmp -s "$key_check/private-public.der" "$key_check/public.der" || DIE 'Signing private key does not match the trusted public key.'
export EASYOA_RELEASE_KEY_FINGERPRINT="$("$OPENSSL_BIN" dgst -sha256 -r "$key_check/public.der" | awk '{print $1}')"
export EASYOA_SOURCE_REF="$TAG"
docker compose --project-directory "$ROOT" --env-file "$ROOT/.env.example" -f "$ROOT/docker-compose.yml" config -q
docker compose --project-directory "$ROOT" --env-file "$ROOT/.env.example" -f "$ROOT/docker-compose.dev.yml" config -q
docker compose --project-directory "$ROOT" --env-file "$ROOT/.env.example" -f "$ROOT/docker-compose.yml" build
docker compose --project-directory "$ROOT" --env-file "$ROOT/.env.example" -f "$ROOT/docker-compose.yml" pull --ignore-buildable
mkdir -p "$OUTPUT/assets"
OUTPUT="$(cd "$OUTPUT" && pwd)"
bundle="EasyOA-$TAG"
bash "$ROOT/scripts/integrity/package-release.sh" --version "$TAG" --output "$OUTPUT/$bundle" --signing-key "$PRIVATE_KEY" --public-key "$PUBLIC_KEY"
python3 "$ROOT/scripts/release/archive.py" create "$OUTPUT/assets/$bundle.tar.gz" --source "$OUTPUT/$bundle"
hash="$("$OPENSSL_BIN" dgst -sha256 -r "$OUTPUT/assets/$bundle.tar.gz" | awk '{print $1}')"
printf '%s  %s.tar.gz\n' "$hash" "$bundle" > "$OUTPUT/assets/$bundle.tar.gz.sha256"
"$OPENSSL_BIN" pkeyutl -sign -inkey "$PRIVATE_KEY" -rawin -in "$OUTPUT/assets/$bundle.tar.gz.sha256" -out "$OUTPUT/assets/$bundle.tar.gz.sha256.sig"
cp "$OUTPUT/$bundle/integrity/manifest.sha256" "$OUTPUT/$bundle/integrity/manifest.sig" "$OUTPUT/$bundle/integrity/release-public-key.pem" "$OUTPUT/assets/"
PATH="$(dirname "$OPENSSL_BIN"):$PATH" python3 - "$ROOT/scripts/release" "$OUTPUT/assets" "$TAG" <<'PY'
import sys
sys.path.insert(0,sys.argv[1])
from publish import validate_assets
validate_assets(sys.argv[2],sys.argv[3])
print('Package, checksum, both signatures and release asset list validated.')
PY
SUCCESS "Release assets ready: $OUTPUT/assets"
