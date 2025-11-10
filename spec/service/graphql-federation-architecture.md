---
title: GraphQL Federation Architecture
type: guide
category: api
status: current
version: 2.0.0
tags: [graphql, federation, apollo, api, architecture, kotlin, micronaut]
related:
  - ARCHITECTURE_OVERVIEW.md
  - adr/0003-kotlin-micronaut-backend.md
  - contracts/graphql-federation.md
  - web/web-graphql-operations.md
---

# GraphQL Federation Architecture

This document outlines the GraphQL Federation architecture implemented in the service layer, leveraging Apollo Federation for scalable, distributed GraphQL API management. The implementation follows true federation patterns where each service is independently deployable and composed by Apollo Router.

---

## Overview

Our service layer implements **Apollo Federation v2** as the core architecture for GraphQL at scale. This approach allows us to build a single, cohesive schema (the Supergraph) from multiple federated services, solving the traditional problems of monolithic GraphQL APIs while providing enterprise-grade tooling and security.

**Key Principles:**
- **True Federation**: Each service runs independently with its own GraphQL endpoint
- **Service Ownership**: Each service owns its domain entities and schema
- **Router Composition**: Apollo Router composes the supergraph from independent services
- **Abstract Base Classes**: Common GraphQL infrastructure provided via abstract base classes
- **Type Safety**: End-to-end type safety from database to GraphQL schema

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
- Example: App service handles Product and Customer data, Security service handles User data
- Aligns with modern microservice architectures
- Enables colocated data sources

#### Decentralized Development
- Multiple teams can independently develop and deploy their parts of the graph
- Services can extend a single type across different domains
- Reduces coordination overhead between teams

#### Cohesive Schema Composition
- **Apollo Router** uses `@key` directives to understand relationships
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
│   │   ├── common/                    # Common GraphQL infrastructure
│   │   │   └── src/main/kotlin/.../graphql/
│   │   │       ├── BaseGraphQLFactory.kt          # Abstract base for GraphQL factories
│   │   │       ├── BaseSchemaRegistryFactory.kt   # Abstract base for schema loading
│   │   │       ├── GraphQLWiringFactory.kt        # Abstract base for resolver registration
│   │   │       ├── GenericCrudResolver.kt         # CRUD resolver with payload handling
│   │   │       ├── GraphQLController.kt           # HTTP endpoint controller
│   │   │       └── ...                            # Utilities and helpers
│   │   ├── app/                      # App service (federated subgraph)
│   │   │   └── src/main/
│   │   │       ├── kotlin/.../graphql/
│   │   │       │   ├── AppGraphQLFactory.kt       # Service-specific factory
│   │   │       │   ├── AppSchemaRegistryFactory.kt # Schema loader
│   │   │       │   ├── AppWiringFactory.kt        # Resolver registration
│   │   │       │   └── resolvers/                 # GraphQL resolvers
│   │   │       └── resources/graphql/
│   │   │           └── schema.graphqls            # Service schema (source of truth)
│   │   └── security/                 # Security service (federated subgraph)
│   │       └── src/main/
│   │           ├── kotlin/.../graphql/
│   │           │   ├── SecurityGraphQLFactory.kt
│   │           │   ├── SecuritySchemaRegistryFactory.kt
│   │   │       │   └── SecurityWiringFactory.kt
│   │           └── resources/graphql/
│   │               └── schema.graphqls
│   └── gateway/
│       └── router/                   # Apollo Router configuration
│           ├── router.yaml           # Production router config
│           └── router.dev.yaml       # Development router config
└── contracts/
    └── graphql/                      # GraphQL contracts (federation hub)
        ├── subgraphs/                # Individual service schemas (synced)
        │   ├── app/
        │   │   └── schema.graphqls   # Synced from service
        │   └── security/
        │       └── schema.graphqls   # Synced from service
        └── supergraph/               # Federation configuration
            ├── supergraph.graphql    # Generated supergraph schema
            └── supergraph.dev.graphql # Development supergraph
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

type User @key(fields: "id") {
  id: ID!
  email: String!
  displayName: String
}
```

#### Entity References
```graphql
type Order {
  id: ID!
  customer: Customer!  # Reference to Customer entity (from App service)
  products: [Product!]! # Reference to Product entities (from App service)
  user: User!          # Reference to User entity (from Security service)
  totalCents: Int!
}
```

#### Service Extensions
```graphql
# In Review service
extend type Product @key(fields: "id") {
  id: ID! @external
  reviews: [Review!]!  # Extended by Review service
}

