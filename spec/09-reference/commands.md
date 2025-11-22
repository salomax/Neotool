---
title: Commands Reference
type: reference
category: commands
status: current
version: 2.0.0
tags: [reference, commands, cli]
ai_optimized: true
search_keywords: [commands, cli, reference]
---

# Commands Reference

> **Purpose**: Quick reference for common CLI commands.

## NeoTool CLI

### Project Setup
```bash
./neotool --version        # Check system requirements
./neotool init             # Initialize project
./neotool setup            # Setup project (rename from neotool)
./neotool clean [--dry-run] # Clean up example code
```

### GraphQL Schema Management
```bash
./neotool graphql sync      # Interactive schema sync
./neotool graphql validate  # Validate schema consistency
./neotool graphql generate  # Generate supergraph schema
./neotool graphql all       # Run complete workflow
```

## Backend Commands

### Gradle
```bash
./gradlew build            # Build project
./gradlew test             # Run unit tests
./gradlew testIntegration  # Run integration tests
./gradlew clean            # Clean build artifacts
```

## Frontend Commands

### npm/pnpm
```bash
npm install                # Install dependencies
npm run dev                # Start development server
npm run build              # Build for production
npm test                   # Run tests
npm run codegen            # Generate TypeScript types
```

## Docker Commands

```bash
docker-compose -f infra/docker/docker-compose.local.yml up -d
docker-compose -f infra/docker/docker-compose.local.yml down
```

