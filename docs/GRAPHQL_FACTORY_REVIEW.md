# GraphQLFactory Federation Implementation Review

## Executive Summary

The current `GraphQLFactory` implementation is **NOT following true GraphQL Federation best practices**. It implements a **modular monolith pattern** that merges schemas at runtime within a single service, rather than true federation where independent services are composed by Apollo Router.

## Current Architecture Issues

### 1. ❌ Schema Merging Within Single Service

**Current Approach:**
- Single `GraphQLFactory` merges schemas from multiple modules (app, security) at runtime
- All modules run in the same service instance
- Schema composition happens in code, not by Apollo Router

**Federation Best Practice:**
- Each service should have its own `GraphQLFactory`
- Services run independently with separate GraphQL endpoints
- Apollo Router composes the supergraph from independent services

### 2. ❌ Entity Key Handling Limitations

**Current Implementation:**
```kotlin
val id = rep["id"]?.toString()
entityFetcher(typename, id)
```

**Issues:**
- Assumes all entities use `id` as the key field
- Doesn't support composite keys (`@key(fields: "id sku")`)
- Doesn't parse actual key fields from `@key` directives in schema

**Federation Best Practice:**
- Parse `@key(fields: "...")` directives to determine actual key fields
- Support multiple key fields (composite keys)
- Pass all key field values to entity fetcher

### 3. ❌ Missing Federation Directive Handling

**Current Implementation:**
- Schema merging doesn't validate federation directives
- No handling of `@external`, `@requires`, `@provides` directives
- No validation that `@key` directives are properly defined

**Federation Best Practice:**
- Validate federation directives during schema composition
- Ensure `@key` directives are present on federated entities
- Handle `@external` fields correctly

### 4. ⚠️ Service Boundaries Not Enforced

**Current Approach:**
- Modules can share types (e.g., both app and security define `User`)
- No clear ownership of entities
- Type conflicts resolved by "first wins" strategy

**Federation Best Practice:**
- Each service owns specific entities
- Services can extend entities from other services using `extend type`
- Clear ownership boundaries

## Recommended Architecture

### True Federation Pattern

```
┌─────────────────────────────────────────┐
│      Apollo Router (Gateway)            │
│      Port: 4000                         │
│      Composes Supergraph                │
└──────────────┬──────────────────────────┘
               │
       ┌───────┴────────┐
       │                │
┌──────▼──────┐  ┌──────▼──────┐
│ App Service │  │Security Svc │
│ Port: 8081  │  │ Port: 8080  │
│             │  │             │
│ AppGraphQL  │  │SecurityGraph│
│ Factory     │  │ Factory     │
└─────────────┘  └─────────────┘
```

### Implementation Steps

#### 1. Separate GraphQL Factories Per Service

**App Service:**
```kotlin
@Factory
class AppGraphQLFactory(
    private val schemaRegistry: TypeDefinitionRegistry,
    private val wiringFactory: AppWiringFactory,
    private val productService: ProductService,
    private val customerService: CustomerService
) {
    @Singleton
    fun graphQL(): graphql.GraphQL {
        // Only handles App service schema
        // No merging of other services
    }
}
```

**Security Service:**
```kotlin
@Factory
class SecurityGraphQLFactory(
    private val schemaRegistry: TypeDefinitionRegistry,
    private val wiringFactory: SecurityWiringFactory,
    private val userRepository: UserRepository
) {
    @Singleton
    fun graphQL(): graphql.GraphQL {
        // Only handles Security service schema
        // No merging of other services
    }
}
```

#### 2. Improve Entity Key Handling

**Enhanced Entity Fetcher:**
```kotlin
.fetchEntities { env ->
    val reps = env.getArgument<List<Map<String, Any>>>("representations")
    reps?.mapNotNull { rep ->
        val typename = rep["__typename"]?.toString() ?: return@mapNotNull null
        
        // Extract all key fields (not just "id")
        val keyFields = rep.filterKeys { it != "__typename" }
        
        // Parse @key directive to determine which fields are keys
        // For now, use all fields in representation as keys
        entityFetcher(typename, keyFields)
    }
}
```

**Update Interface:**
```kotlin
interface GraphQLModuleContributor {
    // Change from (String, String) -> Any? to support multiple key fields
    fun getFederationEntityFetcher(): ((String, Map<String, Any>) -> Any?)? {
        return null
    }
}
```

#### 3. Add Federation Directive Validation

```kotlin
private fun validateFederationDirectives(registry: TypeDefinitionRegistry) {
    registry.types().forEach { (typeName, typeDef) ->
        if (typeDef is ObjectTypeDefinition) {
            val hasKeyDirective = typeDef.directives.any { 
                it.name == "key" 
            }
            
            // Entities should have @key directive
            if (isFederatedEntity(typeDef) && !hasKeyDirective) {
                logger.warn("Federated entity ${typeName.name} missing @key directive")
            }
        }
    }
}
```

## Migration Path

### Phase 1: Keep Current Implementation (Short-term)
- ✅ Add architectural warnings in code comments
- ✅ Improve entity key handling to support multiple fields
- ✅ Add federation directive validation
- ✅ Document limitations

### Phase 2: Split Services (Medium-term)
- Create separate `AppGraphQLFactory` in app module
- Create separate `SecurityGraphQLFactory` in security module
- Remove unified `GraphQLFactory` from common module
- Update service configurations to run independently

### Phase 3: True Federation (Long-term)
- Deploy services independently
- Configure Apollo Router to compose supergraph
- Remove schema merging code
- Implement proper service boundaries

## Best Practices Checklist

### ✅ Current Implementation Does Well
- [x] Uses Apollo Federation library correctly
- [x] Provides both `fetchEntities` and `resolveEntityType` callbacks
- [x] Handles errors gracefully
- [x] Logs federation operations
- [x] Merges Query/Mutation/Subscription fields correctly

### ❌ Needs Improvement
- [ ] Support multiple key fields (composite keys)
- [ ] Parse `@key` directives from schema
- [ ] Validate federation directives
- [ ] Separate services with independent GraphQL factories
- [ ] Remove schema merging in favor of router composition
- [ ] Clear entity ownership boundaries

## References

- [Apollo Federation Documentation](https://www.apollographql.com/docs/federation/)
- [Federation Best Practices](https://www.apollographql.com/docs/federation/best-practices/)
- Project Spec: `spec/service/graphql-federation-architecture.md`
- Project Spec: `spec/contracts/graphql-federation.md`

## Conclusion

The current `GraphQLFactory` is a **hybrid approach** that works for development and testing but doesn't follow true GraphQL Federation patterns. For production, consider migrating to separate services with independent GraphQL factories, composed by Apollo Router.

The implementation is **functional** but **not optimal** for true federation. It's suitable for:
- ✅ Development/testing scenarios
- ✅ Monolithic deployments needing modular schemas
- ❌ Production federation with independent services
- ❌ True service boundaries and independent deployment

