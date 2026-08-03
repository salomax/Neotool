---
id: feature.security.authorization-runtime.authorization-audit
domain: security
status: draft
confidence: partial
source_of_truth: code
last_verified: 2026-08-02
evidence:
  backend:
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/authorization/AuthorizationAuditService.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/authorization/AuthorizationService.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/domain/audit/AuthorizationAuditLog.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/repo/AuthorizationAuditLogRepository.kt
    - service/kotlin/security/src/main/resources/application.yml
  graphql: []
  database:
    - service/kotlin/security/src/main/resources/db/migration/V0_6__create_audit_log_tables.sql
  tests:
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/unit/AuthorizationAuditServiceTest.kt
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/unit/HybridAuthorizationServiceTest.kt
  frontend: []
  mobile: []
  workers: []
legacy_refs: []
related_specs:
  - feature.security.authorization-runtime.hybrid-rbac-abac
  - feature.security.authorization-runtime.claim-based-enforcement
schema: spec-metadata-v1
---

# Authorization Runtime — Authorization Audit

## Behavior

- `AuthorizationAuditService.logAuthorizationDecision` persists decisions to `authorization_audit_logs` when enabled.
- Flag: `authorization.audit-logs.enabled` / env `AUTHORIZATION_AUDIT_LOGS_ENABLED` — **default `false`** in `application.yml`.
- When disabled: log methods are no-ops; find APIs return empty lists.
- Called from `AuthorizationService` hybrid/RBAC check paths (user permission checks that go through the full `checkPermission` overload), including deny-fast and post-ABAC outcomes. Records userId, groups, roles, action, resource, rbac/abac/final results, metadata.
- Claim-based `PermissionChecker` does **not** write audit rows.
- Find helpers: by user, by user+time range, by resource — service API only.

## Invariants

- No audit without explicit enablement.
- Audit coverage tracks the DB hybrid path, not GraphQL claim gates.

## Permissions

Backend enforcement: N/A (observability). Enabling audit does not change allow/deny.

UX hints: none (no UI/GraphQL for reading audits).

## Data And Contracts

- DB table from V0_6.
- **No GraphQL** for listing audit logs.
- Config-driven enablement only.

## Impact Surface

- Backend: AuthorizationAuditService + AuthorizationService call sites
- Database: audit log tables
- Tests: AuthorizationAuditServiceTest (+ hybrid tests may assert logging when mocked/enabled)
- Frontend / Bruno: none

## Decisions

- Opt-in persistence to avoid high write volume on every check in hot paths.
- Keep audit coupled to `AuthorizationService` evaluations (admin `checkPermission`, internal Manager), not every claim check across services.
- **Accepted:** do **not** audit `PermissionChecker` (claim-based GraphQL/HTTP) denials — audit remains path B only (hybrid/admin/diagnostic via `AuthorizationService`). High volume on path A is out of scope.

## Known Gaps

- Default off in local/prod-like defaults — operators must enable deliberately; easy to assume audits exist.
- No public API/UI to query audits.
- Claim-based denials across banking/security GraphQL are not audited here (**by design** — path A out of scope).
- No E2E/Bruno coverage for audit persistence.

## Legacy Notes

- None used as authority.
