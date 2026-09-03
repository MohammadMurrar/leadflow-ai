#!/usr/bin/env bash
set -Eeuo pipefail

umask 077
if [[ $# -ne 1 ]]; then
  echo "Usage: $0 /absolute/path/to/leadflow-TIMESTAMP.tar.gz.age" >&2
  exit 2
fi

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
PROJECT_DIR="$(cd -- "$SCRIPT_DIR/../.." && pwd -P)"
COMPOSE_FILE="${COMPOSE_FILE:-$PROJECT_DIR/compose.prod.yaml}"
[[ -f "$1" ]] || { echo "Encrypted backup does not exist: $1" >&2; exit 1; }
encrypted="$(readlink -f -- "$1")"
checksum="$encrypted.sha256"
: "${AGE_IDENTITY_FILE:?AGE_IDENTITY_FILE must identify a readable age private identity file}"
: "${MYSQL_DATABASE:?MYSQL_DATABASE is required}"

command -v docker >/dev/null 2>&1 || { echo "docker is required" >&2; exit 1; }
command -v age >/dev/null 2>&1 || { echo "age is required" >&2; exit 1; }
command -v sha256sum >/dev/null 2>&1 || { echo "sha256sum is required" >&2; exit 1; }
[[ -f "$encrypted" ]] || { echo "Encrypted backup does not exist" >&2; exit 1; }
[[ -f "$checksum" ]] || { echo "Checksum file does not exist" >&2; exit 1; }
[[ -r "$AGE_IDENTITY_FILE" ]] || { echo "age identity file is not readable" >&2; exit 1; }
[[ "$MYSQL_DATABASE" =~ ^[A-Za-z0-9_]+$ ]] || { echo "Unsafe database name" >&2; exit 1; }

(cd -- "$(dirname -- "$encrypted")" && sha256sum -c "$(basename -- "$checksum")")

read -r -p "Type RESTORE $MYSQL_DATABASE to replace that database and n8n data: " confirmation
if [[ "$confirmation" != "RESTORE $MYSQL_DATABASE" ]]; then
  echo "Restore cancelled" >&2
  exit 1
fi

echo "Creating a safety backup of the current state before restoration."
bash "$SCRIPT_DIR/backup.sh"

work_dir="$(mktemp -d "${TMPDIR:-/tmp}/leadflow-restore.XXXXXX")"
cleanup() { rm -rf -- "$work_dir"; }
trap cleanup EXIT INT TERM

age -d -i "$AGE_IDENTITY_FILE" -o "$work_dir/backup.tar.gz" "$encrypted"
tar -xzf "$work_dir/backup.tar.gz" -C "$work_dir"
[[ -s "$work_dir/mysql.sql.gz" ]] || { echo "Backup has no MySQL dump" >&2; exit 1; }
[[ -s "$work_dir/n8n-data.tar.gz" ]] || { echo "Backup has no n8n archive" >&2; exit 1; }

echo "Stopping backend and n8n. They will remain stopped after restoration."
docker compose -f "$COMPOSE_FILE" stop -t 60 backend n8n

gzip -dc "$work_dir/mysql.sql.gz" | docker compose -f "$COMPOSE_FILE" exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --protocol=tcp -h 127.0.0.1 -u root'

gzip -dc "$work_dir/n8n-data.tar.gz" | docker compose -f "$COMPOSE_FILE" run --rm --no-deps -T --entrypoint sh n8n -c \
  'find /home/node/.n8n -mindepth 1 -maxdepth 1 -exec rm -rf -- {} + && tar -xzf - -C /home/node'

echo "Restore completed with backend and n8n intentionally stopped."
echo "Validate Flyway history and Hibernate schema before manually re-enabling the dispatcher."
