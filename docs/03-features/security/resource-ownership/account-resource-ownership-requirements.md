---
title: Account & Resource Ownership Requirements
type: requirements
category: security
status: draft
version: 1.0.0
tags: [security, authorization, resource-ownership, account, multi-tenancy]
ai_optimized: true
search_keywords: [account, resource ownership, multi-tenancy, requirements, authorization]
related:
  - docs/03-features/security/authorization/resource-ownership-domain-model.md
  - docs/03-features/security/authorization/resource-ownership.md
  - docs/92-adr/0009-resource-ownership-strategy.md
---

# Account & Resource Ownership Requirements

## Overview

This document specifies the requirements for implementing Account-based resource ownership in Invistus. The Account entity serves as the primary owner of resources, enabling multi-user collaboration and future billing capabilities.

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

---

## Decision Summary

| Decision | Choice |
|----------|--------|
| Account creation | Auto-create personal account on signup |
| Multi-account support | Yes, users can belong to multiple accounts |
| Account types | PERSONAL, FAMILY, BUSINESS |
| Roles interaction | Account role grants base permissions; RBAC adds fine-grained control |
| Groups scope | Global (not per-account) |
| User removal | Resources stay with account |
| Service accounts | System-wide only (not per-account) |
| Invitation flow | Email invite → accept/decline |
| Personal account | Can be deleted (user profile retained) |
| Cross-account sharing | Not supported (Phase 1) |

---

## Functional Requirements

### FR-1: Account Entity

#### FR-1.1: Account Types

The system shall support the following account types:

| Type | Description | Max Members |
|------|-------------|-------------|
| `PERSONAL` | Single-user account, auto-created on signup | 1 |
| `FAMILY` | Multi-user account for household/family sharing | 10 |
| `BUSINESS` | Multi-user account for business/team use | 50 |

#### FR-1.2: Account Attributes

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
| `id` | UUID | Yes | Unique identifier |
| `name` | String | Yes | Display name (e.g., "Smith Family", "Acme Corp") |
| `type` | Enum | Yes | PERSONAL, FAMILY, BUSINESS |
| `status` | Enum | Yes | ACTIVE, SUSPENDED, DELETED |
| `owner_user_id` | UUID | No | User who created the account (for personal accounts) |
| `created_at` | Timestamp | Yes | Creation timestamp |
| `updated_at` | Timestamp | Yes | Last update timestamp |

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
4. User is set as the "current account" context

**Acceptance Criteria:**
- [ ] New user registration creates both User and Account
- [ ] Account name defaults to "{User's Name}'s Account"
- [ ] User is automatically OWNER of their personal account
- [ ] User's JWT contains the personal account in `accounts` claim

#### FR-2.2: Account Name Requirements

- Minimum: 2 characters
- Maximum: 100 characters
- Allowed: Letters, numbers, spaces, apostrophes, hyphens
- Must be unique per user (for their owned accounts)

---

### FR-3: Account Membership

#### FR-3.1: Membership Roles

| Role | Permissions |
|------|-------------|
| `OWNER` | Full control: manage members, manage account settings, delete account, all resource access |
| `ADMIN` | Manage members (except owner), full resource access |
| `MEMBER` | Standard resource access (read, write based on RBAC) |
| `VIEWER` | Read-only access to resources |

#### FR-3.2: Membership Attributes

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
| `id` | UUID | Yes | Unique identifier |
| `account_id` | UUID | Yes | Reference to Account |
| `user_id` | UUID | Yes | Reference to User |
| `role` | Enum | Yes | OWNER, ADMIN, MEMBER, VIEWER |
| `status` | Enum | Yes | PENDING, ACTIVE, REMOVED |
| `joined_at` | Timestamp | Yes | When membership became active |
| `invited_by` | UUID | No | User who sent invitation |
| `invited_at` | Timestamp | No | When invitation was sent |

#### FR-3.3: Membership Constraints

- Each account must have exactly one OWNER
- User can have only one membership per account
- User can belong to multiple accounts (no limit)
- PERSONAL accounts allow only 1 member (the owner)

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

### FR-7: Resource Ownership

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

A user can access a resource if ANY of these conditions are true:

1. User's current account owns the resource (via ResourceOwnership)
2. User is member of a global Group that has access to the resource
3. Resource is explicitly shared with user (future - not Phase 1)

```
Can user access resource?
        │
        ▼
┌───────────────────┐     Yes
│ Account owns it?  │──────────▶ ✅ Access granted
└────────┬──────────┘            (based on membership role)
         │ No
         ▼
┌───────────────────┐     Yes
│ User's group has  │──────────▶ ✅ Access granted
│ access?           │            (based on group permissions)
└────────┬──────────┘
         │ No
         ▼
      ❌ Access denied
```

#### FR-7.3: Permission Resolution

User's effective permissions on a resource:

```
Effective Permissions =
    AccountRole.basePermissions
    ∩ RBAC.grantedPermissions
    ∩ ResourceOwnership.permissions (if CUSTOM)
```

| Account Role | Base Permissions |
|--------------|------------------|
| OWNER | read, write, delete, share |
| ADMIN | read, write, delete, share |
| MEMBER | read, write |
| VIEWER | read |

These base permissions can be further restricted by RBAC policies.

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
- Default to last used account (stored in user preferences)
- If no preference, default to personal account
- If personal account deleted, default to first available account

---

### FR-9: Groups (Global)

