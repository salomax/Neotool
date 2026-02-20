---
title: Account & Resource Ownership Requirements
type: requirements
category: security
status: draft
version: 1.0.0
tags: [security, authorization, resource-ownership, account, multi-tenancy]
ai_optimized: true
search_keywords:
  [account, resource ownership, multi-tenancy, requirements, authorization]
related:
  - docs/03-features/security/resource-ownership/resource-ownership-domain-model.md
  - docs/03-features/security/resource-ownership/resource-ownership.md
  - docs/92-adr/0009-resource-ownership-strategy.md
---

# Account & Resource Ownership Requirements

## Overview

This document specifies the requirements for implementing Account-based resource ownership in neotool. The Account entity serves as the primary owner of resources, enabling multi-user collaboration and future billing capabilities.

### Goals

1. Enable multi-user access to shared resources (family, team)
2. Establish Account as the owner of resources (not User directly)
3. Support invitation flow for adding members to accounts
4. Maintain data when users leave accounts
5. Prepare foundation for future billing integration

### Out of Scope (Phase 1)

- Billing/subscription management
- Payment processing
- Cross-account resource sharing
- Account transfer between owners
- Group-based resource grants on business rows (Phase 2)

---

## Decision Summary

| Decision              | Choice                                                                                                                                             |
| --------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------- |
| Account creation      | Auto-create personal account on signup                                                                                                             |
| Multi-account support | Yes, users can belong to multiple accounts                                                                                                         |
| Account types         | PERSONAL, FAMILY, BUSINESS                                                                                                                         |
| Roles interaction     | Non-overlap gates: Account membership/role (scope) + Plan gate (account ceiling) + RBAC (action) + Resource ACL (row) + Capacity gate (quota/rate) |
| Groups scope          | Global for RBAC role inheritance; account-scoped row grants are Phase 2                                                                            |
| User removal          | Resources stay with account                                                                                                                        |
| Service accounts      | System-wide only (not per-account)                                                                                                                 |
| Invitation flow       | Email invite → accept/decline                                                                                                                      |
| Personal account      | Can be deleted (user profile retained)                                                                                                             |
| Cross-account sharing | Not supported (Phase 1)                                                                                                                            |

---

## Functional Requirements

### FR-1: Account Entity

#### FR-1.1: Account Types

The system shall support the following account types:

| Type       | Description                                     | Max Members |
| ---------- | ----------------------------------------------- | ----------- |
| `PERSONAL` | Single-user account, auto-created on signup     | 1           |
| `FAMILY`   | Multi-user account for household/family sharing | 10          |
| `BUSINESS` | Multi-user account for business/team use        | 50          |

#### FR-1.2: Account Attributes

| Attribute       | Type      | Required | Description                                                                  |
| --------------- | --------- | -------- | ---------------------------------------------------------------------------- |
| `id`            | UUID      | Yes      | Unique identifier                                                            |
| `name`          | String    | Yes      | Display name (e.g., "Smith Family", "Acme Corp") (DB column: `account_name`) |
| `type`          | Enum      | Yes      | PERSONAL, FAMILY, BUSINESS (DB column: `account_type`)                       |
| `status`        | Enum      | Yes      | ACTIVE, SUSPENDED, DELETED (DB column: `account_status`)                     |
| `owner_user_id` | UUID      | No       | User who created the account (for personal accounts)                         |
| `created_at`    | Timestamp | Yes      | Creation timestamp                                                           |
| `updated_at`    | Timestamp | Yes      | Last update timestamp                                                        |

#### FR-1.3: Account Status Transitions

```
ACTIVE ──────────────────┐
   │                     │
   │ (delete request)    │ (reactivate)
   ▼                     │
DELETED ◀────────────────┘
```

Note: SUSPENDED status reserved for future billing integration.

---

### FR-2: User Registration & Account Creation

#### FR-2.1: Auto-Create Personal Account

When a new user registers:

1. System creates User entity
2. System automatically creates a PERSONAL Account
3. System creates AccountMembership with role=OWNER
4. OWNER membership is marked as `is_default = true`
5. Authentication/session context is initialized with this account as `current_account`

**Acceptance Criteria:**

- [ ] New user registration creates both User and Account
- [ ] Account name defaults to "{User's Name}'s Account"
- [ ] User is automatically OWNER of their personal account
- [ ] OWNER membership for personal account is `is_default = true`
- [ ] User's JWT contains the personal account in `accounts` claim

#### FR-2.2: Account Name Requirements

