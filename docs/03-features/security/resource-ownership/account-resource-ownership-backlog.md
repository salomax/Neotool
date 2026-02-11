---
title: Account Resource Ownership Implementation Backlog
type: backlog
category: security
status: draft
version: 0.2.0
tags: [security, authorization, account, resource-ownership, backlog, milestones]
ai_optimized: true
search_keywords: [account ownership backlog, membership backlog, milestone planning, implementation tasks, validation]
related:
  - 03-features/security/resource-ownership/account-resource-ownership-requirements.md
  - 03-features/security/resource-ownership/resource-ownership-domain-model.md
  - 03-features/security/resource-ownership/resource-ownership.md
  - 08-workflows/feature-development.md
last_updated: 2026-02-11
---

# Account Resource Ownership Implementation Backlog

> **Purpose**: Break down account-based ownership into implementable milestones and detailed tasks, each with a clear target, implementation approach, and validation criteria.

## Overview

This backlog operationalizes the requirements in `account-resource-ownership-requirements.md` into milestone gates and task cards.

Execution principles:
- Account is the primary ownership boundary.
- Membership models user access to account resources.
- Resource authorization remains layered: account context/membership + plan gate + RBAC + ownership + ABAC.
- Capacity controls (quota/rate limit) are enforced separately from permission checks.
- Tasks are complete only when code, tests, and validation evidence exist.

## Milestone Plan

| Milestone | Scope | Exit Condition |
|-----------|-------|----------------|
| M0 | Spec alignment and technical baseline | Terminology and invariants are consistent across requirement/docs and implementation scope is approved |
| M1 | Data model and persistence foundation | Migrations, entities, repositories, and seed/backfill scripts are ready and tested |
| M2 | Account lifecycle and membership core | Account CRUD and membership domain rules implemented with service-level tests |
| M3 | Invitations and member management workflows | Invite, accept/decline/cancel, role changes, remove/leave/transfer flows complete |
| M4 | Auth context and account switching | JWT/account context and token freshness/revocation controls implemented end-to-end |
| M5 | Resource ownership integration in services | Resource creation/access checks consistently account-scoped, with plan/capacity integration contracts |
| M6 | Frontend wiring, hardening, and rollout | UI flows, observability, security checks, and release runbook complete |

---

## M0: Spec Alignment and Baseline

### Task M0-T1: Lock domain vocabulary and invariants

**Target**
- Eliminate ambiguity between account membership and resource ACL grant semantics.
- Align `USER/ACCOUNT/GROUP` principal model across docs.

**How to Implement**
1. Review and align terminology in:
   - `docs/03-features/security/resource-ownership/account-resource-ownership-requirements.md`
   - `docs/03-features/security/resource-ownership/resource-ownership.md`
   - `docs/03-features/security/resource-ownership/resource-ownership-domain-model.md`
2. Confirm explicit distinction:
   - `account_memberships`: user-account relationship.
   - `resource_ownership.grant_type`: direct/shared resource grant metadata.
3. Add one glossary note in requirements for `account`, `membership`, `principal`, `grant`.

**Validation (Done when)**
- [ ] No contradictory principal enum definitions remain in the three docs.
- [ ] Requirements and implementation docs use the same ownership resolution order.
- [ ] Team review sign-off captured in PR description.

**Depends On**: None  
**Estimate**: 0.5 day

---

### Task M0-T2: Define permission matrix for account roles

**Target**
- Provide an enforceable authorization matrix for `OWNER`, `ADMIN`, `MEMBER`, `VIEWER` with explicit plan/capacity gate mapping.

**How to Implement**
1. Add a permission matrix table for account operations:
   - Invite/cancel invitation
   - Change role
   - Remove member
   - Delete account
   - Transfer ownership
   - Leave account
2. Map each operation to:
   - account role guard
   - plan action key (account-level allowlist key)
   - RBAC permission
   - service validation rule
3. Define when operation is capacity-limited and map the metric key (if any).
4. Include explicit denial cases and reason codes.

