# LeadFlow AI production deployment

This runbook describes the approved single-VPS foundation. It does not provision a server, alter DNS, or create secrets.

> **Deployment gate:** do not accept production traffic until an encrypted off-VPS backup destination is configured and a full isolated restore drill succeeds. A backup left on the VPS is not a production backup.

## Architecture and pinned images

Use one Linux VPS with at least 2 vCPU, 4 GB RAM, adequate SSD storage, and swap for safe upgrades.

| Service | Pinned image | Exposure |
|---|---|---|
| web | project-built `nginxinc/nginx-unprivileged:1.30.4-alpine3.24` with current Alpine security updates | host ports 80 and 443 |
| backend | Maven 3.9.9/Temurin 21 build; Temurin 21.0.12+8 JRE Alpine 3.24 runtime with current Alpine security updates | private |
| mysql | `mysql:8.4.11` | private |
| n8n | `n8nio/n8n:2.37.7` (Docker Hardened Images Alpine 3.24 base) | private webhook; editor at `127.0.0.1:5678` |
| certbot | `certbot/certbot:v5.2.2` | one-shot/profile service |

Record the resolved image digests after the first verified build. Review release notes before changing any pin.

The edge, application, data, and restricted-egress networks are separated. MySQL joins only data. Backend joins application, data, and egress so it can reach the approved SMTP endpoint. n8n joins application and egress for the approved AI provider. Web joins edge and application. Enforce destination-level egress allowlists in the host firewall or deployment platform because a Compose bridge cannot restrict destinations by hostname. Public Nginx returns 404 for `/api/v1/automation` and `/actuator` before its general API proxy.

## Host preparation

Install Docker Engine and the Compose plugin from their supported repositories. Also install `age`, `sha256sum`, `tar`, `gzip`, and OpenSSH tools. Create a non-root deployment account and root-controlled backup staging directory.

Configure DNS A/AAAA records, UTC time synchronization, automatic security updates, disk monitoring, and a firewall. Permit application ports 80 and 443. Restrict SSH to an administrator IP or VPN. Never open 3306, 5678, 8080, or Actuator publicly.

Ensure `deploy/mysql/leadflow.cnf` is not group- or world-writable on Linux (`chmod 644 deploy/mysql/leadflow.cnf`). MySQL ignores world-writable option files. Compose also supplies the same deliberate server options directly so container behavior remains explicit.

## Environment and secrets

Create the production environment with owner-only permissions:

```bash
install -m 600 .env.example .env
```

Replace every placeholder with independent random values. Required names are:

- `APP_DOMAIN`
- `LEADFLOW_PUBLIC_FRONTEND_URL`, the public HTTPS frontend origin only (for example,
  `https://app.your-domain.com`). It must not contain a path, query, fragment, secret, or reset
  token. TLS remains the reverse proxy/load balancer's responsibility.
- `ACME_EMAIL`
- `MYSQL_DATABASE`
- `DB_USERNAME`
- `DB_PASSWORD`
- `MYSQL_ROOT_PASSWORD`
- `AUTOMATION_API_KEY`
- `N8N_ENCRYPTION_KEY`
- `PASSWORD_RESET_ACTIVE_KEY_VERSION` and `PASSWORD_RESET_HMAC_KEYS`
- `EMAIL_SMTP_HOST`, `EMAIL_SMTP_PORT`, `EMAIL_SMTP_USERNAME`, and `EMAIL_SMTP_PASSWORD`
- `EMAIL_SENDER_ADDRESS`, plus the optional display name and reply-to address

Never store the administrator password, Gemini credential, certificate private key, session ID, CSRF token, or real workflow credential in Git. Back up `N8N_ENCRYPTION_KEY` separately in the approved secret store; restored n8n credentials cannot be decrypted without it.

Production builds the frontend with `/api/v1`, requires secure cookies, trusts framework-parsed forwarded headers, disables the legacy callback, enables Step 17 safe retry, and uses private Docker DNS.

## Build and configuration validation

```bash
docker compose -f compose.prod.yaml config --quiet
docker compose -f compose.prod.yaml build backend web
```

Do not publish or copy a fully rendered Compose configuration because it contains secrets. Confirm only web publishes 80/443 and n8n publishes 5678 to loopback. Backend and MySQL must have no `ports` entry.

