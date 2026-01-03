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

### Project Management
```bash
./neotool --version        # Check system requirements
```

### GraphQL Schema Management
```bash
./neotool graphql sync      # Interactive schema sync
./neotool graphql validate  # Validate schema consistency
./neotool graphql generate  # Generate supergraph schema
./neotool graphql all       # Run complete workflow
```

### Validation
```bash
./neotool validate                    # Run all validations (web + services)
./neotool validate --web              # Run only web (frontend) validations
./neotool validate --service          # Run all service (backend) validations
./neotool validate --service security # Run only security service validations
./neotool validate --service app      # Run only app service validations
./neotool validate --skip-coverage    # Run validations without coverage checks
```

### Kafka Management
```bash
./neotool kafka --topic                              # List all topics (excluding internals)
./neotool kafka --topic <name>                       # Describe a specific topic
./neotool kafka --consumer-group                     # List all consumer groups
./neotool kafka --consumer-group <name>              # Describe a specific consumer group
./neotool kafka --reset-offsets                      # Reset consumer group offsets (requires --group, --topic, and strategy)
./neotool kafka --topic --bootstrap-server <server> # Use custom bootstrap server
./neotool kafka --topic --docker                     # Force Docker execution
```

### Upstream Merge Strategy Management
```bash
./neotool upstream add <file|pattern>                 # Add file/pattern to use "ours" merge strategy
./neotool upstream list                              # List all configured files/patterns
./neotool upstream remove <file|pattern>              # Remove file/pattern from merge strategy
```

**Kafka Options**:
- `--bootstrap-server <server>`: Override default bootstrap server (default: `localhost:9092`)
- `--docker`: Force using Docker exec (useful when Kafka tools aren't installed locally)
- Environment variable `KAFKA_BOOTSTRAP_SERVER`: Set default bootstrap server

**Reset Offsets Options**:
- `--group <name>`: Consumer group name (required)
- `--topic <name>`: Topic name (required)
- `--to-earliest`: Reset to beginning of topic (reprocess all messages)
- `--to-latest`: Reset to end of topic (skip all existing messages)
- `--to-offset <offset>`: Reset to specific offset (per partition)
- `--to-datetime <time>`: Reset to messages after datetime (format: YYYY-MM-DDTHH:mm:ss)
- `--execute`: Actually perform the reset (default is dry-run)
- `--force`: Skip confirmation prompt (use with caution)

**Examples**:
```bash
# List all topics (excluding internal topics like __consumer_offsets)
./neotool kafka --topic

# Describe a specific topic
./neotool kafka --topic swapi.people.v1

# List all consumer groups
./neotool kafka --consumer-group

# Describe a specific consumer group
./neotool kafka --consumer-group swapi-people-consumer-group

# Preview offset reset (dry-run)
./neotool kafka --reset-offsets --group swapi-people-consumer-group --topic swapi.people.v1 --to-earliest

# Reset offsets to beginning (reprocess all messages)
./neotool kafka --reset-offsets --group swapi-people-consumer-group --topic swapi.people.v1 --to-earliest --execute

# Reset offsets to latest (skip existing messages)
./neotool kafka --reset-offsets --group swapi-people-consumer-group --topic swapi.people.v1 --to-latest --execute --force

# Reset offsets to specific datetime
./neotool kafka --reset-offsets --group swapi-people-consumer-group --topic swapi.people.v1 --to-datetime 2024-01-01T00:00:00 --execute

# Use custom bootstrap server
./neotool kafka --topic --bootstrap-server kafka.example.com:9092

# Force Docker execution
./neotool kafka --topic --docker
```

**Upstream Merge Strategy Examples**:
```bash
# Add specific files
./neotool upstream add web/public/favicon.ico
./neotool upstream add project.config.json
./neotool upstream add web/src/config/branding.ts

# Add directory patterns
./neotool upstream add "web/src/app/product/**"
./neotool upstream add "design/assets/logos/**"

# List all configured files
./neotool upstream list

# Remove a file from auto-merge strategy
./neotool upstream remove web/public/favicon.ico
```

**Note**: This command manages `.gitattributes` entries to automatically resolve merge conflicts by always keeping your version ("ours") of specified files when merging from upstream. This is useful for product-specific customizations like branding, config files, and product-specific code. The `.gitattributes` file is already configured with default entries (favicon and logos) when you clone NeoTool.

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
pnpm test                  # Run all tests
pnpm test <pattern>        # Run specific test file(s) by pattern
pnpm test:watch            # Run tests in watch mode
pnpm test:coverage         # Run tests with coverage report
pnpm run codegen           # Run GraphQL codegen + fragment fixer
```

**Running Specific Test Files**:
```bash
# Run tests matching a pattern (without -- separator)
pnpm test usePageTitle                    # Matches any file containing "usePageTitle"
pnpm test UserDrawer                      # Matches any test file with "UserDrawer" in path
pnpm test "**/usePageTitle.test.tsx"      # Full path pattern with glob

# Direct Vitest command (alternative)
pnpm vitest run usePageTitle
```

**Note**: When using `pnpm test`, pass the file pattern directly without the `--` separator. Using `pnpm run test -- <pattern>` does not work correctly with Vitest's file filtering.

> `npm run codegen` (or `pnpm run codegen`) executes `graphql-codegen --config codegen.ts` and then `node scripts/fix-generated-types.mjs`, which regenerates shared fragments from `common.graphql` and normalizes Apollo imports in every `.generated.ts` file.

## Docker Commands

```bash
docker-compose -f infra/docker/docker-compose.local.yml up -d
docker-compose -f infra/docker/docker-compose.local.yml down
```