**Validation (Done when)**
- [ ] Each account/member operation has an explicit allow/deny rule.
- [ ] All FR-4/FR-5/FR-6 actions map to matrix entries.
- [ ] Matrix distinguishes `PLAN_NOT_ALLOWED` vs `RBAC_DENIED` vs `QUOTA_EXCEEDED`.
- [ ] Matrix is referenced by implementation tasks in M2-M4.

**Depends On**: Task M0-T1  
**Estimate**: 0.5 day

---

### Task M0-T3: Prepare implementation cut and release strategy

**Target**
- Define safe rollout boundaries and migration sequencing before coding.

**How to Implement**
1. Define feature flags for:
   - account claims in JWT
   - account-scoped ownership checks
   - account-switch UI exposure
2. Identify backward compatibility constraints:
   - tokens without account claims
   - legacy resources owned by `USER` principal
3. Document rollback policy per milestone.

**Validation (Done when)**
- [ ] Rollout/rollback steps documented in backlog notes.
- [ ] Feature flags list includes owner and default per environment.
- [ ] Legacy compatibility checklist exists.

**Depends On**: Task M0-T1  
**Estimate**: 0.5 day

---

### Task M0-T4: Define action and capacity registries

**Target**
- Eliminate overlap by defining one shared action catalog and one capacity metric catalog.

**How to Implement**
1. Define canonical action keys shared by security and billing (for example: `report:view`, `report:export`, `budget:create`).
2. Define canonical capacity metric keys (for example: `budgets.max`, `api.rate_limit.rpm`).
3. Document ownership and change process for both catalogs.
4. Enforce "absent in plan allowlist = denied" semantics in the spec.

**Validation (Done when)**
- [ ] No duplicate `feature.*` keys exist for behaviors already represented by action keys.
- [ ] Action and metric catalogs are referenced from requirements and billing blueprint.
- [ ] Team sign-off recorded in PR discussion.

**Depends On**: Task M0-T2  
**Estimate**: 0.5 day

---

## Rollout and Release Strategy

This section fulfills M0-T3: it defines feature flags, backward compatibility constraints, a legacy compatibility checklist, rollback policy per milestone, and rollout steps. Implementation tasks M1–M6 reference this strategy.

### Feature Flags

Account ownership is gated by the following feature flags (Unleash or equivalent). Owner and defaults per environment must be set before enabling in production.

| Flag name | Purpose | Owner | Default (dev) | Default (staging) | Default (production) |
|-----------|---------|--------|---------------|-------------------|----------------------|
| `accountOwnership.jwtClaims` | When enabled, issued tokens include `current_account`, `accounts`, and session/version claims (M4-T1). When disabled, tokens omit these; services must tolerate absence. | Security / TBD | On | Off | Off |
| `accountOwnership.scopedChecks` | When enabled, resource access and creation use account principal and membership (M5). When disabled, legacy USER principal and pre-migration behavior apply. | Security / TBD | On | Off | Off |
| `accountOwnership.switchUI` | When enabled, account switcher and account context are visible in the web UI (M6-T2). When disabled, UI does not show switcher. | Frontend / TBD | On | Off | Off |

### Backward Compatibility Constraints

- **Tokens without account claims**: Legacy or old tokens may not contain `current_account` or `accounts`. Token validation must not fail when these claims are absent (M4-T1). Account-scoped operations must either reject with a clear error (e.g. require re-login or token refresh) or fall back to a defined behavior (e.g. derive current account from default membership lookup). The chosen behavior must be documented and tested.

- **Legacy resources owned by USER principal**: Until M1-T4 and M5-T3 migrations run, some `resource_ownership` rows have `principal_type = USER`. During the transition, services must accept both USER and ACCOUNT principals for ownership resolution, or the cutover sequence (run migration and backfill before enabling account-scoped checks) must be followed so that account-scoped checks only run after migration is complete.

### Legacy Compatibility Checklist

- [ ] Tokens without `current_account` / `accounts` claims do not cause runtime errors; behavior is documented and verified in tests.
- [ ] Legacy `resource_ownership` rows with `principal_type = USER` are handled (read and/or migrated) per migration plan (M1-T4, M5-T3).
- [ ] Rollback of account-claims flag leaves tokens valid and services operational; verified in rollback drill.
- [ ] Rollback of account-scoped ownership flag restores USER-only ownership checks where applicable; verified in rollback drill.

