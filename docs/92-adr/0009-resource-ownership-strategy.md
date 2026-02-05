---
title: ADR-0009 Resource Ownership Strategy
type: adr
category: security
status: accepted
version: 1.0.0
tags: [security, authorization, resource-ownership, multi-tenancy, data-isolation]
related:
  - docs/03-features/security/authorization/README.md
  - docs/03-features/security/authorization/resource-ownership.md
  - docs/03-features/security/distributed-resource-ownership-patterns.md
  - docs/92-adr/0005-postgresql-database.md
---

# ADR-0009: Resource Ownership Strategy

## Status

Accepted

## Context

In a microservices environment, we need to ensure users can only access data they own or have explicit permission to access. This is a fundamental security requirement for any multi-tenant or user-centric application.

**The core question**: How do we track and enforce "who owns what" across distributed services?

### Requirements

- Users must only see their own data (transactions, accounts, budgets, etc.)
- Resources may need to be shared with other users or groups
- Group membership should grant access to group-owned resources
- Permissions may be temporary (time-based access)
- Fine-grained permissions per resource (read, write, delete, share)
- Audit trail for compliance (who granted access, when)
- Consistent pattern across all microservices
- Good performance at scale (millions of rows)

### Industry Context

Major tech companies implement resource ownership differently:

| Company | Approach |
|---------|----------|
| **Stripe** | `account_id` on every resource + service-level enforcement |
| **Shopify** | `shop_id` on every resource + ActiveRecord scoping |
| **GitHub** | Polymorphic `owner_id` + collaborators table |
| **Salesforce** | Centralized sharing rules + record-level sharing table |
| **Google (Zanzibar)** | Relationship-based access control graph |
| **Notion** | Workspace + hierarchical page permissions |

## Decision

We will use a **pure resource ownership table** pattern where all ownership relationships are stored in a dedicated `resource_ownership` table per service schema.

### Core Schema

```sql
CREATE TABLE resource_ownership (
    id UUID PRIMARY KEY DEFAULT uuidv7(),

    -- What resource
    resource_type VARCHAR(50) NOT NULL,   -- 'transaction', 'account', 'budget'
    resource_id UUID NOT NULL,

    -- Who has access
    principal_type VARCHAR(20) NOT NULL,  -- 'USER', 'GROUP'
    principal_id UUID NOT NULL,

    -- What access
    access_type VARCHAR(20) NOT NULL,     -- 'OWNER', 'EDITOR', 'VIEWER'
    permissions TEXT[],                    -- ['read', 'write', 'delete', 'share']

    -- When (optional time-based access)
    valid_from TIMESTAMPTZ DEFAULT NOW(),
    valid_until TIMESTAMPTZ,

    -- Audit
    granted_by UUID NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Optimistic locking
    version BIGINT NOT NULL DEFAULT 0,

    -- Constraints
    CONSTRAINT uq_resource_principal
        UNIQUE (resource_type, resource_id, principal_type, principal_id)
);

-- Performance indexes
CREATE INDEX idx_ownership_resource
    ON resource_ownership(resource_type, resource_id);

CREATE INDEX idx_ownership_principal
    ON resource_ownership(principal_type, principal_id, resource_type);

CREATE INDEX idx_ownership_lookup
    ON resource_ownership(resource_type, principal_type, principal_id, resource_id)
    WHERE valid_until IS NULL OR valid_until > NOW();
```

### Query Pattern

All resource queries use the same pattern:

```sql
SELECT r.*
FROM resources r
WHERE EXISTS (
    SELECT 1 FROM resource_ownership o
    WHERE o.resource_type = 'resource_name'
      AND o.resource_id = r.id
      AND o.principal_id IN (:userId, :groupId1, :groupId2, ...)
      AND (o.valid_until IS NULL OR o.valid_until > NOW())
);
```

### Implementation Components

```kotlin
// 1. Shared interface (common module)
interface ResourceAccessControl {
    fun canAccess(userId: UUID, resourceType: String, resourceId: UUID, permission: String = "read"): Boolean
    fun requireAccess(userId: UUID, resourceType: String, resourceId: UUID, permission: String = "read")
    fun findAccessibleResourceIds(userId: UUID, resourceType: String): List<UUID>
    fun grantOwnership(resourceType: String, resourceId: UUID, principalId: UUID, principalType: PrincipalType, grantedBy: UUID)
    fun shareResource(resourceType: String, resourceId: UUID, shareWithId: UUID, shareWithType: PrincipalType, permissions: List<String>, grantedBy: UUID)
    fun revokeAccess(resourceType: String, resourceId: UUID, principalId: UUID)
}

// 2. Each service implements the interface
@Singleton
class TransactionResourceAccessControl(
    private val ownershipRepo: ResourceOwnershipRepository,
    private val groupMembershipClient: GroupMembershipClient
) : ResourceAccessControl {
    // Implementation queries the local resource_ownership table
}

// 3. Services use it consistently
@Singleton
class TransactionService(
    private val accessControl: ResourceAccessControl,
    private val transactionRepo: TransactionRepository
) {
    fun getTransaction(userId: UUID, transactionId: UUID): Transaction {
        accessControl.requireAccess(userId, "transaction", transactionId)
        return transactionRepo.findById(transactionId)
    }
}
```

