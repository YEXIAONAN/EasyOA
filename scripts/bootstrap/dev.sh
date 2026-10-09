#!/usr/bin/env bash
# Local development supervisor; sourced by easyoactl, compatible with Bash 3.2.
dev_compose() { docker compose --project-directory "$ROOT" --env-file "$ROOT/.env" -f "$ROOT/docker-compose.dev.yml" "$@"; }

dev_value() {
  local value
  value="$(printenv "$1" || true)"
  [ -n "$value" ] || value="$(env_get "$ROOT" "$1" || true)"
  printf '%s' "${value:-$2}"
}

dev_require_tools() {
  require_command curl "curl"
  require_command node "Node.js >=22.12"
  require_command npm "npm"
  node -e 'const [a,b]=process.versions.node.split(".").map(Number);process.exit(a>22 || (a===22 && b>=12)?0:1)' || DIE "Node.js >=22.12 is required."
  local version
  version="$(java -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -1 || true)"
  if [ "$version" != 21 ] && [ -x /usr/libexec/java_home ]; then
    local jdk
    jdk="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
    # java_home can return the default JDK even when no requested version exists.
    if [ -n "$jdk" ] && "$jdk/bin/java" -version 2>&1 | head -1 | grep -q 'version "21[."]'; then
      export JAVA_HOME="$jdk"; export PATH="$JAVA_HOME/bin:$PATH"
    fi
  fi
  require_command java "JDK 21"
  version="$(java -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -1)"
  [ "$version" = 21 ] || DIE "JDK 21 is required (current Java: ${version:-unknown}). Set JAVA_HOME and PATH to a JDK 21 installation."
  [ -x "$ROOT/backend/mvnw" ] || DIE "backend/mvnw is missing or not executable."
}

dev_port_free() {
  node - "$1" <<'JS'
const net = require('net');
const port = Number(process.argv[2]);
if (!Number.isInteger(port) || port<1 || port>65535) process.exit(2);
const server = net.createServer();
server.once('error', () => process.exit(1));
server.listen(port, '127.0.0.1', () => server.close());
JS
}

dev_stop_tree() {
  local pid="$1" child
  for child in $(ps -ax -o pid= -o ppid= | awk -v parent="$pid" '$2==parent {print $1}'); do
    dev_stop_tree "$child"
  done
  kill -TERM "$pid" 2>/dev/null || true
}

dev_cleanup() {
  trap - EXIT INT TERM
  local pid
  for pid in "${DEV_API_PID:-}" "${DEV_WEB_PID:-}"; do
    [ -z "$pid" ] || dev_stop_tree "$pid"
  done
  [ -z "${DEV_API_PID:-}" ] || wait "$DEV_API_PID" 2>/dev/null || true
  [ -z "${DEV_WEB_PID:-}" ] || wait "$DEV_WEB_PID" 2>/dev/null || true
  INFO "API/Web processes stopped. PostgreSQL and its data are preserved."
}

dev_wait_http() {
  local url="$1" pid="$2" label="$3" timeout="$4" elapsed=0
  while ! curl -fsS --max-time 2 "$url" >/dev/null 2>&1; do
    kill -0 "$pid" 2>/dev/null || DIE "$label exited before becoming ready. See $DEV_LOG_DIR."
    [ "$elapsed" -lt "$timeout" ] || DIE "$label did not become ready within ${timeout}s. See $DEV_LOG_DIR."
    sleep 2
    elapsed=$((elapsed+2))
  done
}

