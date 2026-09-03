#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
PROJECT_DIR="$(cd -- "$SCRIPT_DIR/../.." && pwd -P)"
COMPOSE_FILE="${COMPOSE_FILE:-$PROJECT_DIR/compose.prod.yaml}"

command -v docker >/dev/null 2>&1 || { echo "docker is required" >&2; exit 1; }

# nginx-unprivileged runs as UID/GID 101. Certbot creates private material for
# root; grant only that container group read/traverse access, never world access.
docker compose --profile tls -f "$COMPOSE_FILE" run --rm --no-deps \
  --entrypoint sh certbot -c '
    chgrp 101 /etc/letsencrypt
    chmod 750 /etc/letsencrypt
    for certificate_tree in /etc/letsencrypt/live /etc/letsencrypt/archive; do
      if [ -d "$certificate_tree" ]; then
        find "$certificate_tree" -type d -exec chgrp 101 {} + -exec chmod 750 {} +
        find "$certificate_tree" -type f -exec chgrp 101 {} + -exec chmod 640 {} +
      fi
    done
  '

echo "TLS certificate permissions prepared for the unprivileged web service."
