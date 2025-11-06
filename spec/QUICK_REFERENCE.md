---
title: NeoTool Quick Reference
type: reference
category: quick-reference
status: current
version: 1.0.0
tags: [quick-reference, cheat-sheet, reference]
---

# NeoTool Quick Reference

> **Purpose**: Quick reference guide for common tasks, patterns, and commands in NeoTool. Optimized for quick lookups.

## Technology Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Backend Language | Kotlin | Latest |
| Backend Framework | Micronaut | Latest |
| Frontend Framework | Next.js | 14+ |
| Frontend Language | TypeScript | Latest |
| API | GraphQL (Apollo Federation) | Latest |
| Database | PostgreSQL | 15+ |
| Container Runtime | Docker | Latest |
| Orchestration | Kubernetes | Latest |

## Project Structure

```
neotool/
├── service/          # Backend services
│   ├── kotlin/      # Main service
│   └── gateway/     # Apollo Router
├── web/              # Web frontend
├── mobile/           # Mobile app
├── infra/            # Infrastructure
├── contracts/        # API contracts
├── design/           # Design system
└── spec/             # Specification (this folder)
```

## Common Commands

### Project Setup
```bash
./neotool --version        # Check system requirements
./neotool init             # Initialize project
./neotool rename-project   # Rename project
./neotool clean-examples   # Remove example code
```

### Development
```bash
# Backend
cd service/kotlin
./gradlew build           # Build
./gradlew test            # Run tests
./gradlew run             # Run locally

# Frontend
cd web
npm install               # Install dependencies
npm run dev               # Development server
npm run build             # Production build
npm test                  # Run tests
```

### Docker
```bash
docker-compose -f infra/docker/docker-compose.local.yml up -d
docker-compose -f infra/docker/docker-compose.local.yml down
```

## GraphQL Patterns

### Entity Definition
```graphql
type Product @key(fields: "id") {
  id: ID!
  name: String!
  price: Float!
}
```

### Resolver (Kotlin)
```kotlin
@Singleton
class ProductResolver : GraphQLResolver<Product> {
    fun name(product: Product): String = product.name
}
```

### Query (TypeScript)
```typescript
const GET_PRODUCTS = gql`
  query GetProducts {
    products {
      id
      name
      price
    }
  }
`;
```

## Frontend Patterns

### Server Component
```typescript
// app/page.tsx (default is server component)
export default async function Page() {
  const data = await fetchData();
  return <div>{data}</div>;
}
```

### Client Component
```typescript
'use client';

export function InteractiveComponent() {
  const [state, setState] = useState();
  return <button onClick={() => setState('clicked')}>Click</button>;
}
```

### GraphQL Hook
```typescript
import { useQuery } from '@apollo/client';
import { GET_PRODUCTS } from '@/lib/graphql/queries';

export function useProducts() {
  const { data, loading, error } = useQuery(GET_PRODUCTS);
  return { products: data?.products, loading, error };
}
```

## Backend Patterns

### Entity (JPA)
```kotlin
@Entity
@Table(name = "products")
data class ProductEntity(
    @Id
    @GeneratedValue
    val id: Long? = null,
    val name: String,
    val price: BigDecimal
)
```

### Repository
```kotlin
@Repository
interface ProductRepository : CrudRepository<ProductEntity, Long> {
    fun findByName(name: String): List<ProductEntity>
}
```

### Service
```kotlin
@Singleton
class ProductService(
    private val repository: ProductRepository
) {
    fun findAll(): List<Product> {
        return repository.findAll().map { it.toDomain() }
    }
}
```

## Database Patterns

### Migration (Flyway)
```sql
-- V1__create_products_table.sql
CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    price DECIMAL(10, 2) NOT NULL
);
```

### Query with JSONB
```sql
SELECT * FROM products 
WHERE metadata->>'category' = 'electronics';
```

## File Naming Conventions

| Type | Convention | Example |
|------|-----------|---------|
| Components | PascalCase | `UserProfile.tsx` |
| Files | kebab-case | `user-profile.tsx` |
| Hooks | camelCase with `use` | `useUserProfile` |
| Types | PascalCase | `UserProfileType` |
| Entities | PascalCase with `Entity` | `ProductEntity` |
| Services | PascalCase with `Service` | `ProductService` |

## Directory Structure Patterns

### Frontend (`web/src/`)
```
src/
├── app/              # Next.js App Router pages
├── lib/              # External integrations
│   ├── api/         # API clients
│   └── graphql/     # GraphQL operations
├── shared/           # Shared application code
│   ├── components/  # Reusable components
│   └── hooks/       # Shared hooks
└── styles/          # Global styles
```

### Backend (`service/kotlin/app/src/main/kotlin/`)
```
kotlin/
└── com/company/app/
    ├── api/          # GraphQL resolvers
    ├── domain/       # Domain models
    ├── service/      # Business logic
    ├── repository/   # Data access
    └── entity/       # JPA entities
```

## Key Concepts

- **Monorepo**: Single repository for all components
- **GraphQL Federation**: Distributed GraphQL schema composition
- **Type Safety**: End-to-end type safety from DB to UI
- **Server Components**: React components that render on server
- **Client Components**: Interactive React components
- **Domain-Driven Design**: Business logic organized by domain

## Related Documentation

- [Architecture Overview](./ARCHITECTURE_OVERVIEW.md)
- [Glossary](./GLOSSARY.md)
- [Specification Manifest](./SPECIFICATION_MANIFEST.md)
- [Project Setup](./PROJECT_SETUP.md)