- Minimum: 2 characters
- Maximum: 100 characters
- Allowed: Letters, numbers, spaces, apostrophes, hyphens
- Must be unique per user (for their owned accounts)

---

### FR-3: Account Membership

#### FR-3.1: Membership Roles

| Role     | Permissions                                                                                                 |
| -------- | ----------------------------------------------------------------------------------------------------------- |
| `OWNER`  | Full account governance (members/settings/delete/transfer). Resource actions still require RBAC + row grant |
| `ADMIN`  | Manage members (except owner). Resource actions still require RBAC + row grant                              |
| `MEMBER` | Standard resource actions, constrained by RBAC + row grant                                                  |
| `VIEWER` | Read-only ceiling; cannot perform write/delete/share even if RBAC role would allow                          |

#### FR-3.2: Membership Attributes

| Attribute    | Type      | Required | Description                                               |
| ------------ | --------- | -------- | --------------------------------------------------------- |
| `id`         | UUID      | Yes      | Unique identifier                                         |
| `account_id` | UUID      | Yes      | Reference to Account                                      |
| `user_id`    | UUID      | Yes      | Reference to User                                         |
| `role`       | Enum      | Yes      | OWNER, ADMIN, MEMBER, VIEWER (DB column: `account_role`)  |
| `status`     | Enum      | Yes      | PENDING, ACTIVE, REMOVED (DB column: `membership_status`) |
| `joined_at`  | Timestamp | Yes      | When membership became active                             |
| `is_default` | Boolean   | Yes      | Default account preference for login/session bootstrap    |
| `invited_by` | UUID      | No       | User who sent invitation                                  |
| `invited_at` | Timestamp | No       | When invitation was sent                                  |

#### FR-3.3: Membership Constraints

- Each account must have exactly one OWNER
- User can have only one membership per account
- User can belong to multiple accounts (no limit)
- PERSONAL accounts allow only 1 member (the owner)
- At most one ACTIVE default membership (`is_default = true`) per user

---

### FR-4: Invitation Flow

#### FR-4.1: Send Invitation

**Actors:** Account OWNER or ADMIN

**Preconditions:**

- Inviter has OWNER or ADMIN role in the account
- Account has not reached member limit
- Invitee is not already a member

**Flow:**

1. Inviter enters invitee's email address and selects role
2. System validates email format
3. System checks if user with email exists:
   - If exists: Create PENDING membership
   - If not exists: Create invitation record (user will see on signup)
4. System sends invitation email with accept/decline link
5. System creates membership with status=PENDING

**Acceptance Criteria:**

- [ ] Only OWNER/ADMIN can send invitations
- [ ] Cannot invite existing members
- [ ] Cannot exceed account member limit
- [ ] Email sent with secure, time-limited token
- [ ] Invitation expires after 7 days

#### FR-4.2: Accept Invitation

**Actors:** Invited User

**Flow:**

1. User clicks accept link in email (or accepts in app)
2. System validates invitation token
3. System updates membership status to ACTIVE
4. System sets `joined_at` timestamp
5. User can now switch to the new account

**Acceptance Criteria:**

- [ ] Only the invited user can accept
- [ ] Expired invitations cannot be accepted
- [ ] User's JWT updated with new account on next token refresh

#### FR-4.3: Decline Invitation

**Actors:** Invited User

**Flow:**

1. User clicks decline link (or declines in app)
2. System deletes the PENDING membership
3. Inviter notified (optional)

#### FR-4.4: Cancel Invitation

**Actors:** Account OWNER or ADMIN

**Flow:**

1. Inviter selects pending invitation to cancel
2. System deletes the PENDING membership
3. Invitation link becomes invalid

---

### FR-5: Account Management

#### FR-5.1: Create New Account

**Actors:** Any authenticated User

**Flow:**

1. User selects "Create Account"
2. User enters account name and type (FAMILY or BUSINESS)
3. System creates Account with user as OWNER
4. User can optionally switch to new account

**Acceptance Criteria:**

- [ ] User becomes OWNER of new account
- [ ] User retains membership in existing accounts
- [ ] New account appears in user's account list

#### FR-5.2: Update Account

**Actors:** Account OWNER

**Fields editable:**

- Account name

**Acceptance Criteria:**

- [ ] Only OWNER can update account details
- [ ] Type cannot be changed after creation

#### FR-5.3: Delete Account

**Actors:** Account OWNER

**Preconditions:**

- User is OWNER of the account
- Account is not PERSONAL type OR user confirms profile deletion

**Flow:**

