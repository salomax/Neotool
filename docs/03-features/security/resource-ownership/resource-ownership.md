# Resource Ownership and Access Control

> **Object-level authorization for fine-grained resource access control across microservices**

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Implementation](#implementation)
4. [Usage Patterns](#usage-patterns)
5. [Integration with RBAC/ABAC](#integration-with-rbacabac)
6. [Performance Considerations](#performance-considerations)
7. [Migration Guide](#migration-guide)
8. [Security Best Practices](#security-best-practices)

---

## Overview

### Purpose

Resource Ownership provides **object-level access control** to answer the question: _"Can this user access THIS SPECIFIC resource?"_

This complements RBAC/ABAC which answers: _"Can this user perform THIS ACTION?"_

### Design Philosophy

- **Per-microservice ownership tables**: Each service manages ownership of its own resources
- **Account-first ownership**: Resources are primarily owned by accounts, with optional user/group grants
- **Service autonomy**: No cross-service database dependencies
- **Performance-first**: Optimized for high-throughput filtering queries
- **Defense in depth**: Works alongside RBAC/ABAC, not replacing them

### Key Capabilities

| Capability | Description |
|------------|-------------|
| **User Ownership** | Resources tied to individual users (survives group changes) |
| **Account Ownership** | Resources tied to accounts (access revoked when user loses account membership) |
| **Group Ownership** | Optional grants to groups inside an account for team-level access |
| **Efficient Filtering** | Query thousands of resources with single indexed SQL query |
| **Resource Sharing** | Optional: Share resources with explicit grants |
| **Time-based Access** | Optional: Temporary access with expiration |
| **Audit Trail** | Track who granted access and when |

---

## Architecture

### System Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     Client Layer                            │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐           │
│  │  Web App   │  │ Mobile App │  │  Service   │           │
│  └────────────┘  └────────────┘  └────────────┘           │
└─────────────────────────────────────────────────────────────┘
                         │
                         ▼ JWT Token
┌─────────────────────────────────────────────────────────────┐
│              GraphQL / REST API Layer                       │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  RBAC Permission Check (Action-Level)                │  │
│  │  "Can user PERFORM action?"                          │  │
│  │  - GraphQL: GraphQLPermissionHelper                  │  │
│  │  - REST: @RequiresAuthorization                      │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                         │
                         ▼ userId + currentAccountId
┌─────────────────────────────────────────────────────────────┐
│                  Service Layer                              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  Resource Ownership Check (Object-Level)             │  │
│  │  "Can user ACCESS this resource?"                    │  │
│  │  - ResourceAccessControl.requireAccess()             │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                         │
                         ▼ SQL Query
┌─────────────────────────────────────────────────────────────┐
│              Per-Service Data Layer                         │
│                                                              │
│  transaction_service.resource_ownership                     │
│  document_service.resource_ownership                        │
│  asset_service.resource_ownership                           │
└─────────────────────────────────────────────────────────────┘
```

### Authorization Layers

```
Request
  ↓
┌────────────────────────────────────────┐
│ Layer 1: Authentication                │
│ "Who are you?"                         │
│ - JWT validation                       │
│ - Principal extraction                 │
└────────────────────────────────────────┘
  ↓
┌────────────────────────────────────────┐
│ Layer 2: Account Context & Membership  │
│ "Which account are you acting in?"     │
│ - current_account from JWT             │
│ - ACTIVE membership validation         │
└────────────────────────────────────────┘
  ↓
┌────────────────────────────────────────┐
│ Layer 3: RBAC (Action-Level)           │
│ "Can you perform THIS ACTION?"         │
│ - GraphQL Layer / REST Interceptor     │
│ - Permission: "transaction:update"     │
└────────────────────────────────────────┘
  ↓
┌────────────────────────────────────────┐
│ Layer 4: Resource Ownership            │
│ "Can you access THIS RESOURCE?"        │
│ - Service Layer                        │
│ - Check: account/user grant matches row│
└────────────────────────────────────────┘
  ↓
Business Logic
```

**Key Insight:** Each layer serves a distinct purpose (context, action, row) and cannot be bypassed.

---

## Implementation

### Database Schema (Per-Service)

Each microservice has its own `resource_ownership` table:

```sql
-- Example: transaction_service.resource_ownership
CREATE TABLE resource_ownership (
    id UUID PRIMARY KEY DEFAULT uuid_v7(),

    -- Resource identification
    resource_type TEXT NOT NULL,   -- 'bank_transaction', 'invoice', etc.
    resource_id UUID NOT NULL,

    -- Access grant
    access_type TEXT NOT NULL DEFAULT 'OWNER',      -- 'OWNER', 'EDIT', 'VIEW'
    grant_type TEXT NOT NULL DEFAULT 'DIRECT',      -- 'DIRECT', 'SHARED'
    principal_type TEXT NOT NULL DEFAULT 'ACCOUNT', -- 'USER', 'ACCOUNT', 'GROUP'
    principal_id UUID NOT NULL,                     -- user_id, account_id, or group_id
    permissions TEXT[],                             -- optional action list; NULL/empty means full access

   -- Optional: Time-based access
    valid_from TIMESTAMPTZ,
    valid_until TIMESTAMPTZ,

    -- Audit
    granted_by UUID NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    version BIGINT NOT NULL DEFAULT 0,

    -- Constraints
    CONSTRAINT chk_access_type CHECK (access_type IN ('OWNER', 'EDIT', 'VIEW')),
    CONSTRAINT chk_grant_type CHECK (grant_type IN ('DIRECT', 'SHARED')),
    CONSTRAINT chk_principal_type CHECK (principal_type IN ('USER', 'ACCOUNT', 'GROUP')),
    CONSTRAINT uq_owner_per_resource
        UNIQUE (resource_type, resource_id, access_type)
        WHERE access_type = 'OWNER'
);

-- CRITICAL: Performance indexes
CREATE INDEX idx_resource_ownership_principal
    ON resource_ownership(principal_type, principal_id, resource_type);

CREATE INDEX idx_resource_ownership_resource
    ON resource_ownership(resource_type, resource_id);

CREATE INDEX idx_resource_ownership_lookup
    ON resource_ownership(resource_type, resource_id, principal_type, principal_id, valid_until);

-- Optional: assists expiration cleanup and temporal filtering.
-- Avoid NOW() in partial index predicates.
CREATE INDEX idx_resource_ownership_valid_until
    ON resource_ownership(valid_until)
    WHERE valid_until IS NOT NULL;


```

### Shared Interface (common/security)

```kotlin
// common/security/ownership/ResourceAccessControl.kt
package io.github.salomax.neotool.common.security.ownership

/**
 * Interface for resource-level access control.
 * Each microservice implements this with its own resource_ownership table.
 */
interface ResourceAccessControl {

    /**
     * Check if user has access to a resource.
     *
     * @param userId User ID
     * @param resourceType Resource type (e.g., "bank_transaction")
     * @param resourceId Resource ID
     * @param permission Required permission (e.g., "write", "delete")
     * @return true if user or user's accounts/groups have access
     */
    fun canAccess(
        userId: UUID,
        resourceType: String,
        resourceId: UUID,
        permission: String = "write"
    ): Boolean

    /**
     * Require access to a resource, throw exception if denied.
     *
     * @throws ResourceAccessDeniedException if access denied
     */
    fun requireAccess(
        userId: UUID,
        resourceType: String,
        resourceId: UUID,
        permission: String = "write"
    )

    /**
     * Find all resource IDs accessible to user.
     * Used for filtering list queries.
     *
     * @param userId User ID
     * @param resourceType Resource type
     * @return List of accessible resource IDs
     */
    fun findAccessibleResources(
        userId: UUID,
        resourceType: String
    ): List<UUID>

    /**
     * Grant ownership when creating a resource.
     *
     * @param resourceType Resource type
     * @param resourceId Resource ID
     * @param ownerId Owner ID (user, account, or group)
     * @param ownerType USER, ACCOUNT, or GROUP
     * @param grantedBy User granting access
     */
    fun grantOwnership(
        resourceType: String,
        resourceId: UUID,
        ownerId: UUID,
        ownerType: PrincipalType,
        grantedBy: UUID
    )

    /**
     * Share resource with user, account, or group.
     *
     * @param resourceType Resource type
     * @param resourceId Resource ID
     * @param shareWithId User, account, or group to share with
     * @param shareWithType USER, ACCOUNT, or GROUP
     * @param permissions Specific permissions to grant
     * @param grantedBy User granting access
     */
    fun shareResource(
        resourceType: String,
        resourceId: UUID,
        shareWithId: UUID,
        shareWithType: PrincipalType,
        permissions: List<String>,
        grantedBy: UUID
    )

    /**
     * Revoke access to a resource.
     *
     * @param resourceType Resource type
     * @param resourceId Resource ID
     * @param principalId User, account, or group to revoke
     */
    fun revokeAccess(
        resourceType: String,
        resourceId: UUID,
        principalId: UUID
    )
}

enum class AccessType { OWNER, EDIT, VIEW }
enum class GrantType { DIRECT, SHARED }
enum class PrincipalType { USER, ACCOUNT, GROUP }

/**
 * Exception thrown when resource access is denied.
 */
class ResourceAccessDeniedException(
    message: String,
    val userId: UUID? = null,
    val resourceType: String? = null,
    val resourceId: UUID? = null
) : RuntimeException(message)
```

### Service Implementation Example

```kotlin
// transaction-service/ownership/TransactionResourceAccessControl.kt
@Singleton
class TransactionResourceAccessControl(
    private val repository: TransactionResourceOwnershipRepository,
    private val accountMembershipRepository: AccountMembershipRepository,
    private val groupMembershipRepository: GroupMembershipRepository
) : ResourceAccessControl {

    override fun canAccess(
        userId: UUID,
        resourceType: String,
        resourceId: UUID,
        permission: String
    ): Boolean {
        val principalIds = getPrincipalIds(userId)

        return repository.existsByResourceAndPrincipalWithPermission(
            resourceType = resourceType,
            resourceId = resourceId,
            principalIds = principalIds,
            permission = permission
        )
    }

    override fun requireAccess(
        userId: UUID,
        resourceType: String,
        resourceId: UUID,
        permission: String
    ) {
        if (!canAccess(userId, resourceType, resourceId, permission)) {
            throw ResourceAccessDeniedException(
                message = "Access denied to $resourceType:$resourceId",
                userId = userId,
                resourceType = resourceType,
                resourceId = resourceId
            )
        }
    }

    override fun findAccessibleResources(
        userId: UUID,
        resourceType: String
    ): List<UUID> {
        val principalIds = getPrincipalIds(userId)

        return repository.findAccessibleResourceIds(
            resourceType = resourceType,
            principalIds = principalIds
        )
    }

    /**
     * Get all principal IDs for user (user + account IDs; +group IDs when enabled)
     */
    private fun getPrincipalIds(userId: UUID): List<UUID> {
        val accountIds = accountMembershipRepository
            .findActiveByUserId(userId)
            .map { it.accountId }

        val userGroupIds = groupMembershipRepository
            .findActiveGroupsByUserId(userId)
            .map { it.groupId }

        return listOf(userId) + accountIds + userGroupIds
    }

    // ... other methods
}
```

---

## Usage Patterns

### Pattern 1: Create Resource with Ownership

```kotlin
@Singleton
class BankTransactionService(
    private val transactionRepo: BankTransactionRepository,
    private val resourceAccessControl: ResourceAccessControl
) {

    fun create(
        userId: UUID,
        amount: BigDecimal,
        ownerType: PrincipalType,
        ownerId: UUID
    ): BankTransaction {
        // Validate ownership request
        when (ownerType) {
            PrincipalType.USER -> {
                require(ownerId == userId) {
                    "Can only create user-owned resources for yourself"
                }
            }
            PrincipalType.ACCOUNT -> {
                // Validate user has ACTIVE membership in account
                // (implementation omitted for brevity)
            }
            PrincipalType.GROUP -> {
                // Validate user is member of group
                // (implementation omitted for brevity)
            }
        }

        // Create resource
        val entity = transactionRepo.save(
            BankTransactionEntity(amount = amount)
        )

        // Grant ownership
        resourceAccessControl.grantOwnership(
            resourceType = "bank_transaction",
            resourceId = entity.id,
            ownerId = ownerId,
            ownerType = ownerType,
            grantedBy = userId
        )

        return entity.toDomain()
    }
}
```

### Pattern 2: Update Resource (Validate Ownership)

```kotlin
fun update(
    userId: UUID,
    transactionId: UUID,
    amount: BigDecimal?,
    version: Long
): BankTransaction {
    // ✅ OWNERSHIP CHECK - Service layer
    resourceAccessControl.requireAccess(
        userId = userId,
        resourceType = "bank_transaction",
        resourceId = transactionId,
        permission = "write"
    )

    // Fetch and update
    val existing = transactionRepo.findById(transactionId)
        .orElseThrow { ResourceNotFoundException("Transaction not found") }

    require(existing.version == version) { "Version mismatch" }

    val updated = existing.copy(
        amount = amount ?: existing.amount,
        updatedAt = Instant.now()
    )

    return transactionRepo.save(updated).toDomain()
}
```

### Pattern 3: List Accessible Resources

```kotlin
fun list(userId: UUID, pageable: Pageable): Page<BankTransaction> {
    // Get accessible resource IDs (FAST indexed query)
    val accessibleIds = resourceAccessControl.findAccessibleResources(
        userId = userId,
        resourceType = "bank_transaction"
    )

    if (accessibleIds.isEmpty()) {
        return Page.empty()
    }

    // Fetch transactions
    return transactionRepo.findAllById(accessibleIds, pageable)
        .map { it.toDomain() }
}
```

### Pattern 4: GraphQL Resolver Integration

```kotlin
@Singleton
class BankTransactionMutationResolver(
    private val transactionService: BankTransactionService,
    private val requestPrincipalProvider: RequestPrincipalProvider,
    private val authorizationChecker: AuthorizationChecker
) {

    fun updateBankTransaction(
        env: DataFetchingEnvironment,
        input: UpdateBankTransactionInput
    ): BankTransactionDTO {
        // ✅ Gate 2: RBAC check (GraphQL layer)
        return GraphQLPermissionHelper.withPermissionAndPrincipal(
            env = env,
            action = "transaction:bank_transaction:update",  // RBAC permission
            requestPrincipalProvider = requestPrincipalProvider,
            authorizationChecker = authorizationChecker
        ) { principal ->
            // ✅ Gate 1: current account context is already present in principal
            // ✅ Gate 3: Service validates row-level resource ownership
            val transaction = transactionService.update(
                userId = principal.userId,
                transactionId = UUID.fromString(input.id),
                amount = input.amount?.let { BigDecimal(it) },
                version = input.version
            )

            BankTransactionDTO.fromDomain(transaction)
        }
    }
}
```

---

## Integration with RBAC/ABAC

### Four-Layer Security Model (No Overlap)

```
┌─────────────────────────────────────────────────────────────┐
│ Layer 1: Account Context & Membership                        │
│ Question: "Which account is this request for?"               │
│ Check: `current_account` present + membership ACTIVE         │
│ Location: Request context / service guard                    │
└─────────────────────────────────────────────────────────────┘
                         ↓ PASS
┌─────────────────────────────────────────────────────────────┐
│ Layer 2: RBAC (GraphQL/REST)                                │
│ Question: "Can user PERFORM this action?"                   │
│ Check: User has permission "transaction:update"             │
│ Location: GraphQL Resolver / REST Interceptor               │
└─────────────────────────────────────────────────────────────┘
                         ↓ PASS
┌─────────────────────────────────────────────────────────────┐
│ Layer 3: Resource Ownership (Service)                       │
│ Question: "Can user ACCESS this resource?"                  │
│ Check: current account/user has matching row grant          │
│ Location: Service Layer                                     │
└─────────────────────────────────────────────────────────────┘
                         ↓ PASS
┌─────────────────────────────────────────────────────────────┐
│ Layer 4: ABAC (Optional - Service)                         │
│ Question: "Under what CONDITIONS can user act?"            │
│ Check: Resource state, context attributes                   │
│ Location: Service Layer (via AuthorizationManager)          │
└─────────────────────────────────────────────────────────────┘
                         ↓ PASS
                  Business Logic
```

### When to Use Each Layer

| Layer | Use When | Example |
|-------|----------|---------|
| **Account Context** | Binding request to tenant scope | "Is user active in account A?" |
| **RBAC** | Checking action-level permissions | "Can user create transactions?" |
| **Resource Ownership** | Checking object-level access | "Can user update transaction #123 in account A?" |
| **ABAC** | Checking complex business rules | "Can user approve transactions >$10k in draft status?" |

### Example: All Four Layers Together

```kotlin
fun approveTransaction(
    userId: UUID,
    currentAccountId: UUID,
    transactionId: UUID
): BankTransaction {
    // ✅ Account context + membership gate
    accountAccessControl.requireActiveMembership(
        userId = userId,
        accountId = currentAccountId
    )

    // ✅ RBAC already checked in GraphQL layer
    // Permission: "transaction:approve"

    // ✅ Resource ownership check (row-level)
    resourceAccessControl.requireAccess(
        userId = userId,
        resourceType = "bank_transaction",
        resourceId = transactionId,
        permission = "write"
    )

    // Fetch transaction
    val transaction = transactionRepo.findById(transactionId)
        .orElseThrow { ResourceNotFoundException() }

    // ✅ ABAC check (business rule)
    if (transaction.amount > BigDecimal("10000")) {
        authorizationManager.require(
            principal = RequestPrincipal(userId = userId, ...),
            action = "transaction:approve:large",
            resourceType = "bank_transaction",
            resourceId = transactionId,
            resourceAttributes = mapOf(
                "amount" to transaction.amount,
                "status" to transaction.status
            )
        )
    }

    // Business logic
    return transaction.copy(status = "APPROVED")
}
```

---

## Performance Considerations

### Query Performance

**Single Resource Check** (O(log n) with index):
```sql
SELECT COUNT(*) > 0
FROM resource_ownership
WHERE resource_type = 'bank_transaction'
  AND resource_id = :resourceId
  -- Phase 1: account/user principals only. Add group IDs in Phase 2 if enabled.
  AND principal_id IN (:userId, :accountId)
  AND (
      permissions IS NULL
      OR cardinality(permissions) = 0
      OR :permission = ANY(permissions)
  );
```

**List Accessible Resources** (O(log n) with index):
```sql
SELECT DISTINCT resource_id
FROM resource_ownership
WHERE resource_type = 'bank_transaction'
  -- Phase 1: account/user principals only. Add group IDs in Phase 2 if enabled.
  AND principal_id IN (:userId, :accountId);
```

### Index Strategy

```sql
-- CRITICAL: Fast lookup by principal (for list queries)
CREATE INDEX idx_resource_ownership_principal
    ON resource_ownership(principal_type, principal_id, resource_type);

-- CRITICAL: Fast lookup by resource (for single checks)
CREATE INDEX idx_resource_ownership_resource
    ON resource_ownership(resource_type, resource_id);

-- CRITICAL: Composite index for access checks
CREATE INDEX idx_resource_ownership_lookup
    ON resource_ownership(resource_type, resource_id, principal_type, principal_id, valid_until);
```

### Caching Strategy

```kotlin
// Optional: Cache resolved account/group principals per user
data class PrincipalCacheEntry(
    val accountIds: List<UUID>,
    val groupIds: List<UUID>
)

private val principalCache = ConcurrentHashMap<UUID, PrincipalCacheEntry>()

fun getPrincipalIds(userId: UUID): List<UUID> {
    val cached = principalCache[userId]
    if (cached != null) {
        return listOf(userId) + cached.accountIds + cached.groupIds
    }

    val accountIds = accountMembershipRepository
        .findActiveByUserId(userId)
        .map { it.accountId }

    val groupIds = groupMembershipRepository
        .findActiveGroupsByUserId(userId)
        .map { it.groupId }

    principalCache[userId] = PrincipalCacheEntry(accountIds, groupIds)
    return listOf(userId) + accountIds + groupIds
}
```

---

## Migration Guide

### Step 1: Add Schema to Each Service

```sql
-- Run in each service database
-- Example: transaction_service, document_service, asset_service

CREATE TABLE resource_ownership (
    -- ... schema from above
);

-- Create indexes
CREATE INDEX idx_resource_ownership_principal ...
CREATE INDEX idx_resource_ownership_resource ...
CREATE INDEX idx_resource_ownership_lookup ...
```

### Step 2: Implement ResourceAccessControl

```kotlin
// Each service implements the interface
@Singleton
class TransactionResourceAccessControl(...) : ResourceAccessControl {
    // Implementation
}
```

### Step 3: Update Service Methods

**Before (no ownership check):**
```kotlin
fun update(transactionId: UUID, amount: BigDecimal): BankTransaction {
    val existing = transactionRepo.findById(transactionId).orElseThrow()
    return transactionRepo.save(existing.copy(amount = amount))
}
```

**After (with ownership check):**
```kotlin
fun update(
    userId: UUID,  // ← Add userId parameter
    transactionId: UUID,
    amount: BigDecimal
): BankTransaction {
    // ✅ Add ownership check
    resourceAccessControl.requireAccess(
        userId = userId,
        resourceType = "bank_transaction",
        resourceId = transactionId,
        permission = "write"
    )

    val existing = transactionRepo.findById(transactionId).orElseThrow()
    return transactionRepo.save(existing.copy(amount = amount))
}
```

### Step 4: Migrate Existing Data

```sql
-- Grant ownership for existing resources
-- Example: Make creator the owner

INSERT INTO resource_ownership (
    resource_type,
    resource_id,
    access_type,
    grant_type,
    principal_type,
    principal_id,
    permissions,
    granted_by,
    granted_at
)
SELECT
    'bank_transaction',
    id,
    'OWNER',
    'DIRECT',
    'ACCOUNT',
    account_id,          -- Assuming account_id already exists
    ARRAY['read', 'write', 'delete'],
    created_by_user_id,
    created_at
FROM bank_transaction
WHERE account_id IS NOT NULL;
```

---

## Security Best Practices

### ✅ DO

1. **Always validate ownership in service layer**
   - Never skip ownership checks
   - Service layer is the single source of truth

2. **Use indexed queries**
   - Ensure proper indexes on resource_ownership table
   - Monitor query performance

3. **Grant minimal permissions**
   - NULL or empty permissions array = full access
   - Specify explicit permissions when sharing

4. **Audit ownership changes**
   - Track who granted access (granted_by)
   - Track when access was granted (granted_at)

5. **Handle account/group membership changes gracefully**
   - Account/group grants automatically stop applying when membership becomes inactive
   - No cleanup needed

### ❌ DON'T

1. **Don't check ownership in GraphQL/REST layer**
   - Service layer is responsible for ownership
   - API layer handles RBAC only

2. **Don't bypass ownership checks**
   - Even for "admin" users
   - Create explicit admin sharing if needed

3. **Don't share resource_ownership tables across services**
   - Each service has its own table
   - Maintains service autonomy

4. **Don't hard-code ownership logic**
   - Use ResourceAccessControl interface
   - Keep logic centralized

5. **Don't forget to grant ownership on create**
   - Every resource must have an owner
   - Grant ownership immediately after creation

---

## Industry Standards & References

This implementation follows established patterns:

- **ACL (Access Control Lists)**: Per-resource permission lists
- **Google Zanzibar**: Relationship-based access control (simplified implementation)
- **Multi-Tenancy Patterns**: Resource isolation using discriminator pattern
- **NIST ABAC**: Attribute-based access control integration

**References:**
- [Zanzibar: Google's Authorization System](https://research.google/pubs/pub48190/)
- [NIST SP 800-162: ABAC Guide](https://csrc.nist.gov/publications/detail/sp/800-162/final)
- [Multi-Tenancy Architecture Patterns](https://docs.microsoft.com/en-us/azure/architecture/guide/multitenant/)

---

## Related Documentation

- [Authorization Overview](./README.md) - RBAC and ABAC architecture
- [Security Module](../README.md) - Complete security documentation
- [Service Architecture](../../../02-architecture/) - Microservices design
- [Database Schema Standards](../../../04-domain/database-schema-standards.md)

---

## Future Enhancements

### Planned

- [ ] **GraphQL API for resource sharing**
  - Share resources via GraphQL mutations
  - UI for resource sharing management

- [ ] **Ownership transfer**
  - Transfer ownership between users
  - Transfer between user/account/group

- [ ] **Access analytics**
  - Dashboard showing resource access patterns
  - Identify unused or over-shared resources

### Under Consideration

- [ ] **Hierarchical ownership**
  - Parent-child resource relationships
  - Inherit access from parent resources

- [ ] **Delegated ownership**
  - Allow owners to delegate management
  - Separate "owner" from "manager"

---

**Last Updated**: 2026-02-10
**Version**: 1.1
**Status**: Active Development