# In Recommendation service
extend type Product @key(fields: "id") {
  id: ID! @external
  recommendations: [Product!]! # Extended by Recommendation service
}
```

---

## Abstract Layer Architecture

The common GraphQL infrastructure provides abstract base classes that each service extends to create its own GraphQL implementation. This ensures consistency while allowing service-specific customization.

### Core Abstract Classes

#### 1. BaseGraphQLFactory

**Purpose**: Abstract base class for creating GraphQL instances with federation support.

**Location**: `service/kotlin/common/src/main/kotlin/io/github/salomax/neotool/graphql/BaseGraphQLFactory.kt`

**Key Responsibilities**:
- Transforms schema with Apollo Federation
- Configures entity fetching and type resolution
- Adds instrumentation (query complexity, depth limits)
- Configures error handling

**Abstract Methods**:
- `fetchEntity(typename: String, keyFields: Map<String, Any>): Any?` - Fetch entities by key fields
- `resolveEntityType(entity: Any): String?` - Resolve entity object to GraphQL type name

**Helper Methods**:
- `extractId(keyFields: Map<String, Any>): String?` - Extract ID from key fields
- `extractKeyField(keyFields: Map<String, Any>, fieldName: String): String?` - Extract specific key field

**Usage Example**:
```kotlin
@Factory
class AppGraphQLFactory(
    schemaRegistry: TypeDefinitionRegistry,
    wiringFactory: AppWiringFactory,
    private val productService: ProductService,
    private val customerService: CustomerService
) : BaseGraphQLFactory(
    schemaRegistry = schemaRegistry,
    runtimeWiring = wiringFactory.build(),
    serviceName = "App"
) {
    @Singleton
    fun graphQL(): graphql.GraphQL {
        return buildGraphQL()
    }
    
    override fun fetchEntity(typename: String, keyFields: Map<String, Any>): Any? {
        val id = extractId(keyFields) ?: return null
        return when (typename) {
            "Product" -> productService.get(toUUID(id))
            "Customer" -> customerService.get(toUUID(id))
            else -> null
        }
    }
    
    override fun resolveEntityType(entity: Any): String? {
        return when (entity) {
            is Product -> "Product"
            is Customer -> "Customer"
            else -> null
        }
    }
}
```

#### 2. BaseSchemaRegistryFactory

**Purpose**: Abstract base class for loading GraphQL schema definitions from resources.

**Location**: `service/kotlin/common/src/main/kotlin/io/github/salomax/neotool/graphql/BaseSchemaRegistryFactory.kt`

**Key Responsibilities**:
- Loads schema from classpath resources
- Supports merging multiple schema files
- Each service loads only its own schema

**Methods to Override**:
- `loadBaseSchema(): TypeDefinitionRegistry` - Load the main schema file (default: `graphql/schema.graphqls`)
- `loadModuleSchemas(): List<TypeDefinitionRegistry>` - Load additional schema files (optional)

**Usage Example**:
```kotlin
@Factory
@Singleton
class AppSchemaRegistryFactory : BaseSchemaRegistryFactory() {
    @Singleton
    override fun typeRegistry(): TypeDefinitionRegistry {
        return super.typeRegistry()
    }
    
