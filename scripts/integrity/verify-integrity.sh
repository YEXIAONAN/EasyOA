#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# EasyOA 官方发布完整性验证（fail closed）。
#
# 输入：integrity/release-public-key.pem + integrity/manifest.sha256 + integrity/manifest.sig
# 流程：Ed25519 签名验证 → 清单严格解析 → 逐文件 SHA-256 比对
#
# 任何一步失败都输出原因并以非 0 退出：
#   manifest / signature / public key 缺失或损坏、签名不匹配、受保护文件
#   缺失或被修改、清单格式非法、integrity/ 目录混入意外文件、找不到支持
#   Ed25519 的 openssl —— 一律拒绝，绝不"验证失败但继续"。
#
# 用法：scripts/integrity/verify-integrity.sh [--root <发布包根目录>]
# ---------------------------------------------------------------------------
set -Eeuo pipefail

LF=$'\n'
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="${SCRIPT_DIR}/../.."

while [ $# -gt 0 ]; do
  case "$1" in
    --root) ROOT="${2:?}"; shift 2 ;;
    -h|--help) printf 'Usage: %s [--root <dir>]\n' "$0" >&2; exit 0 ;;
    *) printf 'Usage: %s [--root <dir>]\n' "$0" >&2; exit 1 ;;
  esac
done
ROOT="$(cd "$ROOT" && pwd)"
INTEGRITY_DIR="$ROOT/integrity"

fail() {
  printf '%s\n' "[EasyOA Integrity]" >&2
  printf '%s\n' "Official release verification failed." >&2
  printf '%s\n' "$*" >&2
  printf '%s\n' "Please restore the official release package." >&2
  exit 1
}

# --- 0. integrity 目录内容必须精确 ------------------------------------------
# 只允许这四个文件；出现其他任何文件都视为意外完整性元数据。
ALLOWED_INTEGRITY=(release-public-key.pem manifest.sha256 manifest.sig README.md)
if [ ! -d "$INTEGRITY_DIR" ]; then
  fail "Integrity metadata directory is missing: integrity/"
fi
unexpected=""
while IFS= read -r p; do
  base="$(basename "$p")"
  case " ${ALLOWED_INTEGRITY[*]} " in
    *" $base "*) ;;
    *) unexpected="${unexpected}  ${base}${LF}" ;;
  esac
done < <(find "$INTEGRITY_DIR" -mindepth 1 -maxdepth 1)
if [ -n "$unexpected" ]; then
  fail "Unexpected files in integrity/:$LF${unexpected}"
fi

# --- 1. 三件元数据必须齐全 ---------------------------------------------------
missing_meta=()
for f in release-public-key.pem manifest.sha256 manifest.sig; do
  [ -s "$INTEGRITY_DIR/$f" ] || missing_meta+=("$f")
done
if [ "${#missing_meta[@]}" -gt 0 ]; then
  detail=""
  for f in "${missing_meta[@]}"; do detail="${detail}  integrity/$f"$'\n'; done
  fail "Integrity metadata missing or empty:${LF}${detail}"
fi

# --- 2. 找到支持 Ed25519 的 openssl ------------------------------------------
# shellcheck source=../bootstrap/common.sh
[ -f "$SCRIPT_DIR/../bootstrap/common.sh" ] || fail "Verification tool incomplete: bootstrap/common.sh not found."
# shellcheck disable=SC1091
source "$SCRIPT_DIR/../bootstrap/common.sh"
OPENSSL_BIN="$(find_openssl_ed25519 || true)"
if [ -z "$OPENSSL_BIN" ]; then
  ERROR "No Ed25519-capable openssl found (macOS system LibreSSL is not enough)."
  ERROR "Install OpenSSL 1.1.1+ (e.g. 'brew install openssl' on macOS) and ensure it is in PATH."
  fail "Verification tool failure: no usable openssl."
fi

# --- 3. 验证清单签名（Ed25519） ----------------------------------------------
if ! "$OPENSSL_BIN" pkeyutl -verify -pubin \
      -inkey "$INTEGRITY_DIR/release-public-key.pem" \
      -sigfile "$INTEGRITY_DIR/manifest.sig" \
      -rawin -in "$INTEGRITY_DIR/manifest.sha256" >/dev/null 2>&1; then
  fail "Manifest signature is INVALID for the supplied public key."
fi
INFO "Manifest signature: valid (Ed25519)."

# --- 4. 严格解析清单 -----------------------------------------------------------
# 兼容 macOS 自带 bash 3.2：用平行数组代替关联数组
MANIFEST="$INTEGRITY_DIR/manifest.sha256"
[ -s "$MANIFEST" ] || fail "Manifest is empty: integrity/manifest.sha256"
parse_errors=()
SEEN_PATHS=()
SEEN_HASHES=()