### Rollback Policy per Milestone

| Milestone | Rollback action |
|-----------|-----------------|
| **M1 (Data model)** | Revert Flyway migration (or run down migration if provided). Data created in new tables may need to be preserved or discarded per policy. |
| **M2 (Account lifecycle)** | Disable or revert AccountService/API. New accounts and memberships remain in DB but are not exposed. |
| **M3 (Invitations/members)** | Disable invitation/member endpoints. Pending invitations and membership state remain. |
| **M4 (Auth context)** | Disable `accountOwnership.jwtClaims`; stop issuing account claims. Services already tolerate absent claims. |
| **M5 (Resource ownership integration)** | Disable `accountOwnership.scopedChecks`; services fall back to USER principal resolution for access checks. |
| **M6 (Frontend/rollout)** | Disable `accountOwnership.switchUI` (and optionally other flags). UI hides account switcher; backend can remain enabled for API-only clients. |

Rollback trigger thresholds and response owner for production are detailed in M6-T5 (Production rollout and post-release monitoring).

### Rollout Steps / Migration Sequencing

1. Deploy M1 schema and run Flyway migrations (accounts, account_memberships).
2. Deploy M2 and M3 with feature flags off; verify endpoints and data model.
3. Run backfill (M1-T4): create personal account per existing user, owner membership, migrate existing resource_ownership from USER to ACCOUNT where applicable. Verify no orphaned resources.
4. Enable `accountOwnership.scopedChecks` in staging; validate resource access and creation.
5. Enable `accountOwnership.jwtClaims` in staging; validate token issuance and principal resolution.
6. Enable `accountOwnership.switchUI` in staging; validate account switcher and UI context.
7. Production rollout per M6-T5: deploy behind flags, staged rollout, monitor, then enable flags per environment with rollback readiness.

---

## M1: Data Model and Persistence Foundation

### Task M1-T1: Add Flyway migration for accounts and memberships

**Target**
- Create durable schema for `accounts` and `account_memberships` with membership default selection (`is_default`).

**How to Implement**
1. Create new migration under:
   - `service/kotlin/security/src/main/resources/db/migration/`
2. Add:
   - `security.accounts`
   - `security.account_memberships`
   - `security.account_memberships.is_default`
3. Add indexes and constraints from requirements:
   - unique `(account_id, user_id)`
   - status-based indexes for active memberships
   - unique active default membership per user
   - invitation token lookup index

**Validation (Done when)**
- [ ] Migration applies on clean database.
- [ ] Migration applies on current local schema without data loss.
- [ ] DB integration tests pass: `./scripts/cli/cli validate --service security --skip-coverage`.

**Depends On**: M0 complete  
**Estimate**: 1 day

---

### Task M1-T2: Implement entities and enums for account domain

**Target**
- Introduce persistence models for account and membership lifecycle.

**How to Implement**
1. Add entities in security module model package:
   - `AccountEntity`
   - `AccountMembershipEntity`
2. Add enums:
   - `AccountType`, `AccountStatus`
   - `AccountRole`, `MembershipStatus`
3. Add converters where needed and optimistic locking (`version`).

**Validation (Done when)**
- [ ] Entity mappings match migration schema.
- [ ] Unit tests cover entity constraints and state transitions.
- [ ] Existing tests remain green: `./gradlew :security:test --no-daemon`.

**Depends On**: Task M1-T1  
**Estimate**: 1 day

---

### Task M1-T3: Implement repositories and query contracts

**Target**
- Provide efficient repository operations for account and membership workflows.

**How to Implement**
1. Add repository interfaces/implementations for:
   - account lookup by user/member/status
   - active membership checks
   - invitation token resolution
2. Add query methods for role transitions and owner uniqueness enforcement.
3. Add pagination/sorting support consistent with existing security repo patterns.

**Validation (Done when)**
- [ ] Repository tests cover happy path and constraint edge cases.
- [ ] Critical queries use expected indexes (explain plan verified in test notes).
- [ ] Test suite passes: `./gradlew :security:test --no-daemon`.

**Depends On**: Task M1-T2  
**Estimate**: 1 day

