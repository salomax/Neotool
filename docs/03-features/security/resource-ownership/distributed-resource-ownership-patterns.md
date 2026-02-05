---
title: Distributed Resource Ownership Patterns
type: architecture
category: security
status: reference
version: 1.0.0
tags: [security, authorization, microservices, distributed-systems, zanzibar, resource-ownership]
ai_optimized: true
search_keywords: [resource ownership, distributed authorization, zanzibar, relationship-based access, multi-tenant, microservices security]
related:
  - docs/03-features/security/authorization/resource-ownership.md
  - docs/03-features/security/row-level-security-blueprint.md
  - docs/03-features/security/authorization/README.md
---

# Distributed Resource Ownership Patterns

## Overview

In a microservices architecture, determining "who owns what" becomes complex when resources are distributed across services, each with its own database. This document explores how the industry solves resource ownership at scale.

### The Core Problem

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│  Service A  │     │  Service B  │     │  Service C  │
│  (Orders)   │     │  (Invoices) │     │  (Payments) │
│             │     │             │     │             │
│  user owns  │────▶│  user owns  │────▶│  user owns  │
│  order #1   │     │  invoice #1 │     │  payment #1 │
└─────────────┘     └─────────────┘     └─────────────┘
       ▲                   ▲                   ▲
       │                   │                   │
       └───────── How to manage consistently? ─┘
```

**Key Questions:**
- Where does ownership data live?
- How do you check ownership across services?
- How do you maintain consistency when ownership changes?
- How do you handle cross-resource relationships?

---

## Industry Approaches

### Comparison Matrix

| Approach | Used By | Consistency | Latency | Complexity | Scale |
|----------|---------|-------------|---------|------------|-------|
| **Per-Service Tables** | Stripe, Shopify | Eventual | Low | Low | Medium |
| **Centralized Service** | Auth0 FGA, Ory Keto | Strong | Medium | Medium | High |
| **Relationship Graph** | Google (Zanzibar), Airbnb | Strong | Low | High | Massive |
| **Event Sourcing** | Netflix, Uber | Eventual | Low | High | High |
| **Token-Embedded** | Simple APIs | N/A | Lowest | Lowest | Low |

---

## Pattern 1: Per-Service Ownership Tables

**Used by:** Stripe, Shopify, traditional microservices

Each service maintains its own `resource_ownership` table. Services are autonomous and don't share databases.

### Architecture

```
┌────────────────────────────────────────────────────────────────┐
│                        API Gateway                              │
│                   (JWT with user_id)                           │
└────────────────────────────────────────────────────────────────┘
                              │
         ┌────────────────────┼────────────────────┐
         ▼                    ▼                    ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│  Order Service  │  │ Invoice Service │  │ Payment Service │
│                 │  │                 │  │                 │
│ ┌─────────────┐ │  │ ┌─────────────┐ │  │ ┌─────────────┐ │
│ │   orders    │ │  │ │  invoices   │ │  │ │  payments   │ │
│ └─────────────┘ │  │ └─────────────┘ │  │ └─────────────┘ │
│ ┌─────────────┐ │  │ ┌─────────────┐ │  │ ┌─────────────┐ │
│ │  resource   │ │  │ │  resource   │ │  │ │  resource   │ │
│ │  ownership  │ │  │ │  ownership  │ │  │ │  ownership  │ │
│ └─────────────┘ │  │ └─────────────┘ │  │ └─────────────┘ │
└─────────────────┘  └─────────────────┘  └─────────────────┘
```

### Implementation

```kotlin
// Each service implements the same interface
interface ResourceAccessControl {
    fun canAccess(userId: UUID, resourceType: String, resourceId: UUID): Boolean
    fun grantOwnership(resourceType: String, resourceId: UUID, ownerId: UUID)
    fun revokeAccess(resourceType: String, resourceId: UUID, principalId: UUID)
}

// Order Service implementation
@Singleton
class OrderResourceAccessControl(
    private val ownershipRepo: OrderOwnershipRepository
) : ResourceAccessControl {

    override fun canAccess(userId: UUID, resourceType: String, resourceId: UUID): Boolean {
        return ownershipRepo.existsByResourceAndPrincipal(
            resourceType, resourceId, userId
        )
    }
}
```

### Cross-Service Ownership Check

When Service A needs to verify ownership in Service B:

```kotlin
// Option 1: Synchronous API call (simple but adds latency)
@Client("invoice-service")
interface InvoiceClient {
    @Get("/internal/ownership/{resourceId}/check")
    fun checkOwnership(
        @PathVariable resourceId: UUID,
        @Header("X-User-ID") userId: UUID
    ): Boolean
}

