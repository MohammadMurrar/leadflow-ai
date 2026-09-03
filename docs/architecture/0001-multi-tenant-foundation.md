# ADR 0001: Initial multi-tenant foundation

## Status

Approved for phased implementation.

## Decisions

- A workspace uses an internal UUID and a globally unique, non-sensitive public slug.
- Each MVP user belongs directly to one workspace through `users.workspace_id`; workspace switching and memberships are deferred.
- Workspace roles are `OWNER` and `ADMIN`. The legacy administrator will become the legacy workspace owner in a later contract migration.
- Login email remains globally unique for the MVP.
- The deterministic legacy workspace slug is `leadflow-ai`.
- Public slugs are immutable for the MVP.
- Unknown and suspended public workspaces will return the same generic `404` response.
- The global public inquiry routes will be retired when slug-based backend and frontend routes launch together.
- Automation will retain platform authentication and later add an attempt-bound opaque callback capability tied to workspace, lead, attempt, and operation.
- Suspended-workspace email and qualification jobs are held and may resume only after explicit reactivation.
- Owner email verification is required before a future workspace becomes active.
- Billing and subscription enforcement are deferred.

V12 is an additive expand migration only. It seeds one active legacy workspace, adds nullable ownership columns, and preserves compatibility with the current single-workspace application. Later migrations will establish non-null ownership, workspace-scoped uniqueness, and removal of the singleton settings contract after tenant-aware application code is deployed.
