#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# EasyOA 启动脚本共享函数库（bash）。
#
# 仅被 source 使用，不直接执行。提供：
#   * 统一日志（INFO / SUCCESS / WARN / ERROR / DIE）
#   * 随机密钥生成（openssl 优先，/dev/urandom 兜底）
#   * 支持 Ed25519 的 openssl 探测（macOS 系统 LibreSSL 不支持，需探测）
#   * .env 键值解析（只读解析，绝不 source .env，避免任意代码执行）
# ---------------------------------------------------------------------------
# shellcheck disable=SC2034

# --- 终端颜色（非 tty 时自动关闭） -------------------------------------------
if [ -t 2 ] && [ "${TERM:-}" != "dumb" ]; then
  _C_INFO=$'\033[1;34m'    # 蓝
  _C_OK=$'\033[1;32m'      # 绿
  _C_WARN=$'\033[1;33m'    # 黄
  _C_ERR=$'\033[1;31m'     # 红
  _C_DIM=$'\033[2m'        # 灰
  _C_OFF=$'\033[0m'
else
  _C_INFO='' _C_OK='' _C_WARN='' _C_ERR='' _C_DIM='' _C_OFF=''
fi

INFO()    { printf '%s[%s]%s %s\n' "${_C_INFO}" "INFO" "${_C_OFF}" "$*" >&2; }
SUCCESS() { printf '%s[%s]%s %s\n' "${_C_OK}" "OK"    "${_C_OFF}" "$*" >&2; }
WARN()    { printf '%s[%s]%s %s\n' "${_C_WARN}" "WARN" "${_C_OFF}" "$*" >&2; }
ERROR()   { printf '%s[%s]%s %s\n' "${_C_ERR}" "ERROR" "${_C_OFF}" "$*" >&2; }
DIE()     { ERROR "$@"; exit 1; }

# require_command <命令> <用途说明>：命令不存在则 fail closed
require_command() {
  command -v "$1" >/dev/null 2>&1 || DIE "$2 is required, but '$1' was not found in PATH."
}

# --- 随机密钥 -----------------------------------------------------------------
# rand_b64 <字节数>：输出 base64 随机串（无换行）。
# 优先 openssl rand；openssl 缺失时回退 /dev/urandom | base64；都不可用则失败。
rand_b64() {
  local bytes="${1:?rand_b64: bytes required}"
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -base64 "$bytes" | tr -d '\n'
    return 0
  fi
  if [ -r /dev/urandom ]; then
    head -c "$bytes" /dev/urandom | base64 | tr -d '\n'
    return 0
  fi
  DIE "Cannot generate random secret: neither 'openssl' nor /dev/urandom is available."
}

# --- Ed25519 能力探测 ----------------------------------------------------------
# find_openssl_ed25519：在候选中找到一个能完成 Ed25519 sign/verify 的 openssl，
# 输出其路径；找不到输出为空（调用方决定是否 fail closed）。
# 不能只看版本号：macOS 系统 LibreSSL 与各发行版 OpenSSL 能力差异只能实测。
find_openssl_ed25519() {
  local candidates=() cand tmp ok
  if command -v openssl >/dev/null 2>&1; then
    candidates+=("$(command -v openssl)")
  fi
  # 常见 Homebrew / MacPorts 安装位置（PATH 中只有 LibreSSL 时）
  local extra
  for extra in /opt/homebrew/opt/openssl@3/bin/openssl \
               /usr/local/opt/openssl@3/bin/openssl \
               /opt/local/bin/openssl; do
    [ -x "$extra" ] && candidates+=("$extra")
  done
  tmp="$(mktemp -d)"
  for cand in "${candidates[@]}"; do
    ok=1
    "$cand" genpkey -algorithm ed25519 -out "$tmp/k.pem" >/dev/null 2>&1 || ok=0
    if [ "$ok" -eq 1 ]; then
      printf 'probe' > "$tmp/m"
      "$cand" pkey -in "$tmp/k.pem" -pubout -out "$tmp/pub.pem" >/dev/null 2>&1 || ok=0
      "$cand" pkeyutl -sign -inkey "$tmp/k.pem" -rawin -in "$tmp/m" -out "$tmp/m.sig" >/dev/null 2>&1 || ok=0
      "$cand" pkeyutl -verify -pubin -inkey "$tmp/pub.pem" -rawin -sigfile "$tmp/m.sig" -rawin -in "$tmp/m" >/dev/null 2>&1 || ok=0
    fi
    if [ "$ok" -eq 1 ]; then
      rm -rf "$tmp"
      printf '%s\n' "$cand"
      return 0
    fi
  done
  rm -rf "$tmp"
  return 1
}

# --- .env 解析 -----------------------------------------------------------------
# env_get <root> <变量名>：从 <root>/.env 只读解析变量值（不执行任何代码）。
# 只匹配顶层 KEY=VALUE，忽略注释与空行；值不去做 shell 展开。
env_get() {
  local envfile="$1/.env" key="$2"
  [ -f "$envfile" ] || return 1
  awk -F= -v k="$key" '
    $0 !~ /^[[:space:]]*#/ && $0 !~ /^[[:space:]]*$/ {
      line=$0
      sub(/\r$/, "", line)
      sub(/^[[:space:]]+/, "", line)
      if (index(line, k"=") == 1) {
        sub(/^[^=]*=/, "", line)
        gsub(/^"|"$/, "", line)
        print line
        exit
      }
    }' "$envfile"
}

# is_weak_secret <值> <最小长度>：占位符 / 已知弱值 / 长度不足 → 返回 0（弱）
is_weak_secret() {
  local value="$1" min_len="$2" lc
  [ -n "$value" ] || return 0
  lc="$(printf '%s' "$value" | tr '[:upper:]' '[:lower:]')"
  case "$lc" in
    password123|easyoa123|changeme|change_me|admin123|123456|secret|easyoa_dev_password)
      return 0 ;;
  esac
  case "$value" in
    CHANGE_ME*|changeme*) return 0 ;;
  esac
  [ "${#value}" -lt "$min_len" ] && return 0
  return 1
}