// Option 2: Ownership claim in JWT (no extra call)
// JWT contains: { "owned_invoices": ["uuid1", "uuid2", ...] }
// Limited by token size

// Option 3: Shared cache (Redis)
// Each service publishes ownership to shared cache
```

### Pros & Cons

| Pros | Cons |
|------|------|
| ✅ Service autonomy | ❌ Cross-service queries require API calls |
| ✅ Simple to implement | ❌ Consistency challenges |
| ✅ No single point of failure | ❌ Duplicate ownership logic |
| ✅ Low latency for local checks | ❌ Hard to query "all resources user owns" |

### When to Use

- Microservices with clear boundaries
- Resources rarely need cross-service ownership checks
- Team autonomy is prioritized
- **Invistus current approach** ✓

---

## Pattern 2: Centralized Authorization Service

**Used by:** Auth0 FGA, Ory Keto, Cerbos, smaller-scale Zanzibar implementations

A dedicated service manages all ownership relationships. Services query this central service for authorization decisions.

### Architecture

```
┌────────────────────────────────────────────────────────────────┐
│                        API Gateway                              │
└────────────────────────────────────────────────────────────────┘
                              │
         ┌────────────────────┼────────────────────┐
         ▼                    ▼                    ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│  Order Service  │  │ Invoice Service │  │ Payment Service │
│                 │  │                 │  │                 │
│  (no ownership  │  │  (no ownership  │  │  (no ownership  │
│   tables)       │  │   tables)       │  │   tables)       │
└────────┬────────┘  └────────┬────────┘  └────────┬────────┘
         │                    │                    │
         └────────────────────┼────────────────────┘
                              ▼
              ┌───────────────────────────┐
              │   Authorization Service   │
              │                           │
              │  ┌─────────────────────┐  │
              │  │   relationships     │  │
              │  │   (user, relation,  │  │
              │  │    object)          │  │
              │  └─────────────────────┘  │
              └───────────────────────────┘
```

### Implementation (Auth0 FGA Style)

```kotlin
// Authorization Service API
@Controller("/authz")
class AuthorizationController(
    private val relationshipStore: RelationshipStore
) {

    @Post("/check")
    fun check(@Body request: CheckRequest): CheckResponse {
        val allowed = relationshipStore.check(
            user = request.user,      // "user:alice"
            relation = request.relation, // "owner"
            object = request.object   // "order:123"
        )
        return CheckResponse(allowed = allowed)
    }

    @Post("/write")
    fun write(@Body request: WriteRequest): WriteResponse {
        relationshipStore.write(
            Tuple(
                user = request.user,
                relation = request.relation,
                object = request.object
            )
        )
        return WriteResponse(success = true)
    }

    @Post("/list-objects")
    fun listObjects(@Body request: ListRequest): ListResponse {
        val objects = relationshipStore.listObjects(
            user = request.user,
            relation = request.relation,
            objectType = request.objectType
        )
        return ListResponse(objects = objects)
    }
}

// Client in each service
@Singleton
class AuthorizationClient(
    @Client("authorization-service") private val client: HttpClient
) {

    suspend fun check(userId: String, relation: String, objectId: String): Boolean {
        val response = client.post("/authz/check", CheckRequest(
            user = "user:$userId",
            relation = relation,
            object = objectId
        ))
        return response.allowed
    }
}

// Usage in Order Service
@Singleton
class OrderService(
    private val authzClient: AuthorizationClient,
    private val orderRepo: OrderRepository
) {

    suspend fun getOrder(userId: UUID, orderId: UUID): Order {
        // Check ownership via central service
        val canAccess = authzClient.check(
            userId = userId.toString(),
            relation = "owner",
            objectId = "order:$orderId"
        )

        if (!canAccess) {
            throw AccessDeniedException("Cannot access order $orderId")
        }

        return orderRepo.findById(orderId)
    }
}
```

### Schema Definition (FGA Style)

```yaml
# authorization-model.yaml
model:
  schema: 1.1

types:
  - name: user

  - name: organization
    relations:
      member:
        this: {}
      admin:
        this: {}

  - name: order
    relations:
      owner:
        this: {}
      organization:
        this: {}
      viewer:
        union:
          - this: {}
          - tupleToUserset:
              tupleset: organization
              computedUserset: member

  - name: invoice
    relations:
      owner:
        this: {}
      # Inherit from order
      parent_order:
        this: {}
      viewer:
        tupleToUserset:
          tupleset: parent_order
          computedUserset: viewer
