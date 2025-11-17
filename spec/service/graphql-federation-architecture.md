---
title: GraphQL Federation Architecture
type: guide
category: api
status: current
version: 1.0.0
tags: [graphql, federation, apollo, api, architecture]
related:
  - ARCHITECTURE_OVERVIEW.md
  - adr/0003-kotlin-micronaut-backend.md
---

# GraphQL Federation Architecture

This document outlines the GraphQL Federation architecture implemented in the service layer, leveraging Apollo Federation for scalable, distributed GraphQL API management.

---

## Overview

Our service layer implements **Apollo Federation** as the core architecture for GraphQL at scale. This approach allows us to build a single, cohesive schema (the Supergraph) from multiple federated services, solving the traditional problems of monolithic GraphQL APIs while providing enterprise-grade tooling and security.

---

## Why Apollo Federation?

### Problems Solved by Federation

#### 1. Elimination of Monolith Pain Points
- **Problem**: As GraphQL monoliths grow, they become increasingly difficult to release changes
- **Problem**: Shared API becomes a "hated beast" with complex interdependencies
- **Problem**: Schema structured around specific team needs leads to duplication
- **Solution**: Federation decentralizes schema creation and eliminates complexity

#### 2. Reduction of Duplication from BFFs
- **Problem**: Backend for Frontends (BFFs) create "lot of duplicated functionality"
- **Problem**: Increased infrastructure costs and attack vectors
- **Problem**: Multiple GraphQL servers per experience at scale
- **Solution**: Federation allows separate teams to manage different data domains while composing them into a single coherent schema

#### 3. Overcoming Schema Stitching Fragility
- **Problem**: Schema stitching results in "death by a thousand cuts"
- **Problem**: Complex "glue code" required for gateway interconnections
- **Problem**: Manual connection logic between types
- **Solution**: Federation provides built-in mechanisms for linking types via shared keys

---

## Architecture Benefits

### Key Advantages

#### Separation by Concern
- Each federated service handles **one high-level data type**
- Example: Basket service handles all Basket data, Product service handles all Product data
- Aligns with modern microservice architectures
- Enables colocated data sources

#### Decentralized Development
- Multiple teams can independently develop and deploy their parts of the graph
- Services can extend a single type across different domains
- Reduces coordination overhead between teams

#### Cohesive Schema Composition
- **GraphOS Router** uses `@@key` directives to understand relationships
- Similar to foreign keys in relational databases
- Automatic type linking without manual configuration

#### Intelligent Query Planning
- Built-in query planner automatically splits incoming queries
- Distributes queries efficiently to correct federated services
- Optimizes data retrieval across the distributed system

---

## Service Layer Implementation

### Current Project Structure

```
neotool-starter/
├── service/
│   ├── kotlin/
│   │   └── app/                    # Main application service
│   │       └── src/main/resources/graphql/
│   │           └── schema.graphqls # Service schema (source of truth)
│   └── gateway/
│       └── router/                 # Apollo Router configuration
│           ├── router.yaml         # Production router config
│           └── router.dev.yaml     # Development router config
└── contracts/
    └── graphql/                    # GraphQL contracts (federation hub)
        ├── subgraphs/              # Individual service schemas (synced)
        │   └── app/
        │       └── schema.graphqls # Synced from service
        ├── supergraph/             # Federation configuration
        │   ├── supergraph.yaml     # Federation config
        │   ├── supergraph.graphql  # Generated supergraph schema
        │   └── supergraph.local.graphql # Development supergraph
        └── shared/                 # Shared types and directives
```

### Federation Directives

#### Entity Keys
```graphql
type Product @key(fields: "id") {
  id: ID!
  name: String!
  priceCents: Int!
  stock: Int!
}

type Customer @key(fields: "id") {
  id: ID!
  name: String!
  email: String!
  orders: [Order!]!
}
```

#### Entity References
```graphql
type Order {
  id: ID!
  customer: Customer!  # Reference to Customer entity
  products: [Product!]! # Reference to Product entities
  totalCents: Int!
}
```

#### Service Extensions
```graphql
extend type Product @key(fields: "id") {
  reviews: [Review!]!  # Extended by Review service
  recommendations: [Product!]! # Extended by Recommendation service
}
```

### Schema Synchronization Workflow

The project implements a **service-first development approach** where:

1. **Service modules are the source of truth** for GraphQL schemas
2. **Contracts directory** serves as the federation hub
3. **Interactive sync script** manages schema synchronization

