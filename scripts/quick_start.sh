#!/usr/bin/env bash
# Run a locally trusted installer; never use curl | bash for unverified code.
set -Eeuo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT/scripts/bootstrap/common.sh"
TAG="" TRUSTED_KEY="" DESTINATION="" SELF_SIGNED=0
while [ $# -gt 0 ]; do
  case "$1" in
    --tag) TAG="${2:?}"; shift 2 ;;
    --public-key) TRUSTED_KEY="${2:?}"; shift 2 ;;
    --destination) DESTINATION="${2:?}"; shift 2 ;;
    --self-signed-tls) SELF_SIGNED=1; shift ;;
    -h|--help) printf 'Usage: quick_start.sh --tag vX.Y.Z --public-key TRUSTED_KEY [--destination NEW_DIR] [--self-signed-tls]\n'; exit 0 ;;
    *) DIE 'Unknown installer option.' ;;
  esac
done
require_command curl curl; require_command python3 'Python 3 (safe extraction)'
python3 - "$ROOT/scripts/release" "$TAG" <<'PY'
import sys
sys.path.insert(0,sys.argv[1])
from version import validate
if not sys.argv[2].startswith('v'): raise SystemExit('A release tag is required.')
validate(sys.argv[2][1:])
PY
[ -s "$TRUSTED_KEY" ] || DIE 'Provide a public key obtained through an independently trusted channel.'
DESTINATION="${DESTINATION:-$PWD/EasyOA-$TAG}"
[ ! -e "$DESTINATION" ] && [ ! -L "$DESTINATION" ] || DIE 'Destination already exists; refusing to overwrite it.'
OPENSSL_BIN="$(find_openssl_ed25519 || true)"; [ -n "$OPENSSL_BIN" ] || DIE 'Ed25519-capable OpenSSL is required.'
download="$(mktemp -d)"; trap 'rm -rf "$download"' EXIT
umask 077
archive="EasyOA-$TAG.tar.gz"
base="https://github.com/YEXIAONAN/EasyOA/releases/download/$TAG"
for asset in "$archive" "$archive.sha256" "$archive.sha256.sig" manifest.sha256 manifest.sig release-public-key.pem; do
  curl --proto '=https' --proto-redir '=https' --fail --location --retry 2 --connect-timeout 15 --max-time 900 "$base/$asset" -o "$download/$asset"
done
"$OPENSSL_BIN" pkey -pubin -in "$TRUSTED_KEY" -outform DER -out "$download/trusted.der"
"$OPENSSL_BIN" pkey -pubin -in "$download/release-public-key.pem" -outform DER -out "$download/downloaded.der"
cmp -s "$download/trusted.der" "$download/downloaded.der" || DIE 'Downloaded release key differs from the trusted key.'
"$OPENSSL_BIN" pkeyutl -verify -pubin -inkey "$TRUSTED_KEY" -rawin -in "$download/$archive.sha256" -sigfile "$download/$archive.sha256.sig" >/dev/null || DIE 'Archive checksum signature is invalid.'
"$OPENSSL_BIN" pkeyutl -verify -pubin -inkey "$TRUSTED_KEY" -rawin -in "$download/manifest.sha256" -sigfile "$download/manifest.sig" >/dev/null || DIE 'Manifest signature is invalid.'
hash="$("$OPENSSL_BIN" dgst -sha256 -r "$download/$archive" | awk '{print $1}')"
expected="$(printf '%s  %s\n' "$hash" "$archive")"
[ "$(cat "$download/$archive.sha256")" = "$expected" ] || DIE 'Archive checksum mismatch.'
# Only now inspect/extract the authenticated archive, and reject links and traversal.
python3 "$ROOT/scripts/release/archive.py" extract "$download/$archive" --destination "$download/extracted" --prefix "EasyOA-$TAG"
release="$download/extracted/EasyOA-$TAG"
cmp -s "$download/manifest.sha256" "$release/integrity/manifest.sha256" || DIE 'Package manifest differs from release asset.'
cmp -s "$download/manifest.sig" "$release/integrity/manifest.sig" || DIE 'Package signature differs from release asset.'
bash "$ROOT/scripts/integrity/verify-integrity.sh" --root "$release"
[ ! -e "$DESTINATION" ] && [ ! -L "$DESTINATION" ] || DIE 'Destination appeared during download; refusing to overwrite it.'
mv "$release" "$DESTINATION"
if [ "$SELF_SIGNED" -eq 1 ]; then bash "$DESTINATION/easyoactl" install --self-signed-tls
else bash "$DESTINATION/easyoactl" install; fi
