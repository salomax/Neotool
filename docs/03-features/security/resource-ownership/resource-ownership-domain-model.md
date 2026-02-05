---
title: Resource Ownership Domain Model
type: architecture
category: security
status: draft
version: 1.0.0
tags: [security, authorization, resource-ownership, multi-tenancy, domain-model]
ai_optimized: true
search_keywords: [resource ownership, account, tenant, multi-tenancy, domain model]
related:
  - docs/03-features/security/authorization/README.md
  - docs/03-features/security/authorization/resource-ownership.md
  - docs/92-adr/0009-resource-ownership-strategy.md
---

# Resource Ownership Domain Model

## Overview

This document defines the domain model for resource ownership in Invistus, following industry patterns from Stripe, Shopify, Slack, and similar platforms.

### Core Principle

**Account is the owner of resources, not User directly.**

```
Account (billing entity)
   ├── owns Resources (transactions, budgets, etc.)
   ├── has Members (users with roles)
   └── has Subscription (billing plan)
```

This enables:
- Team/family access to shared data
- Clean billing model (charge per account)
- Data retention when users leave
- Flexible sharing within account

---

## Domain Model

### Entity Relationship Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              SECURITY MODULE                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌──────────────┐         ┌──────────────────┐         ┌──────────────┐    │
│  │   Account    │────────▶│ AccountMembership│◀────────│    User      │    │
│  │              │   1:N   │                  │   N:1   │              │    │
│  │ - id         │         │ - account_id     │         │ - id         │    │
│  │ - name       │         │ - user_id        │         │ - email      │    │
│  │ - type       │         │ - role           │         │ - name       │    │
│  │ - status     │         │ - joined_at      │         │ - status     │    │
│  │ - owner_id   │         │ - invited_by     │         │ - avatar_url │    │
│  └──────┬───────┘         └──────────────────┘         └──────────────┘    │
│         │                                                      │            │
│         │ 1:N                                                  │            │
│         ▼                                                      │            │
│  ┌──────────────────┐                                         │            │
│  │ ResourceOwnership│◀────────────────────────────────────────┘            │
│  │                  │  (principal can be Account or User)                  │
│  │ - resource_type  │                                                       │
│  │ - resource_id    │                                                      │
│  │ - principal_type │                                                      │
│  │ - principal_id   │                                                      │
│  │ - access_type    │                                                     │
│  │ - permissions    │                                                     │
│  │ - valid_until    │                                                    │
│  │ - granted_by     │                                                    │
│  └──────────────────┘                                                     │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                              BILLING MODULE                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌──────────────┐         ┌──────────────────┐         ┌──────────────┐    │
│  │   Account    │────────▶│  Subscription    │────────▶│    Plan      │    │
│  │   (ref)      │   1:1   │                  │   N:1   │              │    │
│  └──────────────┘         │ - account_id     │         │ - id         │    │
│                           │ - plan_id        │         │ - name       │    │
│                           │ - status         │         │ - limits     │    │
│                           │ - current_period │         │ - price      │    │
│                           └──────────────────┘         └──────────────┘    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                           FINANCIAL DATA MODULE                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  All resources belong to Account via ResourceOwnership                      │
│                                                                              │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐                  │
│  │ Transaction  │    │   Account    │    │    Budget    │                  │
│  │ (financial)  │    │  (bank acct) │    │              │                  │
│  │              │    │              │    │              │                  │
│  │ - id         │    │ - id         │    │ - id         │                  │
│  │ - amount     │    │ - name       │    │ - name       │                  │
│  │ - date       │    │ - type       │    │ - amount     │                  │
│  │ - ...        │    │ - ...        │    │ - ...        │                  │
│  └──────────────┘    └──────────────┘    └──────────────┘                  │
│         │                   │                   │                           │
│         └───────────────────┼───────────────────┘                           │
│                             ▼                                               │
│                   ResourceOwnership                                         │
│                   (links to Account)                                        │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Core Entities

### Account

The **billing entity** and **primary owner** of resources.

```kotlin
@Entity
@Table(name = "accounts", schema = "security")
class AccountEntity(
    @Id
    @Column(columnDefinition = "uuid")
    val id: UUID = uuidv7(),

    @Column(nullable = false)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var type: AccountType,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: AccountStatus = AccountStatus.ACTIVE,

    /** The user who created/owns the account (for personal accounts) */
    @Column(name = "owner_user_id")
    var ownerUserId: UUID? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Version
    var version: Long = 0
)

enum class AccountType {
    PERSONAL,    // Single user, free or paid
    FAMILY,      // Multiple users, shared finances
    BUSINESS,    // Company account
    ENTERPRISE   // Large organization
}

enum class AccountStatus {
    ACTIVE,
    SUSPENDED,   // Payment issue
    CANCELLED,
    DELETED
}
```

