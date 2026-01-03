---
title: API Rules
type: rule
category: api
status: current
version: 2.0.0
tags: [api, graphql, rules, federation]
ai_optimized: true
search_keywords: [api, graphql, federation, rules, schema]
related:
  - 04-patterns/api-patterns/graphql-query-pattern.md
  - 04-patterns/backend-patterns/resolver-pattern.md
---

# API Rules

> **Purpose**: Rules for GraphQL API and federation patterns.

## GraphQL Schema Rules

### Rule: Schema Location

**Rule**: GraphQL schemas must be in `src/main/resources/graphql/schema.graphqls`

**Rationale**: Standard location for Micronaut.

### Rule: Federation Directives

**Rule**: Entities must use `@key` directive for federation.

**Rationale**: Enables federation.

**Example**:
```graphql
# ✅ Correct
type Customer @key(fields: "id") {
  id: ID!
  name: String!
}

# ❌ Incorrect
type Customer {
  id: ID!
  name: String!
}
```

## Resolver Rules

### Rule: Resolver Naming

**Rule**: Resolvers must be named `{Type}Resolver`.

**Rationale**: Clear naming convention.

**Example**:
```kotlin
// ✅ Correct
class CustomerResolver

// ❌ Incorrect
class CustomerHandler
class CustomerController
```

### Rule: GraphQL Context Token Extraction

**Rule**: `GraphQLControllerBase` automatically extracts the Bearer token from the `Authorization` header and stores it in GraphQL context as `"token"`. Resolvers should use `AuthenticatedGraphQLWiringFactory` which provides `env.principal()` extension function to extract and validate the principal.

**Rationale**: Centralized token extraction ensures consistent authentication across all GraphQL operations. The token is stored in GraphQL context once per request and can be accessed by all data fetchers. Using `AuthenticatedGraphQLWiringFactory` provides a clean, type-safe API for resolvers.

**Implementation**:

1. **GraphQLControllerBase** extracts token from `Authorization` header:
   - Format: `"Bearer <token>"`
   - Token is stored in GraphQL context as `"token"` (non-nullable String)
   - If no token is provided, context does not contain `"token"` key
   - Token extraction happens once per request before GraphQL execution

2. **Resolvers** use `AuthenticatedGraphQLWiringFactory`:
   - Extend `AuthenticatedGraphQLWiringFactory` instead of `GraphQLWiringFactory`
   - Use `env.principal()` extension function to get authenticated principal
   - Use `env.withPermission(action) { principal -> ... }` for permission checks
   - Principal validation happens lazily on first access
   - Validated principal is cached in GraphQL context as `"requestPrincipal"`

3. **Authentication Flow**:
   ```
   GraphQL Request
   → GraphQLControllerBase extracts token → stores in context
   → GraphQL query executes
   → Resolver calls env.principal()
   → AuthenticatedGraphQLWiringFactory.principal() extension
   → RequestPrincipalProvider.fromGraphQl(env)
   → Checks cache → if not cached, validates JWT token
   → Returns RequestPrincipal (cached for subsequent resolvers)
   ```

**Key Points**:
- Token extraction happens once per request in `GraphQLControllerBase`
- Principal validation happens lazily when resolver calls `env.principal()`
- Principal is validated once and cached in GraphQL context
- Subsequent data fetchers in the same request reuse cached principal (no revalidation)
- Token is stored as non-nullable String in context (null tokens are not stored)
- **No annotation required** - authentication is automatic when resolver requests principal

**Example Usage**:

```kotlin
@Singleton
class AssetWiringFactory(
    private val queryResolver: AssetQueryResolver,
    requestPrincipalProvider: RequestPrincipalProvider,
    authorizationChecker: AuthorizationChecker,
    resolverRegistry: GraphQLResolverRegistry,
) : AuthenticatedGraphQLWiringFactory(requestPrincipalProvider, authorizationChecker) {

    override fun registerQueryResolvers(type: TypeRuntimeWiring.Builder) = type
        .dataFetcher("asset", createValidatedDataFetcher { env ->
            // Get authenticated principal (triggers JWT validation if not cached)
            val principal = env.principal()
            
            // Check permission and execute with principal
            env.withPermission(AssetPermissions.ASSETS_ASSET_VIEW) { principal ->
                val id = getRequiredString(env, "id")
                queryResolver.asset(id, principal.userId.toString())
            }
        })
}
```

