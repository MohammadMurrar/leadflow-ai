#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
PROJECT_DIR="$(cd -- "$SCRIPT_DIR/../.." && pwd -P)"
COMPOSE_FILE="${COMPOSE_FILE:-$PROJECT_DIR/compose.prod.yaml}"

command -v docker >/dev/null 2>&1 || { echo "docker is required" >&2; exit 1; }
: "${APP_DOMAIN:?APP_DOMAIN is required}"
: "${ACME_EMAIL:?ACME_EMAIL is required}"

if [[ ! "$APP_DOMAIN" =~ ^[A-Za-z0-9.-]+$ ]] || [[ "$APP_DOMAIN" != *.* ]]; then
  echo "APP_DOMAIN is not a valid public hostname" >&2
  exit 1
fi

echo "Stopping the web service before standalone ACME issuance."
docker compose -f "$COMPOSE_FILE" stop web >/dev/null 2>&1 || true

docker compose --profile tls -f "$COMPOSE_FILE" run --rm --no-deps -p 80:80 certbot \
  certonly --standalone --non-interactive --agree-tos \
  --email "$ACME_EMAIL" --domain "$APP_DOMAIN"

bash "$SCRIPT_DIR/secure-tls-permissions.sh"

echo "Certificate issued successfully; starting the HTTPS web service."
docker compose -f "$COMPOSE_FILE" up -d web