```

### Pros & Cons

| Pros | Cons |
|------|------|
| ✅ Single source of truth | ❌ Single point of failure |
| ✅ Consistent cross-service | ❌ Added latency per check |
| ✅ Complex relationship queries | ❌ Operational complexity |
| ✅ Centralized audit log | ❌ Network dependency |

### When to Use

- Need cross-service ownership queries frequently
- Complex permission inheritance required
- Centralized audit and compliance requirements
- Willing to accept authorization service dependency

---

## Pattern 3: Relationship-Based Access Control (ReBAC)

**Used by:** Google (Zanzibar), Airbnb (Himeji), Carta, Notion

Google's Zanzibar paper introduced a graph-based approach where permissions are derived from relationships between objects.

### Core Concepts

```
┌─────────────────────────────────────────────────────────────────┐
│                    Relationship Graph                            │
│                                                                  │
│   user:alice ──owner──▶ folder:docs                             │
│                              │                                   │
│                           parent                                 │
│                              ▼                                   │
│                        document:report                           │
│                                                                  │
│   Permission check: Can alice view document:report?             │
│   Answer: Yes, because alice owns folder:docs which is parent   │
│           of document:report, and owners can view children.     │
└─────────────────────────────────────────────────────────────────┘
```

### Tuple Storage

```sql
-- Core relationship table (simplified Zanzibar)
CREATE TABLE relationship_tuples (
    id UUID PRIMARY KEY DEFAULT uuidv7(),

    -- Object being accessed
    object_type VARCHAR(64) NOT NULL,    -- 'document', 'folder', 'org'
    object_id VARCHAR(128) NOT NULL,

    -- Relationship
    relation VARCHAR(64) NOT NULL,        -- 'owner', 'viewer', 'parent'

    -- Subject (user or another object)
    subject_type VARCHAR(64) NOT NULL,    -- 'user', 'group', 'folder'
    subject_id VARCHAR(128) NOT NULL,
    subject_relation VARCHAR(64),         -- For userset: 'member', 'owner'

    -- Metadata
    created_at TIMESTAMPTZ DEFAULT NOW(),

    CONSTRAINT uq_tuple UNIQUE (object_type, object_id, relation,
                                 subject_type, subject_id, subject_relation)
);

-- Indexes for common query patterns
CREATE INDEX idx_tuples_object ON relationship_tuples(object_type, object_id);
CREATE INDEX idx_tuples_subject ON relationship_tuples(subject_type, subject_id);
CREATE INDEX idx_tuples_relation ON relationship_tuples(object_type, relation);
```

### Permission Model Definition

```kotlin
// Define how permissions are computed
data class TypeDefinition(
    val name: String,
    val relations: Map<String, RelationDefinition>
)

sealed class RelationDefinition {
    // Direct assignment: user:alice is owner of doc:1
    object Direct : RelationDefinition()

    // Computed from another relation on same object
    data class ComputedUserset(val relation: String) : RelationDefinition()

    // Follow a relation to another object, then check relation there
    data class TupleToUserset(
        val tupleset: String,      // relation to follow (e.g., "parent")
        val computedUserset: String // relation to check there (e.g., "viewer")
    ) : RelationDefinition()

    // Union of multiple definitions
    data class Union(val children: List<RelationDefinition>) : RelationDefinition()
}