---

### Task M1-T4: Create backfill migration for existing users/resources

**Target**
- Ensure existing users/resources migrate safely to account ownership.

**How to Implement**
1. Create migration script to:
   - create personal account per existing user
   - create owner membership
   - set owner membership `is_default = true`
2. Update `resource_ownership` rows from `USER` owner to account owner where applicable.
3. Keep idempotency and auditability for reruns in non-prod.

**Validation (Done when)**
- [ ] Migration tested against seeded legacy dataset.
- [ ] No orphaned resources after migration.
- [ ] Verification SQL checks documented in migration comments.

**Depends On**: Task M1-T3  
**Estimate**: 1 day

---

## M2: Account Lifecycle and Membership Core

### Task M2-T1: Implement AccountService core operations

**Target**
- Implement create/update/delete account operations with requirements-compliant rules.

**How to Implement**
1. Add `AccountService` in security service layer.
2. Implement:
   - create account (FAMILY/BUSINESS)
   - update account (owner only, immutable type)
   - delete account (soft delete with grace window)
3. Enforce personal account constraints and owner-only operations.

**Validation (Done when)**
- [ ] Service tests cover FR-5 acceptance criteria and denial cases.
- [ ] Soft delete behavior validated with timestamps/status checks.
- [ ] `./gradlew :security:test --no-daemon` passes.

**Depends On**: M1 complete  
**Estimate**: 1.5 days

---

### Task M2-T2: Auto-create personal account on signup

**Target**
- New signup always creates personal account + owner membership + default membership (`is_default = true`).

**How to Implement**
1. Update signup flow in:
   - `service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/authentication/AuthenticationService.kt`
2. Create user and account/membership in one transaction.
3. Generate default account name from display name with validation fallback.

**Validation (Done when)**
- [ ] Integration test proves signup creates user + account + owner membership atomically.
- [ ] Rollback tested when account creation fails.
- [ ] JWT issued after signup contains current account context (or fallback claim strategy).

**Depends On**: Task M2-T1  
**Estimate**: 1 day

---

### Task M2-T3: Implement membership policy engine

**Target**
- Centralize account-role guards and transition rules in reusable logic.

**How to Implement**
1. Add policy component to evaluate:
   - who can invite/remove/change roles
   - self-demotion and owner transfer constraints
2. Expose clear error codes/messages for API use.
3. Reuse policy from account/member service methods.

**Validation (Done when)**
- [ ] Unit tests cover all role transition combinations.
- [ ] Policy results align with FR-3 and FR-6.
- [ ] No duplicated role-check logic remains in resolvers.

**Depends On**: Task M2-T1  
**Estimate**: 1 day

---

### Task M2-T4: Add account audit log events

**Target**
- Track all account and membership mutations for security/compliance.

**How to Implement**
1. Extend audit service/repo usage for account lifecycle events:
   - account created/updated/deleted
   - membership created/activated/removed
   - role changed/ownership transferred
2. Include actor ID, account ID, target user ID, and timestamp.
3. Add structured event naming conventions.

**Validation (Done when)**
- [ ] Each mutating operation emits exactly one audit event.
- [ ] Audit events include required fields for incident reconstruction.
- [ ] Integration tests verify emission on key workflows.

**Depends On**: Task M2-T3  
**Estimate**: 0.5 day

---

## M3: Invitations and Member Management

### Task M3-T1: Implement invitation issuance and cancellation

**Target**
- OWNER/ADMIN can create and cancel invitations with expiry and single-use semantics.

**How to Implement**
1. Add invitation application logic in membership service:
   - generate secure token
   - set `PENDING` membership and expiration
2. Add cancellation operation for pending invitations only.
3. Enforce member limit by account type.

**Validation (Done when)**
- [ ] Expired/cancelled tokens are rejected.
- [ ] Duplicate invites and over-limit invites are blocked.
- [ ] Unit/integration tests cover FR-4.1 and FR-4.4.

**Depends On**: M2 complete  
**Estimate**: 1 day

---

### Task M3-T2: Implement accept/decline invitation flows

**Target**
- Invited user can accept or decline with correct identity and lifecycle checks.