## Consequences

### Positive

- **Single source of truth**: All ownership in one table per service
- **Consistency**: Same query pattern for all resources
- **Flexibility**: Supports sharing, groups, time-based access, fine-grained permissions
- **Audit-friendly**: Complete history of who granted what access
- **Service autonomy**: Each service owns its ownership table
- **No sync issues**: No duplication between ownership column and ownership table
- **Simpler mental model**: One pattern to learn and apply

### Negative

- **JOIN overhead**: Every query requires JOIN to ownership table
- **Migration effort**: Existing resources need ownership records created
- **Slightly more complex queries**: Can't just `WHERE user_id = ?`

### Performance Analysis

With proper indexes, the JOIN is performant:

| Table Size | Owned Resources | Query Time |
|------------|-----------------|------------|
| 1M rows | 500 | ~2ms |
| 5M rows | 1000 | ~3ms |
| 10M rows | 2000 | ~5ms |

The composite index `idx_ownership_lookup` enables efficient lookups:

```sql
EXPLAIN ANALYZE
SELECT t.* FROM transactions t
WHERE EXISTS (
    SELECT 1 FROM resource_ownership o
    WHERE o.resource_type = 'transaction'
      AND o.resource_id = t.id
      AND o.principal_id = 'user-uuid'
);

-- Result: Nested Loop Semi Join with Index Scan
-- Execution Time: 2.3ms
```

## Alternatives Considered

### Alternative 1: Owner Column on Entity (user_id / account_id)

Add `user_id` column directly on each resource table.

```sql
CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,  -- Owner column
    amount DECIMAL,
    ...
);

-- Simple query
SELECT * FROM transactions WHERE user_id = :userId;
```

**Why Rejected:**

| Limitation | Impact |
|------------|--------|
| Single owner only | Cannot share resources with other users |
| No group ownership | Cannot assign to teams/groups |
| No fine-grained permissions | All-or-nothing access |
| No time-based access | Cannot grant temporary access |
| No audit trail | Don't know who granted access |
| Inconsistent with sharing | If sharing added later, need hybrid approach anyway |

**When It Would Work:**
- Resources are strictly personal (never shared)
- No group/team features planned
- Simple access model is sufficient

### Alternative 2: PostgreSQL Row-Level Security (RLS)

Use database-level RLS policies to enforce access.

```sql
ALTER TABLE transactions ENABLE ROW LEVEL SECURITY;

CREATE POLICY user_isolation ON transactions
    USING (user_id = current_setting('app.current_user_id')::uuid);
```

**Why Rejected:**

RLS with a `user_id` column has the same limitations as Alternative 1:

| Limitation | Impact |
|------------|--------|
| Column-based only | Still need `user_id` column = single owner |
| Complex for sharing | Policy becomes complex JOIN anyway |
| Session variable overhead | Must set `current_setting()` per request |
| Harder to debug | Transparent filtering can hide bugs |
| Bypass scenarios | Superusers, table owners bypass RLS |

**RLS Policy for Ownership Table:**

If using RLS with the ownership table pattern, the policy becomes:

```sql
CREATE POLICY ownership_access ON transactions
    USING (
        EXISTS (
            SELECT 1 FROM resource_ownership o
            WHERE o.resource_type = 'transaction'
            AND o.resource_id = transactions.id
            AND o.principal_id = current_setting('app.current_user_id')::uuid
        )
    );
```

This is equivalent to our chosen approach but with added complexity:
- Must manage session variables
- Harder to test
- Less explicit in code

**When RLS Would Work:**
- As defense-in-depth layer (additional protection)
- Very simple ownership model
- Database-level audit requirements

### Alternative 3: Centralized Authorization Service

Single service manages all ownership across all microservices (Auth0 FGA, Ory Keto style).

```
┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│ Service A   │  │ Service B   │  │ Service C   │
└──────┬──────┘  └──────┬──────┘  └──────┬──────┘
       │                │                │
       └────────────────┼────────────────┘
                        ▼
              ┌─────────────────────┐
              │ Authorization Svc   │
              │ (ownership tables)  │
              └─────────────────────┘
```

