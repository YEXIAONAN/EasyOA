#!/usr/bin/env bash
# Explicit operations; no volume deletion outside a confirmed restore.
show_status() {
  printf 'EasyOA Community Edition v%s\n' "$(resolve_version)"
  if bash "$ROOT/scripts/integrity/verify-integrity.sh" --root "$ROOT"; then
    printf 'Release: Official (signature and protected files verified)\n'
  else
    printf 'Release: NOT VERIFIED\n' >&2
  fi
  check_docker_environment
  compose ps
  printf 'URL: %s\n' "$(env_get "$ROOT" EASYOA_BASE_URL || true)"
}

show_logs() {
  local follow=0 service=""
  while [ $# -gt 0 ]; do
    case "$1" in
      -f|--follow) follow=1 ;;
      postgres|easyoa-api|easyoa-web|nginx) [ -z "$service" ] || DIE 'Choose one service.'; service="$1" ;;
      *) DIE 'logs accepts -f and one known service name.' ;;
    esac
    shift
  done
  check_docker_environment
  local options=(logs --tail 200)
  [ "$follow" -eq 0 ] || options+=(-f)
  [ -z "$service" ] || options+=("$service")
  compose "${options[@]}"
}

run_doctor() {
  local failed=0 available service cid state cert_dir
  printf 'EasyOA deployment diagnostics\n'
  if command -v docker >/dev/null && docker compose version >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
    printf 'PASS Docker, Compose v2 and daemon\n'
  else printf 'FAIL Docker / Compose / daemon unavailable\n'; failed=1; fi
  if [ -f "$ROOT/.env" ] && (validate_env_prod "$ROOT" >/dev/null 2>&1); then printf 'PASS Production configuration\n'
  else printf 'FAIL Missing or unsafe .env\n'; failed=1; fi
  cert_dir="$(detect_tls_state "$ROOT")"
  if [ -r "$cert_dir/easyoa.crt" ] && [ -r "$cert_dir/easyoa.key" ]; then printf 'PASS TLS files readable\n'
  else printf 'FAIL TLS certificate/key missing\n'; failed=1; fi
  available="$(df -Pk "$ROOT" | awk 'NR==2 {print $4}')"
  if [ "${available:-0}" -gt 1048576 ]; then printf 'PASS More than 1 GiB free disk space\n'
  else printf 'WARN Less than 1 GiB free disk space\n'; fi
  if bash "$ROOT/scripts/integrity/verify-integrity.sh" --root "$ROOT" >/dev/null 2>&1; then printf 'PASS Official release integrity\n'
  else printf 'FAIL Official release integrity\n'; failed=1; fi
  if compose config -q >/dev/null 2>&1; then
    printf 'PASS Compose configuration\n'
    for service in postgres easyoa-api easyoa-web nginx; do
      cid="$(compose ps -q "$service" 2>/dev/null || true)"
      if [ -z "$cid" ]; then printf 'WARN %s is stopped\n' "$service"; continue; fi
      state="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$cid" 2>/dev/null || true)"
      case "$state" in healthy|running) printf 'PASS %s %s\n' "$service" "$state";; *) printf 'FAIL %s %s\n' "$service" "$state"; failed=1;; esac
    done
    if compose exec -T easyoa-api sh -c 'test -d "$EASYOA_STORAGE_PATH" && test -w "$EASYOA_STORAGE_PATH"' >/dev/null 2>&1; then printf 'PASS Attachment storage writable\n'
    else printf 'WARN Storage permissions not confirmed (API may be stopped)\n'; fi
  else printf 'FAIL Compose configuration\n'; failed=1; fi
  if command -v curl >/dev/null; then
    local port
    port="$(env_get "$ROOT" EASYOA_HTTPS_PORT || true)"; port="${port:-443}"
    if curl -skf --max-time 3 "https://127.0.0.1:$port/healthz" >/dev/null 2>&1; then printf 'PASS HTTPS port and Nginx response\n'
    else printf 'WARN HTTPS port/response unavailable (deployment may be stopped)\n'; fi
    if curl -skf --max-time 3 "https://127.0.0.1:$port/actuator/health" >/dev/null 2>&1; then printf 'PASS API/database health\n'
    else printf 'WARN API/database health not confirmed\n'; fi
  else printf 'FAIL curl unavailable\n'; failed=1; fi
  return "$failed"
}

run_backup() (
  set -Eeuo pipefail
  check_docker_environment; require_command python3 'Python 3 (backup validation)'
  [ -f "$ROOT/.env" ] || DIE 'Deployment .env is required.'
  mkdir -p "$ROOT/backups"; chmod 700 "$ROOT/backups"
  local backup api_was_running=0 completed=0 cert_dir
  backup="$(mktemp -d "$ROOT/backups/easyoa-v$(resolve_version)-$(date -u +%Y%m%dT%H%M%SZ)-XXXXXX")"
  chmod 700 "$backup"
  backup_cleanup() {
    [ "$api_was_running" -eq 0 ] || compose start easyoa-api >/dev/null 2>&1 || ERROR 'API could not be resumed; inspect service status.'
    [ "$completed" -eq 1 ] || ERROR "Backup incomplete; do not restore this directory: $backup"
  }
  trap backup_cleanup EXIT
  if [ -n "$(compose ps --status running -q easyoa-api)" ]; then
    api_was_running=1; INFO 'Pausing API to keep the database and attachment snapshot consistent.'; compose stop easyoa-api
  fi
  umask 077
  compose exec -T postgres sh -c 'exec pg_dump --format=custom --no-owner --no-acl -U "$POSTGRES_USER" -d "$POSTGRES_DB"' > "$backup/database.dump"
  compose run -T --rm --no-deps --entrypoint sh easyoa-api -c 'tar -C "$EASYOA_STORAGE_PATH" -czf - .' > "$backup/storage.tar.gz"
  cp "$ROOT/.env" "$backup/.env"; cp "$ROOT/VERSION" "$backup/VERSION"; printf '1\n' > "$backup/FORMAT"
  cert_dir="$(detect_tls_state "$ROOT")"
  [ ! -f "$cert_dir/easyoa.crt" ] || cp "$cert_dir/easyoa.crt" "$backup/easyoa.crt"
  [ ! -f "$cert_dir/easyoa.key" ] || cp "$cert_dir/easyoa.key" "$backup/easyoa.key"
  python3 "$ROOT/scripts/release/backup.py" write "$backup"
  python3 "$ROOT/scripts/release/backup.py" check "$backup" --current "$ROOT"
  completed=1
  SUCCESS "Backup complete (contains deployment secrets; keep private): $backup"
)