#### Development Workflow

```bash
# 1. Edit schemas in service modules
# File: service/kotlin/app/src/main/resources/graphql/schema.graphqls

# 2. Sync schemas to contracts (using CLI)
./neotool graphql sync

# Or run script directly
./scripts/cli/commands/graphql/sync-schemas.sh sync

# 3. Generate supergraph schema (using CLI)
./neotool graphql generate

# Or run script directly
./scripts/cli/commands/graphql/generate-schema.sh

# 4. Start services with updated schemas
```

#### Schema Discovery

The `sync-schemas.sh` script automatically discovers schemas:
- **Pattern**: `{language}/{module}/src/main/resources/graphql/schema.graphqls`
- **Examples**: `kotlin/app`, `kotlin/security`, `kotlin/common`
- **Interactive**: Select source and target subgraph

#### Available Commands

```bash
# Using Neotool CLI (recommended)
./neotool graphql sync      # Interactive sync from services to contracts
./neotool graphql validate  # Validate schema consistency
./neotool graphql generate  # Generate supergraph schema
./neotool graphql all       # Run all operations

# Or run scripts directly
./scripts/cli/commands/graphql/sync-schemas.sh sync      # Synchronize schemas from services to contracts
./scripts/cli/commands/graphql/sync-schemas.sh validate  # Validate schema consistency
./scripts/cli/commands/graphql/generate-schema.sh        # Generate supergraph schema
./scripts/cli/commands/graphql/sync-schemas.sh all       # Run all operations
```

---

## GraphOS Platform Integration

### Security and Access Management

#### API Security
- **Authentication**: JWT-based authentication across all services
- **Authorization**: Role-based access control (RBAC) implementation
- **Rate Limiting**: Per-client and per-operation rate limiting
- **Query Complexity**: Automatic query complexity analysis and limits

#### Access Management
- **Organization Security**: Multi-tenant organization management
- **API Keys**: Secure API key generation and rotation
- **Client Management**: Centralized client registration and management

### Development and Schema Management

#### Schema Registry
- **Version Control**: Complete schema versioning and history
- **Change Tracking**: Automatic detection of breaking changes
- **Rollback Capability**: Safe rollback to previous schema versions
- **Compatibility Checks**: Automated compatibility validation

#### Schema Proposals
- **Collaborative Development**: Team-based schema change proposals
- **Review Process**: Built-in review and approval workflow
- **Conflict Resolution**: Automatic conflict detection and resolution
- **Testing Integration**: Pre-deployment schema validation

#### Development Tools
- **GraphOS Studio**: Web-based IDE for schema management
- **IDE Extensions**: VS Code and IntelliJ GraphQL extensions
- **Schema Linting**: Automated schema quality checks
- **Code Generation**: Type-safe code generation for all languages

### Performance and Observability

#### API Observability
- **Distributed Tracing**: OpenTelemetry integration for request tracing
- **Performance Metrics**: Real-time performance monitoring
- **Error Tracking**: Comprehensive error logging and alerting
- **Usage Analytics**: Detailed API usage patterns and insights

#### Query Optimization
- **Query Caching**: Intelligent query result caching
- **Entity Caching**: Cross-service entity caching
- **Query Planning**: Optimized query execution planning
- **Response Compression**: Automatic response compression

#### Monitoring Dashboard
- **Real-time Metrics**: Live performance dashboards
- **Alert Management**: Configurable alerting for critical issues
- **Capacity Planning**: Resource usage and scaling recommendations
- **Cost Analysis**: API usage cost tracking and optimization

---

## Implementation Guidelines

### GraphQL Factory Setup

#### Required Federation Resolvers

**RULE**: When using Apollo Federation in Kotlin/Micronaut, **ALL** GraphQL factories **MUST** provide both `fetchEntities` and `resolveEntityType` callbacks, even if the module doesn't actively use entity fetching. These are **mandatory** for Federation to work correctly.

**Why this is required:**
- Apollo Federation requires these resolvers to be present in the schema transformation
- Even if your module doesn't expose federated entities, Federation still needs these callbacks to be defined
- Without them, the GraphQL schema will fail to build and tests will fail

**Error if missing:**
```
FederationError: Missing a type resolver for _Entity
FederationError: Missing a data fetcher for _entities
```