1. Owner requests account deletion
2. System prompts for confirmation
3. For PERSONAL accounts: Warn that this deletes user profile too
4. System soft-deletes account (status=DELETED)
5. All memberships removed
6. Resources remain but inaccessible (for potential recovery)
7. After 30 days: Hard delete account and resources

**Acceptance Criteria:**

- [ ] Only OWNER can delete
- [ ] PERSONAL account deletion requires user profile deletion confirmation
- [ ] 30-day grace period before permanent deletion
- [ ] Other members notified of account deletion

#### FR-5.4: Delete Personal Account (Keep Profile)

**Actors:** User with Personal Account

**Flow:**

1. User requests to delete personal account
2. System checks user has at least one other account membership
3. System deletes personal account and its resources
4. User profile remains (for other account memberships)

**Acceptance Criteria:**

- [ ] User must have another account to keep profile
- [ ] If no other accounts, must delete entire user profile

---

### FR-6: Member Management

#### FR-6.1: View Members

**Actors:** Any account member

All members can view the member list of their account.

#### FR-6.2: Change Member Role

**Actors:** Account OWNER

**Constraints:**

- OWNER can change any member's role (except themselves)
- Cannot demote self from OWNER without transferring ownership first
- ADMIN cannot change roles

**Acceptance Criteria:**

- [ ] Role changes take effect immediately
- [ ] Member notified of role change

#### FR-6.3: Remove Member

**Actors:** Account OWNER or ADMIN

**Constraints:**

- OWNER can remove anyone (except themselves)
- ADMIN can remove MEMBER and VIEWER only
- Cannot remove self

**Flow:**

1. Remover selects member to remove
2. System prompts for confirmation
3. System updates membership status to REMOVED
4. Member loses access immediately
5. Resources created by member remain with account

**Acceptance Criteria:**

- [ ] Removed member cannot access account resources
- [ ] Resources stay with account (not transferred to member)
- [ ] Removed member notified

#### FR-6.4: Leave Account

**Actors:** Any member (except sole OWNER)

**Flow:**

1. Member selects "Leave Account"
2. System prompts for confirmation
3. If member is OWNER: Must transfer ownership first
4. System updates membership status to REMOVED
5. Member's resources remain with account

**Acceptance Criteria:**

- [ ] OWNER cannot leave without transferring ownership
- [ ] Resources remain with account

#### FR-6.5: Transfer Ownership

**Actors:** Account OWNER

**Flow:**

1. Current OWNER selects new owner from ADMIN/MEMBER list
2. System prompts for confirmation
3. Current OWNER becomes ADMIN
4. Selected member becomes OWNER

**Acceptance Criteria:**

- [ ] Only OWNER can transfer ownership
- [ ] Previous owner retains ADMIN role
- [ ] New owner notified

---

### Permission Matrix for Account Roles

This matrix is the source of truth for account and member operations; implementation tasks M2–M4 enforce it. Each operation has an explicit allow/deny rule, plan action key, RBAC permission, service validation, and denial reason codes. Denials are distinguished by gate: `PLAN_NOT_ALLOWED`, `RBAC_DENIED`, `QUOTA_EXCEEDED`, and account-level codes below. Canonical action and capacity metric keys are defined in [Action and Capacity Registries](action-capacity-registries.md); the matrix uses only keys from those catalogs.

#### Matrix Table

