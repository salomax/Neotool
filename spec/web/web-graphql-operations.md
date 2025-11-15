---
title: GraphQL Operations Structure
type: guide
category: frontend
status: current
version: 1.0.0
tags: [graphql, operations, apollo, frontend, code-generation]
related:
  - web/web-src-structure.md
  - web/web-custom-hooks.md
  - service/graphql-federation-architecture.md
---

# GraphQL Operations Structure for Web layer

This document describes the organization and best practices for managing GraphQL operations in a TypeScript web application.  
It follows **enterprise-grade principles** of scalability, modularity, and maintainability.

---

## Complete Development Workflow

### Phase 1: Setup & Configuration

#### 1. Install Dependencies
```bash
# Core GraphQL dependencies
pnpm add @apollo/client graphql

# Development dependencies for code generation
pnpm add -D @graphql-codegen/cli @graphql-codegen/typescript @graphql-codegen/typescript-operations @graphql-codegen/typescript-react-apollo
```

#### 2. Configure Apollo Client
```typescript
// web/src/lib/graphql/client.ts
import { ApolloClient, InMemoryCache } from '@apollo/client';

export const client = new ApolloClient({
  uri: process.env.NEXT_PUBLIC_GRAPHQL_URL || 'http://localhost:4000/graphql',
  cache: new InMemoryCache()
});
```

#### 3. Setup Codegen Configuration
```typescript
// web/codegen.ts
import type { CodegenConfig } from '@graphql-codegen/cli';

const config: CodegenConfig = {
  overwrite: true,
  schema: process.env.NEXT_PUBLIC_GRAPHQL_URL || 'http://localhost:4000/graphql',
  documents: ['src/**/*.{ts,tsx}'],
  generates: {
    'src/lib/graphql/types/__generated__/graphql.ts': {
      plugins: ['typescript'],
      config: {
        defaultScalarType: 'unknown',
        maybeValue: 'T | null',
        nonOptionalTypename: true,
        skipTypeNameForRoot: true,
        avoidOptionals: { field: true, inputValue: false },
      },
    },
    'src/': {
      preset: 'near-operation-file',
      presetConfig: {
        baseTypesPath: 'lib/graphql/types/__generated__/graphql.ts',
        extension: '.generated.ts',
      },
      plugins: [
        'typescript-operations',
        '@graphql-codegen/typed-document-node',
        'typescript-react-apollo',
      ],
      config: {
        withHooks: true,
        reactApolloVersion: 3,
        defaultScalarType: 'unknown',
        nonOptionalTypename: true,
        skipTypeNameForRoot: true,
        avoidOptionals: { field: true, inputValue: false },
      },
    },
  },
};

export default config;
```

### Phase 2: Create Operations Structure

#### 4. Create Domain Structure
```bash
# Create the directory structure
mkdir -p web/src/lib/graphql/operations/domain-a
mkdir -p web/src/lib/graphql/operations/domain-b
mkdir -p web/src/lib/graphql/fragments
```

#### 5. Create Reusable Fragments
```typescript
// web/src/lib/graphql/fragments/common.ts
import { gql } from '@apollo/client';

export const ITEM_FIELDS = gql`
  fragment ItemFields on Item {
    id
    name
    description
    status
    createdAt
    updatedAt
  }
`;

export const USER_FIELDS = gql`
  fragment UserFields on User {
    id
    name
    email
    role
    createdAt
    updatedAt
  }
`;
```

#### 6. Create Domain Operations
```typescript
// web/src/lib/graphql/operations/domain-a/queries.ts
import { gql } from '@apollo/client';
import { ITEM_FIELDS } from '../../fragments/common';

export const GET_ITEMS = gql`
  ${ITEM_FIELDS}
  query GetItems {
    items {
      ...ItemFields
    }
  }
`;

export const GET_ITEM = gql`
  ${ITEM_FIELDS}
  query GetItem($id: ID!) {
    item(id: $id) {
      ...ItemFields
    }
  }
`;
```

```typescript
// web/src/lib/graphql/operations/domain-a/mutations.ts
import { gql } from '@apollo/client';
import { ITEM_FIELDS } from '../../fragments/common';

export const CREATE_ITEM = gql`
  ${ITEM_FIELDS}
  mutation CreateItem($input: ItemInput!) {
    createItem(input: $input) {
      ...ItemFields
    }
  }
