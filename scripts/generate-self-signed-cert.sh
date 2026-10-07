#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# 生成自签名 TLS 证书（本地 / 内网试用）。
#
# 生产环境请替换为受信任证书（例如 Let's Encrypt / 企业 CA），
# 并把证书与私钥放到 infra/nginx/certs/ 下，文件名保持 easyoa.crt / easyoa.key。
#
# 用法：
#   ./scripts/generate-self-signed-cert.sh [域名或IP]
# ---------------------------------------------------------------------------
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CERT_DIR="${SCRIPT_DIR}/../infra/nginx/certs"
DOMAIN="${1:-localhost}"

mkdir -p "${CERT_DIR}"

if [[ -f "${CERT_DIR}/easyoa.crt" && -f "${CERT_DIR}/easyoa.key" ]]; then
  echo "证书已存在：${CERT_DIR}/easyoa.crt（如需重新生成请先删除）"
  exit 0
fi

echo "正在为 ${DOMAIN} 生成自签名证书（有效期 825 天）..."

openssl req -x509 -nodes -newkey rsa:2048 \
  -keyout "${CERT_DIR}/easyoa.key" \
  -out "${CERT_DIR}/easyoa.crt" \
  -days 825 \
  -subj "/C=CN/O=EasyOA/CN=${DOMAIN}" \
  -addext "subjectAltName=DNS:${DOMAIN},DNS:localhost,IP:127.0.0.1"

chmod 600 "${CERT_DIR}/easyoa.key"
chmod 644 "${CERT_DIR}/easyoa.crt"

echo "完成："
echo "  证书：${CERT_DIR}/easyoa.crt"
echo "  私钥：${CERT_DIR}/easyoa.key"