**See Also**: [Security Feature Documentation - GraphQL Token Validation Flow](../../03-features/security/README.md#4-graphql-token-validation-flow) for complete step-by-step authentication flow.

## Performance Rules

### Rule: Avoid N+1 Query Problems

**Rule**: Relationship fields that could be requested multiple times in a single query MUST use DataLoader to batch database queries.

**Rationale**: When a GraphQL query requests a relationship field for multiple parent objects (e.g., `groups { members }`), direct resolver calls result in N+1 queries (1 query for parents + N queries for each parent's relationships). DataLoader batches these requests into a single query, dramatically improving performance.

**When to Use DataLoader**:
- Relationship fields that return lists or single objects based on a parent ID
- Fields that could be requested for multiple parent objects in the same query
- Any field that performs database queries based on parent entity IDs

**Example - Incorrect (N+1 Problem)**:
```kotlin
// ❌ Incorrect - Direct resolver call causes N+1 queries
type.dataFetcher(
    "members",
    createValidatedDataFetcher { env: DataFetchingEnvironment ->
        val group = env.getSource<GroupDTO>()
        group?.let { groupManagementResolver.resolveGroupMembers(it.id) } ?: emptyList()
    },
)
```

**Example - Correct (Using DataLoader)**:
```kotlin
// ✅ Correct - DataLoader batches requests
type.dataFetcher(
    "members",
    createValidatedDataFetcher { env: DataFetchingEnvironment ->
        val group = env.getSource<GroupDTO>()
        if (group == null) {
            emptyList()
        } else {
            val dataLoader = env.getDataLoader<List<UserDTO>>(GroupMembersDataLoader.KEY)
            dataLoader.load(group.id)
        }
    },
)
```

**Implementation Pattern**:
1. Create a batch resolver method that accepts a list of IDs and returns a map
2. Create a DataLoader service using `DataLoaderFactory.newDataLoader`
3. Register the DataLoader in a `DataLoaderRegistryFactory`
4. Use `env.getDataLoader()` in the data fetcher instead of direct resolver calls

### Rule: DataLoaderRegistryFactory Pattern

**Rule**: Each module MUST implement a `DataLoaderRegistryFactory` to register its DataLoaders. The factory is automatically discovered and merged by `GraphQLControllerBase`.

**Rationale**: This pattern allows modules to register their DataLoaders without coupling `GraphQLControllerBase` to specific modules, enabling better separation of concerns and modularity.

**Implementation Pattern**:

1. **Create DataLoaderRegistryFactory**:

```kotlin
package io.github.salomax.neotool.{module}.graphql.dataloader

import io.github.salomax.neotool.common.graphql.DataLoaderRegistryFactory
import io.github.salomax.neotool.{module}.graphql.resolver.{EntityName}ManagementResolver
import jakarta.inject.Named
import jakarta.inject.Singleton
import org.dataloader.DataLoaderRegistry

/**
 * Factory for creating DataLoader registries in the {module} module.
 * Registers all {module}-related DataLoaders.
 */
@Singleton
@Named("{module}")
class {Module}DataLoaderRegistryFactory(
    private val {entityName}ManagementResolver: {EntityName}ManagementResolver,
) : DataLoaderRegistryFactory {
    override fun createDataLoaderRegistry(): DataLoaderRegistry {
        val registry = DataLoaderRegistry()
        registry.register(
            {EntityName}{Relationship}DataLoader.KEY,
            {EntityName}{Relationship}DataLoader.create({entityName}ManagementResolver),
        )
        // Register other DataLoaders...
        return registry
    }
}
```

**Key Points**:
- `@Singleton` annotation required
- `@Named("{module}")` annotation required (use module name, e.g., "security", "product")
- Implements `DataLoaderRegistryFactory` interface
- Creates a new `DataLoaderRegistry` per request
- Registers all module DataLoaders using their `KEY` constants
- `GraphQLControllerBase` automatically discovers and merges all factories

2. **GraphQLControllerBase Integration**:

`GraphQLControllerBase` automatically:
- Discovers all `DataLoaderRegistryFactory` beans via dependency injection
- Merges all DataLoaders from all modules into a single registry per request
- Attaches the merged registry to the GraphQL execution context

**Example - Multiple Modules**:

```kotlin
// Security module
@Singleton
@Named("security")
class SecurityDataLoaderRegistryFactory(...) : DataLoaderRegistryFactory {
    override fun createDataLoaderRegistry(): DataLoaderRegistry {
        val registry = DataLoaderRegistry()
        registry.register(UserRolesDataLoader.KEY, UserRolesDataLoader.create(...))
        registry.register(GroupMembersDataLoader.KEY, GroupMembersDataLoader.create(...))
        return registry
    }
}

// Product module
@Singleton
@Named("product")
class ProductDataLoaderRegistryFactory(...) : DataLoaderRegistryFactory {
    override fun createDataLoaderRegistry(): DataLoaderRegistry {
        val registry = DataLoaderRegistry()
        registry.register(ProductCategoryDataLoader.KEY, ProductCategoryDataLoader.create(...))
        return registry
    }
}

// GraphQLControllerBase automatically merges both registries
```

**Key Points**:
- Each module has its own factory with a unique `@Named` annotation
- All factories are automatically discovered and merged
- No manual registration required in `GraphQLControllerBase`
- DataLoaders from different modules can coexist without conflicts

**Related Documentation**:
- [Resolver Pattern](../04-patterns/backend-patterns/resolver-pattern.md) - For resolver implementation patterns including DataLoader setup

## Related Documentation

- [GraphQL Query Pattern](../../04-patterns/api-patterns/graphql-query-pattern.md)
- [Resolver Pattern](../../04-patterns/backend-patterns/resolver-pattern.md)