For local manual testing only, set `LEADFLOW_PUBLIC_FRONTEND_URL=http://localhost:4173` together
with `LEADFLOW_PUBLIC_FRONTEND_ALLOW_HTTP_LOOPBACK=true` in the backend process environment.
Non-loopback HTTP is rejected. Changing either setting requires a controlled backend restart.

## Database, Flyway, and startup order

Initially keep dispatch disabled:

```bash
export QUALIFICATION_DISPATCHER_ENABLED=false
docker compose -f compose.prod.yaml up -d mysql n8n
docker compose -f compose.prod.yaml up -d backend
docker compose -f compose.prod.yaml ps
```

Flyway applies V1–V14 before Hibernate validates the schema. Use one backend instance for migrations. Inspect logs without dumping environment values. Verify MySQL version, UTC, charset, and collation with an authenticated query from inside the MySQL container.

Take and transfer an encrypted baseline backup before future migrations or image upgrades.

## One-shot administrator bootstrap

This is only for a fresh database. The script runs a non-web backend container, disables dispatch, prompts silently, and clears the password variable:

```bash
export ADMIN_BOOTSTRAP_EMAIL='administrator@example.com'
export ADMIN_BOOTSTRAP_DISPLAY_NAME='LeadFlow Administrator'
bash ./deploy/scripts/bootstrap-admin.sh
unset ADMIN_BOOTSTRAP_EMAIL ADMIN_BOOTSTRAP_DISPLAY_NAME
```

The application refuses to silently update an administrator or create a second one.

## Private n8n editor and workflow activation

Open an SSH tunnel from the administrator workstation:

```bash
ssh -L 5678:127.0.0.1:5678 deploy@your-vps
```

Open `http://localhost:5678`, complete n8n owner setup, and import `automation/LeadFlow AI — Lead Qualification.json`. Bind:

- `LeadFlow Automation Key` to header `X-Automation-Key`, matching `AUTOMATION_API_KEY`.
- `Google Gemini` to the approved Gemini credential.

Keep the editor owner-only and reachable solely through loopback plus the
administrator tunnel/VPN. Do not add arbitrary n8n users, unreviewed workflows,
SSH functionality, or email nodes during the controlled pilot without a new
security and dependency review.

The repository workflow uses the fixed private URL `http://backend:8080/api/v1`; it does not read process environment values from nodes. Test private start, success, and failure callbacks before activating the workflow. Run `n8n audit` after credential binding and review credential, node, database, webhook, and instance findings.

Successful execution payloads are not retained. Failed executions are bounded by age and count. Review privacy needs before increasing those limits.

After activation:

```bash
export QUALIFICATION_DISPATCHER_ENABLED=true
docker compose -f compose.prod.yaml up -d --force-recreate backend
unset QUALIFICATION_DISPATCHER_ENABLED
```

## Initial TLS issuance

Confirm DNS and that port 80 is free. Nginx is not started with a missing certificate. The script stops web, runs standalone Certbot on port 80, then starts HTTPS only after issuance succeeds:

```bash
export APP_DOMAIN ACME_EMAIL
bash ./deploy/scripts/init-tls.sh
```

Verify HTTP redirect, HTTPS, certificate chain, static headers, and these non-revealing rejections:

```bash
curl -i "https://$APP_DOMAIN/actuator/health/readiness"
curl -i "https://$APP_DOMAIN/api/v1/automation/"
```

Both public paths must return 404.

The issuance and renewal scripts restrict certificate directories/files to
root and the unprivileged Nginx container group (GID 101). Do not make private
keys world-readable to work around a container permission error.

## Authentication and proxy checks

Using a fresh browser profile:

1. Load every direct SPA route and test Back/Forward.
2. Confirm normal API calls are same-origin and do not depend on CORS.
3. Obtain CSRF, log in, perform a protected mutation, and log out.
4. Confirm `LEADFLOW_SESSION` is Secure, HttpOnly, SameSite=Lax, path `/`, with no Domain.
5. Confirm `XSRF-TOKEN` is Secure, SameSite=Lax, path `/`, and readable by the SPA as designed.
6. Verify session restoration after backend restart and structured 401/403 responses.
7. Verify an automation key cannot access administrative APIs and a browser session cannot replace the automation key.
8. Verify current pages, searches, polling, notifications, analytics, and details remain functional with no console errors.

Nginx separately rate-limits POST `/api/v1/auth/login` (five requests per minute, burst five) and password-reset initiation (three requests per minute, burst three), keyed by the direct peer address; excessive requests return 429. Confirmation is not rate-limited so a valid token is not consumed by shared-IP initiation abuse. The edge does not trust arbitrary `X-Forwarded-For`. If a CDN is later added, configure real-IP handling only for that provider's verified ranges.