`;

export const UPDATE_ITEM = gql`
  ${ITEM_FIELDS}
  mutation UpdateItem($id: ID!, $input: ItemInput!) {
    updateItem(id: $id, input: $input) {
      ...ItemFields
    }
  }
`;

export const DELETE_ITEM = gql`
  mutation DeleteItem($id: ID!) {
    deleteItem(id: $id)
  }
`;
```

#### 7. Create Index Files
```typescript
// web/src/lib/graphql/operations/domain-a/index.ts
export * from './queries';
export * from './mutations';
```

```typescript
// web/src/lib/graphql/operations/index.ts
export * from './domain-a';
export * from './domain-b';
```

### Phase 3: Generate & Use

#### 8. Run Code Generation
```bash
cd web
pnpm codegen
```

This generates:
- `src/lib/graphql/types/__generated__/graphql.ts` - Base types
- `src/lib/graphql/operations/*/*.generated.ts` - Operation-specific types and hooks

#### 9. Use in Components
```typescript
// web/src/app/domain-a/page.tsx
import {
  useGetItemsQuery,
  GetItemsDocument
} from '@/lib/graphql/operations/domain-a/queries.generated';
import {
  useCreateItemMutation,
  useUpdateItemMutation,
  useDeleteItemMutation
} from '@/lib/graphql/operations/domain-a/mutations.generated';
import { ItemInput } from '@/lib/graphql/types/__generated__/graphql';

export default function DomainAPage() {
  const { data, loading, error, refetch } = useGetItemsQuery();
  const [createItem] = useCreateItemMutation();
  const [updateItem] = useUpdateItemMutation();
  const [deleteItem] = useDeleteItemMutation();

  if (loading) return <div>Loading...</div>;
  if (error) return <div>Error: {error.message}</div>;

  const handleCreateItem = async (input: ItemInput) => {
    try {
      await createItem({
        variables: { input },
        refetchQueries: [GetItemsDocument],
      });
    } catch (err) {
      console.error('Failed to create item:', err);
    }
  };

  return (
    <div>
      {data?.items.map(item => (
        <div key={item.id}>{item.name}</div>
      ))}
    </div>
  );
}
```

### Phase 4: Continuous Development

#### 10. When Schema Changes
1. Update backend GraphQL schema
2. Update frontend operations if needed
3. Run `pnpm codegen`
4. Fix any TypeScript errors
5. Update components if needed

#### 11. When Adding New Features
1. Add new operations to appropriate domain folder
2. Run `pnpm codegen`
3. Use generated types in components
4. Test the integration

---

## Development Workflow Summary

| Step | Action | Command | Output |
|------|--------|---------|--------|
| 1 | Install dependencies | `pnpm add @apollo/client graphql` | Dependencies installed |
| 2 | Configure codegen | Edit `codegen.ts` | Configuration ready |
| 3 | Create operations | Write GraphQL queries/mutations | Operations defined |
| 4 | Generate types & hooks | `pnpm codegen` | TypeScript types and hooks generated |
| 5 | Use in components | Import generated hooks | Fully typed components with Apollo Client |

---

## Directory Structure

```
web/src/lib/graphql/
├── operations/                 # Domain-based GraphQL operations
│   ├── domain-a/
│   │   ├── queries.ts          # Domain queries
│   │   ├── mutations.ts        # Domain mutations
│   │   └── index.ts            # Re-exports
│   ├── domain-b/
│   │   ├── queries.ts
│   │   ├── mutations.ts
│   │   └── index.ts
│   └── index.ts                # Centralized exports
├── fragments/                  # Reusable field fragments
│   └── common.ts
├── types/                      # TypeScript definitions (prefer auto-generated)
│   └── __generated__/
│       └── graphql.ts          # Generated types
├── client.ts                   # Apollo Client configuration
└── GraphQLProvider.tsx         # React context provider
```

---

## Import Examples