| Operation                              | FR     | Allowed roles                                       | Plan action key               | RBAC permission               | Service validation                                                                            | Capacity-limited   | Metric key                        | Denial reason codes                                                                                                                |
| -------------------------------------- | ------ | --------------------------------------------------- | ----------------------------- | ----------------------------- | --------------------------------------------------------------------------------------------- | ------------------ | --------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------- |
| Send invitation                        | FR-4.1 | OWNER, ADMIN                                        | `account:invite`              | `account:invite`              | Member limit not exceeded; invitee not already member; valid email                            | Optional (Phase 2) | `invitations.pending_per_account` | NOT_ACCOUNT_MEMBER, ACCOUNT_ROLE_INSUFFICIENT, PLAN_NOT_ALLOWED, RBAC_DENIED, QUOTA_EXCEEDED, ALREADY_MEMBER, MEMBER_LIMIT_REACHED |
| Cancel invitation                      | FR-4.4 | OWNER, ADMIN                                        | `account:invitation_cancel`   | `account:invitation_cancel`   | Target is PENDING invitation only                                                             | No                 | —                                 | NOT_ACCOUNT_MEMBER, ACCOUNT_ROLE_INSUFFICIENT, PLAN_NOT_ALLOWED, RBAC_DENIED, NOT_PENDING_INVITATION                               |
| Accept invitation                      | FR-4.2 | Invitee (token holder)                              | `account:invitation_accept`   | `account:invitation_accept`   | Token valid, not expired; actor is invited identity                                           | No                 | —                                 | INVITATION_EXPIRED, INVITATION_INVALID, RBAC_DENIED                                                                                |
| Decline invitation                     | FR-4.3 | Invitee (token holder)                              | `account:invitation_decline`  | `account:invitation_decline`  | Actor is invited identity                                                                     | No                 | —                                 | INVITATION_EXPIRED, INVITATION_INVALID, RBAC_DENIED                                                                                |
| Create account                         | FR-5.1 | Any authenticated user                              | `account:create`              | `account:create`              | Authenticated; account type FAMILY or BUSINESS                                                | Optional (Phase 2) | `accounts.max_per_user`           | PLAN_NOT_ALLOWED, RBAC_DENIED, QUOTA_EXCEEDED                                                                                      |
| Update account                         | FR-5.2 | OWNER                                               | `account:update`              | `account:update`              | Type immutable after creation                                                                 | No                 | —                                 | NOT_ACCOUNT_MEMBER, ACCOUNT_ROLE_INSUFFICIENT, PLAN_NOT_ALLOWED, RBAC_DENIED                                                       |
| Delete account                         | FR-5.3 | OWNER                                               | `account:delete`              | `account:delete`              | Confirmation for PERSONAL; soft-delete                                                        | No                 | —                                 | NOT_ACCOUNT_MEMBER, ACCOUNT_ROLE_INSUFFICIENT, PLAN_NOT_ALLOWED, RBAC_DENIED                                                       |
| Delete personal account (keep profile) | FR-5.4 | User (owner of personal)                            | `account:delete`              | `account:delete`              | User has ≥1 other ACTIVE account membership                                                   | No                 | —                                 | NO_OTHER_ACCOUNT, PLAN_NOT_ALLOWED, RBAC_DENIED                                                                                    |
| View members                           | FR-6.1 | Any ACTIVE member                                   | `account:members_view`        | `account:members_view`        | ACTIVE membership in account                                                                  | No                 | —                                 | NOT_ACCOUNT_MEMBER, PLAN_NOT_ALLOWED, RBAC_DENIED                                                                                  |
| Change member role                     | FR-6.2 | OWNER                                               | `account:members_change_role` | `account:members_change_role` | Not self; cannot demote self from OWNER without transfer first; target is ADMIN/MEMBER/VIEWER | No                 | —                                 | NOT_ACCOUNT_MEMBER, ACCOUNT_ROLE_INSUFFICIENT, SELF_OPERATION_FORBIDDEN, PLAN_NOT_ALLOWED, RBAC_DENIED                             |
| Remove member                          | FR-6.3 | OWNER (any except self), ADMIN (MEMBER/VIEWER only) | `account:members_remove`      | `account:members_remove`      | Not self; ADMIN cannot remove OWNER or ADMIN                                                  | No                 | —                                 | NOT_ACCOUNT_MEMBER, ACCOUNT_ROLE_INSUFFICIENT, SELF_OPERATION_FORBIDDEN, TARGET_ROLE_TOO_HIGH, PLAN_NOT_ALLOWED, RBAC_DENIED       |
| Leave account                          | FR-6.4 | Any member except sole OWNER                        | `account:leave`               | `account:leave`               | Not sole OWNER; must transfer ownership first if OWNER                                        | No                 | —                                 | NOT_ACCOUNT_MEMBER, SOLE_OWNER_CANNOT_LEAVE, PLAN_NOT_ALLOWED, RBAC_DENIED                                                         |
| Transfer ownership                     | FR-6.5 | OWNER                                               | `account:transfer_ownership`  | `account:transfer_ownership`  | New owner is ADMIN or MEMBER in same account                                                  | No                 | —                                 | NOT_ACCOUNT_MEMBER, ACCOUNT_ROLE_INSUFFICIENT, PLAN_NOT_ALLOWED, RBAC_DENIED, TARGET_NOT_ELIGIBLE                                  |

#### Denial Reason Codes