// Example: Document type definition
val documentType = TypeDefinition(
    name = "document",
    relations = mapOf(
        "owner" to RelationDefinition.Direct,
        "parent" to RelationDefinition.Direct,  // Link to folder
        "viewer" to RelationDefinition.Union(listOf(
            RelationDefinition.Direct,  // Direct viewers
            RelationDefinition.ComputedUserset("owner"),  // Owners can view
            RelationDefinition.TupleToUserset(
                tupleset = "parent",
                computedUserset = "viewer"  // Parent folder viewers
            )
        ))
    )
)
```

### Check Algorithm (Simplified)

```kotlin
class ZanzibarEngine(
    private val tupleStore: TupleStore,
    private val typeDefinitions: Map<String, TypeDefinition>
) {

    /**
     * Check if subject has relation to object.
     *
     * Example: check("user:alice", "viewer", "document:report")
     */
    fun check(
        subjectType: String,
        subjectId: String,
        relation: String,
        objectType: String,
        objectId: String,
        visited: MutableSet<String> = mutableSetOf()
    ): Boolean {
        val cacheKey = "$subjectType:$subjectId#$relation@$objectType:$objectId"
        if (cacheKey in visited) return false  // Cycle detection
        visited.add(cacheKey)

        val typeDef = typeDefinitions[objectType]
            ?: throw IllegalArgumentException("Unknown type: $objectType")

        val relationDef = typeDef.relations[relation]
            ?: throw IllegalArgumentException("Unknown relation: $relation")

        return evaluateRelation(
            relationDef, subjectType, subjectId, objectType, objectId, visited
        )
    }

    private fun evaluateRelation(
        relationDef: RelationDefinition,
        subjectType: String,
        subjectId: String,
        objectType: String,
        objectId: String,
        visited: MutableSet<String>
    ): Boolean {
        return when (relationDef) {
            is RelationDefinition.Direct -> {
                // Check for direct tuple
                tupleStore.exists(
                    objectType = objectType,
                    objectId = objectId,
                    relation = relationDef.toString(),
                    subjectType = subjectType,
                    subjectId = subjectId
                )
            }

            is RelationDefinition.ComputedUserset -> {
                // Check another relation on same object
                check(subjectType, subjectId, relationDef.relation,
                      objectType, objectId, visited)
            }

            is RelationDefinition.TupleToUserset -> {
                // Find related objects via tupleset relation
                val relatedObjects = tupleStore.findSubjects(
                    objectType = objectType,
                    objectId = objectId,
                    relation = relationDef.tupleset
                )

                // Check computedUserset relation on each related object
                relatedObjects.any { related ->
                    check(subjectType, subjectId, relationDef.computedUserset,
                          related.type, related.id, visited)
                }
            }

            is RelationDefinition.Union -> {
                // Any child relation matches
                relationDef.children.any { child ->
                    evaluateRelation(child, subjectType, subjectId,
                                    objectType, objectId, visited)
                }
            }
        }
    }
}
```

### Zanzibar-Style API

```kotlin
@Controller("/v1/authz")
class ZanzibarController(
    private val engine: ZanzibarEngine,
    private val tupleStore: TupleStore
) {

    /**
     * Check permission
     * POST /v1/authz/check
     * {
     *   "tuple_key": {
     *     "user": "user:alice",
     *     "relation": "viewer",
     *     "object": "document:report"
     *   }
     * }
     */
    @Post("/check")
    fun check(@Body request: CheckRequest): CheckResponse {
        val (subjectType, subjectId) = parseSubject(request.tupleKey.user)
        val (objectType, objectId) = parseObject(request.tupleKey.object)

        val allowed = engine.check(
            subjectType = subjectType,
            subjectId = subjectId,
            relation = request.tupleKey.relation,
            objectType = objectType,
            objectId = objectId
        )

        return CheckResponse(allowed = allowed)
    }

    /**
     * Write relationship tuple
     * POST /v1/authz/write
     */
    @Post("/write")
    fun write(@Body request: WriteRequest): WriteResponse {
        request.writes.forEach { tuple ->
            tupleStore.write(tuple)
        }
        request.deletes.forEach { tuple ->
            tupleStore.delete(tuple)
        }
        return WriteResponse(success = true)
    }

    /**
     * List objects user has relation to
     * POST /v1/authz/list-objects
     */
    @Post("/list-objects")
    fun listObjects(@Body request: ListObjectsRequest): ListObjectsResponse {
        val objects = engine.listObjects(
            subjectType = request.subjectType,
            subjectId = request.subjectId,
            relation = request.relation,
            objectType = request.objectType
        )
        return ListObjectsResponse(objects = objects)
    }

    /**
     * Expand shows why permission is granted (for debugging)
     * POST /v1/authz/expand
     */
    @Post("/expand")
    fun expand(@Body request: ExpandRequest): ExpandResponse {
        val tree = engine.expand(
            relation = request.relation,
            objectType = request.objectType,
            objectId = request.objectId
        )
        return ExpandResponse(tree = tree)
    }
}
```

### Pros & Cons

| Pros | Cons |
|------|------|
| ✅ Handles complex hierarchies | ❌ Complex to implement correctly |
| ✅ Permission inheritance | ❌ Graph traversal can be expensive |
| ✅ Google-scale proven | ❌ Requires careful cycle detection |
| ✅ Flexible permission modeling | ❌ Debugging can be challenging |

### When to Use

- Complex organizational hierarchies
- Resources have parent-child relationships
- Permissions need to be inherited
- Scale requires distributed consistency

---

## Pattern 4: Event-Driven Ownership Propagation

**Used by:** Netflix, Uber, Event-sourced systems

Ownership changes are published as events. Services subscribe and maintain local materialized views.

### Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                      Event Bus (Kafka)                          │
│                                                                  │
│  Topic: ownership.changes                                       │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ {user: "alice", action: "grant", resource: "order:123"}    ││
│  │ {user: "bob", action: "revoke", resource: "invoice:456"}   ││
│  └─────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘
         │                    │                    │
         ▼                    ▼                    ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│  Order Service  │  │ Invoice Service │  │   Analytics     │
│                 │  │                 │  │    Service      │
│ ┌─────────────┐ │  │ ┌─────────────┐ │  │ ┌─────────────┐ │
│ │  Ownership  │ │  │ │  Ownership  │ │  │ │  Ownership  │ │
│ │  Projector  │ │  │ │  Projector  │ │  │ │  Projector  │ │
│ └──────┬──────┘ │  │ └──────┬──────┘ │  │ └──────┬──────┘ │
│        ▼        │  │        ▼        │  │        ▼        │
│ ┌─────────────┐ │  │ ┌─────────────┐ │  │ ┌─────────────┐ │
│ │   Local     │ │  │ │   Local     │ │  │ │   Global    │ │
│ │  Ownership  │ │  │ │  Ownership  │ │  │ │  Ownership  │ │
│ │    View     │ │  │ │    View     │ │  │ │    Index    │ │
│ └─────────────┘ │  │ └─────────────┘ │  │ └─────────────┘ │
└─────────────────┘  └─────────────────┘  └─────────────────┘
```