    override fun loadBaseSchema(): TypeDefinitionRegistry {
        return loadSchemaFromResource("graphql/schema.graphqls")
    }
}
```

#### 3. GraphQLWiringFactory

**Purpose**: Abstract base class for building GraphQL runtime wiring with resolvers.

**Location**: `service/kotlin/common/src/main/kotlin/io/github/salomax/neotool/graphql/GraphQLWiringFactory.kt`

**Key Responsibilities**:
- Separates Query, Mutation, and Subscription resolver registration
- Supports custom type resolvers
- Provides consistent resolver registration pattern

**Abstract Methods**:
- `registerQueryResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder`
- `registerMutationResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder`
- `registerSubscriptionResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder`

**Optional Override**:
- `registerCustomTypeResolvers(builder: RuntimeWiring.Builder): RuntimeWiring.Builder` - Register resolvers for custom types

**Usage Example**:
```kotlin
@Singleton
class AppWiringFactory(
    private val productResolver: ProductResolver,
    private val customerResolver: CustomerResolver
) : GraphQLWiringFactory() {
    
    override fun registerQueryResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder {
        return type
            .dataFetcher("products", createValidatedDataFetcher { _ ->
                productResolver.list()
            })
            .dataFetcher("product", createCrudDataFetcher("getProductById") { id ->
                productResolver.getById(id)
            })
    }
    
    override fun registerMutationResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder {
        return type
            .dataFetcher("createProduct", createMutationDataFetcher("createProduct") { input ->
                productResolver.create(input)
            })
    }
    
    override fun registerSubscriptionResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder {
        return type // No subscriptions
    }
}
```

#### 4. GenericCrudResolver

**Purpose**: Abstract base class for GraphQL CRUD resolvers with automatic payload handling.

**Location**: `service/kotlin/common/src/main/kotlin/io/github/salomax/neotool/graphql/GenericCrudResolver.kt`

**Key Features**:
- Automatic input validation using Bean Validation
- Success/error payload handling via `GraphQLPayload`
- Graceful error handling (invalid IDs return null, not errors)
- UUID parsing with proper error handling
- Standard CRUD operations: create, update, delete, getById, list

**Abstract Methods**:
- `mapToInputDTO(input: Map<String, Any?>): InputDTO` - Map GraphQL input to DTO
- `mapToEntity(dto: InputDTO, id: ID? = null): Entity` - Map DTO to Entity

**Usage Example**:
```kotlin
@Singleton
class ProductResolver(
    private val productService: ProductService,
    validator: Validator
) : GenericCrudResolver<Product, ProductInputDTO, UUID>() {
    
    override val validator: Validator = validator
    override val service: CrudService<Product, UUID> = ProductCrudService(productService)
    
    override fun mapToInputDTO(input: Map<String, Any?>): ProductInputDTO {
        return ProductInputDTO(
            name = extractField(input, "name"),
            sku = extractField(input, "sku"),
            priceCents = extractField(input, "priceCents", 0L),
            stock = extractField(input, "stock", 0)
        )
    }
    
    override fun mapToEntity(dto: ProductInputDTO, id: UUID?): Product {
        return Product(
            id = id,
            name = dto.name,
            sku = dto.sku,
            priceCents = dto.priceCents,
            stock = dto.stock
        )
    }
}
```

#### 5. GraphQLControllerBase

**Purpose**: Base GraphQL HTTP endpoint controller for federated services.

**Location**: `service/kotlin/common/src/main/kotlin/io/github/salomax/neotool/graphql/GraphQLController.kt`

**Key Features**:
- Provides `/graphql` endpoint following GraphQL over HTTP specification
- Always returns HTTP 200 with errors in response body
- Extracts JWT tokens from Authorization header
- Validates operationName before execution
- Converts all exceptions to GraphQL error format

**Usage**: Each service automatically gets a `/graphql` endpoint by injecting the GraphQL bean. The controller is provided by the common module and works automatically.

### Supporting Utilities

#### GraphQLArgumentUtils

**Purpose**: Utility for handling GraphQL arguments safely and elegantly.

**Key Methods**:
- `getRequiredId(env: DataFetchingEnvironment, argumentName: String = "id"): String`
- `getRequiredInput(env: DataFetchingEnvironment, argumentName: String = "input"): Map<String, Any?>`
- `getOptionalArgument<T>(env: DataFetchingEnvironment, name: String): T?`
- `createCrudDataFetcher(...)` - Factory for CRUD data fetchers
- `createMutationDataFetcher(...)` - Factory for mutation data fetchers

#### GraphQLPayloadDataFetcher

**Purpose**: Utility for creating data fetchers that work with payloads.

**Key Methods**:
- `createPayloadDataFetcher(...)` - Extract data from payload
- `createMutationDataFetcher(...)` - Mutation with automatic payload handling
- `createUpdateMutationDataFetcher(...)` - Update mutation with payload handling

#### GraphQLPayload System

**Purpose**: Relay-style payload pattern for mutations.

**Components**:
- `GraphQLPayload<T>` - Base interface
- `SuccessPayload<T>` - Success case with data
- `ErrorPayload<T>` - Error case with error list
- `GraphQLPayloadFactory` - Factory for creating payloads

---

## Creating a New GraphQL Feature

This section provides a step-by-step guide for creating a new GraphQL feature in a federated service.

### Step 1: Define the GraphQL Schema

Create or update the schema file in your service:

**File**: `service/kotlin/{module}/src/main/resources/graphql/schema.graphqls`

```graphql
directive @key(fields: String!) repeatable on OBJECT | INTERFACE