manifest_hash_for() { # <path>：输出该路径在清单中的哈希；不存在则返回 1
  local i="$1"
  local idx
  for idx in "${!SEEN_PATHS[@]}"; do
    if [ "${SEEN_PATHS[$idx]}" = "$i" ]; then
      printf '%s' "${SEEN_HASHES[$idx]}"
      return 0
    fi
  done
  return 1
}

while IFS= read -r line || [ -n "$line" ]; do
  if ! printf '%s' "$line" | grep -qE '^[0-9a-f]{64}  [^ ].*$' || \
     printf '%s' "$line" | grep -q $'[\t\r]'; then
    parse_errors+=("malformed line: $line")
    continue
  fi
  hash="${line:0:64}"
  rel="${line:66}"
  if [ -z "$rel" ] || [ "${rel:0:1}" = "/" ] || [ "${rel:0:1}" = "-" ] || \
     printf '%s' "$rel" | grep -qE '^[A-Za-z]:|(^|/)\.\.(/|$)|\\'; then
    parse_errors+=("unsafe path: $rel")
    continue
  fi
  if manifest_hash_for "$rel" >/dev/null; then
    parse_errors+=("duplicate path: $rel")
    continue
  fi
  SEEN_PATHS+=("$rel")
  SEEN_HASHES+=("$hash")
done < "$MANIFEST"

if [ "${#parse_errors[@]}" -gt 0 ]; then
  detail=""
  for e in "${parse_errors[@]}"; do detail="${detail}  $e"$'\n'; done
  fail "Manifest parse failure:${LF}${detail}"
fi
if [ "${#SEEN_PATHS[@]}" -eq 0 ]; then
  fail "Manifest contains no entries."
fi

# --- 5. 清单必须覆盖全部关键内容 ---------------------------------------------
# 与 generate-manifest.sh 的 REQUIRED_PROTECTED 保持同步
REQUIRED_PROTECTED=("scripts/integrity/protected-files.txt")
while IFS= read -r required || [ -n "$required" ]; do
  [ -z "$required" ] || REQUIRED_PROTECTED+=("$required")
done < "$ROOT/scripts/integrity/protected-files.txt"

not_covered=()
for req in "${REQUIRED_PROTECTED[@]}"; do
  manifest_hash_for "$req" >/dev/null || not_covered+=("$req")
done
# Nginx loads every *.conf, including files added after a package was signed.
for extra in "$ROOT"/infra/nginx/conf.d/*.conf; do
  [ -e "$extra" ] || continue
  rel="infra/nginx/conf.d/$(basename "$extra")"
  manifest_hash_for "$rel" >/dev/null || not_covered+=("$rel")
done
while IFS= read -r file; do
  rel="${file#"$ROOT/"}"
  manifest_hash_for "$rel" >/dev/null || not_covered+=("$rel")
done < <(find "$ROOT/frontend/dist" -type f | LC_ALL=C sort)
if [ "${#not_covered[@]}" -gt 0 ]; then
  detail=""
  for p in "${not_covered[@]}"; do detail="${detail}  $p"$'\n'; done
  fail "Manifest does not protect required files:${LF}${detail}"
fi

# --- 6. 逐文件哈希比对 ----------------------------------------------------------
missing_files=()
modified_files=()
for rel in "${SEEN_PATHS[@]}"; do
  file="$ROOT/$rel"
  if [ ! -f "$file" ]; then
    missing_files+=("$rel")
    continue
  fi
  actual="$("$OPENSSL_BIN" dgst -sha256 -r "$file" | awk '{print $1}')"
  expected="$(manifest_hash_for "$rel")"
  if [ "$actual" != "$expected" ]; then
    modified_files+=("$rel")
  fi
done

if [ "${#missing_files[@]}" -gt 0 ] || [ "${#modified_files[@]}" -gt 0 ]; then
  detail=""
  if [ "${#modified_files[@]}" -gt 0 ]; then
    detail="${detail}Modified:$LF"
    for p in "${modified_files[@]}"; do detail="${detail}  $p$LF"; done
  fi
  if [ "${#missing_files[@]}" -gt 0 ]; then
    detail="${detail}Missing:$LF"
    for p in "${missing_files[@]}"; do detail="${detail}  $p$LF"; done
  fi
  fail "$detail"
fi

SUCCESS "Official release integrity: VERIFIED (${#SEEN_PATHS[@]} protected files, signature valid)."