| Code                      | Gate / rule                   | Description                                                                           |
| ------------------------- | ----------------------------- | ------------------------------------------------------------------------------------- |
| PLAN_NOT_ALLOWED          | Plan gate                     | Account's plan does not allow the action key.                                         |
| RBAC_DENIED               | RBAC gate                     | User does not have the required RBAC permission.                                      |
| QUOTA_EXCEEDED            | Capacity gate                 | Account or user limit for the metric is reached.                                      |
| RATE_LIMITED              | Capacity gate                 | Rate limit exceeded (when applicable).                                                |
| NOT_ACCOUNT_MEMBER        | Account context / membership  | User has no ACTIVE membership in the account.                                         |
| ACCOUNT_ROLE_INSUFFICIENT | Account role gate             | User's role in the account does not allow this operation.                             |
| SELF_OPERATION_FORBIDDEN  | Service validation            | Operation on self is not allowed (e.g. remove self, demote self without transfer).    |
| TARGET_ROLE_TOO_HIGH      | Service validation            | Target member's role cannot be changed/removed by caller (e.g. ADMIN removing OWNER). |
| TARGET_NOT_ELIGIBLE       | Service validation            | Target does not meet criteria (e.g. new owner not ADMIN/MEMBER).                      |
| INVITATION_EXPIRED        | Service validation            | Invitation token has expired.                                                         |
| INVITATION_INVALID        | Service validation            | Invitation token invalid or already used.                                             |
| ALREADY_MEMBER            | Service validation            | Invitee is already a member of the account.                                           |
| MEMBER_LIMIT_REACHED      | Service validation / capacity | Account has reached maximum members for its type.                                     |
| NO_OTHER_ACCOUNT          | Service validation            | User has no other account (required for delete personal keep profile).                |
| NOT_PENDING_INVITATION    | Service validation            | Invitation is not in PENDING status.                                                  |

---

### FR-7: Resource Ownership

#### FR-7.0: Authorization Evaluation Order (No Overlap)

For account-owned resources, authorization is evaluated in this exact order:

1. **Account Context & Membership Gate**
   - Request has `current_account`
   - User has ACTIVE membership in `current_account` (Phase 1)
   - Future delegation models must pass an equivalent validity check
2. **Account Role Gate**
   - Membership role (`OWNER`/`ADMIN`/`MEMBER`/`VIEWER`) allows operation category
3. **Plan Gate (Commercial Ceiling)**
   - Account's current plan allows the requested `action_key`
4. **RBAC Gate**
   - User has required action permission (role/group-based RBAC)
5. **Resource Ownership Gate**
   - `resource_ownership` contains matching principal grant for this row
6. **ABAC Gate (Optional)**
   - Additional contextual/business-policy constraints
7. **Capacity Gate (When Applicable)**
   - Account usage/rate limits allow execution (`quota`/`throttle`)

Any gate can deny access. No gate bypasses another.

#### FR-7.1: Resource Creation

When a user creates a resource (transaction, budget, etc.):

1. Resource is created in the service database
2. ResourceOwnership record created with:
   - `principal_type` = ACCOUNT
   - `principal_id` = user's current account
   - `access_type` = OWNER
   - `granted_by` = user's ID

**Acceptance Criteria:**

- [ ] Resources always owned by ACCOUNT (not USER)
- [ ] Uses user's "current account" context from JWT
- [ ] Audit trail tracks which user created the resource

#### FR-7.2: Resource Access

A user can access a resource in Phase 1 only when all required gates pass:

1. User has ACTIVE membership in `current_account` (Phase 1)
2. Membership role allows the operation category (read/write/delete/share)
3. Account plan allows the requested action key
4. RBAC allows the requested action (e.g., `transaction:update`)
5. `resource_ownership` has a matching grant for current account principal
6. Optional ABAC predicates pass
7. For quota-limited operations, capacity remains available

Direct user grants may exist for special cases. Group row grants are out of scope in Phase 1.

```
Can user access resource?
        │
        ▼
┌──────────────────────────────────────────┐    Yes
│ Active membership exists?               │──────────▶ Continue
└──────────────┬───────────────────────────┘
               │ No
               ▼
            ❌ Access denied
               │
               ▼
┌──────────────────────────────────────────┐    Yes
│ Role allows operation category?         │──────────▶ Continue
└──────────────┬───────────────────────────┘
               │ No
               ▼
            ❌ Access denied
               │
               ▼
┌──────────────────────────────────────────┐    Yes
│ Plan allows requested action key?       │──────────▶ Continue
└──────────────┬───────────────────────────┘
               │ No
               ▼
            ❌ Access denied
               │
               ▼
┌──────────────────────────────────────────┐    Yes
│ RBAC action allowed?                    │──────────▶ Continue
└──────────────┬───────────────────────────┘
               │ No
               ▼
            ❌ Access denied
               │
               ▼
┌──────────────────────────────────────────┐    Yes
│ Row grant exists for current account?   │──────────▶ Continue
└──────────────┬───────────────────────────┘
               │ No
               ▼
            ❌ Access denied
               │
               ▼
┌──────────────────────────────────────────┐    Yes
│ ABAC predicates pass? (optional)        │──────────▶ Continue
└──────────────┬───────────────────────────┘
               │ No
               ▼
            ❌ Access denied
               │
               ▼
┌──────────────────────────────────────────┐    Yes
│ Capacity available? (quota/rate limit)  │──────────▶ ✅ Access granted
└──────────────┬───────────────────────────┘
               │ No
               ▼
            ❌ Access denied
```

