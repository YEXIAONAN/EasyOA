#!/usr/bin/env bash
# Production lifecycle. Sourced by easyoactl; signed releases only.
check_docker_environment() {
  INFO "Checking Docker environment..."
  require_command docker "Docker"
  if ! docker compose version >/dev/null 2>&1; then
    DIE "Docker Compose v2 is required ('docker compose ...'). Legacy 'docker-compose' is not supported."
  fi
  if ! docker info >/dev/null 2>&1; then
    DIE "Docker daemon is not available. Start Docker first."
  fi
  SUCCESS "Docker and Docker Compose v2 are available."
}

# ---------------------------------------------------------------------------
# VERSION is the canonical version; production always requires a signed bundle.
resolve_version() { tr -d "[:space:]" < "$ROOT/VERSION"; }

INTEGRITY_RESULT="not applicable (development mode)"

run_integrity_gate() {
  INFO "Verifying official EasyOA release..."
  bash "$ROOT/scripts/integrity/verify-integrity.sh" --root "$ROOT" || DIE "Official release verification failed. No services have been started. Use 'easyoactl dev' for source development."
  INTEGRITY_RESULT="VERIFIED (official signed release)"
}

# ---------------------------------------------------------------------------
# 健康检查等待
# ---------------------------------------------------------------------------
wait_service_healthy() { # <service> <timeout 秒>
  local svc="$1" timeout="$2" elapsed=0 cid state
  cid="$(compose ps -q "$svc" 2>/dev/null || true)"
  [ -n "$cid" ] || return 1
  while :; do
    state="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$cid" 2>/dev/null || printf 'gone')"
    [ "$state" = "healthy" ] && return 0
    if [ "$elapsed" -ge "$timeout" ]; then
      ERROR "$svc did not become healthy within ${timeout}s (state: $state)."
      return 1
    fi
    sleep 5
    elapsed=$((elapsed + 5))
  done
}

wait_https_reachable() { # <端口> <timeout 秒>
  local port="$1" timeout="$2" elapsed=0
  while :; do
    local identity
    if curl -skf --max-time 3 "https://127.0.0.1:${port}/healthz" >/dev/null 2>&1 &&
       curl -skf --max-time 3 "https://127.0.0.1:${port}/actuator/health" >/dev/null 2>&1; then
      identity="$(curl -skf --max-time 5 "https://127.0.0.1:${port}/api/system/about" 2>/dev/null || true)"
      if printf '%s' "$identity" | grep -Fq '"signatureVerified":true' &&
         printf '%s' "$identity" | grep -Fq "\"version\":\"$(resolve_version)\""; then return 0; fi
    fi
    if [ "$elapsed" -ge "$timeout" ]; then
      ERROR "HTTPS entry / API health / signed API identity was not confirmed within ${timeout}s."
      return 1
    fi
    sleep 3
    elapsed=$((elapsed + 3))
  done
}

report_startup_failure() {
  ERROR "EasyOA did not become healthy. Current service status:"
  compose ps >&2 || true
  printf '%s\n' "Inspect logs with:" >&2
  printf '%s\n' "  docker compose logs -f" >&2
  printf '%s\n' "  docker compose logs easyoa-api" >&2
}

print_success_banner() {
  local version url port base
  version="$(resolve_version)"
  base="$(env_get "$ROOT" EASYOA_BASE_URL || true)"
  [ -n "$base" ] || base="https://localhost"
  port="$(env_get "$ROOT" EASYOA_HTTPS_PORT || true)"
  url="$base"
  if [ -n "$port" ] && [ "$port" != "443" ]; then
    # Append only when the configured URL has no explicit port.
    url="$(printf '%s' "$base" | sed -E "s#(^[a-zA-Z]+://(\[[^]]+\]|[^/:]+))(/.*)?\$#\1:${port}\3#")"
  fi

  local pg_state api_state web_state
  pg_state="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}running{{end}}' "$(compose ps -q postgres)")"
  api_state="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}running{{end}}' "$(compose ps -q easyoa-api)")"
  web_state="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}running{{end}}' "$(compose ps -q easyoa-web)")"

  cat <<EOF

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

 EasyOA Community Edition started successfully ✓

 Version:     ${version}
 Mode:        Production
 Integrity:   ${INTEGRITY_RESULT}
 URL:         ${url}

 Services:
   PostgreSQL    ${pg_state}
   API           ${api_state}
   Web           ${web_state}
   Nginx         running

 First run: open ${url} and complete /setup initialization.

 Commands:
   ./easyoactl status
   ./easyoactl logs -f
   ./easyoactl stop

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
EOF
}

# ---------------------------------------------------------------------------
# 生产启动
# ---------------------------------------------------------------------------
run_production() {
  INFO "EasyOA production startup in: $ROOT"

  check_docker_environment
  require_command curl "curl (health checks)"
  run_integrity_gate

  ensure_env_file "$ROOT"
  validate_env_prod "$ROOT"
  # Compose gives ambient environment variables precedence over --env-file.
  # Pin the deployment keys to the configuration that was actually validated.
  local key value
  for key in POSTGRES_DB POSTGRES_USER POSTGRES_PASSWORD EASYOA_SESSION_SECRET EASYOA_BASE_URL EASYOA_HTTP_PORT EASYOA_HTTPS_PORT EASYOA_TLS_CERT_DIR; do
    value="$(env_get "$ROOT" "$key" || true)"
    if [ -n "$value" ]; then export "$key=$value"; else unset "$key"; fi
  done
  export EASYOA_PROFILE=prod

  ensure_tls_prod "$ROOT" "$ALLOW_SELF_SIGNED"

  INFO "Validating Docker Compose configuration..."
  if ! compose config -q; then
    DIE "docker compose config validation failed."
  fi
  SUCCESS "Compose configuration is valid."

  ensure_release_images_and_up

  INFO "Waiting for health checks..."
  local failed=0
  wait_service_healthy postgres 120  || failed=1
  wait_service_healthy easyoa-api 180 || failed=1
  wait_service_healthy easyoa-web 60  || failed=1
  wait_https_reachable "$(env_get "$ROOT" EASYOA_HTTPS_PORT || printf '443')" 30 || failed=1
  if [ "$failed" -ne 0 ]; then
    report_startup_failure
    exit 1
  fi
  SUCCESS "All services are healthy."

  print_success_banner
}

# 官方发布包：始终加载已验证的镜像归档，避免同名本地标签指向其他内容。
ensure_release_images_and_up() {
  local images missing img tarball
  images=()
  while IFS= read -r img; do [ -z "$img" ] || images+=("$img"); done < <(compose config --images)
  [ "${#images[@]}" -gt 0 ] || DIE "No images resolved from docker-compose.yml."

  tarball="$ROOT/release-images.tar.gz"
  [ -f "$tarball" ] || DIE "Signed release image archive is missing."
  INFO "Loading verified release images..."
  docker load -i "$tarball" >/dev/null
  for img in "${images[@]}"; do
    docker image inspect "$img" >/dev/null 2>&1 || DIE "Release image is missing: $img"
  done

  INFO "Starting verified release images (no local rebuild)..."
  compose up -d --no-build --pull never
}

# ---------------------------------------------------------------------------
# 开发启动（明确不启用生产完整性强制）
# ---------------------------------------------------------------------------