## Public inquiry surface and CSRF

The public surface is limited to:

- `/inquiry`
- `/inquiry/{workspaceSlug}`
- `GET /api/v1/public/inquiry-config`
- `POST /api/v1/public/leads`
- `GET /api/v1/public/workspaces/{workspaceSlug}/inquiry-config`
- `POST /api/v1/public/workspaces/{workspaceSlug}/leads`

No other `/api/v1/public/**` route is anonymously authorized. Administrative APIs remain ADMIN-only. Automation callbacks remain API-key protected on the private application network and return 404 at the public Nginx edge.

The same-origin browser submission sequence is:

1. Load `/inquiry` and the public configuration.
2. Obtain a CSRF token through `GET /api/v1/auth/csrf`.
3. Retain the corresponding CSRF cookie.
4. Send the returned token in the `X-XSRF-TOKEN` header.
5. Submit JSON to `/api/v1/public/leads`.

A missing or invalid token returns 403. CSRF is same-origin protection, not bot protection. Do not add a CSRF exemption for public inquiry submission without a separate security review.

Nginx applies dedicated controls at the exact `location = /api/v1/public/leads`. Only POST generates a public-inquiry rate-limit key. The key is the direct peer address, the average rate is `5r/m`, and `burst=5 nodelay` permits a bounded burst; excess requests return 429. Request bodies are limited to `16k`; excess bodies return 413. Edge responses include `Cache-Control: no-store`. Other API routes retain the global 1 MB body limit, and login retains its separate rate-limit zone.

The rate limiter does not trust arbitrary `X-Forwarded-For`. If a CDN or load balancer is introduced, configure real-IP handling only for verified provider address ranges before using a reconstructed client IP as the rate-limit key.

Do not log public request bodies, lead PII, CSRF tokens, or cookies. Nginx access logs may contain the peer IP, route, status, user agent, and timing; apply an approved retention policy. Monitor aggregate status and volume data rather than form contents.

## Public inquiry verification checklist

Use controlled, non-customer test data. Prefer invalid or otherwise non-persisting requests when checking edge controls, and do not create uncontrolled real leads while verifying rate limits.

- [ ] Anonymous `/inquiry` renders without initializing the administrative session workflow.
- [ ] Configuration exposes only workspace name, optional description, and active service IDs/names.
- [ ] A valid-CSRF submission returns 202 and a generic acknowledgement.
- [ ] Missing and invalid CSRF tokens return 403.
- [ ] Duplicate normalized email and filled honeypot submissions return the generic acknowledgement without additional lead, notification, attempt, or outbox records.
- [ ] Unknown and inactive service IDs return indistinguishable safe 400 responses.
- [ ] Unsupported media returns the native safe 415 response.
- [ ] A missing JSON body returns a safe structured 400 response.
- [ ] A body over 16 KB returns 413 at the exact public submission location.
- [ ] Controlled excess POST requests return 429 without creating uncontrolled leads.
- [ ] Neighboring public paths and method combinations remain 401.
- [ ] `/api/v1/automation` and `/actuator` remain 404 at the public edge.
- [ ] Administrative routes remain authentication-, role-, and CSRF-protected.
- [ ] One controlled end-to-end inquiry produces the lead, notification, qualification attempt, transactional outbox delivery, n8n execution, and correlated qualification result.

Do not use real customer emails or other PII in verification requests.

## Health, restart, and logs

Inspect private health from inside the stack:

```bash
docker compose -f compose.prod.yaml ps
docker compose -f compose.prod.yaml exec -T backend wget -q -O - http://127.0.0.1:8080/actuator/health/liveness
docker compose -f compose.prod.yaml exec -T backend wget -q -O - http://127.0.0.1:8080/actuator/health/readiness
docker compose -f compose.prod.yaml exec -T n8n node -e "fetch('http://127.0.0.1:5678/healthz/readiness').then(r=>process.exit(r.ok?0:1))"
```

Readiness includes MySQL. Liveness excludes n8n and Gemini. No other Actuator endpoint is exposed. Stop and restart each service to confirm volume persistence and restart policy. Send SIGTERM to backend and confirm completion within its 45-second Compose grace period.