**How to Implement**
1. Implement token-to-membership resolution and actor verification.
2. On accept:
   - set status `ACTIVE`
   - set `joined_at`
3. On decline:
   - remove or transition pending invitation according to policy.

**Validation (Done when)**
- [ ] Only invited identity can accept invitation.
- [ ] Accept updates account availability on next auth refresh.
- [ ] FR-4.2/FR-4.3 scenarios pass integration tests.

**Depends On**: Task M3-T1  
**Estimate**: 1 day

---

### Task M3-T3: Implement member role change, removal, leave, transfer ownership

**Target**
- Complete all FR-6 membership management operations with strict guards.

**How to Implement**
1. Implement operations in membership service:
   - change member role
   - remove member
   - leave account
   - transfer ownership
2. Apply policy engine checks for owner/admin constraints.
3. Ensure resources remain account-owned on member removal/leave.

**Validation (Done when)**
- [ ] OWNER/Admin constraints enforced exactly as FR-6 states.
- [ ] Member removal immediately revokes account-scoped access.
- [ ] Tests validate no resource ownership reassignment to removed user.

**Depends On**: Task M3-T2  
**Estimate**: 1.5 days

---

### Task M3-T4: Wire invitation notifications

**Target**
- Invitation lifecycle sends required notifications (invite, optional cancellation/removal).

**How to Implement**
1. Integrate with existing email service abstractions in security/comms.
2. Add templates and localization keys for invitation messaging.
3. Make notification sending non-blocking or failure-tolerant for core membership transactions.

**Validation (Done when)**
- [ ] Invite emails include secure action links and expiry hints.
- [ ] Email failure does not corrupt membership state.
- [ ] Template rendering tests pass.

**Depends On**: Task M3-T1  
**Estimate**: 1 day

---

## M4: Auth Context and Account Switching

### Task M4-T1: Extend token model with account claims

**Target**
- Access tokens carry account context and freshness signals, without relying on embedded long-lived permission snapshots.

**How to Implement**
1. Update token issuance in:
   - `service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/jwt/JwtTokenIssuer.kt`
2. Add claims:
   - `current_account`
   - `accounts` (IDs + role summary)
   - `session_version` (or equivalent revocation/version claim)
3. Keep backward compatibility when claims are absent.
4. Avoid embedding broad `perms` lists in long-lived tokens; resolve effective authorization server-side with cache + revalidation.

**Validation (Done when)**
- [ ] Issued token includes account claims for account-enabled users.
- [ ] Old tokens still validate without runtime errors.
- [ ] Membership/role revocation tests confirm stale-token access is denied within propagation window.
- [ ] Token-related tests pass in security module.

**Depends On**: M3 complete  
**Estimate**: 1 day

---

### Task M4-T2: Extend common principal/auth context

**Target**
- Common security context includes account identity for downstream authorization.

**How to Implement**
1. Update:
   - `service/kotlin/common/src/main/kotlin/io/github/salomax/neotool/common/security/principal/AuthContext.kt`
   - `service/kotlin/common/src/main/kotlin/io/github/salomax/neotool/common/security/principal/RequestPrincipal.kt`
   - `service/kotlin/common/src/main/kotlin/io/github/salomax/neotool/common/security/principal/JwtPrincipalDecoder.kt`
2. Parse account claims from token and expose `currentAccountId` and `accountIds`.
3. Parse `session_version` claim (or equivalent).
4. Add validation helpers for account membership/delegation freshness checks.

**Validation (Done when)**
- [ ] Principal decoder maps account claims correctly.
- [ ] Services can read current account without custom claim parsing.
- [ ] `./gradlew :common:test --no-daemon` and `./gradlew :security:test --no-daemon` pass.

**Depends On**: Task M4-T1  
**Estimate**: 1 day

---

### Task M4-T3: Implement switch-account mutation/endpoint

**Target**
- User can switch active account and receive a new token context.

**How to Implement**
1. Add `switchAccount` operation in security API layer:
   - GraphQL resolver and/or REST endpoint per requirements.
2. Validate target account has ACTIVE membership.
3. Issue refreshed token pair with new `current_account` claim.

