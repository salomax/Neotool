# GraphQL Contracts Management

This directory contains the GraphQL schema contracts and federation configuration for NeoTool. It implements enterprise-grade schema management with a single source of truth approach.

📚 **Full Documentation**: See [docs/contracts/graphql-federation.md](../docs/contracts/graphql-federation.md) for complete setup and architecture details.

## 🏗️ Architecture

```
contracts/graphql/
├── subgraphs/           # Individual service schemas (source of truth)
│   └── module/            # Module schema
│       └── schema.graphqls
├── supergraph/         # Federation configuration
│   ├── supergraph.yaml
│   ├── supergraph.graphql (generated)
│   └── scripts/
│       └── generate-schema.sh
├── shared/             # Shared types and directives
├── package.json        # Schema management scripts
└── sync-schemas.sh     # Schema synchronization script
```

## 🎯 Development Workflow

**Service modules are the source of truth for GraphQL schemas.** Developers edit schemas in their service modules, then sync them to contracts for federation.

### Schema Synchronization

The `sync-schemas.sh` script provides an interactive workflow:

```bash
# Interactive sync from service modules to contracts
./sync-schemas.sh sync

# Validate schema consistency
./sync-schemas.sh validate

# Generate supergraph schema
./sync-schemas.sh generate

# Run all operations
./sync-schemas.sh all
```

## 🚀 Development Workflow

### Local Development

1. **Edit schemas** in your service module (e.g., `service/kotlin/app/src/main/resources/graphql/schema.graphqls`)
2. **Sync to contracts**: `./sync-schemas.sh sync`
3. **Select schema source** from the discovered list
4. **Select target subgraph** (existing or create new)
5. **Confirm sync** and schema is copied to contracts
6. **Generate supergraph**: `./sync-schemas.sh generate`
7. **Start services** with updated schemas

### Quick Setup

```bash
# One-time setup
cd contracts/graphql
./sync-schemas.sh all
```

## 🐳 Docker Integration

The solution supports both local and Docker-based rover execution:

### Local Rover (Development)
```bash
# Install rover locally
curl -sSL https://rover.apollo.dev/nix/latest | sh

# Generate schema
./sync-schemas.sh generate
```

### Docker Rover (CI/CD)
```bash
# Use Docker (no local installation needed)
CI=true ./sync-schemas.sh generate

# Or explicitly
USE_DOCKER_ROVER=true ./sync-schemas.sh generate
```

## 🔄 CI/CD Integration

### GitHub Actions

The `.github/workflows/graphql-schema.yml` workflow automatically:

- ✅ Validates schema consistency
- ✅ Generates supergraph schema
- ✅ Checks for uncommitted changes
- ✅ Uploads artifacts

### Manual CI Commands

```bash
# Validate schemas
cd contracts/graphql
CI=true ./sync-schemas.sh validate

# Generate supergraph
CI=true ./sync-schemas.sh generate

# Run all operations
CI=true ./sync-schemas.sh all
```

## 📋 Available Commands

### sync-schemas.sh Commands
```bash
./sync-schemas.sh sync      # Synchronize schemas from contracts to services
./sync-schemas.sh validate  # Validate schema consistency
./sync-schemas.sh generate  # Generate supergraph schema
./sync-schemas.sh all       # Run all operations
```

### Schema Discovery
The script automatically discovers schema sources in the service directory:
- **Pattern**: `{language}/{module}/src/main/resources/graphql/schema.graphqls`
- **Examples**: `kotlin/app`, `kotlin/security`, `python/module_x`, etc.
- **Skips**: `bin/` and `build/` directories
- **Interactive**: Select source and target subgraph

## 🏢 Enterprise Best Practices

This implementation follows patterns used by major tech companies:

### 1. **Service-First Development**
- Schemas developed in service modules (natural development flow)
- Interactive sync to contracts for federation
- No manual duplication or maintenance overhead

### 2. **Environment Consistency**
- Docker-based rover for CI/CD environments
- Local rover for development (optional)
- Consistent behavior across all environments

### 3. **Automated Validation**
- Schema consistency checks between services and contracts
- Supergraph generation validation
- CI/CD pipeline integration

### 4. **Developer Experience**
- Interactive selection of schema sources and targets
- Clear error messages and guidance
- Backup creation for safety

## 🔧 Adding New Services

1. **Create schema** in your service module (e.g., `service/kotlin/security/src/main/resources/graphql/schema.graphqls`)
2. **Sync to contracts**: `./sync-schemas.sh sync` and select your new schema
3. **Update supergraph.yaml**:
   ```yaml
   subgraphs:
     security:
       routing_url: http://security:8080/graphql
       schema:
         file: ../subgraphs/security/schema.graphqls
   ```
4. **Generate supergraph**: `./sync-schemas.sh generate`

## 🚨 Troubleshooting

### Schema Not Synchronized
```bash
# Check status
cd contracts/graphql
./sync-schemas.sh validate

# Fix automatically
./sync-schemas.sh sync
```

### Rover Not Found
```bash
# Use Docker instead
cd contracts/graphql
CI=true ./sync-schemas.sh generate

# Or install locally
curl -sSL https://rover.apollo.dev/nix/latest | sh
```

### Supergraph Generation Fails
```bash
# Check supergraph.yaml configuration
cd contracts/graphql
cat supergraph/supergraph.yaml

# Validate individual schemas
CI=true ./sync-schemas.sh validate
```

## 📚 References

- [Apollo Federation](https://www.apollographql.com/docs/federation/)
- [Rover CLI](https://www.apollographql.com/docs/rover/)
- [GraphQL Best Practices](https://graphql.org/learn/best-practices/)
