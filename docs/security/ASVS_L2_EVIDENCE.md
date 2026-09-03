# LeadFlow ASVS Level 2 aligned evidence

This is a project-specific engineering matrix. It uses short ASVS category
references and does not reproduce the OWASP standard. “Verified” requires test
or production-boundary evidence where execution is practical. This document is
not OWASP certification or an independent penetration test.

| Area / reference | Applicability | Implementation and evidence | Status | Remaining action |
|---|---|---|---|---|
| V1 Architecture | Applicable | Threat model, explicit browser/proxy/API/data/worker trust boundaries, deny-by-default security chain | Partially verified | Revisit after material architecture changes |
| V2 Authentication | Applicable | Persisted-user lookup, active-workspace check, Argon2id encoder, generic failures, owner-only provisioning; authentication tests | Verified | Monitor failed-login rates; independent timing review |
| V3 Sessions | Applicable | Server-side JDBC sessions, session-ID rotation, concurrent-session cap, logout invalidation, reset revocation, no frontend auth token storage | Verified | Production TLS/cookie boundary must remain regression-tested |
| V4 Access control | Applicable | ADMIN authorization and database-level workspace predicates; two-workspace service/API/repository tests | Verified | Repeat cross-tenant matrix before every pilot release |
| V5 Validation/encoding | Applicable | Bean Validation, bounded DTOs/search/page inputs, parameterized repositories, React escaping, email HTML escaping | Partially verified | Continue endpoint inventory and adversarial payload regression |
| V6 Stored cryptography | Applicable | Argon2id password hashes; reset tokens derived with keyed hashing and stored hashed; secrets externalized | Verified | Keep key rotation runbook current |
| V7 Errors/logging | Applicable | Generic API errors, production stack traces disabled, fixed worker failure logs, bounded container logs | Partially verified | Central alerting/redaction remains an operator responsibility |
| V8 Data protection | Applicable | Tenant FKs, private DB network, no browser token persistence, no-store API responses | Partially verified | Pilot retention/deletion schedule and encrypted off-host backups |
| V9 Communications | Applicable | HTTPS redirect, TLS 1.2/1.3, HSTS, secure cookies, private backend/data networks | Verified | Real certificate issuance/renewal monitoring is operational |
| V10 Malicious code | Applicable | Locked npm tree, Maven dependency management, dependency/secret review CI | Partially verified | Review scanner findings and image digests at each release |
| V11 Business logic | Applicable | Duplicate suppression, honeypot, optimistic locking, retry/idempotency rules, bounded edge rates | Verified | CAPTCHA/managed bot controls deferred unless abuse triggers |
| V12 Files/resources | Limited | No user file upload; static resources served from immutable build with nosniff | Verified | Reassess before adding uploads |
| V13 API/web services | Applicable | Explicit DTOs, content types, CSRF, CORS allowlist, callback header auth, size/rate bounds | Verified | Stronger callback nonce/timestamp replay control deferred |
| V14 Configuration | Applicable | Production profile, minimal Actuator, hardened Compose/Nginx, environment secrets, fail-closed secure cookie | Verified | Platform firewall and secret-manager controls are operational |
| GraphQL | Not applicable | No GraphQL endpoint or dependency | Not applicable | Reassess if introduced |
| WebSocket | Not applicable | No WebSocket endpoint or dependency | Not applicable | Reassess if introduced |

## Evidence maintenance

For each release retain the backend/frontend test summaries, migration hashes,
Compose and Nginx validation, dependency scan summaries, disposable-stack HTTP
matrix, direct tenant audit counts, and cleanup record. Never attach credentials,
cookies, reset tokens, lead content, or database dumps to this evidence.