**Solution:**
Always provide both callbacks in your `GraphQLFactory`, even if they only handle your module's specific entities or return `null` for unknown types.

#### GraphQL Factory Pattern

Each module that provides GraphQL functionality must create a `GraphQLFactory` that:

1. **Extends BaseSchemaRegistryFactory** (or implements schema loading)
2. **Creates a WiringFactory** for resolver registration
3. **Provides Federation resolvers** (mandatory)
4. **Creates the GraphQL bean**

```kotlin
@Factory
class [Module]GraphQLFactory(
    private val registry: TypeDefinitionRegistry,
    private val wiringFactory: [Module]WiringFactory,
    private val entityRepository: EntityRepository // Inject repositories needed for federation
) {
    @Singleton
    fun graphQL(): graphql.GraphQL {
        val runtimeWiring = wiringFactory.build()

        // ⚠️ MANDATORY: Federation requires both callbacks
        // Even if your module doesn't expose federated entities, you MUST provide these
        val federatedSchema = Federation.transform(registry, runtimeWiring)
            .fetchEntities { env ->
                // This callback is called when Federation needs to fetch entities
                // by their key fields (e.g., when another service references your entities)
                val reps = env.getArgument<List<Map<String, Any>>>("representations")
                reps?.mapNotNull { rep ->
                    val id = rep["id"]
                    if (id == null) {
                        null
                    } else {
                        try {
                            when (rep["__typename"]) {
                                "YourEntity" -> {
                                    // Fetch entity from repository
                                    val entity = entityRepository.findById(UUID.fromString(id.toString())).orElse(null)
                                    entity?.let { 
                                        // Convert entity to DTO for GraphQL
                                        YourDTO(
                                            id = it.id.toString(),
                                            // ... other fields
                                        )
                                    }
                                }
                                else -> null // Return null for unknown types
                            }
                        } catch (e: Exception) {
                            // Log and return null if ID conversion fails
                            val logger = org.slf4j.LoggerFactory.getLogger([Module]GraphQLFactory::class.java)
                            logger.debug("Failed to fetch entity for federation: ${rep["__typename"]} with id: $id", e)
                            null
                        }
                    }
                }
            }
            .resolveEntityType { env ->
                // This callback resolves a DTO object to its GraphQL type
                // Required for Federation to understand entity types
                val entity = env.getObject<Any?>()
                val schema = env.schema
                
                if (schema == null) {
                    throw IllegalStateException("GraphQL schema is null in resolveEntityType")
                }

                when (entity) {
                    is YourDTO -> schema.getObjectType("YourEntity")
                        ?: throw IllegalStateException("YourEntity type not found in schema")
                    else -> throw IllegalStateException(
                        "Unknown federated type for entity: ${entity?.javaClass?.name}"
                    )
                }
            }
            .build()

        return graphql.GraphQL.newGraphQL(federatedSchema)
            .instrumentation(MaxQueryComplexityInstrumentation(100))
            .instrumentation(MaxQueryDepthInstrumentation(10))
            .defaultDataFetcherExceptionHandler(GraphQLOptimisticLockExceptionHandler())
            .build()
    }
}
```