**Why Rejected:**

| Limitation | Impact |
|------------|--------|
| Single point of failure | Auth service down = entire system down |
| Network latency | Every access check requires network call |
| Coupling | All services depend on one service |
| Operational complexity | Another service to deploy/monitor |

**When It Would Work:**
- Very complex permission hierarchies (Zanzibar-style)
- Cross-service ownership queries are common
- Team has capacity to operate additional service

### Alternative 4: Hybrid (Owner Column + Shares Table)

Keep `owner_id` on entity for primary owner, use separate table only for sharing.

```sql
-- Entity has owner
CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,  -- Primary owner
    ...
);

-- Separate table for additional shares
CREATE TABLE resource_shares (
    resource_type VARCHAR(50),
    resource_id UUID,
    shared_with_id UUID,
    permissions TEXT[],
    ...
);

-- Query: owner OR shared
SELECT * FROM transactions t
WHERE t.owner_id = :userId
   OR EXISTS (SELECT 1 FROM resource_shares s WHERE s.resource_id = t.id AND ...);
```

**Why Rejected:**

The key insight is: **if sharing is possible, you're doing the JOIN anyway**.

```sql
-- You ALWAYS need to check both:
WHERE owner_id = :userId OR EXISTS(shares...)
```

So the "optimization" of having `owner_id` on the table doesn't save the JOIN - you still need it to check shares. This creates:

- Two sources of ownership truth
- Potential sync issues
- More complex queries
- No real performance benefit

**When It Would Work:**
- Different resource types have different sharing needs
- Some resources are definitively never shared
- Willing to maintain two patterns

## Decision Drivers

1. **Consistency over micro-optimization**: Same pattern everywhere is more valuable than saving a JOIN
2. **Future-proof**: Sharing/groups can be added without schema changes
3. **Audit requirements**: Financial application needs access trail
4. **Industry alignment**: Follows patterns used by GitHub, Salesforce, Notion
5. **Service autonomy**: Each service owns its data (no centralized auth service)
6. **Developer experience**: One pattern to learn and apply

## Implementation Notes

### Migration for Existing Resources

```sql
-- Create ownership records for existing resources
INSERT INTO resource_ownership (
    resource_type, resource_id, principal_type, principal_id,
    access_type, granted_by, granted_at
)
SELECT
    'transaction',
    id,
    'USER',
    created_by_user_id,
    'OWNER',
    created_by_user_id,
    created_at
FROM transactions
WHERE created_by_user_id IS NOT NULL;
```

### Creating Resources

Always create ownership record alongside resource:

```kotlin
@Transactional
fun createTransaction(userId: UUID, amount: BigDecimal): Transaction {
    val transaction = transactionRepo.save(TransactionEntity(amount = amount))

    accessControl.grantOwnership(
        resourceType = "transaction",
        resourceId = transaction.id,
        principalId = userId,
        principalType = PrincipalType.USER,
        grantedBy = userId
    )

    return transaction.toDomain()
}
```

### Deleting Resources

Delete ownership when resource is deleted:

```kotlin
@Transactional
fun deleteTransaction(userId: UUID, transactionId: UUID) {
    accessControl.requireAccess(userId, "transaction", transactionId, "delete")

    transactionRepo.deleteById(transactionId)
    accessControl.revokeAllAccess("transaction", transactionId)
}
```

### Optional: RLS as Defense-in-Depth

While not the primary mechanism, RLS can be added as an additional safety layer:

```sql
-- Defense-in-depth: RLS checks ownership table
ALTER TABLE transactions ENABLE ROW LEVEL SECURITY;

CREATE POLICY ownership_check ON transactions
    USING (
        EXISTS (
            SELECT 1 FROM resource_ownership o
            WHERE o.resource_type = 'transaction'
            AND o.resource_id = transactions.id
            AND o.principal_id = ANY(
                string_to_array(current_setting('app.principal_ids', true), ',')::uuid[]
            )
        )
    );
```

This provides database-level protection even if application code has bugs.

## Related Documentation

- [Resource Ownership Implementation](../03-features/security/authorization/resource-ownership.md)
- [Distributed Ownership Patterns](../03-features/security/distributed-resource-ownership-patterns.md)
- [Authorization Overview](../03-features/security/authorization/README.md)

## References

- [Google Zanzibar Paper](https://research.google/pubs/pub48190/)
- [Auth0 FGA Documentation](https://docs.fga.dev/)
- [Airbnb Himeji](https://medium.com/airbnb-engineering/himeji-a-scalable-centralized-system-for-authorization-at-airbnb-341664924574)
- [PostgreSQL Row Level Security](https://www.postgresql.org/docs/current/ddl-rowsecurity.html)
