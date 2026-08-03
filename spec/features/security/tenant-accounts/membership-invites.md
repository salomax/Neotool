---
id: feature.security.tenant-accounts.membership-invites
domain: security
status: draft
confidence: verified
source_of_truth: code
last_verified: 2026-08-02
evidence:
  backend:
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/domain/AccountMembership.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/management/membership/AccountMembershipService.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/graphql/resolver/AccountMembershipResolver.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/graphql/SecurityWiringFactory.kt
  graphql:
    - contracts/graphql/subgraphs/security/schema.graphqls
  database:
    - service/kotlin/security/src/main/resources/db/migration/V0_23__create_accounts_and_memberships.sql
    - service/kotlin/security/src/main/resources/db/migration/V0_33__add_membership_invitee_email.sql
  tests:
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/unit/AccountMembershipServiceTest.kt
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/integration/AccountMembershipServiceIntegrationTest.kt
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/unit/MockEmailServiceInvitationTest.kt
    - web/tests/e2e/account-membership.e2e.spec.ts
    - web/src/features/account-membership/__tests__/inviteRoles.test.ts
  frontend:
    - web/src/features/account-membership/inviteRoles.ts
    - web/src/features/account-membership/components/AccountMembersSection.tsx
    - web/src/lib/graphql/operations/auth/accountMembership.ts
  mobile: []
  workers: []
legacy_refs: []
related_specs:
  - feature.security.tenant-accounts.account-model
  - feature.security.tenant-accounts.membership-policy-matrix
  - feature.security.authentication.sign-up
schema: spec-metadata-v1
---

# Membership Invites

## Behavior

GraphQL (authenticated unless noted): `inviteMember`, `cancelInvitation`, `acceptInvitation`, `declineInvitation`, `accountMembers`, `removeMember`, `changeMemberRole`, `leaveAccount`.

### Invite (`inviteMember`)

- Interim policy: invite role **must be MEMBER** (`AccountMembership.InviteMemberCommand`; schema default MEMBER; comment references future per-membership RBAC #398).
- Policy: OWNER or ADMIN active actor (`MembershipPolicyEngine.INVITE`).
- Invite-before-signup: PENDING row may have `user_id` null and `invitee_email` set (V0_33); invitee need not exist yet.
- Blocks: inactive account; invitee ACTIVE on another account (`ACCOUNT_ALREADY_MEMBER`); already ACTIVE/PENDING on this account; PERSONAL accounts (`MEMBER_LIMIT_REACHED`); FAMILY/BUSINESS at cap (10/50 counting ACTIVE+PENDING).
- Creates PENDING membership, 7-day token, emails invitation; REMOVED rows can be reactivated.

### Cancel / accept / decline

- `cancelInvitation`: OWNER/ADMIN; target must be PENDING; deletes pending membership.
- `acceptInvitation`: invitee only (by `userId` or matching `inviteeEmail`); not expired; PENDING; **v1 1:1** — actor must not already have ACTIVE membership on a different account (`ACCOUNT_ALREADY_MEMBER`); sets ACTIVE, clears token; sets `isDefault` if first ACTIVE membership.
- `declineInvitation`: invitee only; deletes PENDING membership.

### List / remove / change role / leave

- `accountMembers`: caller must be ACTIVE member of that account; returns ACTIVE+PENDING (not REMOVED).
- `removeMember` / `changeMemberRole` / `leaveAccount`: see policy-matrix spec; enforced in `AccountMembershipService`.

Wiring: membership mutations use authenticated `principal()`; **no** `env.withPermission("security:account…")` on invite/accept/etc. (unlike `isUserActiveMemberOfAccount`, which requires `security:account-membership:view`).

## Invariants

- Invite role cannot be OWNER/ADMIN/VIEWER (command validation).
- PERSONAL cannot gain additional members via invite.
- Accept is blocked while the user already has another ACTIVE tenant membership (current product constraint).

## Permissions

Backend enforcement:

- Role matrix via `MembershipPolicyEngine` + service validations (not JWT permission catalog for these ops).
- Signup binds pending invites and may skip personal account so accept is not blocked by 1:1 (`AuthenticationService.registerUser`).

UX hints:

- `INVITEABLE_ACCOUNT_ROLES = [MEMBER]` mirrors backend.
- Invite/remove/cancel buttons gated by OWNER/ADMIN (`canManage`); change-role UI only for OWNER (`canEditRole`).

## Data And Contracts

- GraphQL: `InviteMemberInput`, `AccountMember`, `AcceptInvitationPayload`, membership mutations/queries above.
- DB: invitation_token, invitation_expires_at, invitee_email, nullable user_id for PENDING.

## Impact Surface

- Backend: AccountMembershipService, domain commands, resolver, email
- GraphQL: membership ops
- Database: account_memberships invite columns
- Tests: unit/integration membership + invitation email mocks; web E2E; inviteRoles unit test
- Frontend: AccountMembersSection, accountMembership operations
- Bruno: **none** for these ops
- Mobile / Workers: none

## Decisions

- Invite-before-signup over requiring a pre-existing user.
- Interim invite-only-MEMBER until banking honors account_role.

## Known Gaps

- No Bruno coverage for membership/invite flows.
- Live invite→email→accept E2E limited (token email-only; FAMILY/BUSINESS create not on GraphQL).
- Accept/membership-change does not rotate caller JWT; stale `accounts` claims until login/refresh (see account-claims-in-token).

## Legacy Notes

- None used as authority.
