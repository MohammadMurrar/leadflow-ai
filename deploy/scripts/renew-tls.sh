#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
PROJECT_DIR="$(cd -- "$SCRIPT_DIR/../.." && pwd -P)"
COMPOSE_FILE="${COMPOSE_FILE:-$PROJECT_DIR/compose.prod.yaml}"

command -v docker >/dev/null 2>&1 || { echo "docker is required" >&2; exit 1; }

extra_args=()
if [[ "${1:-}" == "--dry-run" ]]; then
  extra_args+=(--dry-run)
elif [[ $# -gt 0 ]]; then
  echo "Usage: $0 [--dry-run]" >&2
  exit 2
fi

docker compose --profile tls -f "$COMPOSE_FILE" run --rm --no-deps certbot \
  renew --webroot --webroot-path /var/www/certbot "${extra_args[@]}"

if [[ ${#extra_args[@]} -eq 0 ]]; then
  docker compose -f "$COMPOSE_FILE" exec -T web nginx -s reload
  echo "Certificate renewal completed and Nginx reloaded."
else
  echo "Certificate renewal dry run completed; Nginx was not reloaded."
fi
