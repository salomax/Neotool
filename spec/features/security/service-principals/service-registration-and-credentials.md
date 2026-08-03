---
id: feature.security.service-principals.service-registration-and-credentials
domain: security
status: draft
confidence: verified
source_of_truth: code
last_verified: 2026-08-02
evidence:
  backend:
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/http/ServiceRegistrationController.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/http/dto/ServiceRegistrationDTOs.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/management/ServicePrincipalService.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/authentication/AuthenticationService.kt
  graphql: []
  database:
    - service/kotlin/security/src/main/resources/db/migration/V0_11__create_principals_tables.sql
    - service/kotlin/security/src/main/resources/db/migration/V0_15__add_service_credentials.sql
  tests:
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/http/ServiceRegistrationControllerIntegrationTest.kt
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/unit/ServicePrincipalServiceTest.kt
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/integration/ServicePrincipalIntegrationTest.kt
  frontend: []
  mobile: []
  workers: []
legacy_refs: []
related_specs:
  - feature.security.service-principals.client-credentials-flow
  - feature.security.rbac-administration.permission-catalog
schema: spec-metadata-v1
---

# Service Principals — Registration And Credentials

## Boundary

- Machine identity lifecycle for SERVICE principals (register, credentials, principal_permissions).
- **Not** OAuth2 AS Dynamic Client Registration (`POST /oauth2/register` — public MCP/agent clients).
- **Not** human user enable/disable (identity-users).

## Behavior

### Register (HTTP)

- `POST /api/internal/services/register` (`ServiceRegistrationController`).
- Body: `serviceId` + `permissions[]` (names must exist in catalog).
- Creates `principals` row (`principal_type=SERVICE`, `external_id=serviceId`, enabled), generates 32-byte secret (base64url), stores **hash** in `service_credentials` via `AuthenticationService.hashPassword`, assigns `principal_permissions` (null resource_pattern).
- Response 201: `serviceId`, `principalId`, **one-time clear `clientSecret`**, assigned `permissions`. Secret never returned again from this API.
- Duplicate `serviceId` → conflict.
- **Intentionally unauthenticated** at app layer; comment documents K8s NetworkPolicy restriction to internal network (infra, not enforced in this controller code).

### Credential rotation (service API)

- `ServicePrincipalService.rotateServiceCredentials(serviceId, currentSecret)`: proves current secret, replaces hash with new generated secret, returns one-time clear secret.
- **No dedicated HTTP controller** found for rotate — callable only if another surface wires it (today: unit-tested service method).

### Permission updates (service API)

- `updateServicePermissions(serviceId, permissionsToAdd, permissionsToRemove)`: mutates `principal_permissions`.
- **No dedicated HTTP controller** found — service/unit coverage only.

### Validation

- `validateServiceCredentials` used by `/oauth/token` and rotate: find SERVICE by external_id, must be enabled, verify password hash.

## Invariants

- Clear secret is show-once at register (and rotate when that API is exposed).
- Permissions assigned at register must already exist in `security.permissions`.
- DCR OAuth2 clients are a separate table/registry — do not mix with `principals` / `service_credentials`.

## Permissions

Backend enforcement:

- Register endpoint has no JWT gate in code (network trust model).
- Assigned permission strings gate later S2S calls via token claims.

UX hints: none. Bruno register request is an operator/bootstrap helper.

## Data And Contracts

- HTTP register DTOs only.
- DB: `security.principals`, `security.service_credentials`, `security.principal_permissions` (V0_11, V0_15).
- No GraphQL for service principal admin.

## Impact Surface

- Backend: ServiceRegistrationController, ServicePrincipalService
- Database: principals + credentials migrations
- Tests: registration IT, ServicePrincipal unit/IT
- Bruno: `Add Service Account Credential.bru` (register only; empty permissions example)
- Workers: consume resulting credentials via config — do not register themselves in-process

## Decisions

- Server-generated secrets (clients never submit a chosen secret at register).
- Bootstrap register left open in-app; rely on network policy for production exposure.
- No HTTP for rotate / update permissions / enable-disable for now — service methods remain internal; revisit only if ops needs them.

## Known Gaps

- Rotate / update permissions / enable-disable: service layer only (**accepted** — no ops need today).
- Register unauthenticated in application code — misconfigured NetworkPolicy is a security risk (infra gap, not tested here).
- Bruno register uses `permissions: []` — does not demonstrate assigning real catalog permissions.
- No Bruno for credential rotate.

## Legacy Notes

- None used as authority.
