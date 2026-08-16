#!/usr/bin/env bash
set -Eeuo pipefail

umask 077
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
PROJECT_DIR="$(cd -- "$SCRIPT_DIR/../.." && pwd -P)"
COMPOSE_FILE="${COMPOSE_FILE:-$PROJECT_DIR/compose.prod.yaml}"
BACKUP_DIR="${BACKUP_DIR:-$PROJECT_DIR/deploy/backups}"
RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-14}"

command -v docker >/dev/null 2>&1 || { echo "docker is required" >&2; exit 1; }
command -v age >/dev/null 2>&1 || { echo "age is required for encrypted backups" >&2; exit 1; }
command -v sha256sum >/dev/null 2>&1 || { echo "sha256sum is required" >&2; exit 1; }
: "${AGE_RECIPIENT:?AGE_RECIPIENT is required and must be an age public recipient}"
: "${MYSQL_DATABASE:?MYSQL_DATABASE is required}"

if [[ ! "$RETENTION_DAYS" =~ ^[0-9]+$ ]] || (( RETENTION_DAYS < 1 )); then
  echo "BACKUP_RETENTION_DAYS must be a positive integer" >&2
  exit 1
fi

mkdir -p -- "$BACKUP_DIR"
BACKUP_DIR="$(cd -- "$BACKUP_DIR" && pwd -P)"
if [[ "$BACKUP_DIR" == "/" || "$BACKUP_DIR" == "$PROJECT_DIR" ]]; then
  echo "Refusing unsafe backup directory: $BACKUP_DIR" >&2
  exit 1
fi

work_dir="$(mktemp -d "$BACKUP_DIR/.work.XXXXXX")"
n8n_was_running=false
cleanup() {
  rm -rf -- "$work_dir"
  if [[ "$n8n_was_running" == true ]]; then
    docker compose -f "$COMPOSE_FILE" start n8n >/dev/null || true
  fi
}
trap cleanup EXIT INT TERM

timestamp="$(date -u +'%Y%m%dT%H%M%SZ')"
archive="$BACKUP_DIR/leadflow-$timestamp.tar.gz"
encrypted="$archive.age"
checksum="$encrypted.sha256"

echo "Creating a transactionally consistent MySQL dump."
docker compose -f "$COMPOSE_FILE" exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump --protocol=tcp -h 127.0.0.1 -u root --single-transaction --routines --triggers --events --hex-blob --add-drop-database --databases "$MYSQL_DATABASE"' \
  | gzip -9 > "$work_dir/mysql.sql.gz"

if docker compose -f "$COMPOSE_FILE" ps --status running --services | grep -qx n8n; then
  n8n_was_running=true
  echo "Stopping n8n briefly for a consistent persistent-data archive."
  docker compose -f "$COMPOSE_FILE" stop -t 60 n8n
fi

docker compose -f "$COMPOSE_FILE" run --rm --no-deps -T --entrypoint tar n8n \
  -czf - -C /home/node .n8n > "$work_dir/n8n-data.tar.gz"

printf '%s\n' \
  "created_at_utc=$timestamp" \
  "mysql_database=$MYSQL_DATABASE" \
  "n8n_encryption_key_included=false" \
  "offsite_copy_required=true" > "$work_dir/manifest.txt"

tar -czf "$archive" -C "$work_dir" mysql.sql.gz n8n-data.tar.gz manifest.txt
age -r "$AGE_RECIPIENT" -o "$encrypted" "$archive"
rm -f -- "$archive"
(cd -- "$BACKUP_DIR" && sha256sum "$(basename -- "$encrypted")" > "$(basename -- "$checksum")")

while IFS= read -r -d '' candidate; do
  resolved="$(readlink -f -- "$candidate")"
  if [[ "$(dirname -- "$resolved")" != "$BACKUP_DIR" ]]; then
    echo "Refusing to delete path outside backup directory: $resolved" >&2
    exit 1
  fi
  rm -f -- "$resolved" "$resolved.sha256"
done < <(find "$BACKUP_DIR" -maxdepth 1 -type f -name 'leadflow-*.tar.gz.age' -mtime "+$RETENTION_DAYS" -print0)

echo "Encrypted backup staged: $encrypted"
echo "Checksum: $checksum"
echo "WARNING: local staging is not a production backup until both files are copied to an encrypted off-VPS destination."
echo "Back up N8N_ENCRYPTION_KEY separately in the approved secret store."