### Event Schema

```kotlin
// Ownership change events
sealed class OwnershipEvent {
    abstract val eventId: UUID
    abstract val timestamp: Instant
    abstract val resourceType: String
    abstract val resourceId: UUID
    abstract val principalType: PrincipalType
    abstract val principalId: UUID
    abstract val initiatedBy: UUID

    data class OwnershipGranted(
        override val eventId: UUID = UUID.randomUUID(),
        override val timestamp: Instant = Instant.now(),
        override val resourceType: String,
        override val resourceId: UUID,
        override val principalType: PrincipalType,
        override val principalId: UUID,
        override val initiatedBy: UUID,
        val accessType: AccessType,
        val permissions: List<String>? = null,
        val validUntil: Instant? = null
    ) : OwnershipEvent()

    data class OwnershipRevoked(
        override val eventId: UUID = UUID.randomUUID(),
        override val timestamp: Instant = Instant.now(),
        override val resourceType: String,
        override val resourceId: UUID,
        override val principalType: PrincipalType,
        override val principalId: UUID,
        override val initiatedBy: UUID,
        val reason: String? = null
    ) : OwnershipEvent()

    data class OwnershipTransferred(
        override val eventId: UUID = UUID.randomUUID(),
        override val timestamp: Instant = Instant.now(),
        override val resourceType: String,
        override val resourceId: UUID,
        override val principalType: PrincipalType,
        override val principalId: UUID,  // New owner
        override val initiatedBy: UUID,
        val previousOwnerId: UUID,
        val previousOwnerType: PrincipalType
    ) : OwnershipEvent()
}
```

### Event Publisher

```kotlin
@Singleton
class OwnershipEventPublisher(
    @Client("kafka") private val kafkaClient: KafkaClient,
    private val objectMapper: ObjectMapper
) {

    private val topic = "ownership.changes"

    suspend fun publishOwnershipGranted(
        resourceType: String,
        resourceId: UUID,
        principalId: UUID,
        principalType: PrincipalType,
        accessType: AccessType,
        initiatedBy: UUID
    ) {
        val event = OwnershipEvent.OwnershipGranted(
            resourceType = resourceType,
            resourceId = resourceId,
            principalType = principalType,
            principalId = principalId,
            accessType = accessType,
            initiatedBy = initiatedBy
        )

        kafkaClient.send(
            topic = topic,
            key = "$resourceType:$resourceId",  // Partition by resource for ordering
            value = objectMapper.writeValueAsString(event)
        )
    }
}
```

### Event Consumer (Projector)

```kotlin
@Singleton
class OwnershipProjector(
    private val ownershipRepository: OwnershipRepository,
    private val objectMapper: ObjectMapper
) {

    @KafkaListener(
        topics = ["ownership.changes"],
        groupId = "order-service-ownership"
    )
    suspend fun handleOwnershipEvent(record: ConsumerRecord<String, String>) {
        val event = objectMapper.readValue(record.value(), OwnershipEvent::class.java)

        // Only process events for resource types this service cares about
        if (!isRelevantResourceType(event.resourceType)) {
            return
        }

        when (event) {
            is OwnershipEvent.OwnershipGranted -> {
                ownershipRepository.save(
                    ResourceOwnershipEntity(
                        resourceType = event.resourceType,
                        resourceId = event.resourceId,
                        principalType = event.principalType,
                        principalId = event.principalId,
                        accessType = event.accessType,
                        permissions = event.permissions,
                        validUntil = event.validUntil,
                        grantedBy = event.initiatedBy,
                        grantedAt = event.timestamp
                    )
                )
            }

            is OwnershipEvent.OwnershipRevoked -> {
                ownershipRepository.deleteByResourceAndPrincipal(
                    resourceType = event.resourceType,
                    resourceId = event.resourceId,
                    principalId = event.principalId
                )
            }

            is OwnershipEvent.OwnershipTransferred -> {
                ownershipRepository.transfer(
                    resourceType = event.resourceType,
                    resourceId = event.resourceId,
                    newOwnerId = event.principalId,
                    newOwnerType = event.principalType
                )
            }
        }
    }

    private fun isRelevantResourceType(type: String): Boolean {
        return type in setOf("order", "order_item", "shipping_label")
    }
}
```

