# LeadFlow AI

LeadFlow AI is a full-stack lead operations platform for service businesses. It combines a secured Spring Boot API, React operations workspace, MySQL persistence, and an attempt-aware n8n/Gemini qualification workflow.

## Current capabilities

- ADMIN authentication with Argon2id passwords and MySQL-backed Spring Session
- SPA CSRF protection and structured API errors
- Public hosted inquiry page at `/inquiry` with active-service configuration and secure lead submission
- Duplicate and honeypot suppression for public inquiries
- Lead creation, search, lifecycle management, and optimistic concurrency
- Service catalog and immutable requested-service snapshots
- AI Qualification workspace with durable attempts, transactional outbox delivery, correlation, timeout handling, and safe retry
- Database-backed notifications
- Dashboard and date-range analytics
- Workspace settings
- Self-hosted n8n workflow with protected start, success, and failure callbacks
- Protected administrative dashboard

## Architecture

```text
Anonymous browser --HTTPS--> Nginx --> /inquiry
                                      --> public configuration/submission API
                                      --> existing lead creation transaction
                                      --> notification + qualification attempt + transactional outbox
                                      --> n8n --HTTPS--> Gemini
                                      --> protected administrative dashboard
```

Production uses same-origin `/api/v1` routing. The backend, MySQL, Actuator, n8n webhook, and n8n editor are not public. The n8n editor is reached through an SSH tunnel.
Public inquiries enter the same transactional lead creation and qualification pipeline used by the application; outbox delivery connects that pipeline to the n8n/Gemini workflow.

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

The public inquiry page is available at `/inquiry`. It requires a running backend, a database with Flyway complete, available workspace settings, and at least one active service. Qualification dispatch may remain disabled for UI-only testing.

## API areas

- `/api/v1/auth` — CSRF, login, current session, and logout
- `GET /api/v1/public/inquiry-config` — public workspace name, description, and active-service options
- `POST /api/v1/public/leads` — CSRF-protected public inquiry submission
- `/api/v1/leads` — lead creation and management
- `/api/v1/notifications` — notification inbox
- `/api/v1/dashboard` and `/api/v1/analytics` — operational reporting
- `/api/v1/services` — service catalog
- `/api/v1/settings` — workspace settings
- `/api/v1/automation` — private automation callbacks protected by `X-Automation-Key`
- `/actuator/health/liveness` and `/actuator/health/readiness` — private container health only

The two method-and-path combinations above are the only anonymously authorized public-inquiry API routes. Administrative APIs remain ADMIN-only. Automation callbacks remain protected by `X-Automation-Key` on the private application network and are blocked at the public Nginx edge.

## Production deployment

See [deploy/DEPLOYMENT.md](deploy/DEPLOYMENT.md) for server preparation, required variables, TLS issuance, administrator bootstrap, n8n activation, verification, backups, restore drills, upgrades, and rollback.

Production deployment is blocked until an encrypted off-VPS backup destination is configured and a successful isolated restore drill is recorded.
