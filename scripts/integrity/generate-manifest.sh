#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# EasyOA 官方发布完整性清单生成工具（Maintainer / CI 使用）。
#
# 只做一件事：为发布包内受保护的程序内容生成 SHA-256 清单（manifest.sha256）。
#
# 它 deliberately 不做签名：
#   签名需要 Release Signing Private Key，只能在 CI（GitHub Secrets）或
#   维护者本机离线完成。普通部署用户拿到本工具也无法把篡改后的程序
#   重新认证为官方版本——因为签名验证只认 integrity/release-public-key.pem 对应
#   的私钥。
#
# 用法：
#   scripts/integrity/generate-manifest.sh [--root <发布包根目录>] [--output <manifest 路径>]
#
# 清单格式（与 sha256sum 兼容）：
#   <64 位小写 hex>  <相对路径>\n   （按路径排序）
# ---------------------------------------------------------------------------
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="${SCRIPT_DIR}/../.."
OUTPUT=""

usage() { printf 'Usage: %s [--root <dir>] [--output <file>]\n' "$0" >&2; }

while [ $# -gt 0 ]; do
  case "$1" in
    --root)   ROOT="${2:?}"; shift 2 ;;
    --output) OUTPUT="${2:?}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) usage; exit 1 ;;
  esac
done
ROOT="$(cd "$ROOT" && pwd)"
[ -n "$OUTPUT" ] || OUTPUT="$ROOT/integrity/manifest.sha256"

# ---------------------------------------------------------------------------
# 受保护内容清单：官方发布包中必须保持不可修改的程序内容。
# 与 verify-integrity.sh 中的 REQUIRED_PROTECTED 保持同步。
# 运行时数据（.env、TLS 证书、数据库卷、上传文件、日志）绝不进入清单。
# ---------------------------------------------------------------------------
REQUIRED_PROTECTED=("scripts/integrity/protected-files.txt")
while IFS= read -r required || [ -n "$required" ]; do
  [ -z "$required" ] || REQUIRED_PROTECTED+=("$required")
done < "$ROOT/scripts/integrity/protected-files.txt"


# 动态追加：conf.d 下所有站点配置都必须受保护
for extra in "$ROOT"/infra/nginx/conf.d/*.conf; do
  [ -f "$extra" ] || continue
  rel="infra/nginx/conf.d/$(basename "$extra")"
  # 去重（easyoa.conf 已在固定清单中）
  printf '%s\n' "${REQUIRED_PROTECTED[@]}" | grep -qxF "$rel" || REQUIRED_PROTECTED+=("$rel")
done

# Frontend files are independently covered as well as carried by the image archive.
while IFS= read -r file; do REQUIRED_PROTECTED+=("${file#"$ROOT/"}"); done < <(find "$ROOT/frontend/dist" -type f | LC_ALL=C sort)
# De-duplicate the list file and static nginx entry.
unique=()
for item in "${REQUIRED_PROTECTED[@]}"; do
  if [ "${#unique[@]}" -eq 0 ] || ! printf '%s\n' "${unique[@]}" | grep -qxF "$item"; then unique+=("$item"); fi
done
REQUIRED_PROTECTED=("${unique[@]}")

command -v openssl >/dev/null 2>&1 || { echo "ERROR: openssl is required to hash files." >&2; exit 1; }

# 逐个校验存在性（缺失即失败——不生成不完整清单）
missing=0
for rel in "${REQUIRED_PROTECTED[@]}"; do
  if [ ! -f "$ROOT/$rel" ]; then
    echo "ERROR: protected file missing: $rel" >&2
    missing=1
  fi
done
[ "$missing" -eq 0 ] || { echo "ERROR: refusing to generate an incomplete manifest." >&2; exit 1; }

mkdir -p "$(dirname "$OUTPUT")"
tmp="$(mktemp)"
trap 'rm -f "$tmp"' EXIT

for rel in "${REQUIRED_PROTECTED[@]}"; do
  hash="$(openssl dgst -sha256 -r "$ROOT/$rel" | awk '{print $1}')"
  printf '%s  %s\n' "$hash" "$rel" >> "$tmp"
done

LC_ALL=C sort -k2,2 -o "$tmp" "$tmp"
mv "$tmp" "$OUTPUT"
trap - EXIT

count="$(wc -l < "$OUTPUT" | tr -d ' ')"
echo "OK: wrote $OUTPUT ($count files hashed)."
echo "Next step (release signing only): sign it with the Release Signing Private Key,"
echo "  openssl pkeyutl -sign -inkey <private.pem> -rawin -in manifest.sha256 -out manifest.sig"
echo "This tool never signs and never needs the private key."