#### FR-7.3: Permission Resolution

User's effective permissions on a resource are an intersection:

```
Effective Permissions =
    AccountRole.maxAllowedActions
    ∩ BillingPlan.allowedActions
    ∩ RBAC.grantedPermissions
    ∩ ResourceOwnership.permissions (if set; NULL/empty = no extra restriction)
```

| Account Role | Max Allowed Actions        |
| ------------ | -------------------------- |
| OWNER        | read, write, delete, share |
| ADMIN        | read, write, delete, share |
| MEMBER       | read, write                |
| VIEWER       | read                       |

Account role sets an upper bound for the account context. Plan policy, RBAC, and row grants can further restrict access.
Capacity checks (quota/rate limit) are evaluated in addition to the action intersection above.

---

### FR-8: Account Switching

#### FR-8.1: Switch Current Account

**Actors:** User with multiple accounts

**Flow:**

1. User selects account from account switcher UI
2. API endpoint validates user is active member
3. System issues new JWT with updated `current_account`
4. UI refreshes to show new account's data

**Acceptance Criteria:**

- [ ] Can only switch to accounts where membership is ACTIVE
- [ ] All subsequent API calls use new account context
- [ ] UI clearly shows current account

#### FR-8.2: Default Account

When user logs in:

- Use ACTIVE membership marked `is_default = true` (if present)
- If no default membership, prefer ACTIVE personal account
- If no personal account, use first available ACTIVE membership

`current_account` is request/session context (e.g., JWT claim), not a column on `security.users`.

#### FR-8.3: Token Freshness and Revocation

JWT provides request context, not final source of truth for long-lived authorization state.

Rules:

- Token should carry `current_account` and may carry `session_version`.
- Sensitive operations must re-check ACTIVE membership (and, in future phases, delegation) plus revocation-sensitive state via cache/DB.
- Permission and plan gates must tolerate stale tokens by enforcing server-side checks.

**Acceptance Criteria:**

- [ ] Membership removal revokes account access even for previously issued tokens within configured propagation window.
- [ ] Account role changes are reflected without requiring full logout/login cycle.
- [ ] Security tests cover stale-token denial scenarios.

---

### FR-9: Groups (RBAC-First in Phase 1)

#### FR-9.1: Group Scope

Groups are global and not scoped to accounts for RBAC assignment. This allows:

- System-wide permission grouping
- Reusable RBAC role inheritance
- Operational/admin team structures

In Phase 1, groups do **not** grant cross-account row access.

#### FR-9.2: Group Access to Resources (Phase 2)

Group-to-row grants are deferred to Phase 2 and must preserve account boundaries:

- grant is valid only inside current account context
- grant cannot be used for cross-account resource access

Example (Phase 2 design direction):

```sql
-- Grant group access to a resource
INSERT INTO resource_ownership (
    resource_type, resource_id,
    principal_type, principal_id,
    access_type, permissions,
    granted_by
) VALUES (
    'report', 'report-uuid',
    'GROUP', 'finance-team-uuid',
    'VIEW', ARRAY['read'],
    'granter-uuid'
);
```

---

## Non-Functional Requirements

### NFR-1: Performance

| Operation                 | Target                               |
| ------------------------- | ------------------------------------ |
| Access check              | < 5ms (with pre-resolved principals) |
| Principal resolution      | < 10ms                               |
| List accessible resources | < 50ms for 1000 resources            |

### NFR-2: Security

- Membership changes require re-authentication for sensitive roles (OWNER)
- Invitation tokens expire after 7 days
- Invitation tokens are single-use
- All membership changes logged in audit table
- Authorization revocations (membership/role/delegation) propagate within a defined and monitored window
- Denials are reason-coded at the correct gate (`PLAN_NOT_ALLOWED`, `QUOTA_EXCEEDED`, `RATE_LIMITED`, etc.)

### NFR-3: Data Integrity

- Account deletion is soft-delete with 30-day recovery window
- Resources are never orphaned (always have account owner)
- Membership history retained for audit (status changes, not hard deletes)

---

## API Endpoints

### Account Management

