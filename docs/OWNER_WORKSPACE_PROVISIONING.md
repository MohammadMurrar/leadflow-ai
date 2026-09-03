# Owner-controlled workspace provisioning

This offline command creates one approved active workspace, its first ADMIN, one settings aggregate,
and optional notification recipients and services in a single transaction. It exposes no HTTP route
and performs no email, qualification, webhook, n8n, or AI operation.

## Preconditions

- Build and verify the approved application first.
- Confirm the database target. Never point this command at an unintended or production database.
- Use the dedicated `WorkspaceProvisioningApplication`; do not enable provisioning on the normal server.
- Keep provisioning values in process-local environment variables. Never commit an input file or credentials.

Required variables:

```text
LEADFLOW_PROVISIONING_ENABLED=true
PROVISIONING_WORKSPACE_NAME=<approved display name>
PROVISIONING_PUBLIC_SLUG=<canonical lowercase slug>
PROVISIONING_ADMIN_NAME=<first administrator display name>
PROVISIONING_ADMIN_EMAIL=<first administrator email>
PROVISIONING_ADMIN_PASSWORD=<secret initial password>
```

Optional variables:

```text
PROVISIONING_PUBLIC_DESCRIPTION=<public description>
PROVISIONING_NOTIFICATION_RECIPIENTS=<comma-separated addresses>
PROVISIONING_INITIAL_SERVICES=<true|Name|Description;false|Another name|Description>
PROVISIONING_DRY_RUN=true
```

The password must be supplied through the environment, never as a command argument. Clear it from the
launching process after completion. Do not place it in a project `.env`, shell script, log, or report.

## Preview and execution

Run the dedicated main class through the existing build tooling. A preview uses the same parsing and
validation and performs read-only conflict checks:

```text
PROVISIONING_DRY_RUN=true
```

Its safe success output is `Workspace provisioning preview passed; no data was written`. Preview does
not reserve a slug or email and cannot guarantee that a later concurrent write will succeed.

For the real invocation, omit `PROVISIONING_DRY_RUN` or set it to `false`. Safe success output is
`Workspace provisioning completed successfully`. Each client requires a separate invocation.

Duplicate slug or email errors do not modify the existing tenant. Correct the input and invoke the
command again. Any other failure rolls back the complete transaction; do not try to repair rows manually.

## Verification

After a successful invocation:

1. Log in using the new ADMIN and confirm the workspace settings.
2. Confirm services belong only to that workspace.
3. Open `/inquiry/{workspaceSlug}` and confirm the approved public branding and services.
4. Clear the initial password from the operator environment and any clipboard used to enter it.

If recipients were omitted, runtime notification delivery retains the workspace-scoped enabled-ADMIN
fallback. Provisioning itself sends nothing.

## Future self-service

A future public flow can perform rate limiting, CAPTCHA, consent, and email verification before creating
the same trusted command and invoking `WorkspaceProvisioningService`. The service has no CLI, HTTP,
`CurrentWorkspace`, verification, or billing dependency. A flow that needs a pre-verification `PENDING`
workspace will require a separately designed orchestration/state change; owner-controlled callers remain
restricted to approved `ACTIVE` workspaces.