run_dev() {
  INFO "Development mode: local data only; release integrity is not enforced."
  check_docker_environment
  [ "$DATABASE_ONLY" -eq 1 ] || dev_require_tools
  ensure_env_file "$ROOT" dev
  validate_env_dev "$ROOT"
  if [ "$DATABASE_ONLY" -eq 0 ]; then
    export POSTGRES_DB="$(dev_value POSTGRES_DB easyoa)"
    export POSTGRES_USER="$(dev_value POSTGRES_USER easyoa)"
    export POSTGRES_PASSWORD="$(dev_value POSTGRES_PASSWORD easyoa_dev_password)"
    export EASYOA_API_PORT="$(dev_value EASYOA_API_PORT 8080)"
    DEV_WEB_PORT="$(dev_value EASYOA_DEV_WEB_PORT 5173)"
    dev_port_free "$EASYOA_API_PORT" || DIE "API port is invalid or occupied: $EASYOA_API_PORT"
    dev_port_free "$DEV_WEB_PORT" || DIE "Web port is invalid or occupied: $DEV_WEB_PORT"
    [ "$EASYOA_API_PORT" != "$DEV_WEB_PORT" ] || DIE "API and Web must use different ports."
    export EASYOA_SESSION_SECRET="$(dev_value EASYOA_SESSION_SECRET dev-only-session-secret-please-change-32chars)"
    export EASYOA_DEV_SEED="$(dev_value EASYOA_DEV_SEED true)"
    export EASYOA_DB_URL="jdbc:postgresql://127.0.0.1:$(dev_value POSTGRES_DEV_PORT 5432)/$POSTGRES_DB"
    export EASYOA_STORAGE_PATH="$ROOT/storage/files"
    export EASYOA_DEV_API_TARGET="http://127.0.0.1:$EASYOA_API_PORT"
    export SPRING_PROFILES_ACTIVE=dev
    export SERVER_ADDRESS=127.0.0.1
    if [ ! -d "$ROOT/frontend/node_modules" ] || [ "$ROOT/frontend/package-lock.json" -nt "$ROOT/frontend/node_modules" ]; then
      INFO "Installing frontend dependencies from package-lock.json..."
      (cd "$ROOT/frontend" && npm ci --no-audit --no-fund)
    fi
  fi
  dev_compose config -q || DIE "Development Compose configuration is invalid."
  dev_compose up -d
  local cid elapsed=0 state
  cid="$(dev_compose ps -q postgres)"
  [ -n "$cid" ] || DIE "Development PostgreSQL did not start."
  while :; do
    state="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$cid" 2>/dev/null || true)"
    [ "$state" = healthy ] && break
    [ "$elapsed" -lt 120 ] || DIE "PostgreSQL did not become healthy. Inspect docker compose -f docker-compose.dev.yml logs postgres."
    sleep 2; elapsed=$((elapsed+2))
  done
  if [ "$DATABASE_ONLY" -eq 1 ]; then SUCCESS "Development PostgreSQL is healthy."; return; fi
  mkdir -p "$ROOT/logs/dev"
  DEV_LOG_DIR="$(mktemp -d "$ROOT/logs/dev/session.XXXXXX")"
  trap dev_cleanup EXIT
  trap 'exit 130' INT
  trap 'exit 143' TERM
  (cd "$ROOT/backend" && exec ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev) >"$DEV_LOG_DIR/api.log" 2>&1 &
  DEV_API_PID=$!
  (cd "$ROOT/frontend" && exec npm run dev -- --host 127.0.0.1 --port "$DEV_WEB_PORT" --strictPort) >"$DEV_LOG_DIR/web.log" 2>&1 &
  DEV_WEB_PID=$!
  dev_wait_http "$EASYOA_DEV_API_TARGET/actuator/health" "$DEV_API_PID" API 180
  dev_wait_http "http://127.0.0.1:$DEV_WEB_PORT/" "$DEV_WEB_PID" Web 60
  SUCCESS "EasyOA development is ready: http://127.0.0.1:$DEV_WEB_PORT"
  INFO "API: $EASYOA_DEV_API_TARGET | Logs: $DEV_LOG_DIR"
  INFO "Press Ctrl+C to stop API/Web. Database and uploaded files are preserved."
  while kill -0 "$DEV_API_PID" 2>/dev/null && kill -0 "$DEV_WEB_PID" 2>/dev/null; do sleep 2; done
  DIE "An application process exited. See $DEV_LOG_DIR."
}
