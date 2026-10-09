#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# EasyOA 部署环境引导（bash）。
#
# 仅被 easyoactl source 使用，不直接执行。提供：
#   * ensure_env_file      —— .env 不存在时从 .env.example 生成并注入随机密钥
#   * validate_env_prod    —— 生产模式 fail-closed 配置校验
#   * validate_env_dev     —— 开发模式宽松校验（只提醒，不阻断）
#   * ensure_tls_prod      —— 生产 TLS 证书检查（缺失时允许显式自签名）
#   * detect_tls_state     —— 证书状态探测（供启动器输出）
# ---------------------------------------------------------------------------
# shellcheck disable=SC2034

# ensure_env_file <root>
# 幂等：.env 已存在则直接返回，绝不覆盖用户已有配置。
ensure_env_file() {
  local root="$1" mode="${2:-production}"
  local envfile="$root/.env" example="$root/.env.example"

  if [ -f "$envfile" ]; then
    INFO ".env already exists, keeping it untouched."
    return 0
  fi
  [ -f "$example" ] || DIE ".env not found and .env.example is missing under $root."

  local pg_password session_secret
  pg_password="$(rand_b64 24)"
  session_secret="$(rand_b64 48)"
  [ -n "$pg_password" ] && [ -n "$session_secret" ] || DIE "Failed to generate random secrets."

  # 纯 bash 逐行替换占位符（避免 sed -i 在 BSD/GNU 间的差异），其余内容原样保留
  local line
  while IFS= read -r line || [ -n "$line" ]; do
    case "$line" in
      POSTGRES_PASSWORD=*)        printf 'POSTGRES_PASSWORD=%s\n' "$pg_password" ;;
      EASYOA_SESSION_SECRET=*)    printf 'EASYOA_SESSION_SECRET=%s\n' "$session_secret" ;;
      EASYOA_DEV_SEED=*)          if [ "$mode" = dev ]; then printf 'EASYOA_DEV_SEED=true\n'; else printf 'EASYOA_DEV_SEED=false\n'; fi ;;
      *)                          printf '%s\n' "$line" ;;
    esac
  done < "$example" > "$envfile"
  chmod 600 "$envfile"

  SUCCESS "Created .env from .env.example with random secrets:"
  SUCCESS "  POSTGRES_PASSWORD     (random, $(printf '%s' "$pg_password" | wc -c | tr -d ' ') chars)"
  SUCCESS "  EASYOA_SESSION_SECRET (random, $(printf '%s' "$session_secret" | wc -c | tr -d ' ') chars)"
  WARN  "Review .env before exposing this deployment (EASYOA_BASE_URL, ports, etc.)."
}

# validate_env_prod <root>
# 生产模式 fail-closed 校验：任何安全关键字段不合格即退出非 0。
validate_env_prod() {
  local root="$1" failed=0
  local v

  v="$(env_get "$root" POSTGRES_DB || true)"
  if [ -z "$v" ]; then ERROR "POSTGRES_DB is not set in .env."; failed=1; fi

  v="$(env_get "$root" POSTGRES_USER || true)"
  if [ -z "$v" ]; then ERROR "POSTGRES_USER is not set in .env."; failed=1; fi

  v="$(env_get "$root" POSTGRES_PASSWORD || true)"
  if is_weak_secret "$v" 16; then
    ERROR "POSTGRES_PASSWORD is missing, a placeholder, a known weak value, or shorter than 16 chars."
    ERROR "  Generate one with: openssl rand -base64 24"
    failed=1
  fi

  v="$(env_get "$root" EASYOA_SESSION_SECRET || true)"
  if is_weak_secret "$v" 32; then
    ERROR "EASYOA_SESSION_SECRET is missing, a placeholder, or shorter than 32 chars."
    ERROR "  Generate one with: openssl rand -base64 48"
    failed=1
  fi

  v="$(env_get "$root" EASYOA_PROFILE || true)"
  if [ -n "$v" ] && [ "$v" != "prod" ]; then
    ERROR "EASYOA_PROFILE='$v' is not allowed for production startup (expect 'prod' or unset)."
    ERROR "  Development/demo profiles must never run as production."
    failed=1
  fi

  v="$(env_get "$root" EASYOA_DEV_SEED || true)"
  if [ "$(printf '%s' "$v" | tr '[:upper:]' '[:lower:]')" = "true" ] || [ "$v" = "1" ]; then
    ERROR "Set EASYOA_DEV_SEED=false for production."; failed=1
  fi

  [ "$failed" -eq 0 ] && SUCCESS ".env passed production checks." || exit 1
}

# validate_env_dev <root>：开发模式只提醒占位符，不阻断
validate_env_dev() {
  local root="$1"
  local v
  v="$(env_get "$root" POSTGRES_PASSWORD || true)"
  if is_weak_secret "$v" 1; then
    WARN ".env POSTGRES_PASSWORD looks like a placeholder (fine for local dev only)."
  fi
  return 0
}

# detect_tls_state <root>：输出证书目录与 easyoa.crt/easyoa.key 是否存在
detect_tls_state() {
  local root="$1"
  local cert_dir
  cert_dir="$(env_get "$root" EASYOA_TLS_CERT_DIR || true)"
  [ -n "$cert_dir" ] || cert_dir="./infra/nginx/certs"
  case "$cert_dir" in /*) ;; *) cert_dir="$root/$cert_dir" ;; esac
  printf '%s' "$cert_dir"
}

# ensure_tls_prod <root> <allow_self_signed:0|1>
# 证书缺失时：显式选择（--self-signed-tls、交互确认）才生成自签名证书；
# 无法交互又未显式选择 → fail closed。
ensure_tls_prod() {
  local root="$1" allow_flag="$2"
  local cert_dir gen_script
  cert_dir="$(detect_tls_state "$root")"
  gen_script="$root/scripts/generate-self-signed-cert.sh"

  if [ -f "$cert_dir/easyoa.crt" ] && [ -f "$cert_dir/easyoa.key" ]; then
    SUCCESS "TLS certificate found: $cert_dir/easyoa.crt"
    return 0
  fi

  WARN "No TLS certificate found in $cert_dir (expected easyoa.crt + easyoa.key)."
  WARN "EasyOA does not silently serve production over plain HTTP."

  local answer=""
  if [ "$allow_flag" -eq 1 ]; then
    answer="y"
  elif [ -t 0 ]; then
    printf '%s\n' "Self-signed certificates are for local/internal testing only." >&2
    printf '%s' "Generate a self-signed certificate for local testing? [y/N] " >&2
    IFS= read -r answer || answer=""
  fi

  case "$answer" in
    y|Y|yes|YES)
      [ -f "$gen_script" ] || DIE "Self-signed generator not found: $gen_script"
      local domain host
      host="$(env_get "$root" EASYOA_BASE_URL || true)"
      domain="$(printf '%s' "$host" | sed -E 's#^[a-zA-Z]+://##; s#[/:].*$##')"
      [ -n "$domain" ] || domain="localhost"
      WARN "Self-signed certificates are for local/internal testing only."
      WARN "For production, replace them with a certificate from a trusted CA."
      bash "$gen_script" "$domain" "$cert_dir" || DIE "Self-signed certificate generation failed."
      SUCCESS "Self-signed certificate generated for '$domain'."
      ;;
    *)
      DIE "No TLS certificate. Place easyoa.crt + easyoa.key in $cert_dir, or re-run with --self-signed-tls for local testing only."
      ;;
  esac
}
