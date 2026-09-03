# Controlled-pilot security operations

## Pilot restriction

LeadFlow may be operated only for 5–10 owner-provisioned clients behind the
approved HTTPS reverse proxy. No backend, MySQL, Actuator, SMTP capture, or
automation editor port may be publicly exposed. Independent penetration testing
and jurisdiction-specific legal/privacy review remain required before broader
public launch.

## Backup and restore

- Create encrypted automated database and n8n backups at least daily; copy them
  off-host and restrict access to named operators.
- Pilot targets: RPO 24 hours and RTO 8 hours. Reassess before broader launch.
- Retain daily backups for 14 days and monthly recovery points for 90 days,
  subject to the approved deletion and legal policy.
- Store encryption keys separately from backup ciphertext. Never include runtime
  secrets in Git, logs, tickets, or the backup manifest.
- Run an isolated restore drill before first client data and quarterly thereafter.
  Validate migration history, workspace ownership, aggregate counts, and service
  startup before declaring the drill successful.
- A failed restore or stale/off-host-missing backup blocks deployment.

## Monitoring and alerting

Alert on availability and 5xx rate, authentication failures, 403/413/415/429
spikes, worker backlog age, delivery/qualification failures, database health,
disk use, certificate expiry, and backup age/failure. Use aggregate counts and
opaque internal correlation IDs only—never log credentials, cookies, reset
tokens, recipient addresses, lead text, AI content, or request bodies.

## Incident response

1. Detect and open a restricted incident record with UTC timestamps.
2. Contain: disable affected ingress/workers, preserve evidence, and avoid
   destructive cleanup.
3. Rotate exposed database, SMTP, automation, reset-HMAC, n8n, and operator
   credentials as applicable; invalidate sessions after credential incidents.
4. Determine affected workspaces and data without copying PII into general chat
   or tickets. Escalate the client-notification decision to the owner and legal
   counsel.
5. Recover from a verified clean image and tested backup, validate tenant
   ownership, then restore traffic gradually.
6. Complete a post-incident review and track corrective actions.

Lost operator devices require immediate session revocation, credential rotation,
and access-log review. Compromised SMTP or automation providers require disabling
that integration before rotation. Suspected cross-tenant exposure is a critical
incident: stop normal processing, preserve the database and logs, and obtain
independent security/legal assistance.

## Privacy and data handling

Collect only inquiry and sales-workflow fields required by the client. Workspace
clients own their lead data; platform-owner access is limited to support and
incident duties. Establish written retention, export, correction, and deletion
procedures, including how deletion propagates to backups after their retention
window. SMTP and automation providers are processors requiring contract and
regional review. Production data must never be reused as test data. This is a
technical baseline, not a statement of GDPR or other legal compliance.

## Release and operations checklist

- Review dependency and image vulnerability metadata; resolve applicable high or
  critical findings or record an approved mitigation.
- Confirm migration hashes, tests, production builds, Compose render, Nginx
  syntax, TLS policy, headers, rate limits, direct-port exposure, and tenant audit.
- Confirm encrypted off-host backup freshness and certificate renewal monitoring.
- Provision independent random secrets with least privilege and owner-only access.
- Keep debug/SQL parameter logging disabled and cap log retention.
- Rotate secrets after staff changes or suspected exposure and rehearse restore
  and incident procedures quarterly.