```sql
CREATE TABLE security.accounts (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    name VARCHAR(255) NOT NULL,
    type VARCHAR(20) NOT NULL,  -- PERSONAL, FAMILY, BUSINESS, ENTERPRISE
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    owner_user_id UUID,  -- For personal accounts
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_account_owner FOREIGN KEY (owner_user_id)
        REFERENCES security.users(id)
);

CREATE INDEX idx_accounts_owner ON security.accounts(owner_user_id);
CREATE INDEX idx_accounts_type_status ON security.accounts(type, status);
```

### AccountMembership

Links Users to Accounts with roles.

```kotlin
@Entity
@Table(name = "account_memberships", schema = "security")
class AccountMembershipEntity(
    @Id
    @Column(columnDefinition = "uuid")
    val id: UUID = uuidv7(),

    @Column(name = "account_id", nullable = false)
    val accountId: UUID,

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var role: AccountRole,

    @Column(name = "joined_at", nullable = false)
    val joinedAt: Instant = Instant.now(),

    @Column(name = "invited_by")
    val invitedBy: UUID? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: MembershipStatus = MembershipStatus.ACTIVE,

    @Version
    var version: Long = 0
)

enum class AccountRole {
    OWNER,       // Full control, can delete account, manage billing
    ADMIN,       // Can manage members, full resource access
    MEMBER,      // Standard access to resources
    VIEWER,      // Read-only access
    GUEST        // Limited, time-bound access
}

enum class MembershipStatus {
    PENDING,     // Invited, not yet accepted
    ACTIVE,
    SUSPENDED,
    REMOVED
}
```

```sql
CREATE TABLE security.account_memberships (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    account_id UUID NOT NULL,
    user_id UUID NOT NULL,
    role VARCHAR(20) NOT NULL,  -- OWNER, ADMIN, MEMBER, VIEWER, GUEST
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    invited_by UUID,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_membership_account FOREIGN KEY (account_id)
        REFERENCES security.accounts(id) ON DELETE CASCADE,
    CONSTRAINT fk_membership_user FOREIGN KEY (user_id)
        REFERENCES security.users(id) ON DELETE CASCADE,
    CONSTRAINT fk_membership_inviter FOREIGN KEY (invited_by)
        REFERENCES security.users(id),
    CONSTRAINT uq_account_user UNIQUE (account_id, user_id)
);

CREATE INDEX idx_memberships_user ON security.account_memberships(user_id);
CREATE INDEX idx_memberships_account ON security.account_memberships(account_id);
```

### ResourceOwnership (Updated)

Now supports Account as principal type.

```kotlin
@Entity
@Table(name = "resource_ownership", schema = "security")
class ResourceOwnershipEntity(
    @Id
    @Column(columnDefinition = "uuid")
    val id: UUID = uuidv7(),

    @Column(name = "resource_type", nullable = false)
    val resourceType: String,

    @Column(name = "resource_id", nullable = false)
    val resourceId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "principal_type", nullable = false)
    val principalType: PrincipalType,

    @Column(name = "principal_id", nullable = false)
    val principalId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "access_type", nullable = false)
    var accessType: AccessType,

    @Column(name = "permissions", columnDefinition = "text[]")
    var permissions: List<String>? = null,

    @Column(name = "valid_from")
    val validFrom: Instant = Instant.now(),

    @Column(name = "valid_until")
    var validUntil: Instant? = null,

    @Column(name = "granted_by", nullable = false)
    val grantedBy: UUID,

    @Column(name = "granted_at", nullable = false)
    val grantedAt: Instant = Instant.now(),

    @Version
    var version: Long = 0
)

enum class PrincipalType {
    ACCOUNT,     // Account owns the resource (primary)
    USER,        // User has direct access (for sharing outside account)
    GROUP        // Group has access (within account)
}

enum class AccessType {
    OWNER,       // Full control
    EDITOR,      // Read + write
    VIEWER,      // Read only
    CUSTOM       // Use permissions array
}
```