```typescript
// Import generated hooks from queries
import {
  useGetItemsQuery,
  useGetItemQuery,
  GetItemsDocument
} from '@/lib/graphql/operations/domain-a/queries.generated';

// Import generated hooks from mutations
import {
  useCreateItemMutation,
  useUpdateItemMutation,
  useDeleteItemMutation
} from '@/lib/graphql/operations/domain-a/mutations.generated';

// Import generated types
import { ItemInput, Item } from '@/lib/graphql/types/__generated__/graphql';

// Import raw operations (if needed)
import { GET_ITEMS, CREATE_ITEM } from '@/lib/graphql/operations/domain-a';
```

---

## Using Apollo Client Hooks

```typescript
import {
  useGetItemsQuery,
  GetItemsDocument
} from '@/lib/graphql/operations/domain-a/queries.generated';
import {
  useCreateItemMutation,
  useUpdateItemMutation,
  useDeleteItemMutation
} from '@/lib/graphql/operations/domain-a/mutations.generated';
import { ItemInput } from '@/lib/graphql/types/__generated__/graphql';

function ItemList() {
  const { data, loading, error, refetch } = useGetItemsQuery();
  const [createItem] = useCreateItemMutation();
  const [updateItem] = useUpdateItemMutation();
  const [deleteItem] = useDeleteItemMutation();

  const handleCreateItem = async (input: ItemInput) => {
    try {
      await createItem({
        variables: { input },
        refetchQueries: [GetItemsDocument],
      });
    } catch (err) {
      console.error('Failed to create item:', err);
    }
  };

  // Component logic...
}
```

---

## Generated Hooks vs Raw Apollo Client Hooks

### Why Use Generated Hooks?

**Generated Hooks (Recommended):**
```typescript
import { useGetItemsQuery } from '@/lib/graphql/operations/domain-a/queries.generated';

const { data, loading, error } = useGetItemsQuery();
```

**Raw Apollo Client Hooks (Legacy):**
```typescript
import { useQuery } from '@apollo/client';
import { GET_ITEMS } from '@/lib/graphql/operations/domain-a';

const { data, loading, error } = useQuery(GET_ITEMS);
```

### Benefits of Generated Hooks:

1. **Type Safety**: Fully typed with generated TypeScript interfaces
2. **Better IntelliSense**: Auto-completion for variables and return types
3. **Consistent Naming**: Predictable hook names (`useGetItemsQuery`, `useCreateItemMutation`)
4. **Built-in Optimizations**: Pre-configured with optimal Apollo Client settings
5. **Error Prevention**: Compile-time validation of variables and return types
6. **Future-Proof**: Automatically updated when schema changes

### When to Use Each:

- **Use Generated Hooks**: For all new development and existing components
- **Use Raw Hooks**: Only when you need custom Apollo Client configurations

---

## Best Practices

### 1. Domain-Based Organization
- Group operations by **business domain** (`domain-a`, `domain-b`, etc.)
- Keep **queries** and **mutations** in separate files
- Always re-export through an `index.ts` for clean imports

---

### 2. Fragment Reusability
- Define common fragments under `fragments/common.ts`
- Use fragments to avoid field duplication and enable composition
- Keep fragments cohesive and descriptive

```typescript
export const ITEM_FIELDS = gql`
  fragment ItemFields on Item {
    id
    name
    description
    status
    createdAt
    updatedAt
  }
`;
```

---

### 3. Naming Conventions
| Type        | Prefix/Suffix Example        | Notes |
|--------------|------------------------------|--------|
| **Queries**  | `GET_*`, `FETCH_*`           | Always name your operations |
| **Mutations**| `CREATE_*`, `UPDATE_*`, `DELETE_*` | Keep actions consistent |
| **Fragments**| `*_FIELDS`                   | Include entity name |
| **Types**    | `GetItemsQuery`, `CreateItemMutation` | Prefer auto-generated types |

> **Tip:** Named operations are easier to debug and monitor.  

---