run_restore() {
  [ $# -ge 1 ] && [ $# -le 2 ] || DIE 'Usage: easyoactl restore BACKUP_DIRECTORY [--yes]'
  local backup="$1" answer="" cert_dir
  [ -d "$backup" ] || DIE 'Backup directory does not exist.'
  if [ $# -eq 2 ]; then [ "$2" = --yes ] || DIE 'Unknown restore option.'; answer=RESTORE; fi
  require_command python3 'Python 3 (restore safety checks)'
  python3 "$ROOT/scripts/release/backup.py" check "$backup" --current "$ROOT"
  run_integrity_gate
  WARN 'Restore will replace this deployment database, attachments and configuration. A safety backup is taken first.'
  if [ "$answer" != RESTORE ]; then
    [ -t 0 ] || DIE 'Restore requires an interactive confirmation or explicit --yes.'
    printf 'Type RESTORE to continue: ' >&2; IFS= read -r answer
  fi
  [ "$answer" = RESTORE ] || DIE 'Restore cancelled; no data was changed.'
  run_backup
  compose stop easyoa-api nginx
  compose exec -T postgres sh -c 'exec pg_restore --clean --if-exists --no-owner --no-privileges -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < "$backup/database.dump" || DIE 'Database restore failed; API remains stopped. Recover using the safety backup.'
  compose run -T --rm --no-deps --entrypoint sh easyoa-api -c 'cd "$EASYOA_STORAGE_PATH" && find . -mindepth 1 -maxdepth 1 -exec rm -rf -- {} + && tar -xzf -' < "$backup/storage.tar.gz" || DIE 'Attachment restore failed; API remains stopped. Recover using the safety backup.'
  cp "$backup/.env" "$ROOT/.env"; chmod 600 "$ROOT/.env"
  cert_dir="$(detect_tls_state "$ROOT")"; mkdir -p "$cert_dir"
  [ ! -f "$backup/easyoa.crt" ] || cp "$backup/easyoa.crt" "$cert_dir/easyoa.crt"
  if [ -f "$backup/easyoa.key" ]; then cp "$backup/easyoa.key" "$cert_dir/easyoa.key"; chmod 600 "$cert_dir/easyoa.key"; fi
  run_production
  SUCCESS 'Restore complete; deployment health checks passed.'
}

run_upgrade() {
  [ $# -eq 1 ] || DIE 'Usage: easyoactl upgrade NEW_RELEASE_DIRECTORY (download/extract a signed release first).'
  local target
  [ -d "$1" ] || DIE 'New release directory does not exist.'
  target="$(cd "$1" && pwd)"
  [ "$target" != "$ROOT" ] || DIE 'Upgrade requires a separate new release directory.'
  run_integrity_gate
  local ssl
  ssl="$(find_openssl_ed25519 || true)"; [ -n "$ssl" ] || DIE 'OpenSSL is required.'
  cmp -s <("$ssl" pkey -pubin -in "$ROOT/integrity/release-public-key.pem" -outform DER) <("$ssl" pkey -pubin -in "$target/integrity/release-public-key.pem" -outform DER) || DIE 'New release uses a different trusted key; review a key rotation separately.'
  bash "$ROOT/scripts/integrity/verify-integrity.sh" --root "$target"
  require_command python3 'Python 3 (upgrade version validation)'
  python3 - "$ROOT/VERSION" "$target/VERSION" <<'PY'
import sys
from pathlib import Path
def version(path):
    text=Path(path).read_text().strip()
    if '-' in text: raise SystemExit('Automatic upgrade requires normal release versions.')
    return tuple(map(int,text.split('.')))
if version(sys.argv[2])<=version(sys.argv[1]): raise SystemExit('Upgrade version must be newer; downgrades are not supported.')
PY
  [ ! -e "$target/.env" ] || DIE 'New release already contains .env; refusing to overwrite it.'
  run_backup
  cp "$ROOT/.env" "$target/.env"; chmod 600 "$target/.env"
  local cert_dir relative
  relative="$(env_get "$ROOT" EASYOA_TLS_CERT_DIR || true)"; relative="${relative:-./infra/nginx/certs}"
  case "$relative" in /*) ;; *)
    cert_dir="$(detect_tls_state "$ROOT")"; mkdir -p "$target/$relative"
    [ ! -f "$cert_dir/easyoa.crt" ] || cp "$cert_dir/easyoa.crt" "$target/$relative/easyoa.crt"
    [ ! -f "$cert_dir/easyoa.key" ] || cp "$cert_dir/easyoa.key" "$target/$relative/easyoa.key"
    ;;
  esac
  compose stop
  bash "$target/easyoactl" start || DIE 'Upgrade failed; old deployment is preserved. Review migration state and safety backup before recovery.'
  SUCCESS "Upgrade complete. Use the new deployment directory: $target"
}