```sql
CREATE TABLE security.resource_ownership (
    id UUID PRIMARY KEY DEFAULT uuidv7(),

    -- What resource
    resource_type VARCHAR(50) NOT NULL,
    resource_id UUID NOT NULL,

    -- Who has access (Account, User, or Group)
    principal_type VARCHAR(20) NOT NULL,
    principal_id UUID NOT NULL,

    -- What access
    access_type VARCHAR(20) NOT NULL,
    permissions TEXT[],

    -- When
    valid_from TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    valid_until TIMESTAMPTZ,

    -- Audit
    granted_by UUID NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uq_resource_principal
        UNIQUE (resource_type, resource_id, principal_type, principal_id)
);

CREATE INDEX idx_ownership_resource
    ON security.resource_ownership(resource_type, resource_id);

CREATE INDEX idx_ownership_principal
    ON security.resource_ownership(principal_type, principal_id, resource_type);

CREATE INDEX idx_ownership_account
    ON security.resource_ownership(principal_id)
    WHERE principal_type = 'ACCOUNT';
```

---

## Access Resolution Algorithm

When checking if a user can access a resource:

```kotlin
/**
 * Resolves all principal IDs that grant a user access.
 *
 * Order of precedence:
 * 1. Direct user ownership (USER principal)
 * 2. Account membership (ACCOUNT principal)
 * 3. Group membership (GROUP principal)
 */
@Singleton
class PrincipalResolver(
    private val membershipRepo: AccountMembershipRepository,
    private val groupMembershipRepo: GroupMembershipRepository
) {

    /**
     * Get all principal IDs that could grant access for a user.
     */
    fun resolvePrincipals(userId: UUID): ResolvedPrincipals {
        // 1. Direct user
        val userPrincipal = PrincipalRef(PrincipalType.USER, userId)

        // 2. User's accounts (active memberships)
        val accountMemberships = membershipRepo.findActiveByUserId(userId)
        val accountPrincipals = accountMemberships.map { membership ->
            AccountPrincipal(
                principalRef = PrincipalRef(PrincipalType.ACCOUNT, membership.accountId),
                role = membership.role
            )
        }

        // 3. User's groups (within their accounts)
        val groupMemberships = groupMembershipRepo.findActiveByUserId(userId)
        val groupPrincipals = groupMemberships.map { membership ->
            PrincipalRef(PrincipalType.GROUP, membership.groupId)
        }

        return ResolvedPrincipals(
            userId = userId,
            userPrincipal = userPrincipal,
            accountPrincipals = accountPrincipals,
            groupPrincipals = groupPrincipals
        )
    }

    /**
     * Get all principal IDs as a flat list for SQL IN clause.
     */
    fun resolvePrincipalIds(userId: UUID): List<UUID> {
        val principals = resolvePrincipals(userId)
        return listOf(principals.userId) +
               principals.accountPrincipals.map { it.principalRef.id } +
               principals.groupPrincipals.map { it.id }
    }
}

data class ResolvedPrincipals(
    val userId: UUID,
    val userPrincipal: PrincipalRef,
    val accountPrincipals: List<AccountPrincipal>,
    val groupPrincipals: List<PrincipalRef>
)

data class PrincipalRef(
    val type: PrincipalType,
    val id: UUID
)

data class AccountPrincipal(
    val principalRef: PrincipalRef,
    val role: AccountRole
)
```

### Access Check Query

```sql
-- Check if user can access a resource
SELECT EXISTS (
    SELECT 1 FROM security.resource_ownership o
    WHERE o.resource_type = :resourceType
      AND o.resource_id = :resourceId
      AND (
          -- Direct user access
          (o.principal_type = 'USER' AND o.principal_id = :userId)
          OR
          -- Account access (user is member of account)
          (o.principal_type = 'ACCOUNT' AND o.principal_id IN (
              SELECT account_id FROM security.account_memberships
              WHERE user_id = :userId AND status = 'ACTIVE'
          ))
          OR
          -- Group access
          (o.principal_type = 'GROUP' AND o.principal_id IN (
              SELECT group_id FROM security.group_memberships
              WHERE user_id = :userId
              AND (valid_until IS NULL OR valid_until > NOW())
          ))
      )
      AND (o.valid_until IS NULL OR o.valid_until > NOW())
) AS has_access;
```

### Optimized Query (Pre-resolved Principals)

```kotlin
// In service layer, pre-resolve principals once per request
val principalIds = principalResolver.resolvePrincipalIds(userId)

// Then use simple IN clause
val hasAccess = ownershipRepo.existsByResourceAndPrincipals(
    resourceType = "transaction",
    resourceId = transactionId,
    principalIds = principalIds
)
```