### Handling Eventual Consistency

```kotlin
@Singleton
class ConsistentOwnershipChecker(
    private val localOwnership: OwnershipRepository,
    private val ownershipServiceClient: OwnershipServiceClient  // Fallback
) {

    /**
     * Check ownership with consistency guarantees.
     *
     * For critical operations, verify with source of truth.
     */
    suspend fun checkOwnership(
        userId: UUID,
        resourceType: String,
        resourceId: UUID,
        consistencyLevel: ConsistencyLevel = ConsistencyLevel.LOCAL
    ): Boolean {
        // Fast path: Check local materialized view
        val localResult = localOwnership.existsByResourceAndPrincipal(
            resourceType, resourceId, userId
        )

        return when (consistencyLevel) {
            ConsistencyLevel.LOCAL -> localResult

            ConsistencyLevel.STRONG -> {
                // For critical operations, verify with source service
                if (localResult) {
                    // Double-check with source of truth
                    ownershipServiceClient.verify(resourceType, resourceId, userId)
                } else {
                    // Local says no, but might be eventual consistency lag
                    // Check source of truth
                    ownershipServiceClient.verify(resourceType, resourceId, userId)
                }
            }
        }
    }
}

enum class ConsistencyLevel {
    LOCAL,   // Fast, eventually consistent
    STRONG   // Slower, strongly consistent
}
```

### Pros & Cons

| Pros | Cons |
|------|------|
| ✅ Decoupled services | ❌ Eventual consistency complexity |
| ✅ Audit trail built-in | ❌ Event ordering challenges |
| ✅ Scalable | ❌ Harder to debug |
| ✅ Replay capability | ❌ Duplicate event handling |

### When to Use

- Already using event-driven architecture
- Audit trail is a requirement
- Can tolerate eventual consistency
- Need to replicate ownership across services

---

## Pattern 5: Token-Embedded Ownership

**Used by:** Simple APIs, Internal tools

Ownership information is embedded in the JWT token. No database lookup required.

### Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        Auth Service                              │
│                                                                  │
│  On login, fetch user's resources and embed in JWT:            │
│                                                                  │
│  {                                                               │
│    "sub": "user-123",                                           │
│    "owned_orders": ["order-1", "order-2"],                     │
│    "owned_invoices": ["inv-1"],                                │
│    "group_memberships": ["group-a", "group-b"]                 │
│  }                                                               │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼ JWT
┌─────────────────────────────────────────────────────────────────┐
│                    Any Microservice                              │
│                                                                  │
│  // No database lookup needed!                                  │
│  fun canAccess(jwt: JWT, resourceId: String): Boolean {        │
│    return resourceId in jwt.claims["owned_orders"]             │
│  }                                                               │
└─────────────────────────────────────────────────────────────────┘
```

### Implementation

```kotlin
// Auth Service: Build token with ownership
@Singleton
class TokenBuilder(
    private val ownershipRepository: OwnershipRepository
) {

    fun buildToken(userId: UUID): String {
        val ownedResources = ownershipRepository.findAllByPrincipal(userId)

        val claims = mapOf(
            "sub" to userId.toString(),
            "owned_orders" to ownedResources
                .filter { it.resourceType == "order" }
                .map { it.resourceId.toString() },
            "owned_invoices" to ownedResources
                .filter { it.resourceType == "invoice" }
                .map { it.resourceId.toString() },
            // ... other resource types
        )

        return jwtBuilder.build(claims)
    }
}

// Any Service: Check ownership from token
@Singleton
class TokenOwnershipChecker {

