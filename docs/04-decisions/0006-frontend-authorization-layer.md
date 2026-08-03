---
title: ADR-0006 Frontend Authorization Layer
type: adr
status: accepted
updated: 2026-08-03
owners: [platform]
source_of_truth:
  - web/src/shared/providers/AuthorizationProvider.tsx
  - web/src/shared/components/authorization/PermissionGate.tsx
  - web/src/shared/components/authorization/DisableIfNoPermission.tsx
load_when:
  - gating UI by permissions
  - extending authz primitives on web
supersedes:
schema: docs-metadata-v1
---

# ADR-0006: Frontend Authorization Layer

Purpose: record the durable decision and consequences for frontend authorization layer.

## Status

accepted

## Context

Authentication alone is insufficient for admin/settings UI; visibility and enablement must follow backend permissions.

## Decision

Keep backend permissions authoritative. On web, wrap auth with `AuthorizationProvider` and use `PermissionGate` / `DisableIfNoPermission` plus authorization hooks for declarative checks.

## Consequences

- Consistent permission UX across settings modules.
- Frontend gates are convenience only; APIs must still enforce authz.

## Evidence

- `AuthorizationProvider.tsx`, `PermissionGate.tsx`, and `DisableIfNoPermission.tsx` exist under `web/src/shared`.
- Settings pages import these primitives.

## Related

- [README](README.md)
- [Security architecture](../01-architecture/README.md)
