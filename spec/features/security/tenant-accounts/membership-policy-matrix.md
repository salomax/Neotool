---
id: feature.security.tenant-accounts.membership-policy-matrix
domain: security
status: draft
confidence: verified
source_of_truth: code
last_verified: 2026-08-02
evidence:
  backend:
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/management/membership/MembershipPolicyEngine.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/management/membership/AccountMembershipService.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/management/AccountService.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/domain/AccountMembership.kt
  graphql:
    - contracts/graphql/subgraphs/security/schema.graphqls
  database: []
  tests:
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/unit/MembershipPolicyEngineTest.kt
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/unit/AccountMembershipServiceTest.kt
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/integration/AccountMembershipServiceIntegrationTest.kt
  frontend:
    - web/src/features/account-membership/components/AccountMembersSection.tsx
    - web/src/features/account-membership/inviteRoles.ts
  mobile: []
  workers: []
legacy_refs: []
related_specs:
  - feature.security.tenant-accounts.membership-invites
  - feature.security.tenant-accounts.account-model
schema: spec-metadata-v1
---

# Membership Policy Matrix

Source of truth: `MembershipPolicyEngine.evaluate` (stateless; callers supply membership context). Additional command validations in `AccountMembership` / services.

## Matrix (as implemented)

| Operation | Who (ACTIVE actor) | Extra rules | Denial codes (typical) |
|---|---|---|---|
| `UPDATE_ACCOUNT` | OWNER | AccountService only | `NOT_ACCOUNT_MEMBER`, `ACCOUNT_ROLE_INSUFFICIENT` |
| `DELETE_ACCOUNT` | OWNER | PERSONAL needs another ACTIVE membership | same + `MUST_HAVE_ONE_ACTIVE_ACCOUNT` |
| `INVITE` | OWNER or ADMIN | Invite role MEMBER only; caps; not PERSONAL | policy + `MEMBER_LIMIT_REACHED`, `TARGET_NOT_ELIGIBLE` |
| `CANCEL_INVITATION` | OWNER or ADMIN | Target PENDING | `NOT_PENDING_INVITATION` |
| `CHANGE_ROLE` | OWNER only | Not self; newRole ∈ {ADMIN, MEMBER, VIEWER}; cannot assign OWNER via change | `SELF_OPERATION_FORBIDDEN`, `TARGET_NOT_ELIGIBLE` |
| `REMOVE_MEMBER` | OWNER or ADMIN | Not self; ADMIN cannot remove OWNER/ADMIN | `TARGET_ROLE_TOO_HIGH`, `SELF_OPERATION_FORBIDDEN` |
| `LEAVE` | Any ACTIVE member | Sole OWNER cannot leave | `SOLE_OWNER_CANNOT_LEAVE` |
| `TRANSFER_OWNERSHIP` | OWNER | New owner ACTIVE ADMIN or MEMBER | `TARGET_NOT_ELIGIBLE` |

Critical OWNER-only change-role rule (engine):

```140:160:service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/management/membership/MembershipPolicyEngine.kt
    private fun evaluateChangeRole(
        actor: ActorMembershipContext?,
        target: TargetMemberContext?,
        newRole: AccountRole?,
    ): MembershipPolicyResult {
        // ...
        if (actor.role != AccountRole.OWNER) {
            return MembershipPolicyResult.Denied(SecurityErrorCode.ACCOUNT_ROLE_INSUFFICIENT)
        }
        // ...
        if (newRole != AccountRole.ADMIN && newRole != AccountRole.MEMBER && newRole != AccountRole.VIEWER) {
            return MembershipPolicyResult.Denied(SecurityErrorCode.TARGET_NOT_ELIGIBLE)
        }
        return MembershipPolicyResult.Allowed
    }
```

## GraphQL vs service surface

- Exposed over GraphQL: invite/cancel/accept/decline/remove/changeRole/leave/updateAccount (+ accountMembers).
- **Implemented in service, not in GraphQL schema:** `transferOwnership`, `AccountService.create`, `AccountService.delete`.

## Behavior notes

- VIEWER: can leave; cannot invite/manage; change-role forbidden.
- OWNER cannot change own role via `CHANGE_ROLE` (use transfer for ownership).
- Transfer demotes previous owner in service logic (see `AccountMembershipService.transferOwnership`) — only callable from service/tests today.

## Permissions

Backend enforcement:

- Policy engine + service validations are authoritative.
- Membership GraphQL ops do not wrap `withPermission(security:account…)`; they rely on principal + policy.

UX hints:

- Invite/manage: OWNER/ADMIN.
- Change role UI: OWNER only (`canEditRole`) — aligned with backend.
- Remove: OWNER/ADMIN; UI blocks Admin removing Owner/Admin (aligned with `TARGET_ROLE_TOO_HIGH`).
- `CHANGE_ROLE_OPTIONS_BY_ACTOR[Admin]` lists MEMBER/VIEWER in code but Admin cannot open change-role UI (`canEditRole` is Owner-only) — dead option map entry, not an alternate enforcement path.

## Data And Contracts

- No dedicated GraphQL type for the matrix; behavior is procedural.
- Error codes live in `SecurityErrorCode`.

## Impact Surface

- Backend: MembershipPolicyEngine, AccountMembershipService, AccountService
- Tests: MembershipPolicyEngineTest, membership service unit/integration
- Frontend: AccountMembersSection action gates
- GraphQL: subset of operations (see above)

## Decisions

- Role hierarchy for remove: Admin < Owner (Admin cannot touch Owner/Admin).
- Ownership transfer is a first-class policy op even without GraphQL yet.

## Known Gaps

- No GraphQL for transferOwnership / create / delete account — policy exists ahead of API.
- Permission strings `security:account:invite` / `security:account-membership:manage` exist in Flyway seeds but are not what gates the membership GraphQL mutations (policy engine does).
- Bruno does not exercise the matrix.

## Legacy Notes

- Comments still cite FR-3 / FR-6; treat as labels, not doc authority.