type Product @key(fields: "id") {
  id: ID!
  name: String!
  sku: String!
  priceCents: Int!
  stock: Int!
  createdAt: String
  updatedAt: String
  version: Int!
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

input ProductInput {
  name: String!
  sku: String!
  priceCents: Int!
  stock: Int!
}
```

**Key Points**:
- Use `@key(fields: "id")` directive on entities for federation
- Define Query, Mutation, and Subscription types
- Use input types for mutations
- Follow GraphQL naming conventions (PascalCase for types, camelCase for fields)

### Step 2: Create Input DTO

Create an input DTO class with validation:

**File**: `service/kotlin/{module}/src/main/kotlin/.../dto/ProductInputDTO.kt`

```kotlin
import io.github.salomax.neotool.graphql.BaseInputDTO
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Min

@Serdeable
@Introspected
data class ProductInputDTO(
    @NotBlank(message = "Name is required")
    val name: String,
    
    @NotBlank(message = "SKU is required")
    val sku: String,
    
    @Min(value = 0, message = "Price must be positive")
    val priceCents: Long,
    
    @Min(value = 0, message = "Stock must be non-negative")
    val stock: Int
) : BaseInputDTO()
```

### Step 3: Create Resolver

Create a resolver extending `GenericCrudResolver`:

**File**: `service/kotlin/{module}/src/main/kotlin/.../graphql/resolvers/ProductResolver.kt`

```kotlin
@Singleton
class ProductResolver(
    private val productService: ProductService,
    validator: Validator
) : GenericCrudResolver<Product, ProductInputDTO, UUID>() {
    
    override val validator: Validator = validator
    override val service: CrudService<Product, UUID> = ProductCrudService(productService)
    
    override fun mapToInputDTO(input: Map<String, Any?>): ProductInputDTO {
        return ProductInputDTO(
            name = extractField(input, "name"),
            sku = extractField(input, "sku"),
            priceCents = extractField(input, "priceCents", 0L),
            stock = extractField(input, "stock", 0)
        )
    }
    
    override fun mapToEntity(dto: ProductInputDTO, id: UUID?): Product {
        return Product(
            id = id,
            name = dto.name,
            sku = dto.sku,
            priceCents = dto.priceCents,
            stock = dto.stock
        )
    }
    
    private fun <T> extractField(input: Map<String, Any?>, name: String, defaultValue: T? = null): T {
        @Suppress("UNCHECKED_CAST")
        return input[name] as? T ?: defaultValue ?: throw IllegalArgumentException("Field '$name' is required")
    }
}

// Adapter to make ProductService compatible with CrudService interface
class ProductCrudService(private val productService: ProductService) : CrudService<Product, UUID> {
    override fun create(entity: Product): Product = productService.create(entity)
    override fun update(entity: Product): Product? = productService.update(entity)
    override fun delete(id: UUID): Boolean {
        productService.delete(id)
        return true
    }
    override fun getById(id: UUID): Product? = productService.get(id)
    override fun list(): List<Product> = productService.list()
}
```

### Step 4: Register Resolvers in Wiring Factory

Update your service's wiring factory to register the new resolvers:

**File**: `service/kotlin/{module}/src/main/kotlin/.../graphql/AppWiringFactory.kt`

```kotlin
@Singleton
class AppWiringFactory(
    private val productResolver: ProductResolver
) : GraphQLWiringFactory() {
    
    override fun registerQueryResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder {
        return type
            .dataFetcher("products", createValidatedDataFetcher { _ ->
                productResolver.list()
            })
            .dataFetcher("product", createCrudDataFetcher("getProductById") { id ->
                productResolver.getById(id)
            })
    }
    
    override fun registerMutationResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder {
        return type
            .dataFetcher("createProduct", createMutationDataFetcher("createProduct") { input ->
                productResolver.create(input)
            })
            .dataFetcher("updateProduct", createUpdateMutationDataFetcher("updateProduct") { id, input ->
                productResolver.update(id, input)
            })
            .dataFetcher("deleteProduct", createCrudDataFetcher("deleteProduct") { id ->
                productResolver.delete(id)
            })
    }
    
    override fun registerSubscriptionResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder {
        return type // No subscriptions
    }
}
```

### Step 5: Update Entity Fetching in GraphQL Factory

If this is a new entity, update the service's GraphQL factory to handle entity fetching:

**File**: `service/kotlin/{module}/src/main/kotlin/.../graphql/AppGraphQLFactory.kt`

```kotlin
override fun fetchEntity(typename: String, keyFields: Map<String, Any>): Any? {
    val id = extractId(keyFields) ?: return null
    
    return try {
        when (typename) {
            "Product" -> productService.get(toUUID(id))
            // ... other entities
            else -> {
                logger.debug("Unknown entity type for App service: $typename")
                null
            }
        }
    } catch (e: Exception) {
        logger.debug("Failed to fetch entity for federation: $typename with id: $id", e)
        null
    }
}

override fun resolveEntityType(entity: Any): String? {
    return when (entity) {
        is Product -> "Product"
        // ... other entities
        else -> null
    }
}
```

### Step 6: Sync Schema to Contracts

Sync your service schema to the contracts directory:

```bash
# Using Neotool CLI (recommended)
./neotool graphql sync

# Interactive selection:
# 1. Select source: kotlin/app (or your module)
# 2. Select target: app (or create new)
# 3. Confirm sync
```

**What this does**:
- Copies `service/kotlin/{module}/src/main/resources/graphql/schema.graphqls`
- To `contracts/graphql/subgraphs/{subgraph}/schema.graphqls`
- Ensures contracts directory has the latest schema

### Step 7: Generate Supergraph Schema

Generate the composed supergraph schema:

```bash
# Using Neotool CLI (recommended)
./neotool graphql generate

# Or with Docker (for CI/CD)
./neotool graphql generate --docker
```

**What this does**:
- Uses Apollo Rover to compose all subgraph schemas
- Generates `contracts/graphql/supergraph/supergraph.graphql`
- Generates `contracts/graphql/supergraph/supergraph.dev.graphql`
- Validates federation directives and schema composition

### Step 8: Test the Feature

Test your GraphQL feature:

```bash
# Start your service
./gradlew :app:run

# Query the service directly
curl -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{
    "query": "query { products { id name priceCents } }"
  }'

# Or test through Apollo Router (if configured)
curl -X POST http://localhost:4000/graphql \
  -H "Content-Type: application/json" \
  -d '{
    "query": "query { products { id name priceCents } }"
  }'