| Method | Endpoint                    | Description            | Auth   |
| ------ | --------------------------- | ---------------------- | ------ |
| POST   | `/api/accounts`             | Create new account     | User   |
| GET    | `/api/accounts`             | List user's accounts   | User   |
| GET    | `/api/accounts/{id}`        | Get account details    | Member |
| PATCH  | `/api/accounts/{id}`        | Update account         | Owner  |
| DELETE | `/api/accounts/{id}`        | Delete account         | Owner  |
| POST   | `/api/accounts/{id}/switch` | Switch current account | Member |

### Member Management

| Method | Endpoint                                 | Description        | Auth        |
| ------ | ---------------------------------------- | ------------------ | ----------- |
| GET    | `/api/accounts/{id}/members`             | List members       | Member      |
| POST   | `/api/accounts/{id}/invitations`         | Send invitation    | Owner/Admin |
| DELETE | `/api/accounts/{id}/invitations/{invId}` | Cancel invitation  | Owner/Admin |
| POST   | `/api/invitations/{token}/accept`        | Accept invitation  | Invitee     |
| POST   | `/api/invitations/{token}/decline`       | Decline invitation | Invitee     |
| PATCH  | `/api/accounts/{id}/members/{userId}`    | Change member role | Owner       |
| DELETE | `/api/accounts/{id}/members/{userId}`    | Remove member      | Owner/Admin |
| POST   | `/api/accounts/{id}/leave`               | Leave account      | Member      |
| POST   | `/api/accounts/{id}/transfer-ownership`  | Transfer ownership | Owner       |

### GraphQL Schema (Draft)

```graphql
type Account {
  id: ID!
  name: String!
  type: AccountType!
  status: AccountStatus!
  members: [AccountMembership!]!
  pendingInvitations: [AccountInvitation!]!
  createdAt: DateTime!
  updatedAt: DateTime!
}

type AccountMembership {
  id: ID!
  user: User!
  role: AccountRole!
  status: MembershipStatus!
  joinedAt: DateTime
  isDefault: Boolean!
  invitedBy: User
}

type AccountInvitation {
  id: ID!
  email: String!
  role: AccountRole!
  invitedBy: User!
  invitedAt: DateTime!
  expiresAt: DateTime!
}

enum AccountType {
  PERSONAL
  FAMILY
  BUSINESS
}

enum AccountRole {
  OWNER
  ADMIN
  MEMBER
  VIEWER
}

type Query {
  # Current user's accounts
  myAccounts: [Account!]!

  # Get specific account (must be member)
  account(id: ID!): Account

  # Current account context
  currentAccount: Account!
}

type Mutation {
  # Account management
  createAccount(input: CreateAccountInput!): Account!
  updateAccount(id: ID!, input: UpdateAccountInput!): Account!
  deleteAccount(id: ID!): Boolean!
  switchAccount(accountId: ID!): AuthToken!

  # Invitations
  inviteMember(accountId: ID!, input: InviteMemberInput!): AccountInvitation!
  cancelInvitation(invitationId: ID!): Boolean!
  acceptInvitation(token: String!): AccountMembership!
  declineInvitation(token: String!): Boolean!

  # Member management
  changeMemberRole(
    accountId: ID!
    userId: ID!
    role: AccountRole!
  ): AccountMembership!
  removeMember(accountId: ID!, userId: ID!): Boolean!
  leaveAccount(accountId: ID!): Boolean!
  transferOwnership(accountId: ID!, newOwnerId: ID!): Account!
}
```

---

## Database Schema

### New Tables