### 4. Type Safety
- **Auto-generate** types with [GraphQL Code Generator](https://the-guild.dev/graphql/codegen)
- Prefer `near-operation-file` strategy (types live next to `.ts` files)
- Export operation-specific types:  
  `GetItemsQuery`, `CreateItemMutation`, etc.
- Avoid manually maintaining type definitions whenever possible

---

### 5. File Organization
- Use **one file per operation** if complex
- Group related operations together
- Avoid oversized files — split by entity or use case
- Keep naming consistent with schema entities

---

## Migration from Legacy Structure

The legacy `queries.ts` file was removed in favour of a domain-based approach.

### New Import Style:
```typescript
import { GET_ITEMS } from '@/lib/graphql/operations';
import { CREATE_ITEM } from '@/lib/graphql/operations/domain-a';
```

### Benefits:
1. Tree-shakable imports (smaller bundles)
2. Easier team collaboration
3. Scalable structure for future domains

---

## Adding New Operations

### 1. Create Domain Files
```bash
mkdir web/src/lib/graphql/operations/domain-c
touch web/src/lib/graphql/operations/domain-c/{queries,mutations,index}.ts
```

### 2. Define the Operation
```typescript
// operations/domain-c/queries.ts
import { gql } from '@apollo/client';
import { ENTITY_FIELDS } from '../../fragments/common';

export const GET_ENTITIES = gql`
  query GetEntities {
    entities {
      ...EntityFields
    }
  }
  ${ENTITY_FIELDS}
`;
```

### 3. Export from Domain Index
```typescript
// operations/domain-c/index.ts
export * from './queries';
export * from './mutations';
```

### 4. Update Main Index
```typescript
// operations/index.ts
export * from './domain-c';
```

---

## Fragment Guidelines

**When to create fragments:**
- Fields reused across multiple operations
- Nested or complex entity structures
- Objects expected to evolve frequently

**Naming convention:**
- Descriptive and entity-based: `ITEM_FIELDS`, `USER_FIELDS`
- Use `_FIELDS` suffix for fragments

> Avoid “micro-fragments” (too granular) — they reduce readability.

---

## Common Pitfalls & Troubleshooting

### Common Pitfalls

| Pitfall | Recommendation |
|----------|----------------|
| Circular imports between domains | Avoid cross-domain dependencies |
| Duplicated fields | Reuse fragments |
| Missing exports | Always re-export via index files |
| Inconsistent naming | Follow prefix/suffix conventions |
| Monolithic files | Split into focused files per entity |

### Troubleshooting

#### Codegen Errors

**Error: "Cannot query field 'X' on type 'Y'"**
- **Cause**: Invalid GraphQL fragment or operation
- **Solution**: Check your GraphQL schema and ensure fields exist
- **Example**: Remove invalid fragments like `MUTATION_RESPONSE_FIELDS`

**Error: "GraphQL Document Validation failed"**
- **Cause**: Malformed GraphQL syntax or invalid field references
- **Solution**: Validate your operations against the schema
- **Check**: Use GraphQL Playground or Apollo Studio to test queries

**Error: "Unable to find field 'X' on type 'Y'"**
- **Cause**: Field doesn't exist in the schema or typo in field name
- **Solution**: Verify field names match the schema exactly

#### Type Generation Issues

**Error: "Type 'X' is not assignable to type 'Y'"**
- **Cause**: Generated types don't match your usage
- **Solution**: Run `pnpm codegen` to regenerate types after schema changes

**Error: "Cannot find module '@/lib/graphql/operations'"**
- **Cause**: Missing index.ts files or incorrect exports
- **Solution**: Ensure all domain folders have proper index.ts files

**Error: "Module has no exported member 'useGetItemsQuery'"**
- **Cause**: Trying to import generated hooks from main files instead of .generated.ts files
- **Solution**: Import from the correct generated file:
  ```typescript
  // ❌ Wrong
  import { useGetItemsQuery } from '@/lib/graphql/operations/domain-a';
  
  // ✅ Correct
  import { useGetItemsQuery } from '@/lib/graphql/operations/domain-a/queries.generated';
  ```

**Error: "Property 'useQuery' does not exist on type 'typeof import'"**
- **Cause**: Using old Apollo Client import pattern
- **Solution**: Use generated hooks instead of raw Apollo Client hooks

#### Runtime Issues

**Error: "Network error: Failed to fetch"**
- **Cause**: GraphQL endpoint not accessible
- **Solution**: Check `NEXT_PUBLIC_GRAPHQL_URL` environment variable

**Error: "Apollo Client not initialized"**
- **Cause**: Missing Apollo Provider in component tree
- **Solution**: Wrap your app with `ApolloProvider`

#### Performance Issues

**Slow codegen execution**
- **Cause**: Large schema or many operations
- **Solution**: Use `near-operation-file` preset and exclude unnecessary files

**Large bundle size**
- **Cause**: Importing entire GraphQL operations
- **Solution**: Use tree-shaking and import only needed operations

---

## Scalability & Maintainability Benefits

1. **Tree Shaking:** Import only what you need  
2. **Smaller Bundles:** Unused operations are excluded  
3. **Easier Maintenance:** Isolation per domain  
4. **Team Collaboration:** Developers work independently  
5. **Reusability:** Shared fragments reduce redundancy  
6. **Type Safety:** Generated types ensure correctness  
7. **Monitoring:** Named operations simplify debugging and telemetry  

---

## Advanced Enterprise Guidelines

### 1. Query Complexity Control
Enforce **maximum depth and cost** to prevent abuse and ensure performance.  

### 2. Versioning and Deprecation
Use `@deprecated` for fields scheduled for removal.  
Document schema evolution steps for clients.

### 3. Caching Strategy
Define caching policies (`fetchPolicy`, TTL) per operation.  
Prefer normalized cache for frequently reused entities.

### 4. Authorization and Access Control
Apply authorization at the **field or operation level** via directives (`@auth(role: "ADMIN")`) or middleware.

### 5. Testing and Validation
Include integration tests for GraphQL operations using mocks and snapshots.  

### 6. Monitoring and Observability
Track usage metrics, latency, and failure rates per operation.  

---

## Quick Reference

### Essential Commands

```bash
# Install dependencies
pnpm add @apollo/client graphql
pnpm add -D @graphql-codegen/cli @graphql-codegen/typescript @graphql-codegen/typescript-operations @graphql-codegen/typescript-react-apollo

# Generate types and hooks
pnpm codegen

# Watch mode for development
pnpm codegen --watch

# Validate GraphQL schema
pnpm codegen --check
```

### File Patterns

| Pattern | Purpose | Example |
|---------|---------|---------|
| `operations/{domain}/queries.ts` | Domain queries | `GET_ITEMS`, `GET_ITEM` |
| `operations/{domain}/mutations.ts` | Domain mutations | `CREATE_ITEM`, `UPDATE_ITEM` |
| `operations/{domain}/index.ts` | Domain exports | `export * from './queries'` |
| `fragments/common.ts` | Shared fragments | `ITEM_FIELDS`, `USER_FIELDS` |
| `types/__generated__/graphql.ts` | Generated types | Auto-generated from schema |

### Environment Variables

```bash
# .env.local
NEXT_PUBLIC_GRAPHQL_URL=http://localhost:4000/graphql
```

### Package.json Scripts

```json
{
  "scripts": {
    "codegen": "graphql-codegen --config codegen.ts",
    "codegen:watch": "graphql-codegen --config codegen.ts --watch",
    "codegen:check": "graphql-codegen --config codegen.ts --check"
  }
}
```

---

## Additional References

| Topic | Source |
|-------|--------|
| Apollo Client React Operations | [Apollo Docs](https://www.apollographql.com/docs/react/) |
| GraphQL Schema and Fragments | [GraphQL.org](https://graphql.org/learn/queries/#fragments) |
| GraphQL Code Generator | [The Guild](https://the-guild.dev/graphql/codegen) |
| Naming Conventions & API Design | [Lee Byron – GraphQL API Design Best Practices](https://lessw.medium.com/graphql-api-design-best-practices-a-summary-from-4-years-of-graphql-by-lee-byron-9fb8a82fa89e) |
| Query Complexity & Limits | [Apollo Server – Performance Docs](https://www.apollographql.com/docs/apollo-server/performance/) |
| GraphQL Modules for Large-Scale Projects | [The Guild Blog](https://the-guild.dev/graphql/hive/blog/graphql-modules) |
| Structuring Operation | [Apollo Client Docs – Structuring Operations](https://www.apollographql.com/docs/react/data/operation-best-practices/) |
| GraphQL Modules for Large-Scale Apps | [The Guild – GraphQL Modules for Large-Scale Apps](https://the-guild.dev/graphql/hive/blog/graphql-modules)
| Fragments | [GraphQL.org – Fragments](https://graphql.org/learn/queries/#fragments) |
| Query Insights | [Apollo Studio – Query Insights](https://www.apollographql.com/docs/studio/metrics/) | 