```

---

## Contracts and Schema Management

### Contracts Directory Structure

The `contracts/graphql/` directory serves as the federation hub where all service schemas are composed into a supergraph.

```
contracts/graphql/
├── subgraphs/              # Individual service schemas (synced from services)
│   ├── app/
│   │   └── schema.graphqls # App service schema
│   └── security/
│       └── schema.graphqls # Security service schema
└── supergraph/             # Composed supergraph
    ├── supergraph.graphql  # Production supergraph (generated)
    └── supergraph.dev.graphql # Development supergraph (generated)
```

### Schema Synchronization Workflow

The project implements a **service-first development approach** where:

1. **Service modules are the source of truth** for GraphQL schemas
2. **Contracts directory** serves as the federation hub for composition
3. **Neotool CLI** manages schema synchronization and supergraph generation

#### Development Workflow

```bash
# 1. Edit schemas in service modules
# File: service/kotlin/app/src/main/resources/graphql/schema.graphqls

# 2. Sync schemas to contracts (using CLI)
./neotool graphql sync

# Interactive selection:
# - Select source: kotlin/app
# - Select target: app (or create new)
# - Confirm sync

# 3. Generate supergraph schema (using CLI)
./neotool graphql generate

# This creates:
# - contracts/graphql/supergraph/supergraph.graphql
# - contracts/graphql/supergraph/supergraph.dev.graphql

# 4. Start services with updated schemas
```

### Neotool CLI Commands

The Neotool CLI provides unified commands for GraphQL schema management:

#### Sync Command

**Purpose**: Synchronize schemas from service modules to contracts directory.

```bash
./neotool graphql sync
```

**What it does**:
- Discovers all `schema.graphqls` files in service modules
- Prompts to select source and target subgraph
- Copies schema from service to contracts directory
- Validates schema syntax

**Schema Discovery Pattern**:
- Pattern: `service/{language}/{module}/src/main/resources/graphql/schema.graphqls`
- Examples: `service/kotlin/app`, `service/kotlin/security`
- Excludes: `build/` and `bin/` directories

#### Validate Command

**Purpose**: Validate schema consistency between services and contracts.

```bash
./neotool graphql validate
```

**What it does**:
- Compares service schemas with contract schemas
- Detects schema drift
- Validates GraphQL syntax
- Checks for missing or extra files

#### Generate Command

**Purpose**: Generate supergraph schema from subgraph schemas.

```bash
# Local development (requires rover installed)
./neotool graphql generate

# CI/CD environment (uses Docker)
./neotool graphql generate --docker

# Or with environment variable
CI=true ./neotool graphql generate
```

**What it does**:
- Uses Apollo Rover to compose all subgraph schemas
- Validates federation directives (`@key`, `@external`, etc.)
- Generates composed supergraph schema
- Creates both production and development supergraphs

**Requirements**:
- Apollo Rover installed locally, OR
- Docker available (for CI/CD)

#### All Command

**Purpose**: Run sync, validate, and generate in sequence.

```bash
./neotool graphql all

# With Docker
./neotool graphql all --docker
```

**What it does**:
1. Syncs schemas from services to contracts
2. Validates schema consistency
3. Generates supergraph schema

**Use Cases**:
- Before committing schema changes
- In CI/CD pipelines
- After major schema updates

### Schema File Locations

**Service Schemas** (Source of Truth):
- `service/kotlin/app/src/main/resources/graphql/schema.graphqls`
- `service/kotlin/security/src/main/resources/graphql/schema.graphqls`

**Contract Schemas** (Federation Hub):
- `contracts/graphql/subgraphs/app/schema.graphqls`
- `contracts/graphql/subgraphs/security/schema.graphqls`

**Supergraph Schemas** (Generated):
- `contracts/graphql/supergraph/supergraph.graphql` (production)
- `contracts/graphql/supergraph/supergraph.dev.graphql` (development)

---

## Service Setup Pattern

Each federated service follows a consistent pattern with three main components:

### 1. Schema Registry Factory

**Purpose**: Loads the service's GraphQL schema from resources.

**Pattern**:
```kotlin
@Factory
@Singleton
class {Service}SchemaRegistryFactory : BaseSchemaRegistryFactory() {
    @Singleton
    override fun typeRegistry(): TypeDefinitionRegistry {
        return super.typeRegistry()
    }
    
