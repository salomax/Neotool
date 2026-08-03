---
id: feature.security.tenant-accounts.account-model
domain: security
status: draft
confidence: verified
source_of_truth: code
last_verified: 2026-08-02
evidence:
  backend:
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/model/account/AccountEnums.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/model/account/AccountEntity.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/model/account/AccountMembershipEntity.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/domain/AccountManagement.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/management/AccountService.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/graphql/resolver/AccountResolver.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/authentication/AuthenticationService.kt
  graphql:
    - contracts/graphql/subgraphs/security/schema.graphqls
  database:
    - service/kotlin/security/src/main/resources/db/migration/V0_23__create_accounts_and_memberships.sql
    - service/kotlin/security/src/main/resources/db/migration/V0_24__backfill_personal_accounts_and_memberships.sql
    - service/kotlin/security/src/main/resources/db/migration/V0_33__add_membership_invitee_email.sql
  tests:
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/unit/AccountServiceTest.kt
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/model/AccountEntityTest.kt
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/integration/AccountMembershipServiceIntegrationTest.kt
    - web/tests/e2e/account-membership.e2e.spec.ts
  frontend:
    - web/src/features/account-membership/components/AccountMembersSection.tsx
  mobile: []
  workers: []
legacy_refs: []
related_specs:
  - feature.security.tenant-accounts.membership-invites
  - feature.security.tenant-accounts.membership-policy-matrix
  - feature.security.tenant-accounts.account-claims-in-token
  - feature.security.authentication.sign-up
schema: spec-metadata-v1
---

# Tenant Account Model

## Naming collision (explicit)

- **Tenant account** = `security.accounts` / GraphQL `Account` in the security subgraph — workspace ownership boundary for membership and resource scoping.
- **Banking/financial account** = banking domain entities / `banking:account:*` permissions — money accounts. Not this feature.
- Do not treat GraphQL/banking “account” strings as interchangeable.

## Behavior

- Tables `security.accounts` and `security.account_memberships` (V0_23): account is primary ownership boundary; users access via membership.
- Account types: `PERSONAL`, `FAMILY`, `BUSINESS`. Statuses: `ACTIVE`, `SUSPENDED`, `DELETED`.
- Membership roles: `OWNER`, `ADMIN`, `MEMBER`, `VIEWER`. Membership statuses: `PENDING`, `ACTIVE`, `REMOVED`.
- Constraints: unique `(account_id, user_id)`; at most one ACTIVE default membership per user (`is_default`); default implies ACTIVE.
- **PERSONAL** accounts are created on signup/OAuth when no pending invites (`AuthenticationService.createPersonalAccountAndMembership`). API create command rejects PERSONAL (`AccountManagement.CreateAccountCommand` — FAMILY/BUSINESS only).
- `AccountService.create`: persists account + OWNER ACTIVE membership for caller.
- `AccountService.update`: OWNER only (via policy); name editable; type immutable.
- `AccountService.delete`: soft-delete (DELETED + `deleted_at`); OWNER only; PERSONAL requires another ACTIVE membership; marks memberships REMOVED.
- GraphQL today: `myAccounts`, `updateAccount`. **No** `createAccount` / `deleteAccount` on the public schema (service methods exist without GraphQL exposure).
- Member caps used by invites (not create): PERSONAL=1, FAMILY=10, BUSINESS=50 (`maxMembersForAccountType`).

## Invariants

- Soft-deleted accounts must not be updated further as active.
- PERSONAL soft-delete blocked if it would leave the user with zero ACTIVE memberships.
- GraphQL `myAccounts` returns only ACTIVE memberships’ accounts with `isOwner` derived from membership role.

## Permissions

Backend enforcement:

- Update/delete guarded by `MembershipPolicyEngine` OWNER-only ops (not JWT permission strings).
- `myAccounts` / `updateAccount` require authenticated principal; no `security:account:*` claim check on those fetchers.

UX hints:

- Profile members UI assumes a current tenant account from the user’s memberships.

## Data And Contracts

- GraphQL `Account { id, accountName, accountType, accountStatus, isOwner }`, `updateAccount`, `myAccounts`.
- DB: V0_23 (+ V0_24 backfill; V0_33 nullable `user_id` / `invitee_email` for pending invites).

## Impact Surface

- Backend: AccountService, AccountEntity, enums, signup personal-account bootstrap
- GraphQL: myAccounts, updateAccount
- Database: accounts, account_memberships
- Tests: AccountServiceTest, entity tests, membership integration, E2E myAccounts
- Frontend: account-membership profile section
- Mobile / Workers: none

## Decisions

- PERSONAL is bootstrap-only via signup; FAMILY/BUSINESS create is service-level without GraphQL yet.
- Ownership boundary is tenant account, not RBAC group.

## Known Gaps

- Bruno Security collection has **no** membership/account operations.
- `createAccount` / `deleteAccount` / `transferOwnership` implemented in services but not exposed in GraphQL (E2E comments note FAMILY/BUSINESS create unavailable publicly).
- No GraphQL field for account soft-delete.

## Legacy Notes

- None used as authority.