    fun canAccessOrder(authentication: Authentication, orderId: UUID): Boolean {
        val ownedOrders = authentication.attributes["owned_orders"] as? List<*>
            ?: return false
        return orderId.toString() in ownedOrders
    }
}
```

### Pros & Cons

| Pros | Cons |
|------|------|
| ✅ Zero latency | ❌ Token size limits (~8KB header limit) |
| ✅ No database dependency | ❌ Stale data until token refresh |
| ✅ Simple implementation | ❌ Doesn't scale with many resources |
| ✅ Stateless | ❌ Token refresh on ownership change |

### When to Use

- Users own few resources (< 100)
- Ownership changes are rare
- Low latency is critical
- Simple authorization requirements

---

## Hybrid Approach (Recommended)

Most production systems combine multiple patterns:

### Netflix's Approach

```
┌─────────────────────────────────────────────────────────────────┐
│  1. Token contains: user_id, account_id, basic claims          │
│  2. API Gateway injects headers: X-User-ID, X-Account-ID       │
│  3. Each service has local ownership tables (per-service)      │
│  4. Critical cross-service checks use async verification       │
│  5. Event bus propagates ownership changes for consistency     │
└─────────────────────────────────────────────────────────────────┘
```

### Stripe's Approach

```
┌─────────────────────────────────────────────────────────────────┐
│  1. Every resource has account_id column (tenant isolation)    │
│  2. Every API request includes account context                 │
│  3. Services filter by account_id at query level              │
│  4. Cross-account access requires explicit grants              │
│  5. Connect platform handles complex multi-party ownership     │
└─────────────────────────────────────────────────────────────────┘
```

### Recommended Hybrid for Invistus

```
┌─────────────────────────────────────────────────────────────────┐
│                   Invistus Ownership Strategy                    │
│                                                                  │
│  Layer 1: JWT Token                                             │
│  ├─ user_id, account_id                                        │
│  ├─ group_memberships (IDs only)                               │
│  └─ basic permissions (RBAC)                                    │
│                                                                  │
│  Layer 2: Per-Service Ownership Tables (Current)               │
│  ├─ resource_ownership table in each service                   │
│  ├─ Fast local lookups                                         │
│  └─ Service autonomy maintained                                │
│                                                                  │
│  Layer 3: RLS at Database (New - from RLS Blueprint)           │
│  ├─ PostgreSQL RLS policies                                    │
│  ├─ Defense in depth                                           │
│  └─ Automatic query filtering                                   │
│                                                                  │
│  Layer 4: Event Propagation (Future Enhancement)               │
│  ├─ Ownership changes published to Kafka                       │
│  ├─ Services subscribe to relevant events                      │
│  └─ Enables cross-service ownership queries                    │
└─────────────────────────────────────────────────────────────────┘
```

---

## Cross-Service Ownership Patterns

### Pattern A: Service-to-Service API Call

```kotlin
// Order Service needs to check if user owns related invoice
@Singleton
class OrderService(
    private val invoiceClient: InvoiceClient
) {

    suspend fun getOrderWithInvoice(userId: UUID, orderId: UUID): OrderWithInvoice {
        val order = orderRepository.findById(orderId)

        // Check if user can access related invoice
        val canAccessInvoice = invoiceClient.checkOwnership(
            userId = userId,
            invoiceId = order.invoiceId
        )

        return if (canAccessInvoice) {
            val invoice = invoiceClient.getInvoice(order.invoiceId)
            OrderWithInvoice(order, invoice)
        } else {
            OrderWithInvoice(order, invoice = null)
        }
    }
}
```

### Pattern B: Ownership Claim Service

```kotlin
// Dedicated service that aggregates ownership from all services
@Controller("/ownership")
class OwnershipAggregatorController(
    private val orderOwnershipClient: OrderOwnershipClient,
    private val invoiceOwnershipClient: InvoiceOwnershipClient,
    private val paymentOwnershipClient: PaymentOwnershipClient
) {

    /**
     * Get all resources owned by user across all services
     */
    @Get("/users/{userId}/resources")
    suspend fun getUserResources(
        @PathVariable userId: UUID
    ): UserResourcesResponse {
        // Parallel calls to all services
        val (orders, invoices, payments) = coroutineScope {
            val ordersDeferred = async { orderOwnershipClient.getOwnedResources(userId) }
            val invoicesDeferred = async { invoiceOwnershipClient.getOwnedResources(userId) }
            val paymentsDeferred = async { paymentOwnershipClient.getOwnedResources(userId) }

            Triple(
                ordersDeferred.await(),
                invoicesDeferred.await(),
                paymentsDeferred.await()
            )
        }

        return UserResourcesResponse(
            orders = orders,
            invoices = invoices,
            payments = payments
        )
    }
}
```

### Pattern C: Shared Cache (Redis)

```kotlin
// Each service publishes ownership to shared Redis
@Singleton
class OwnershipCachePublisher(
    private val redis: RedisClient
) {

    suspend fun publishOwnership(
        userId: UUID,
        resourceType: String,
        resourceId: UUID
    ) {
        // Key: ownership:{userId}:{resourceType}
        // Value: Set of resource IDs
        redis.sadd(
            "ownership:$userId:$resourceType",
            resourceId.toString()
        )

        // Reverse index for resource lookup
        redis.set(
            "owner:$resourceType:$resourceId",
            userId.toString()
        )
    }
}

