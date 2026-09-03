# LeadFlow controlled-pilot threat model

## Scope and assurance boundary

This threat model covers the LeadFlow web application, API, workers, deployment
configuration, and supporting MySQL, SMTP, and qualification integrations for a
controlled pilot of 5–10 owner-provisioned workspaces. It is an internal
engineering assessment, not an independent penetration test or compliance
certification.

## Assets and actors

Protected assets are ADMIN credentials, session cookies, password-reset tokens,
automation credentials, workspace ownership, lead/customer PII, workspace
settings and recipients, qualification results, email and qualification
outboxes, database and SMTP credentials, deployment secrets, logs, and backups.

Relevant actors are anonymous visitors, legitimate and malicious workspace
ADMINs, external attackers, credential-stuffing bots, compromised automation or
SMTP providers, platform operators, and background workers.

## Trust boundaries

1. Browser to the TLS-terminating reverse proxy.
2. Reverse proxy to the private Spring Boot service.
3. Spring Boot to the private MySQL service.
4. Spring Boot to SMTP and the qualification endpoint.
5. Authenticated callback to stored lead, attempt, and outbox state.
6. Public workspace slug to active-workspace resolution.
7. Authenticated session to trusted `CurrentWorkspace` resolution.
8. Worker claim to tenant-owned outbox lifecycle.
9. Deployment environment to secrets and runtime configuration.

## Threat register

| Threat | Existing control/evidence | Gap or residual risk | Severity | Correction status |
|---|---|---|---|---|
| Broken tenant isolation / IDOR | Workspace-scoped repositories and services, trusted session workspace, ownership FKs, two-workspace tests, production-boundary matrix, direct-DB audit | Repeat the adversarial matrix for each release | Critical | Verified for controlled pilot |
| Authentication bypass / credential stuffing | Spring Security, Argon2, generic errors, edge login/reset limiting, authentication matrix | Central alert delivery remains operational | High | Verified for controlled pilot |
| Session fixation or theft | Session-ID change on login, JDBC sessions, logout invalidation, reset revocation, secure production cookie verified over disposable TLS | Real-certificate browser check remains a pre-client gate | High | Verified for controlled pilot |
| CSRF / CORS abuse | Cookie/header CSRF, explicit callback exclusions, exact allowlisted origin, hostile-origin/method tests | Recheck if a CDN or new origin is introduced | High | Verified for controlled pilot |
| Password-reset poisoning or token theft | Random tokens, hash-at-rest, expiry/single-use, configured public base URL, concurrent-use and origin-poisoning tests | Maintain key rotation and session-revocation checks | Critical | Verified for controlled pilot |
| Stored/reflected/DOM XSS | React text rendering, DTO validation, strict CSP, hostile-content component/API tests | Independent browser penetration testing remains deferred | High | Verified for controlled pilot |
| SQL/JPQL injection | Parameterized repositories, bounded DTOs, static query inventory, adversarial payload tests | Re-audit if dynamic queries are introduced | High | Verified for controlled pilot |
| Host/forwarded-header poisoning | Configured public URL, fixed proxy headers, hostile Host and forwarded-header execution | Reconfigure only for verified proxy/CDN ranges | High | Verified for controlled pilot |
| Public inquiry spam / denial of service | CSRF, honeypot, duplicate window, 16 KB edge limit, per-IP limiting | Managed bot protection is deferred for the small pilot | Medium | Compensating controls; monitor |
| Enumeration | Generic login, reset, public acknowledgement, and foreign-ID behavior | Timing equivalence is not formally proven | Medium | Test and monitor |
| Callback forgery/replay | Header credential, stored trusted ownership, terminal idempotency | No timestamp/nonce replay scheme; schema change may be required for stronger replay defence | Medium | Deferred; rotate secret and restrict network |
| Worker cross-tenant or inactive processing | Active-workspace and ownership-consistency claim predicates, worker adversarial tests, direct-DB audit | Repeat before each pilot release | Critical | Verified for controlled pilot |
| SMTP/email injection or disclosure | Server-side recipient resolution, escaping, fixed subjects, safe logging | Provider security and TLS remain operational dependencies | High | Configuration-dependent |
| SSRF | Qualification URL is deployment configuration, not client input | Egress restriction depends on platform/network policy | Medium | Document and restrict egress |
| Unsafe deserialization / mass assignment | Explicit request DTOs, unknown-property rejection, endpoint/DTO inventory, ownership fields absent from writable DTOs | Re-audit when request contracts change | High | Verified for controlled pilot |
| Secret or sensitive-log exposure | Environment-based secrets, generic failure logs, Git/bundle/image/runtime-log scans | Secret rotation and external log controls remain operational | Critical | Verified for controlled pilot |
| Container/dependency compromise | Pinned images, non-root application images, private networks, npm/Trivy scans | Restricted n8n findings and image updates require release-by-release review | High | Mitigated for controlled pilot |
| Database exposure or privilege abuse | No production DB port publication, non-root application account | Separate migration/runtime DB credentials are not yet proven | High | Operational hardening pending |
| Backup loss / incident unpreparedness | Encrypted disposable backup/restore drill and incident/operations runbooks | Configure automated encrypted off-host backups before first client | High | Executably verified; production operation required |
| Resource exhaustion | Proxy limits, bounded worker batches, timeouts, container limits, rate/body boundary tests | Platform monitoring and managed bot protection remain operational/deferred | Medium | Mitigated for controlled pilot |

## Pilot assumptions and restrictions

- Only the reverse proxy is internet-facing; backend, database, SMTP capture, and
  automation services remain private.
- Workspaces are owner-provisioned; there is no self-service registration or
  workspace switching.
- Production secrets are supplied by an access-controlled secret manager or
  protected deployment environment and are rotated after suspected exposure.
- Managed bot protection/CAPTCHA may be added before broader public launch if
  inquiry abuse exceeds the documented edge controls.
- Independent human penetration testing remains required before a broader
  public launch and cannot be replaced by this internal audit.
