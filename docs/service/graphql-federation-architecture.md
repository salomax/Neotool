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
        │   ├── supergraph.dev.graphql # Development supergraph
        │   └── scripts/            # Schema management scripts
        │       └── generate-schema.sh
        ├── shared/                 # Shared types and directives
        └── sync-schemas.sh         # Schema synchronization script
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

# 2. Sync schemas to contracts
cd contracts/graphql
./sync-schemas.sh sync

# 3. Generate supergraph schema
./sync-schemas.sh generate

# 4. Start services with updated schemas
```

#### Schema Discovery

The `sync-schemas.sh` script automatically discovers schemas:
- **Pattern**: `{language}/{module}/src/main/resources/graphql/schema.graphqls`
- **Examples**: `kotlin/app`, `kotlin/security`, `kotlin/common`
- **Interactive**: Select source and target subgraph

#### Available Commands

```bash
./sync-schemas.sh sync      # Synchronize schemas from services to contracts
./sync-schemas.sh validate  # Validate schema consistency
./sync-schemas.sh generate  # Generate supergraph schema
./sync-schemas.sh all       # Run all operations
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
# Navigate to contracts directory
cd contracts/graphql

# Sync schema from service to contracts
./sync-schemas.sh sync

# Select source: kotlin/app
# Select target: app (or create new)
# Confirm sync
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
# Sync schemas from services to contracts
cd contracts/graphql
./sync-schemas.sh sync

# Interactive selection:
# 1. Select source: kotlin/app
# 2. Select target: app
# 3. Confirm sync
```

#### 3. Supergraph Generation
```bash
# Generate composed supergraph schema
cd contracts/graphql
./sync-schemas.sh generate

# This creates:
# - contracts/graphql/supergraph/supergraph.graphql
# - contracts/graphql/supergraph/supergraph.dev.graphql
```

#### 4. Router Configuration
```yaml
# File: service/gateway/router/router.yaml
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
          cd contracts/graphql
          CI=true ./sync-schemas.sh validate
      - name: Generate Supergraph Schema
        run: |
          cd contracts/graphql
          CI=true ./sync-schemas.sh generate
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

# Generate schema
cd contracts/graphql
./sync-schemas.sh generate
```

#### CI/CD Environment
```bash
# Use Docker (no local installation needed)
cd contracts/graphql
CI=true ./sync-schemas.sh generate

# Or explicitly
USE_DOCKER_ROVER=true ./sync-schemas.sh generate
```

### Schema Validation Pipeline

1. **Schema Consistency Check**: Validates schemas between services and contracts
2. **Supergraph Generation**: Creates composed schema using Apollo Rover
3. **Change Detection**: Checks for uncommitted schema changes
4. **Artifact Upload**: Uploads generated schemas as build artifacts

---

## Best Practices

### Service Design
1. **Single Responsibility**: Each service should handle one business domain
2. **Entity Ownership**: Clear ownership of entities across services
3. **Minimal Dependencies**: Reduce inter-service dependencies
4. **Consistent Naming**: Use consistent naming conventions across services

### Schema Management
1. **Backward Compatibility**: Maintain backward compatibility for public APIs
2. **Deprecation Strategy**: Use `@deprecated` directive for planned removals
3. **Documentation**: Comprehensive schema documentation with examples
4. **Testing**: Automated schema validation and integration testing

### Performance Optimization
1. **Query Complexity**: Implement query complexity limits
2. **Caching Strategy**: Use appropriate caching at service and router levels
3. **Connection Pooling**: Optimize database connections
4. **Monitoring**: Continuous performance monitoring and alerting

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
- **Apollo Rover**: Schema composition and validation
- **sync-schemas.sh**: Interactive schema synchronization script
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