```sql
-- Simplified query with pre-resolved principals
SELECT EXISTS (
    SELECT 1 FROM security.resource_ownership
    WHERE resource_type = :resourceType
      AND resource_id = :resourceId
      AND principal_id IN (:principalIds)  -- Pre-resolved list
      AND (valid_until IS NULL OR valid_until > NOW())
) AS has_access;
```

---

## Ownership Patterns

### Pattern 1: Personal Account (B2C)

User signs up, gets personal account automatically:

```kotlin
@Singleton
class UserRegistrationService(
    private val userRepo: UserRepository,
    private val accountRepo: AccountRepository,
    private val membershipRepo: AccountMembershipRepository
) {

    @Transactional
    fun registerUser(email: String, name: String): User {
        // 1. Create user
        val user = userRepo.save(UserEntity(
            email = email,
            displayName = name
        ))

        // 2. Create personal account
        val account = accountRepo.save(AccountEntity(
            name = "$name's Account",
            type = AccountType.PERSONAL,
            ownerUserId = user.id
        ))

        // 3. Add user as owner of account
        membershipRepo.save(AccountMembershipEntity(
            accountId = account.id,
            userId = user.id,
            role = AccountRole.OWNER
        ))

        return user.toDomain()
    }
}
```

### Pattern 2: Resource Creation (Account-Owned)

Resources are owned by Account, not User:

```kotlin
@Singleton
class TransactionService(
    private val transactionRepo: TransactionRepository,
    private val ownershipRepo: ResourceOwnershipRepository,
    private val tenantContext: TenantContext
) {

    @Transactional
    fun createTransaction(input: CreateTransactionInput): Transaction {
        // 1. Get user's current account context
        val accountId = tenantContext.requireAccountId()
        val userId = tenantContext.requireUserId()

        // 2. Create transaction
        val transaction = transactionRepo.save(TransactionEntity(
            amount = input.amount,
            description = input.description,
            createdBy = userId
        ))

        // 3. Grant ownership to ACCOUNT (not user!)
        ownershipRepo.save(ResourceOwnershipEntity(
            resourceType = "transaction",
            resourceId = transaction.id,
            principalType = PrincipalType.ACCOUNT,
            principalId = accountId,
            accessType = AccessType.OWNER,
            grantedBy = userId
        ))

        return transaction.toDomain()
    }
}
```

### Pattern 3: Family/Team Account

Multiple users share access through account:

```kotlin
@Singleton
class AccountService(
    private val accountRepo: AccountRepository,
    private val membershipRepo: AccountMembershipRepository
) {

    @Transactional
    fun inviteMember(
        accountId: UUID,
        inviterId: UUID,
        inviteeEmail: String,
        role: AccountRole
    ): AccountMembership {
        // Verify inviter has permission to invite
        val inviterMembership = membershipRepo.findByAccountAndUser(accountId, inviterId)
            ?: throw AccessDeniedException("Not a member of this account")

        require(inviterMembership.role in listOf(AccountRole.OWNER, AccountRole.ADMIN)) {
            "Only owners and admins can invite members"
        }

        // Find or create user
        val invitee = userRepo.findByEmail(inviteeEmail)
            ?: throw NotFoundException("User not found")

        // Create pending membership
        return membershipRepo.save(AccountMembershipEntity(
            accountId = accountId,
            userId = invitee.id,
            role = role,
            status = MembershipStatus.PENDING,
            invitedBy = inviterId
        ))
    }
}
```

### Pattern 4: Cross-Account Sharing

Share specific resource with external user:

```kotlin
@Transactional
fun shareWithExternalUser(
    resourceType: String,
    resourceId: UUID,
    shareWithUserId: UUID,
    permissions: List<String>,
    validUntil: Instant?,
    grantedBy: UUID
) {
    // Verify granter has share permission
    accessControl.requireAccess(grantedBy, resourceType, resourceId, "share")

    // Grant direct USER access (not through account)
    ownershipRepo.save(ResourceOwnershipEntity(
        resourceType = resourceType,
        resourceId = resourceId,
        principalType = PrincipalType.USER,  // Direct user access
        principalId = shareWithUserId,
        accessType = AccessType.CUSTOM,
        permissions = permissions,
        validUntil = validUntil,
        grantedBy = grantedBy
    ))
}
```