    override fun loadBaseSchema(): TypeDefinitionRegistry {
        return loadSchemaFromResource("graphql/schema.graphqls")
    }
}
```

**Examples**:
- `AppSchemaRegistryFactory` - Loads App service schema
- `SecuritySchemaRegistryFactory` - Loads Security service schema

### 2. Wiring Factory

**Purpose**: Registers all GraphQL resolvers for the service.

**Pattern**:
```kotlin
@Singleton
class {Service}WiringFactory(
    private val resolvers: List<Resolver>
) : GraphQLWiringFactory() {
    
    override fun registerQueryResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder {
        return type
            .dataFetcher("field1", dataFetcher1)
            .dataFetcher("field2", dataFetcher2)
    }
    
    override fun registerMutationResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder {
        return type
            .dataFetcher("mutation1", mutationDataFetcher1)
    }
    
    override fun registerSubscriptionResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder {
        return type // No subscriptions
    }
}
```

**Examples**:
- `AppWiringFactory` - Registers Product and Customer resolvers
- `SecurityWiringFactory` - Registers User and Auth resolvers

### 3. GraphQL Factory

**Purpose**: Creates the GraphQL instance with federation support.

**Pattern**:
```kotlin
@Factory
class {Service}GraphQLFactory(
    schemaRegistry: TypeDefinitionRegistry,
    wiringFactory: {Service}WiringFactory,
    private val entityServices: List<EntityService>
) : BaseGraphQLFactory(
    schemaRegistry = schemaRegistry,
    runtimeWiring = wiringFactory.build(),
    serviceName = "{Service}"
) {
    @Singleton
    fun graphQL(): graphql.GraphQL {
        return buildGraphQL()
    }
    
    override fun fetchEntity(typename: String, keyFields: Map<String, Any>): Any? {
        val id = extractId(keyFields) ?: return null
        return when (typename) {
            "Entity1" -> entityService1.get(toUUID(id))
            "Entity2" -> entityService2.get(toUUID(id))
            else -> null
        }
    }
    
    override fun resolveEntityType(entity: Any): String? {
        return when (entity) {
            is Entity1 -> "Entity1"
            is Entity2 -> "Entity2"
            else -> null
        }
    }
}
```

**Examples**:
- `AppGraphQLFactory` - Handles Product and Customer entities
- `SecurityGraphQLFactory` - Handles User entities

### Complete Service Example

**App Service Setup**:

```kotlin
// 1. Schema Registry
@Factory
@Singleton
class AppSchemaRegistryFactory : BaseSchemaRegistryFactory() {
    override fun loadBaseSchema(): TypeDefinitionRegistry {
        return loadSchemaFromResource("graphql/schema.graphqls")
    }
}

// 2. Wiring Factory
@Singleton
class AppWiringFactory(
    private val productResolver: ProductResolver,
    private val customerResolver: CustomerResolver
) : GraphQLWiringFactory() {
    override fun registerQueryResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder {
        return type
            .dataFetcher("products", createValidatedDataFetcher { _ -> productResolver.list() })
            .dataFetcher("product", createCrudDataFetcher("getProductById") { id -> productResolver.getById(id) })
    }
    
    override fun registerMutationResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder {
        return type
            .dataFetcher("createProduct", createMutationDataFetcher("createProduct") { input -> productResolver.create(input) })
    }
    
    override fun registerSubscriptionResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder {
        return type
    }
}

// 3. GraphQL Factory
@Factory
class AppGraphQLFactory(
    schemaRegistry: TypeDefinitionRegistry,
    wiringFactory: AppWiringFactory,
    private val productService: ProductService,
    private val customerService: CustomerService
) : BaseGraphQLFactory(
    schemaRegistry = schemaRegistry,
    runtimeWiring = wiringFactory.build(),
    serviceName = "App"
) {
    @Singleton
    fun graphQL(): graphql.GraphQL {
        return buildGraphQL()
    }
    
    override fun fetchEntity(typename: String, keyFields: Map<String, Any>): Any? {
        val id = extractId(keyFields) ?: return null
        return when (typename) {
            "Product" -> productService.get(toUUID(id))
            "Customer" -> customerService.get(toUUID(id))
            else -> null
        }
    }
    
    override fun resolveEntityType(entity: Any): String? {
        return when (entity) {
            is Product -> "Product"
            is Customer -> "Customer"
            else -> null
        }
    }
}
```

---

## Apollo Router Configuration

The Apollo Router acts as the gateway that composes the supergraph from independent services.

### Router Configuration

**File**: `infra/gateway/router/router.yaml`

```yaml
supergraph:
  path: ./supergraph.graphql

server:
  listen: 0.0.0.0:4000
  cors:
    origins:
      - http://localhost:3000
    allow_credentials: true

subgraphs:
  app:
    routing_url: http://localhost:8081/graphql
    schema:
      subgraph_url: http://localhost:8081/graphql
  
  security:
    routing_url: http://localhost:8080/graphql
    schema:
      subgraph_url: http://localhost:8080/graphql

telemetry:
  tracing:
    propagation:
      trace_context: true
      baggage: true
  metrics:
    prometheus:
      enabled: true
      path: /metrics
```

### Router Startup

The router reads the supergraph schema and routes queries to the appropriate services:

```bash
# Start Apollo Router
docker run -p 4000:4000 \
  -v $(pwd)/infra/gateway/router:/dist \
  -v $(pwd)/contracts/graphql/supergraph:/dist \
  ghcr.io/apollographql/router:latest \
  --config /dist/router.yaml \
  --supergraph /dist/supergraph.graphql
