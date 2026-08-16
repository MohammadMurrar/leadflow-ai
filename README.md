# LeadFlow AI

LeadFlow AI is a full-stack lead operations platform for service businesses. It combines a secured Spring Boot API, React operations workspace, MySQL persistence, and an attempt-aware n8n/Gemini qualification workflow.

## Current capabilities

- ADMIN authentication with Argon2id passwords and MySQL-backed Spring Session
- SPA CSRF protection and structured API errors
- Lead creation, search, lifecycle management, and optimistic concurrency
- Service catalog and immutable requested-service snapshots
- AI Qualification workspace with durable attempts, transactional outbox delivery, correlation, timeout handling, and safe retry
- Database-backed notifications
- Dashboard and date-range analytics
- Workspace settings
- Self-hosted n8n workflow with protected start, success, and failure callbacks

## Architecture

```text
Browser --HTTPS--> Nginx --private HTTP--> Spring Boot --JDBC--> MySQL 8.4
                                      |                    (sessions + app data)
                                      +--private webhook--> n8n --HTTPS--> Gemini
                                      ^                    |
                                      +--protected callbacks+
```

Production uses same-origin `/api/v1` routing. The backend, MySQL, Actuator, n8n webhook, and n8n editor are not public. The n8n editor is reached through an SSH tunnel.

## Repository structure

```text
backend/          Spring Boot 3.5 / Java 21 API
frontend/         React / TypeScript / Vite operations UI
automation/       Importable n8n qualification workflow
deploy/           Nginx, MySQL, TLS, backup, restore, and runbook assets
compose.yaml      Local MySQL development service
compose.prod.yaml Single-VPS production topology
```

## Local development

Prerequisites: Java 21, Maven 3.9+, Node 22+, and Docker Desktop.

```bash
docker compose up -d mysql
cd backend
mvn spring-boot:run
```

In a second terminal:

```bash
cd frontend
npm ci
npm run dev
```

Local defaults are development-only. Override the automation key and database credentials outside a local workstation.

## API areas

- `/api/v1/auth` — CSRF, login, current session, and logout
- `/api/v1/leads` — lead creation and management
- `/api/v1/notifications` — notification inbox
- `/api/v1/dashboard` and `/api/v1/analytics` — operational reporting
- `/api/v1/services` — service catalog
- `/api/v1/settings` — workspace settings
- `/api/v1/automation` — private automation callbacks protected by `X-Automation-Key`
- `/actuator/health/liveness` and `/actuator/health/readiness` — private container health only

## Production deployment

See [deploy/DEPLOYMENT.md](deploy/DEPLOYMENT.md) for server preparation, required variables, TLS issuance, administrator bootstrap, n8n activation, verification, backups, restore drills, upgrades, and rollback.

Production deployment is blocked until an encrypted off-VPS backup destination is configured and a successful isolated restore drill is recorded.