Docker JSON logs rotate at 10 MB with five files. Keep timestamps in UTC. Never enable request-body, SQL-parameter, cookie, session, CSRF, automation-key, Gemini prompt/response, or raw lead-payload logging. Use attempt UUIDs for safe correlation.

Monitor aggregate public inquiry volume and rates of 400, 403, 413, 415, 429, and 5xx responses. Also monitor disk space, container health, oldest pending transactional outbox work, failed/timed-out qualification attempts, certificate expiry, backup age, and off-host transfer success.

## Encrypted backup and off-host handoff

Configure an age public recipient and explicit staging directory:

```bash
export AGE_RECIPIENT='age1...public-recipient...'
export BACKUP_DIR='/srv/leadflow/backups'
export BACKUP_RETENTION_DAYS=14
bash ./deploy/scripts/backup.sh
```

The script creates a transactionally consistent MySQL dump, briefly stops n8n for a consistent data archive, encrypts the combined archive, creates a SHA-256 checksum, and prunes only validated matching files inside the resolved backup directory. It deliberately excludes `N8N_ENCRYPTION_KEY`.

Transfer both `.age` and `.sha256` files to the approved encrypted off-VPS destination and verify the checksum there. Until that succeeds, deployment remains blocked.

## Restore drill

Restore first into an isolated Compose project/host. The script creates a safety backup before destructive work, so configure both encryption directions:

```bash
export AGE_RECIPIENT='age1...public-recipient...'
export AGE_IDENTITY_FILE='/root/secure/leadflow-backup-key.txt'
export BACKUP_DIR='/srv/leadflow/backups'
bash ./deploy/scripts/restore.sh /absolute/path/to/leadflow-TIMESTAMP.tar.gz.age
```

The script verifies the checksum, requires typing the exact database name, creates a safety backup, stops backend and n8n, restores both data sets, and intentionally leaves operational services stopped.

Before resuming:

1. Preserve the failed pre-restore state and safety backup off-host.
2. Validate `flyway_schema_history` through V14.
3. Start one backend with dispatch disabled and confirm Flyway, Hibernate, readiness, users, sessions, attempts, and outbox.
4. Start n8n and verify credential decryption and workflow state.
5. Manually re-enable dispatch only after consistency checks.

Never run restore from normal startup or unattended automation.

## Certificate renewal

```bash
bash ./deploy/scripts/renew-tls.sh --dry-run
bash ./deploy/scripts/renew-tls.sh
```

Schedule the real script with a root-controlled systemd timer or cron. Nginx reloads only after successful real renewal. Monitor expiry separately.

## Automation smoke test

Create at most one unique controlled lead. Verify one lead, Attempt 1, outbox delivery, correlated n8n execution, accepted start before Gemini, one terminal callback, one terminal state, and no duplicate notification. Verify Step 17 retry remains attempt-aware and safe. Browser requests to callback paths must return 404; all automation traffic uses private Docker DNS.

## Upgrade and rollback

Before upgrading, review release notes, produce and transfer an encrypted backup, record tags/digests, pass CI, and stop dispatch during database-sensitive work. Start one backend for Flyway, then verify Hibernate, authentication, health, and automation.

**Application-image rollback alone may be unsafe after a non-backward-compatible Flyway migration.** Migrations are forward-only. Safe rollback may require stopping backend/n8n, preserving the failed database, restoring the pre-deployment database and n8n backup, starting the prior image with dispatch disabled, validating everything, and only then re-enabling dispatch.

## Known limitations

- One VPS is a single failure domain.
- No Redis, queue mode, Kubernetes, or automatic failover is included.
- Actual TLS/security verification requires real DNS and a VPS.
- The off-VPS provider remains undecided and is a deployment blocker.
- Public and administrative routes are lazy-loaded separately; continue monitoring production asset size during frontend changes.
- The public inquiry is one hosted same-origin page; iframe embedding and cross-origin integration APIs are not supported.
- CAPTCHA is not included initially.
- Public inquiry rate limiting is in-memory state within one Nginx instance.
- Users behind the same NAT share one IP quota, while distributed bots can use multiple IP addresses.
- Public workspace slugs deliberately expose only active-workspace branding and service options; owner provisioning remains offline and controlled.
- CSP permits inline style attributes because the charting/layout libraries generate runtime style attributes. Inline scripts, inline style elements, and `unsafe-eval` remain prohibited.
- Actual certificate-chain behavior must be validated again with the deployment platform's real certificate before traffic is accepted.