#### FR-9.1: Group Scope

Groups are global and not scoped to accounts. This allows:
- Cross-account permission assignment
- System-wide team structures
- Reusable permission sets

#### FR-9.2: Group Access to Resources

A group can be granted access to specific resources:

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
    'CUSTOM', ARRAY['read'],
    'granter-uuid'
);
```

**Use Cases:**
- Auditors group has read access to all financial reports
- Support team has read access to user accounts (for troubleshooting)

---

## Non-Functional Requirements

### NFR-1: Performance

| Operation | Target |
|-----------|--------|
| Access check | < 5ms (with pre-resolved principals) |
| Principal resolution | < 10ms |
| List accessible resources | < 50ms for 1000 resources |

### NFR-2: Security

- Membership changes require re-authentication for sensitive roles (OWNER)
- Invitation tokens expire after 7 days
- Invitation tokens are single-use
- All membership changes logged in audit table

### NFR-3: Data Integrity

- Account deletion is soft-delete with 30-day recovery window
- Resources are never orphaned (always have account owner)
- Membership history retained for audit (status changes, not hard deletes)

---

## API Endpoints

### Account Management

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/api/accounts` | Create new account | User |
| GET | `/api/accounts` | List user's accounts | User |
| GET | `/api/accounts/{id}` | Get account details | Member |
| PATCH | `/api/accounts/{id}` | Update account | Owner |
| DELETE | `/api/accounts/{id}` | Delete account | Owner |
| POST | `/api/accounts/{id}/switch` | Switch current account | Member |

### Member Management

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/api/accounts/{id}/members` | List members | Member |
| POST | `/api/accounts/{id}/invitations` | Send invitation | Owner/Admin |
| DELETE | `/api/accounts/{id}/invitations/{invId}` | Cancel invitation | Owner/Admin |
| POST | `/api/invitations/{token}/accept` | Accept invitation | Invitee |
| POST | `/api/invitations/{token}/decline` | Decline invitation | Invitee |
| PATCH | `/api/accounts/{id}/members/{userId}` | Change member role | Owner |
| DELETE | `/api/accounts/{id}/members/{userId}` | Remove member | Owner/Admin |
| POST | `/api/accounts/{id}/leave` | Leave account | Member |
| POST | `/api/accounts/{id}/transfer-ownership` | Transfer ownership | Owner |

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
  changeMemberRole(accountId: ID!, userId: ID!, role: AccountRole!): AccountMembership!
  removeMember(accountId: ID!, userId: ID!): Boolean!
  leaveAccount(accountId: ID!): Boolean!
  transferOwnership(accountId: ID!, newOwnerId: ID!): Account!
}
```

---

## Database Schema

### New Tables

```sql
-- Accounts table
CREATE TABLE security.accounts (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    name VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL CHECK (type IN ('PERSONAL', 'FAMILY', 'BUSINESS')),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETED')),
    owner_user_id UUID REFERENCES security.users(id),
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_accounts_owner ON security.accounts(owner_user_id) WHERE owner_user_id IS NOT NULL;
CREATE INDEX idx_accounts_status ON security.accounts(status) WHERE status = 'ACTIVE';

-- Account memberships table
CREATE TABLE security.account_memberships (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    account_id UUID NOT NULL REFERENCES security.accounts(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES security.users(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER', 'VIEWER')),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'ACTIVE', 'REMOVED')),
    joined_at TIMESTAMPTZ,
    invited_by UUID REFERENCES security.users(id),
    invited_at TIMESTAMPTZ,
    invitation_token VARCHAR(255),
    invitation_expires_at TIMESTAMPTZ,
    removed_at TIMESTAMPTZ,
    removed_by UUID REFERENCES security.users(id),
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uq_account_user UNIQUE (account_id, user_id)
);

CREATE INDEX idx_memberships_user ON security.account_memberships(user_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_memberships_account ON security.account_memberships(account_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_memberships_token ON security.account_memberships(invitation_token) WHERE invitation_token IS NOT NULL;

-- Update resource_ownership to support ACCOUNT principal type
-- (Already supports it, just documenting)
-- principal_type: 'ACCOUNT', 'USER', 'GROUP'
```

### Schema Changes to Existing Tables

```sql
-- Add current_account_id to users table (for default account preference)
ALTER TABLE security.users
ADD COLUMN current_account_id UUID REFERENCES security.accounts(id);
```

---

## Migration Plan

### Phase 1: Schema & Basic Operations

1. Create `accounts` table
2. Create `account_memberships` table
3. Update `resource_ownership` queries to support ACCOUNT principal
4. Migrate existing users:
   - Create PERSONAL account for each existing user
   - Create OWNER membership
   - Update existing resource_ownership from USER to ACCOUNT

### Phase 2: Invitation Flow

1. Implement invitation endpoints
2. Email integration for invitations
3. UI for invitation management

### Phase 3: Account Switching

1. Update JWT to include accounts list
2. Implement account switching endpoint
3. Update TenantContext to use account

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

### Account Switching

- [ ] User can switch between accounts
- [ ] JWT updated with new current account
- [ ] API calls use correct account context

---

## Related Documentation

- [Resource Ownership Domain Model](./resource-ownership-domain-model.md)
- [Resource Ownership Implementation](./resource-ownership.md)
- [ADR-0009: Resource Ownership Strategy](../../../92-adr/0009-resource-ownership-strategy.md)
- [Authorization Overview](./README.md)