**Validation (Done when)**
- [ ] Switch blocked for non-members and inactive memberships.
- [ ] Returned token changes `current_account`.
- [ ] Integration test validates subsequent calls are account-scoped.

**Depends On**: Task M4-T2  
**Estimate**: 1 day

---

### Task M4-T4: Enforce account context in request execution path

**Target**
- All account-sensitive operations execute with explicit current account context.

**How to Implement**
1. Add guard/utility in shared security layer to require account context.
2. Update resolvers/services to pass account context instead of implicit user-only checks.
3. Add safe defaults for non-account features to avoid regressions.

**Validation (Done when)**
- [ ] Sensitive operations fail fast when account context is missing.
- [ ] No silent fallback to cross-account reads.
- [ ] Security regression tests pass.

**Depends On**: Task M4-T3  
**Estimate**: 1 day

---

## M5: Resource Ownership Integration

### Task M5-T1: Implement account-first principal resolution

**Target**
- Phase 1 access checks resolve principals using user + account. Group row grants stay Phase 2.

**How to Implement**
1. Update ownership access control implementations to resolve:
   - direct user principal
   - active account principals
2. Ensure query/index strategy supports this resolution efficiently.
3. Keep extension points for `GROUP` principals without enabling cross-account row grants.

**Validation (Done when)**
- [ ] Access checks pass for account-owned resources.
- [ ] Removed membership revokes account-based access immediately.
- [ ] Query performance meets NFR-1 targets on test dataset.

**Depends On**: M4 complete  
**Estimate**: 1.5 days

---

### Task M5-T2: Enforce account ownership on resource creation paths

**Target**
- New resources are consistently created with `principal_type = ACCOUNT`.

**How to Implement**
1. For each affected service, update create flows to use current account:
   - set account discriminator on resource
   - insert ownership grant for account principal
2. Add helper utilities in common ownership module to reduce duplicated code.
3. Keep audit metadata (`granted_by`) as acting user.

**Validation (Done when)**
- [ ] New resources never default to user-only ownership in Phase 1 flows.
- [ ] Create/update/list tests validate account isolation.
- [ ] Cross-service smoke tests pass for at least one resource type per service.

**Depends On**: Task M5-T1  
**Estimate**: 2 days

---

### Task M5-T3: Migrate and verify legacy ownership records

**Target**
- Legacy `USER` owner rows are migrated to account ownership without orphaned access.

**How to Implement**
1. Run migration/backfill strategy from M1 on pre-prod copy.
2. Add verification SQL checks:
   - count legacy owner rows remaining
   - count resources without account owner
3. Define exception handling for records lacking account mapping.

**Validation (Done when)**
- [ ] Verification queries show zero orphaned account-owned resources.
- [ ] Any unresolved rows are logged with remediation plan.
- [ ] Migration runbook updated with rollback steps.

**Depends On**: Task M5-T2  
**Estimate**: 1 day

---

### Task M5-T4: Add ownership and membership cache invalidation strategy

**Target**
- Avoid stale authorization after membership or role changes.

**How to Implement**
1. Identify cache points for principal resolution and ownership checks.
2. Invalidate cache on:
   - membership activate/remove
   - role change
   - ownership grant/revoke
3. Add conservative TTL fallback and metrics for cache hit/miss.

**Validation (Done when)**
- [ ] Access revocation is reflected within defined propagation window.
- [ ] Cache metrics are observable.
- [ ] Concurrency tests cover rapid role/membership changes.

**Depends On**: Task M5-T1  
**Estimate**: 1 day

---

### Task M5-T5: Define and validate plan/capacity enforcement contract

**Target**
- Ensure account plan gating and capacity checks are applied without overlap or race conditions.

**How to Implement**
1. Define service contract for `plan_allows(action_key)` and `capacity_available(metric_key)`.
2. Ensure write paths use atomic compare-and-increment semantics for quota counters.
3. Define distributed throttling path for high-frequency API limits (`rpm`, `rps`).
4. Standardize denial reason mapping (`PLAN_NOT_ALLOWED`, `QUOTA_EXCEEDED`, `RATE_LIMITED`).

