# API Contracts Documentation

This section covers API contracts, GraphQL federation, and service-to-service communication patterns.

## Contents

### GraphQL Federation
- **[graphql-standards.md](graphql-standards.md)** — GraphQL API standards and naming conventions
- **[graphql-query-pattern.md](graphql-query-pattern.md)** — Query resolver patterns

### Future Documentation
The following documents are planned and will be created:
- **graphql-federation.md** — Federation architecture and patterns
- **graphql-router-config.md** — Apollo Router configuration
- **subgraph-patterns.md** — Subgraph design best practices
- **schema-evolution.md** — Versioning and backward compatibility
- **rest-api-standards.md** — REST API guidelines (when needed)
- **api-versioning.md** — API version management strategies

## GraphQL Schema Location

The actual GraphQL schemas live in the codebase:
```
contracts/graphql/
├── supergraph/
│   ├── supergraph.graphql      # Unified federated schema
│   ├── supergraph.yaml         # Router configuration
│   └── supergraph.local.yaml   # Local dev overrides
└── subgraphs/
    ├── app/schema.graphqls           # App service schema
    ├── security/schema.graphqls      # Security service schema
    └── assistant/schema.graphqls     # Assistant service schema
```

## Quick Reference

### Common Tasks
- **Add new query**: Follow [GraphQL Standards](graphql-standards.md)
- **Create mutation**: See [GraphQL Standards](graphql-standards.md)
- **Design new subgraph**: Review schema standards
- **Update router config**: Check router YAML files in `/contracts/graphql/supergraph/`

## CLI Tools

Use the NeoTool CLI for schema operations:
```bash
./neotool graphql sync       # Interactive schema sync
./neotool graphql validate   # Validate schema consistency
./neotool graphql generate   # Build supergraph schema
```

## Related Documentation

- **Backend resolvers**: See [05-backend/patterns/graphql-resolver-pattern.md](../05-backend/patterns/graphql-resolver-pattern.md)
- **Frontend queries**: See [07-frontend/patterns/graphql-query-pattern.md](../07-frontend/patterns/graphql-query-pattern.md)
- **Frontend mutations**: See [07-frontend/patterns/graphql-mutation-pattern.md](../07-frontend/patterns/graphql-mutation-pattern.md)
- **Reference schema**: See [93-reference/graphql-schema.md](../93-reference/graphql-schema.md)