```

---

## GraphQL Payload Pattern

The project uses a Relay-style payload pattern for mutations, providing consistent error handling and partial success support.

### Payload Structure

```kotlin
interface GraphQLPayload<T> {
    val data: T?
    val errors: List<GraphQLError>
    val success: Boolean
}
```

### Usage in Resolvers

```kotlin
// Success case
fun create(input: Map<String, Any?>): GraphQLPayload<Entity> {
    return try {
        val entity = service.create(mapToEntity(mapToInputDTO(input)))
        GraphQLPayloadFactory.success(entity)
    } catch (e: Exception) {
        GraphQLPayloadFactory.error(e)
    }
}
```

### GraphQL Schema

```graphql
type Mutation {
  createProduct(input: ProductInput!): ProductPayload!
}

type ProductPayload {
  data: Product
  errors: [GraphQLError!]!
  success: Boolean!
}

type GraphQLError {
  field: [String!]!
  message: String!
  code: String
}
```

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

### Federation Patterns

1. **Entity Keys**: Always define `@key(fields: "...")` on federated entities
2. **External Fields**: Use `@external` for fields defined in other services
3. **Minimal Requires**: Minimize use of `@requires` for performance
4. **Clear Ownership**: Each service owns specific entities

### Performance Optimization

1. **Query Complexity**: Implement query complexity limits (default: 100)
2. **Query Depth**: Prevent deeply nested queries (default: 10 levels)
3. **Caching Strategy**: Use appropriate caching at service and router levels
4. **Connection Pooling**: Optimize database connections

### Security

1. **Authentication**: Implement consistent authentication across services
2. **Authorization**: Use fine-grained authorization policies
3. **Input Validation**: Validate all inputs at service boundaries
4. **Audit Logging**: Comprehensive audit logging for security events

---

## Common Patterns and Examples

### CRUD Operations Pattern

**Schema**:
```graphql
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

**Resolver**:
```kotlin
class ProductResolver : GenericCrudResolver<Product, ProductInputDTO, UUID>() {
    // Implement mapToInputDTO and mapToEntity
}
```

**Wiring**:
```kotlin
.dataFetcher("products", createValidatedDataFetcher { _ -> productResolver.list() })
.dataFetcher("product", createCrudDataFetcher("getProductById") { id -> productResolver.getById(id) })
.dataFetcher("createProduct", createMutationDataFetcher("createProduct") { input -> productResolver.create(input) })
```

### Custom Query Pattern

**Schema**:
```graphql
type Query {
  searchProducts(query: String!, limit: Int): [Product!]!
}
```

**Resolver**:
```kotlin
class ProductResolver {
    fun search(query: String, limit: Int?): List<Product> {
        return productService.search(query, limit ?: 10)
    }
}
```

**Wiring**:
```kotlin
.dataFetcher("searchProducts", createValidatedDataFetcher { env ->
    val query = GraphQLArgumentUtils.getRequiredString(env, "query")
    val limit = GraphQLArgumentUtils.getOptionalArgument<Int>(env, "limit")
    productResolver.search(query, limit)
})
```

### Custom Mutation Pattern

**Schema**:
```graphql
type Mutation {
  updateProductStock(id: ID!, stock: Int!): ProductPayload!
}
```

**Resolver**:
```kotlin
class ProductResolver {
    fun updateStock(id: String, stock: Int): GraphQLPayload<Product> {
        return try {
            val product = productService.updateStock(toUUID(id), stock)
            GraphQLPayloadFactory.success(product)
        } catch (e: Exception) {
            GraphQLPayloadFactory.error(e)
        }
    }
}
```

**Wiring**:
```kotlin
.dataFetcher("updateProductStock", createValidatedDataFetcher(listOf("id", "stock")) { env ->
    val id = GraphQLArgumentUtils.getRequiredId(env)
    val stock = GraphQLArgumentUtils.getRequiredArgument<Int>(env, "stock")
    productResolver.updateStock(id, stock)
})
```

---

## Tools and Technologies

### Core Technologies
- **Apollo Federation v2**: GraphQL federation implementation
- **Apollo Router**: Enterprise-grade GraphQL gateway
- **graphql-java**: GraphQL Java implementation
- **Kotlin + Micronaut**: Service implementation framework
- **Gradle**: Build system and dependency management

### Schema Management Tools
- **Neotool CLI**: Unified command-line interface for schema management (`./neotool graphql`)
- **Apollo Rover**: Schema composition and validation
- **sync-schemas.sh**: Interactive schema synchronization script
- **Docker**: Containerized rover execution for CI/CD

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
        run: CI=true ./neotool graphql validate
      - name: Generate Supergraph Schema
        run: CI=true ./neotool graphql generate
      - name: Check for Changes
        run: git diff --exit-code contracts/graphql/supergraph/
