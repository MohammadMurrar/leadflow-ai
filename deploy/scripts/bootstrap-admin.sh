#!/usr/bin/env bash
set -Eeuo pipefail

umask 077
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
PROJECT_DIR="$(cd -- "$SCRIPT_DIR/../.." && pwd -P)"
COMPOSE_FILE="${COMPOSE_FILE:-$PROJECT_DIR/compose.prod.yaml}"

command -v docker >/dev/null 2>&1 || { echo "docker is required" >&2; exit 1; }
: "${ADMIN_BOOTSTRAP_EMAIL:?Set ADMIN_BOOTSTRAP_EMAIL for the new administrator}"
: "${ADMIN_BOOTSTRAP_DISPLAY_NAME:?Set ADMIN_BOOTSTRAP_DISPLAY_NAME for the new administrator}"

read -r -s -p "Administrator password (12-256 characters): " ADMIN_BOOTSTRAP_PASSWORD
printf '\n'
cleanup() { unset ADMIN_BOOTSTRAP_PASSWORD; }
trap cleanup EXIT INT TERM

password_length=${#ADMIN_BOOTSTRAP_PASSWORD}
if (( password_length < 12 || password_length > 256 )); then
  echo "Password must contain between 12 and 256 characters" >&2
  exit 1
fi

export ADMIN_BOOTSTRAP_PASSWORD
docker compose -f "$COMPOSE_FILE" run --rm --no-deps \
  -e SPRING_MAIN_WEB_APPLICATION_TYPE=none \
  -e QUALIFICATION_DISPATCHER_ENABLED=false \
  -e ADMIN_BOOTSTRAP_ENABLED=true \
  -e ADMIN_BOOTSTRAP_EMAIL \
  -e ADMIN_BOOTSTRAP_DISPLAY_NAME \
  -e ADMIN_BOOTSTRAP_PASSWORD \
  backend

echo "Administrator bootstrap completed. The one-shot container has exited."