**Key Points:**
1. **Both callbacks are mandatory** - Never skip `fetchEntities` or `resolveEntityType`
2. **Handle unknown types gracefully** - Return `null` in `fetchEntities` for unknown `__typename` values
3. **Convert entities to DTOs** - Federation works with DTOs, not JPA entities directly
4. **Error handling** - Always wrap ID parsing in try-catch to handle invalid formats
5. **Logging** - Use debug-level logging for federation failures (they're expected for unknown types)

#### Module Independence

**Each module MUST have its own GraphQL setup** to respect module boundaries:

- ✅ **Security module**: `SecurityGraphQLFactory`, `SecurityWiringFactory`, `SecuritySchemaRegistryFactory`
- ✅ **App module**: `GraphQLFactory`, `AppWiringFactory`, `AppSchemaRegistryFactory`
- ✅ **Future modules**: Each module provides its own complete GraphQL infrastructure

**Benefits:**
- Modules can test GraphQL functionality independently
- No cross-module dependencies for GraphQL testing
- Clear separation of concerns
- Easier to maintain and evolve

**Common Module Provides:**
- `GraphQLControllerBase` - HTTP endpoint (reusable across modules)
- `GraphQLWiringFactory` - Base wiring factory (abstract class)
- `BaseSchemaRegistryFactory` - Base schema loading (abstract class)
- GraphQL dependencies (via `api` dependencies)

**Module-Specific:**
- GraphQL bean creation (`@Factory` class)
- Schema loading (extends `BaseSchemaRegistryFactory`)
- Resolver wiring (extends `GraphQLWiringFactory`)
- Federation entity resolvers

### Service Development

#### 1. Create Service Schema
```graphql
# File: service/kotlin/app/src/main/resources/graphql/schema.graphqls
type Product @key(fields: "id") {
  id: ID!
  name: String!
  priceCents: Int!
  stock: Int!
  createdAt: String
  updatedAt: String
}

type Query {
  products: [Product!]!
  product(id: ID!): Product
}

type Mutation {
  createProduct(input: ProductInput!): Product!
  updateProduct(id: ID!, input: ProductInput!): Product!
  deleteProduct(id: ID!): Boolean!
}
```

#### 2. Implement Resolvers
```kotlin
// File: service/kotlin/app/src/main/kotlin/.../ProductResolver.kt
@GraphQLApi
class ProductResolver {
    @Query
    fun products(): List<Product> = productRepository.findAll()
    
    @Query
    fun product(@Argument id: ID): Product? = productRepository.findById(id)
    
    @Mutation
    fun createProduct(@Argument input: ProductInput): Product {
        return productService.create(input)
    }
}
```

#### 3. Sync Schema to Contracts
```bash
# Using Neotool CLI (recommended)
./neotool graphql sync

# Select source: kotlin/app
# Select target: app (or create new)
# Confirm sync

# Or run script directly
./scripts/cli/commands/graphql/sync-schemas.sh sync
```

#### 4. Update Federation Configuration
```yaml
# File: contracts/graphql/supergraph/supergraph.yaml
federation_version: 2.4
subgraphs:
  app:
    routing_url: http://app:8080/graphql
    schema:
      file: ../subgraphs/app/schema.graphqls
```

### Schema Management

#### 1. Service Schema Development
```graphql
# File: service/kotlin/app/src/main/resources/graphql/schema.graphqls
type Product @key(fields: "id") {
  id: ID!
  name: String!
  priceCents: Int!
  stock: Int!
  createdAt: String
  updatedAt: String
}

type Query {
  products: [Product!]!
  product(id: ID!): Product
}
```

#### 2. Schema Synchronization
```bash
# Using Neotool CLI (recommended)
./neotool graphql sync

# Interactive selection:
# 1. Select source: kotlin/app
# 2. Select target: app (auto-detected, press Enter to accept)
# 3. Confirm sync

# Or run script directly
./scripts/cli/commands/graphql/sync-schemas.sh sync
```

#### 3. Supergraph Generation
```bash
# Using Neotool CLI (recommended)
./neotool graphql generate

# This creates:
# - contracts/graphql/supergraph/supergraph.graphql
# - contracts/graphql/supergraph/supergraph.local.graphql

# Or run script directly
./scripts/cli/commands/graphql/generate-schema.sh
```

#### 4. Router Configuration
```yaml
# File: infra/gateway/router/router.yaml
router:
  cors:
    origins: ["http://localhost:3000"]
  headers:
    all:
      request:
        - name: "x-request-id"
          value: "{request.headers.x-request-id}"
  plugins:
    - name: "auth"
      config:
        jwt_secret: "${env.JWT_SECRET}"
```

---

## CI/CD Integration

### GitHub Actions Workflow

The project includes automated CI/CD for GraphQL schema management:

```yaml
# .github/workflows/graphql-schema.yml
name: GraphQL Schema Management
on:
  push:
    branches: [main, develop]
    paths: ['contracts/graphql/**', 'service/**/schema.graphqls']

jobs:
  schema-validation:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Validate Schema Consistency
        run: |
          # Using CLI (automatically uses Docker in CI)
          CI=true ./neotool graphql validate
          # Or run script directly
          # CI=true ./scripts/cli/commands/graphql/sync-schemas.sh validate
      - name: Generate Supergraph Schema
        run: |
          # Using CLI (automatically uses Docker in CI)
          CI=true ./neotool graphql generate
          # Or run script directly
          # CI=true ./scripts/cli/commands/graphql/generate-schema.sh
      - name: Check for Changes
        run: |
          git diff --exit-code contracts/graphql/supergraph/
```

### Docker Integration

The solution supports both local and Docker-based execution:

#### Local Development
```bash
# Install rover locally
curl -sSL https://rover.apollo.dev/nix/latest | sh

# Generate schema using CLI
./neotool graphql generate

# Or run script directly
./scripts/cli/commands/graphql/generate-schema.sh
```

#### CI/CD Environment
```bash
# Use Docker (no local installation needed) - using CLI
./neotool graphql generate --docker

# Or with environment variable
CI=true ./neotool graphql generate

# Or run script directly
CI=true ./scripts/cli/commands/graphql/generate-schema.sh

# Or explicitly
USE_DOCKER_ROVER=true ./scripts/cli/commands/graphql/generate-schema.sh
```

### Schema Validation Pipeline

1. **Schema Consistency Check**: Validates schemas between services and contracts
2. **Supergraph Generation**: Creates composed schema using Apollo Rover
3. **Change Detection**: Checks for uncommitted schema changes
4. **Artifact Upload**: Uploads generated schemas as build artifacts

---

## Testing

### Local Testing
- Use Apollo Studio for schema testing
- Test individual subgraphs in isolation
- Validate supergraph composition
- Test federation queries end-to-end

### Integration Testing
- Test cross-service queries
- Validate entity resolution
- Test error handling and fallbacks
- Performance testing with federation overhead

### Schema Testing
- Automated schema validation and integration testing
- Test backward compatibility for public APIs
- Validate federation directives and entity keys
- Test schema composition and supergraph generation

## Monitoring and Observability

### Query Tracing
- Track queries across subgraphs
- Monitor query execution paths
- Identify performance bottlenecks
- Debug federation query planning

### Performance Metrics
- Monitor federation overhead
- Track query response times per subgraph
- Measure query complexity and depth
- Monitor entity resolution performance

### Error Tracking
- Track subgraph failures and fallbacks
- Monitor entity resolution errors
- Alert on schema composition failures
- Log federation-specific errors

### Schema Changes
- Monitor schema evolution and breaking changes
- Track schema version history
- Alert on incompatible schema changes
- Monitor supergraph composition success rates

### Observability Tools
- **OpenTelemetry**: Distributed tracing and metrics
- **Prometheus**: Metrics collection and storage
- **Grafana**: Monitoring dashboards and visualization
- **Apollo Studio**: GraphQL-specific observability

## Deployment

### Development
- Local Docker Compose setup
- Individual service development
- Schema composition testing
- Local supergraph generation

### Production
- Kubernetes deployment
- GitOps with ArgoCD
- Schema registry integration
- Automated schema validation
- Blue-green deployments for schema changes

## Troubleshooting

### Common Issues

#### 1. Schema Composition Failures
- **Symptom**: Supergraph generation fails
- **Causes**: 
  - Missing or incorrect federation directives
  - Conflicting type definitions across subgraphs
  - Invalid `@key` field definitions
- **Solution**: 
  - Verify all entities have `@key` directives
  - Check for type name conflicts
  - Validate federation directive syntax
  - Review composition error messages

#### 2. Entity Resolution Errors
- **Symptom**: Queries fail with entity resolution errors
- **Causes**:
  - Missing `fetchEntities` callback in GraphQLFactory
  - Incorrect `@key` field definitions
  - Entity not found in repository
- **Solution**:
  - Ensure `fetchEntities` callback is implemented
  - Verify `@key` fields match entity keys
  - Check entity repository implementation
  - Validate entity ID format and parsing

#### 3. Performance Issues
- **Symptom**: Slow query execution
- **Causes**:
  - Excessive use of `@requires` directives
  - Missing query complexity limits
  - Inefficient entity fetching
  - Network latency between services
- **Solution**:
  - Optimize `@requires` usage
  - Implement query complexity limits
  - Add caching for frequently accessed entities
  - Monitor and optimize network calls

#### 4. Breaking Changes
- **Symptom**: Schema changes break existing clients
- **Causes**:
  - Removing required fields
  - Changing field types
  - Removing types or queries
- **Solution**:
  - Use schema versioning
  - Deprecate fields before removal
  - Test schema changes in staging
  - Use GraphOS schema registry for change tracking

### Debug Tools
- **Apollo Studio Explorer**: Interactive query testing and debugging
- **Federation composition logs**: Detailed composition error messages
- **Query execution traces**: Step-by-step query execution tracking
- **Schema validation tools**: Automated schema validation and linting
- **Rover CLI**: Command-line tools for schema management and debugging

## Best Practices

### Service Design
1. **Single Responsibility**: Each service should handle one business domain
2. **Entity Ownership**: Clear ownership of entities across services
3. **Minimal Dependencies**: Reduce inter-service dependencies
4. **Consistent Naming**: Use consistent naming conventions across services

### Schema Design
- Use clear entity boundaries
- Minimize cross-service dependencies
- Design for independent service evolution
- Use consistent naming conventions
- Always define `@key` for entities
- Use `@external` for fields defined elsewhere
- Minimize use of `@requires` for performance
- Use `@provides` to share data efficiently

### Schema Management
1. **Backward Compatibility**: Maintain backward compatibility for public APIs
2. **Deprecation Strategy**: Use `@deprecated` directive for planned removals
3. **Documentation**: Comprehensive schema documentation with examples
4. **Testing**: Automated schema validation and integration testing
5. **Versioning**: Use schema versioning for breaking changes

### Service Independence
- Each service owns its domain data
- Services can evolve independently
- Use federation for data composition, not tight coupling
- Design for eventual consistency

### Performance Optimization
1. **Query Complexity**: Implement query complexity limits
2. **Caching Strategy**: Use appropriate caching at service and router levels
3. **Connection Pooling**: Optimize database connections
4. **Monitoring**: Continuous performance monitoring and alerting
5. **Query Planning**: Leverage Apollo Router's intelligent query planning

### Security
1. **Authentication**: Implement consistent authentication across services
2. **Authorization**: Use fine-grained authorization policies
3. **Input Validation**: Validate all inputs at service boundaries
4. **Audit Logging**: Comprehensive audit logging for security events

---

## Tools and Technologies

### Core Technologies
- **Apollo Federation**: GraphQL federation implementation
- **Apollo Router**: Enterprise-grade GraphQL gateway
- **Kotlin + Micronaut**: Service implementation framework
- **Gradle**: Build system and dependency management

### Schema Management Tools
- **Neotool CLI**: Unified command-line interface for schema management (`./neotool graphql`)
- **Apollo Rover**: Schema composition and validation
- **sync-schemas.sh**: Interactive schema synchronization script (`scripts/cli/commands/graphql/sync-schemas.sh`)
- **generate-schema.sh**: Supergraph generation script (`scripts/cli/commands/graphql/generate-schema.sh`)
- **Docker**: Containerized rover execution for CI/CD
- **GitHub Actions**: Automated schema validation and generation

### Development Tools
- **GraphQL Codegen**: Type-safe code generation (frontend)
- **Micronaut GraphQL**: Server-side GraphQL implementation
- **Docker**: Containerization and deployment
- **Gradle**: Build automation and dependency management

### Monitoring and Observability
- **OpenTelemetry**: Distributed tracing and metrics
- **Prometheus**: Metrics collection and storage
- **Grafana**: Monitoring dashboards and visualization

---

## Conclusion

Apollo Federation provides the ideal path for scaling GraphQL in our service layer. By implementing a federated architecture with GraphOS platform integration, we achieve:

- **Scalability**: Independent service development and deployment
- **Maintainability**: Clear service boundaries and responsibilities
- **Performance**: Intelligent query planning and caching
- **Security**: Comprehensive security and access management
- **Observability**: Full visibility into API performance and usage

This architecture enables our teams to build and maintain complex, distributed GraphQL APIs while providing the tooling and security features necessary for enterprise-scale applications.

---

## Resources

### Official Documentation
- [Apollo Federation Documentation](https://www.apollographql.com/docs/federation/)
- [Federation Best Practices](https://www.apollographql.com/docs/federation/best-practices/)
- [Schema Composition Guide](https://www.apollographql.com/docs/federation/quickstart/)
- [Apollo Router Documentation](https://www.apollographql.com/docs/router/)
- [GraphOS Platform](https://www.apollographql.com/docs/graphos/)

### Tools and Utilities
- [Apollo Rover CLI](https://www.apollographql.com/docs/rover/)
- [GraphQL Code Generator](https://the-guild.dev/graphql/codegen)
- [GraphQL Playground](https://github.com/graphql/graphql-playground)

### Community Resources
- [Apollo Federation Community](https://community.apollographql.com/)
- [GraphQL Specification](https://spec.graphql.org/)
- [Federation Examples](https://github.com/apollographql/federation-demo)