---

## JWT Token Structure

Include account context in token:

```json
{
  "sub": "user-uuid",
  "email": "user@example.com",
  "accounts": [
    {
      "id": "account-uuid-1",
      "name": "Personal",
      "role": "OWNER"
    },
    {
      "id": "account-uuid-2",
      "name": "Family Smith",
      "role": "MEMBER"
    }
  ],
  "current_account": "account-uuid-1",
  "groups": ["group-uuid-1", "group-uuid-2"],
  "permissions": ["financial_data:transaction:read", "..."]
}
```

### Account Switching

User can switch between accounts in UI:

```kotlin
@Controller("/api/accounts")
class AccountController(
    private val tokenService: TokenService
) {

    @Post("/{accountId}/switch")
    fun switchAccount(
        @PathVariable accountId: UUID,
        authentication: Authentication
    ): TokenResponse {
        val userId = authentication.userId

        // Verify user is member of account
        val membership = membershipRepo.findByAccountAndUser(accountId, userId)
            ?: throw AccessDeniedException("Not a member of this account")

        // Issue new token with different current_account
        return tokenService.issueToken(
            userId = userId,
            currentAccountId = accountId
        )
    }
}
```

---

## Billing Integration

Billing is always at Account level:

```kotlin
@Entity
@Table(name = "subscriptions", schema = "billing")
class SubscriptionEntity(
    @Id
    val id: UUID = uuidv7(),

    @Column(name = "account_id", nullable = false)
    val accountId: UUID,  // Billing tied to Account

    @Column(name = "plan_id", nullable = false)
    var planId: UUID,

    @Enumerated(EnumType.STRING)
    var status: SubscriptionStatus,

    @Column(name = "current_period_start")
    var currentPeriodStart: Instant,

    @Column(name = "current_period_end")
    var currentPeriodEnd: Instant,

    // ... other billing fields
)
```

### Usage Limits by Account

```kotlin
@Singleton
class UsageLimitService(
    private val subscriptionRepo: SubscriptionRepository,
    private val planRepo: PlanRepository
) {

    fun checkLimit(accountId: UUID, feature: String, currentUsage: Long): Boolean {
        val subscription = subscriptionRepo.findByAccountId(accountId)
            ?: return false

        val plan = planRepo.findById(subscription.planId)
        val limit = plan.limits[feature] ?: return true  // No limit defined

        return currentUsage < limit
    }
}
```

---

## Migration from User-Owned to Account-Owned

If migrating existing data:

```sql
-- 1. Create personal accounts for existing users
INSERT INTO security.accounts (id, name, type, owner_user_id, created_at)
SELECT
    uuidv7(),
    display_name || '''s Account',
    'PERSONAL',
    id,
    created_at
FROM security.users
WHERE NOT EXISTS (
    SELECT 1 FROM security.accounts a
    WHERE a.owner_user_id = users.id
);

-- 2. Create memberships
INSERT INTO security.account_memberships (id, account_id, user_id, role, joined_at)
SELECT
    uuidv7(),
    a.id,
    a.owner_user_id,
    'OWNER',
    a.created_at
FROM security.accounts a
WHERE a.owner_user_id IS NOT NULL;

-- 3. Update resource ownership from USER to ACCOUNT
UPDATE security.resource_ownership o
SET
    principal_type = 'ACCOUNT',
    principal_id = (
        SELECT a.id FROM security.accounts a
        WHERE a.owner_user_id = o.principal_id
    )
WHERE o.principal_type = 'USER'
  AND EXISTS (
      SELECT 1 FROM security.accounts a
      WHERE a.owner_user_id = o.principal_id
  );
```

---

## Summary

| Aspect | Design |
|--------|--------|
| **Owner of resources** | Account (not User directly) |
| **User relationship** | User is member of Account(s) |
| **Billing entity** | Account |
| **Multi-user access** | Through Account membership |
| **Cross-account sharing** | Direct USER principal type |
| **Groups** | Scoped within Account |
| **Personal accounts** | AccountType.PERSONAL, single user |

This aligns with Stripe, Shopify, Slack, and other successful platforms.

---

## Related Documentation

- [ADR-0009: Resource Ownership Strategy](../../../92-adr/0009-resource-ownership-strategy.md)
- [Resource Ownership Implementation](./resource-ownership.md)
- [Distributed Ownership Patterns](../distributed-resource-ownership-patterns.md)
- [Authorization Overview](./README.md)