```

### Docker Integration

The solution supports both local and Docker-based execution:

#### Local Development
```bash
# Install rover locally
curl -sSL https://rover.apollo.dev/nix/latest | sh

# Generate schema using CLI
./neotool graphql generate
```

#### CI/CD Environment
```bash
# Use Docker (no local installation needed)
./neotool graphql generate --docker

# Or with environment variable
CI=true ./neotool graphql generate
```

---

## Suggested Improvements

### 1. Enhanced Key Field Support

**Current Limitation**: `BaseGraphQLFactory.extractId()` only handles simple "id" keys.

**Improvement**: Add support for composite keys and custom key fields:

```kotlin
// Support composite keys like @key(fields: "id sku")
protected fun extractKeyFields(keyFields: Map<String, Any>, keyDefinition: String): Map<String, Any> {
    val fields = keyDefinition.split(" ")
    return fields.associateWith { keyFields[it] }
}
```

### 2. Schema Validation in BaseGraphQLFactory

**Current**: No validation of federation directives in base factory.

**Improvement**: Add validation to ensure all entities have `@key` directives:

```kotlin
private fun validateFederationDirectives(registry: TypeDefinitionRegistry) {
    registry.types().forEach { (typeName, typeDef) ->
        if (typeDef is ObjectTypeDefinition && isFederatedEntity(typeDef)) {
            val hasKey = typeDef.directives.any { it.name == "key" }
            if (!hasKey) {
                logger.warn("Federated entity ${typeName.name} missing @key directive")
            }
        }
    }
}
```

### 3. Enhanced Error Handling

**Current**: Basic error handling in entity fetching.

**Improvement**: Add retry logic and better error reporting:

```kotlin
protected open fun fetchEntity(typename: String, keyFields: Map<String, Any>): Any? {
    return retryWithBackoff(maxRetries = 3) {
        try {
            // Fetch logic
        } catch (e: Exception) {
            logger.error("Failed to fetch entity: $typename", e)
            throw e
        }
    }
}
```

### 4. Query Complexity Customization

**Current**: Fixed complexity limit (100) in `BaseGraphQLFactory`.

**Improvement**: Make complexity limits configurable per service:

```kotlin
abstract class BaseGraphQLFactory(
    // ...
    protected val maxQueryComplexity: Int = 100,
    protected val maxQueryDepth: Int = 10
) {
    fun buildGraphQL(): graphql.GraphQL {
        return graphql.GraphQL.newGraphQL(federatedSchema)
            .instrumentation(MaxQueryComplexityInstrumentation(maxQueryComplexity))
            .instrumentation(MaxQueryDepthInstrumentation(maxQueryDepth))
            .build()
    }
}
```

### 5. Subscription Support

**Current**: Subscription resolvers are registered but not fully implemented.

**Improvement**: Add reactive subscription support using reactive streams:

```kotlin
override fun registerSubscriptionResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder {
    return type
        .dataFetcher("productUpdated", createSubscriptionDataFetcher { env ->
            productService.subscribeToUpdates()
        })
}
```

### 6. Schema Caching

**Current**: Schema is loaded on every application start.

**Improvement**: Add schema caching for faster startup:

```kotlin
@Singleton
class AppSchemaRegistryFactory : BaseSchemaRegistryFactory() {
    @Volatile
    private var cachedRegistry: TypeDefinitionRegistry? = null
    
    override fun typeRegistry(): TypeDefinitionRegistry {
        return cachedRegistry ?: synchronized(this) {
            cachedRegistry ?: super.typeRegistry().also { cachedRegistry = it }
        }
    }
}
```

### 7. Enhanced Payload Error Codes

**Current**: Basic error codes in `GraphQLPayloadFactory`.

**Improvement**: Add more specific error codes and error categorization:

```kotlin
enum class GraphQLErrorCode {
    VALIDATION_ERROR,
    NOT_FOUND,
    UNAUTHORIZED,
    FORBIDDEN,
    CONFLICT,
    INTERNAL_ERROR
}
```

### 8. Batch Entity Fetching

**Current**: Entities are fetched one at a time in federation.

**Improvement**: Add batch fetching support for better performance:

```kotlin
protected abstract fun fetchEntitiesBatch(
    requests: List<EntityRequest>
): List<Any?>

data class EntityRequest(
    val typename: String,
    val keyFields: Map<String, Any>
)
```

---

## Conclusion

Apollo Federation provides the ideal path for scaling GraphQL in our service layer. By implementing a federated architecture with abstract base classes, we achieve:

- **Scalability**: Independent service development and deployment
- **Maintainability**: Clear service boundaries and responsibilities
- **Consistency**: Common patterns via abstract base classes
- **Type Safety**: End-to-end type safety from database to GraphQL schema
- **Performance**: Intelligent query planning and caching
- **Security**: Comprehensive security and access management
- **Observability**: Full visibility into API performance and usage

This architecture enables our teams to build and maintain complex, distributed GraphQL APIs while providing the tooling and security features necessary for enterprise-scale applications.
