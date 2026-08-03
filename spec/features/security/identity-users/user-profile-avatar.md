---
id: feature.security.identity-users.user-profile-avatar
domain: security
status: draft
confidence: verified
source_of_truth: code
last_verified: 2026-08-02
evidence:
  backend:
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/management/UserManagementService.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/graphql/resolver/UserManagementResolver.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/graphql/SecurityWiringFactory.kt
  graphql:
    - contracts/graphql/subgraphs/security/schema.graphqls
  database:
    - service/kotlin/security/src/main/resources/db/migration/V0_1__init.sql
  tests:
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/unit/UserManagementServiceTest.kt
  frontend:
    - web/src/shared/providers/AuthorizationProvider.tsx
  mobile: []
  workers: []
legacy_refs: []
related_specs:
  - feature.security.identity-users.user-lifecycle
  - feature.security.authentication.oauth-identity-providers
schema: spec-metadata-v1
---

# Identity Users — Profile Avatar

## Behavior

- User fields: `avatarUrl`, `avatarProvider`, `avatarUpdatedAt` (provider set for OAuth users).
- `updateUserAvatar(input: UpdateUserAvatarInput!)` — authenticated caller updates **own** avatar URL via `UserManagementService.updateAvatar`.
- `removeUserAvatar` — clears own custom avatar.
- Rules:
  - If `avatarProvider != null` (OAuth-linked avatar), custom upload/remove is rejected (`AVATAR_UPLOAD_NOT_ALLOWED_FOR_OAUTH_USERS`).
  - Email/password users may set/clear `avatarUrl`.
- Wiring uses `env.principal()` (self-service); not gated by `security:user:save` admin permission.
- `AuthorizationProvider` syncs `avatarUrl` / `avatarProvider` / displayName from `currentUser` into AuthProvider storage when they change.

## Invariants

- OAuth users keep provider-controlled avatar; cannot override via these mutations.
- Avatar mutations are self-scoped (caller principal), not admin impersonation.

## Permissions

Backend enforcement:

- Authentication required; OAuth-provider guard on mutation.
- Admin user lifecycle permissions do not replace this self-service path.

UX hints:

- Profile UI may hide upload for OAuth users (confirm in web profile surfaces if present).

## Data And Contracts

- GraphQL: `updateUserAvatar`, `removeUserAvatar`, User avatar fields, `UpdateUserAvatarInput`.
- Storage of binary assets (if any) is outside this security mutation (URL string persistence only here).

## Impact Surface

- Backend UserManagementService avatar methods + resolver/wiring
- GraphQL contract
- Frontend: AuthorizationProvider sync; profile upload UI if present under web
- Tests: UserManagementServiceTest (avatar cases)

## Decisions

- Provider avatar wins for OAuth identities; custom avatars for password users only.

## Known Gaps

- Confirm dedicated web E2E for avatar update/remove.
- No Bruno requests for `updateUserAvatar` / `removeUserAvatar`.
- Asset upload pipeline (where URL comes from) not owned by this security feature — only URL persistence.

## Legacy Notes

- None used as authority.