**Validation (Done when)**
- [ ] Integration tests show action denied when plan disallows even if RBAC allows.
- [ ] Concurrency tests show no quota overshoot under parallel requests.
- [ ] Rate-limit tests confirm deterministic `RATE_LIMITED` behavior.

**Depends On**: Task M5-T2  
**Estimate**: 1 day

---

## M6: Frontend, Quality Gates, and Rollout

### Task M6-T1: Add account GraphQL contracts and generated types

**Target**
- Frontend and backend share typed contracts for account/member workflows.

**How to Implement**
1. Extend security GraphQL schema with account operations from requirements.
2. Add operations under:
   - `web/src/lib/graphql/operations/`
3. Regenerate TypeScript types and update callers.

**Validation (Done when)**
- [ ] Schema compiles and GraphQL validation passes.
- [ ] Generated TS types include account/member payloads.
- [ ] `./scripts/cli/cli graphql validate` passes.

**Depends On**: M4 complete  
**Estimate**: 1 day

---

### Task M6-T2: Implement account switcher and account context handling in web

**Target**
- Authenticated users can view and switch active account from UI.

**How to Implement**
1. Update auth state/provider layer to store account context.
2. Add account switcher UI in shell/navigation.
3. Refresh in-memory user context after successful switch.

**Validation (Done when)**
- [ ] Switching account updates visible context in UI immediately.
- [ ] Data views reload using new account context.
- [ ] Frontend unit tests updated and passing.

**Depends On**: Task M6-T1  
**Estimate**: 1.5 days

---

### Task M6-T3: Implement member and invitation management UI flows

**Target**
- UI supports invite, accept/decline, role update, remove member, and leave account.

**How to Implement**
1. Add settings/account management pages and forms.
2. Add role-aware action availability in UI.
3. Implement optimistic/error states aligned with API error codes.

**Validation (Done when)**
- [ ] UI operations match backend permission matrix.
- [ ] Failure states are localized and user actionable.
- [ ] E2E tests cover main flows and denial flows.

**Depends On**: Task M6-T2  
**Estimate**: 2 days

---

### Task M6-T4: Security and performance validation gate

**Target**
- Validate NFRs before production enablement.

**How to Implement**
1. Execute focused security test cases:
   - privilege escalation attempts
   - cross-account access attempts
   - replay/expired invitation token usage
2. Execute performance checks for principal resolution and access filtering.
3. Record findings and mitigations in a release checklist.

**Validation (Done when)**
- [ ] NFR-1 and NFR-2 targets are measured and documented.
- [ ] No critical/high unresolved security findings remain.
- [ ] Validation command passes in CI profile.

**Depends On**: Task M6-T3  
**Estimate**: 1 day

---

### Task M6-T5: Production rollout and post-release monitoring

**Target**
- Release account ownership safely with observability and rollback readiness.

**How to Implement**
1. Deploy behind feature flags in staged rollout.
2. Monitor:
   - invitation conversion
   - switch-account failures
   - authorization denial spikes
   - ownership query latency
3. Define rollback trigger thresholds and response owner.

**Validation (Done when)**
- [ ] Rollout checklist completed and approved.
- [ ] Dashboards and alerts are live.
- [ ] Post-release review completed after first production window.

**Depends On**: Task M6-T4  
**Estimate**: 0.5 day

---

## Definition of Done for the Backlog

This feature is done when:
- [ ] All FR-1 to FR-9 requirements are traceable to implemented tasks.
- [ ] Account-based ownership is default for all Phase 1 in-scope resource flows.
- [ ] Membership and invitation lifecycle operations are covered by tests.
- [ ] Account context is propagated end-to-end (JWT -> principal -> service checks).
- [ ] Plan/action gating and capacity denials are validated with deterministic error codes.
- [ ] Security and performance NFR targets have recorded evidence.
- [ ] Runbooks and docs are updated for operations and support teams.

## Suggested Execution Order

1. Complete M0 and M1 before writing API/UI code.
2. Deliver M2 + M3 together for coherent account/member behavior.
3. Gate M4 token/context changes behind feature flags.
4. Integrate M5 service ownership changes incrementally per service.
5. Finalize M6 only after non-prod migration and security validation.