```sql
-- Accounts table (account_name, account_type, account_status avoid reserved words "name", "type", "status")
CREATE TABLE security.accounts (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    account_name VARCHAR(100) NOT NULL,
    account_type VARCHAR(20) NOT NULL CHECK (account_type IN ('PERSONAL', 'FAMILY', 'BUSINESS')),
    account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (account_status IN ('ACTIVE', 'SUSPENDED', 'DELETED')),
    owner_user_id UUID REFERENCES security.users(id),
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_accounts_owner ON security.accounts(owner_user_id) WHERE owner_user_id IS NOT NULL;
CREATE INDEX idx_accounts_status ON security.accounts(account_status) WHERE account_status = 'ACTIVE';

-- Account memberships table (account_role, membership_status avoid reserved words "role", "status")
CREATE TABLE security.account_memberships (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    account_id UUID NOT NULL REFERENCES security.accounts(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES security.users(id) ON DELETE CASCADE,
    account_role VARCHAR(20) NOT NULL CHECK (account_role IN ('OWNER', 'ADMIN', 'MEMBER', 'VIEWER')),
    membership_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (membership_status IN ('PENDING', 'ACTIVE', 'REMOVED')),
    joined_at TIMESTAMPTZ,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    invited_by UUID REFERENCES security.users(id),
    invited_at TIMESTAMPTZ,
    invitation_token VARCHAR(255),
    invitation_expires_at TIMESTAMPTZ,
    removed_at TIMESTAMPTZ,
    removed_by UUID REFERENCES security.users(id),
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uq_account_user UNIQUE (account_id, user_id),
    CONSTRAINT chk_default_membership_active
        CHECK (NOT is_default OR membership_status = 'ACTIVE')
);

CREATE INDEX idx_memberships_user ON security.account_memberships(user_id) WHERE membership_status = 'ACTIVE';
CREATE INDEX idx_memberships_account ON security.account_memberships(account_id) WHERE membership_status = 'ACTIVE';
CREATE INDEX idx_memberships_token ON security.account_memberships(invitation_token) WHERE invitation_token IS NOT NULL;
CREATE UNIQUE INDEX uq_memberships_default_per_user
    ON security.account_memberships(user_id)
    WHERE membership_status = 'ACTIVE' AND is_default = TRUE;

-- Update resource_ownership to support ACCOUNT principal type
-- (Already supports it, just documenting)
-- principal_type: 'ACCOUNT', 'USER', 'GROUP'
```

### Persistence conventions (JPA entities)

For tables whose primary key is defined with **`DEFAULT uuidv7()`** in migration DDL, JPA entities must follow the [Database-generated UUID (uuidv7)](../../../05-backend/patterns/entity-pattern.md#database-generated-uuid-uuidv7) convention in the Entity Pattern. Reference implementations: `AccountEntity` and `AccountMembershipEntity` (security module).

### Schema Changes to Existing Tables

No schema changes required on `security.users` for account selection preference.

---

## Migration Plan

### Phase 1: Schema & Basic Operations

1. Create `accounts` table
2. Create `account_memberships` table
3. Update `resource_ownership` queries to support ACCOUNT principal
4. Migrate existing users:
   - Create PERSONAL account for each existing user
   - Create OWNER membership
   - Set OWNER membership `is_default = true`
   - Update existing resource_ownership from USER to ACCOUNT

### Phase 2: Invitation Flow

1. Implement invitation endpoints
2. Email integration for invitations
3. UI for invitation management

### Phase 3: Account Switching

1. Update JWT to include accounts list
2. Add token/session freshness strategy (`session_version` or equivalent)
3. Implement account switching endpoint
4. Update TenantContext to use account

### Phase 4: Full Integration

1. Update all services to use account-based ownership
2. Audit and testing
3. Documentation

---

## Test Scenarios

### Account Creation

- [ ] User registration creates personal account automatically
- [ ] User can create additional FAMILY account
- [ ] User can create additional BUSINESS account
- [ ] Account name validation works correctly

### Invitation Flow

- [ ] OWNER can invite new member
- [ ] ADMIN can invite new member
- [ ] MEMBER cannot invite
- [ ] Invitation email is sent
- [ ] Invitee can accept invitation
- [ ] Invitee can decline invitation
- [ ] Expired invitation cannot be accepted
- [ ] Cannot invite existing member

### Member Management

- [ ] OWNER can change any member's role
- [ ] OWNER cannot demote self without transfer
- [ ] ADMIN cannot change roles
- [ ] Member can leave account
- [ ] OWNER cannot leave without transfer
- [ ] Resources remain when member leaves

### Resource Access

- [ ] Member can access account-owned resources
- [ ] Non-member cannot access resources
- [ ] VIEWER has read-only access
- [ ] MEMBER has read/write access
- [ ] OWNER has full access
- [ ] Plan gate denies action not included in account plan even when RBAC allows
- [ ] Quota/rate gate denies action when capacity is exhausted

### Account Switching

- [ ] User can switch between accounts
- [ ] JWT updated with new current account
- [ ] API calls use correct account context
- [ ] Exactly one ACTIVE membership can be marked `is_default` per user
- [ ] Revoked membership is denied after propagation window even with previously issued token

---

## Related Documentation

- [Implementation Backlog](./account-resource-ownership-backlog.md)
- [Resource Ownership Domain Model](./resource-ownership-domain-model.md)
- [Resource Ownership Implementation](./resource-ownership.md)
- [ADR-0009: Resource Ownership Strategy](../../../92-adr/0009-resource-ownership-strategy.md)
- [Authorization Overview](./README.md)