// Any service can check ownership via Redis
@Singleton
class OwnershipCacheChecker(
    private val redis: RedisClient
) {

    suspend fun checkOwnership(
        userId: UUID,
        resourceType: String,
        resourceId: UUID
    ): Boolean {
        return redis.sismember(
            "ownership:$userId:$resourceType",
            resourceId.toString()
        )
    }

    suspend fun getOwner(
        resourceType: String,
        resourceId: UUID
    ): UUID? {
        return redis.get("owner:$resourceType:$resourceId")
            ?.let { UUID.fromString(it) }
    }
}
```

---

## Consistency Considerations

### CAP Theorem Trade-offs

| Approach | Consistency | Availability | Partition Tolerance |
|----------|-------------|--------------|---------------------|
| Centralized Service | Strong | Lower | Yes |
| Per-Service Tables | Eventual | High | Yes |
| Shared Cache | Eventual | High | Yes |
| Event Sourcing | Eventual | High | Yes |

### Handling Stale Ownership Data

```kotlin
/**
 * Strategy for handling eventual consistency in ownership checks.
 */
enum class OwnershipCheckStrategy {
    /**
     * Trust local data. Fast but might allow stale access.
     * Use for: Read operations, non-critical paths
     */
    OPTIMISTIC,

    /**
     * Verify with source of truth. Slower but accurate.
     * Use for: Write operations, critical paths
     */
    PESSIMISTIC,

    /**
     * Check local first, verify async, compensate if wrong.
     * Use for: High-throughput scenarios with compensation capability
     */
    COMPENSATING
}

@Singleton
class OwnershipChecker(
    private val localOwnership: LocalOwnershipRepository,
    private val ownershipService: OwnershipServiceClient,
    private val compensationService: CompensationService
) {

    suspend fun check(
        userId: UUID,
        resourceType: String,
        resourceId: UUID,
        strategy: OwnershipCheckStrategy
    ): Boolean {
        return when (strategy) {
            OwnershipCheckStrategy.OPTIMISTIC -> {
                localOwnership.check(userId, resourceType, resourceId)
            }

            OwnershipCheckStrategy.PESSIMISTIC -> {
                // Always verify with source of truth
                ownershipService.verify(userId, resourceType, resourceId)
            }

            OwnershipCheckStrategy.COMPENSATING -> {
                val localResult = localOwnership.check(userId, resourceType, resourceId)

                // Async verification
                coroutineScope {
                    launch {
                        val actualResult = ownershipService.verify(userId, resourceType, resourceId)
                        if (localResult != actualResult) {
                            compensationService.handleInconsistency(
                                userId, resourceType, resourceId, localResult, actualResult
                            )
                        }
                    }
                }

                localResult
            }
        }
    }
}
```

---

## Migration Path for Invistus

### Current State

```
✅ Per-service resource_ownership tables
✅ ResourceAccessControl interface
✅ RBAC + ABAC layers
✅ JWT-based authentication
```

### Recommended Enhancements

```
Phase 1: Add RLS (from RLS Blueprint)
├─ Enable PostgreSQL RLS on tables with user_id
├─ Add Hibernate filters for automatic query filtering
└─ Defense in depth with existing ownership tables

Phase 2: Event-Driven Ownership (Optional)
├─ Publish ownership changes to Kafka topic
├─ Services subscribe to relevant events
└─ Enables future cross-service queries

Phase 3: Ownership Query Service (Optional)
├─ Aggregator service for "show me all my resources"
├─ Parallel queries to all services
└─ Cached responses for dashboard views
```

---

## Related Documentation

- [Resource Ownership](./authorization/resource-ownership.md) - Current implementation
- [Row-Level Security Blueprint](./row-level-security-blueprint.md) - Database-level filtering
- [Authorization Overview](./authorization/README.md) - RBAC + ABAC model
- [Data Architecture](../../02-architecture/data-architecture.md) - Database setup

---

## References

### Industry Papers & Docs

- [Google Zanzibar Paper](https://research.google/pubs/pub48190/) - Original ReBAC paper
- [Auth0 FGA Documentation](https://docs.fga.dev/) - Zanzibar-inspired implementation
- [Ory Keto](https://www.ory.sh/keto/) - Open-source Zanzibar implementation
- [Airbnb Himeji](https://medium.com/airbnb-engineering/himeji-a-scalable-centralized-system-for-authorization-at-airbnb-341664924574) - Airbnb's authorization system

### Open Source Implementations

- [OpenFGA](https://openfga.dev/) - CNCF project, Zanzibar-inspired
- [SpiceDB](https://authzed.com/spicedb) - Zanzibar-inspired, Kubernetes-native
- [Cerbos](https://cerbos.dev/) - Policy-based access control
- [Casbin](https://casbin.org/) - Multi-model authorization library

---

**Last Updated**: 2026-02-04
**Version**: 1.0
**Status**: Reference Documentation
