---
title: Project Setup and Renaming Guide
type: guide
category: setup
status: current
version: 1.0.0
tags: [setup, configuration, initialization, cli, project-setup]
related:
  - README.md
  - QUICK_REFERENCE.md
  - ARCHITECTURE_OVERVIEW.md
---

# Project Setup and Renaming Guide

This guide explains how to customize the project name from "neotool" to your own project name after cloning or integrating the starter template.

## Overview

The project renaming system allows you to customize all project identifiers across the entire codebase, including:

- Package names (npm, Gradle)
- Java/Kotlin package namespaces
- Database names and users
- Service names
- Docker container and image names
- GitHub repository references
- Display names in documentation
- Next.js route groups
- Logo file names

## Quick Start

1. **Check system requirements:**
   ```bash
   ./neotool --version
   ```
   This verifies that Node.js, Docker, and JVM are installed and shows their versions.

2. **Edit `project.config.json`** with your project details

3. **Initialize your project** (recommended):
   ```bash
   ./neotool init
   ```
   This command will:
   - Rename all project references from "neotool" to your project name
   - Optionally clean up example code (with interactive prompts)

   Or run commands individually:
   ```bash
   # Rename project
   ./neotool rename-project
   
   # Clean up examples (optional)
   ./neotool clean-examples --dry-run  # Preview changes first
   ./neotool clean-examples             # Apply changes
   ```

4. **Review and commit the changes:**
   ```bash
   git diff
   git add .
   git commit -m "Rename project from neotool to <your-project-name>"
   ```

## Neotool CLI

The project includes a command-line interface (CLI) tool that simplifies common project setup and maintenance tasks. The CLI is located at `./neotool` and provides the following commands:

### Available Commands

#### `neotool --version` or `neotool -v`
Checks system requirements and displays installed versions:
- Node.js version
- Docker version and daemon status
- JVM (Java) version and JAVA_HOME

**Example:**
```bash
./neotool --version
```

#### `neotool rename-project`
Renames all project references from "neotool" to your project name based on `project.config.json`.

**Example:**
```bash
./neotool rename-project
```

#### `neotool clean-examples [--dry-run]`
Removes customer/product example code from the codebase, keeping only the boilerplate infrastructure.

**Options:**
- `--dry-run`: Preview changes without modifying files

**Example:**
```bash
# Preview changes first
./neotool clean-examples --dry-run

# Apply changes
./neotool clean-examples
```

#### `neotool init`
Initializes the project by running `rename-project` followed by `clean-examples` with interactive prompts.

**Example:**
```bash
./neotool init
```

#### `neotool help`
Shows help text with available commands and options.

**Example:**
```bash
./neotool help
```

#### `neotool graphql <subcommand>`
Manages GraphQL schema synchronization and supergraph generation. This command provides a unified interface for working with GraphQL schemas across the monorepo.

**Subcommands:**

- **`sync`**: Interactive sync from service modules to contracts
  - Prompts to select schema source and target subgraph
  - Creates backups before overwriting
  - Example: `./neotool graphql sync`

- **`validate`**: Validate schema consistency between services and contracts
  - Checks if service schemas match contract schemas
  - Reports mismatches and missing contracts
  - Example: `./neotool graphql validate`

- **`generate`**: Generate supergraph schema from subgraph schemas
  - Uses Apollo Rover to compose federated schemas
  - Supports local rover or Docker-based execution
  - Options:
    - `--docker`: Use Docker for rover (useful for CI/CD)
  - Example: `./neotool graphql generate`
  - Example: `./neotool graphql generate --docker`

- **`all`**: Run sync, validate, and generate in sequence
  - Executes all three operations in order
  - Useful for complete schema workflow
  - Options:
    - `--docker`: Use Docker for rover
  - Example: `./neotool graphql all`

**Environment Variables:**
- `CI=true`: Automatically use Docker for rover (CI environment)
- `USE_DOCKER_ROVER=true`: Force Docker usage for rover

**Examples:**
```bash
# Sync a schema interactively
./neotool graphql sync

# Validate all schemas
./neotool graphql validate

# Generate supergraph (local rover)
./neotool graphql generate

# Generate supergraph (Docker rover)
./neotool graphql generate --docker

# Complete workflow
./neotool graphql all

# Show GraphQL command help
./neotool graphql --help
```

**Underlying Scripts:**
- `sync` → `scripts/cli/commands/graphql/sync-schemas.sh sync`
- `validate` → `scripts/cli/commands/graphql/sync-schemas.sh validate`
- `generate` → `scripts/cli/commands/graphql/generate-schema.sh`

### CLI Architecture

The CLI is organized as a modular system:
- **Main router**: `scripts/cli/cli` - Routes commands to individual scripts
- **Command scripts**: `scripts/cli/commands/` - Each command is a separate script
  - `version.sh` - System requirements check
  - `setup.sh` - Project setup (rename from neotool)
  - `clean.sh` - Example code cleanup
  - `init.sh` - Project initialization
  - `graphql.sh` - GraphQL schema management (with subcommands)
- **Shared utilities**: `scripts/cli/utils.sh` - Common logging and utility functions

All commands can be run independently or through the CLI router. The underlying scripts can also be executed directly if needed.
## Configuration File

The `project.config.json` file contains all the project naming configurations. Here's what each field means:

### Required Fields

| Field | Description | Example |
|-------|-------------|---------|
| `displayName` | Human-readable display name | `"My Project"` |
| `packageName` | npm/Gradle package name (kebab-case) | `"my-project"` |
| `packageNamespace` | Java/Kotlin package namespace | `"com.company.myproject"` |
| `databaseName` | Database name | `"myproject_db"` |
| `databaseUser` | Database user | `"myproject"` |
| `serviceName` | Backend service name | `"myproject-service"` |
| `webPackageName` | Web package name | `"myproject-web"` |
| `dockerImagePrefix` | Docker image prefix | `"myproject"` |
| `routeGroup` | Next.js route group folder name | `"myproject"` |
| `githubOrg` | GitHub organization/username | `"mycompany"` |
| `githubRepo` | GitHub repository name | `"my-project"` |

### Optional Fields

| Field | Description | Default |
|-------|-------------|---------|
| `apiDomain` | API domain | `api.<packageName>` |
| `logoName` | Logo file prefix | `<packageName>-logo` |

### Example Configuration

```json
{
  "displayName": "My Awesome Project",
  "packageName": "my-awesome-project",
  "packageNamespace": "com.mycompany.awesome",
  "databaseName": "awesome_db",
  "databaseUser": "awesome",
  "serviceName": "awesome-service",
  "webPackageName": "awesome-web",
  "dockerImagePrefix": "awesome",
  "routeGroup": "awesome",
  "githubOrg": "mycompany",
  "githubRepo": "my-awesome-project",
  "apiDomain": "api.myawesomeproject.com",
  "logoName": "awesome-logo"
}
```

## What Gets Renamed

The rename script performs comprehensive replacements across the entire codebase:

### Package Files
- `package.json` files (web, mobile)
- `build.gradle.kts` files
- `settings.gradle.kts`

### Source Code
- Kotlin package declarations (`package io.github.salomax.neotool.*`)
- Kotlin imports
- TypeScript/JavaScript imports and references
- Type definitions

### Configuration Files
- `application.yml` (database names, service names)
- `docker-compose.local.yml` (container names, environment variables)
- Kubernetes deployment files
- Grafana dashboard configurations
- Prometheus configurations

### Documentation
- README files
- Markdown documentation files
- Code comments and descriptions

### Files and Folders
- Next.js route group folder: `(neotool)` → `(<routeGroup>)`
- Logo files: `neotool-logo-*.svg` → `<logoName>-*.svg`
- Any files or folders containing "neotool" in their names

### URLs and References
- GitHub repository URLs
- API domain references
- Docker image references

## Detailed Steps

### Step 1: Prepare Your Configuration

Before running the script, decide on your naming conventions:

1. **Package Name**: Use kebab-case (lowercase with hyphens)
   - ✅ Good: `my-project`, `awesome-app`
   - ❌ Bad: `MyProject`, `awesome_app`

2. **Package Namespace**: Use reverse domain notation
   - ✅ Good: `com.company.project`, `io.github.username.app`
   - ❌ Bad: `com.company-project`, `my.project`

3. **Route Group**: Should match package name format (kebab-case)
   - This becomes the Next.js route group folder name

### Step 2: Create Configuration File

Copy the example file and edit it:

```bash
cp project.config.example.json project.config.json
```

Edit `project.config.json` with your project details. Make sure all required fields are filled.

### Step 3: Run the Rename Script

**Prerequisites:** The rename script requires:
- `bash` (available on macOS, Linux, and Windows via Git Bash or WSL)
- `jq` (JSON processor) - Install with: `brew install jq` (macOS) or `apt-get install jq` (Linux)

**Option A: Using the CLI (Recommended)**
```bash
./neotool rename-project
```

**Option B: Run script directly**
```bash
./scripts/rename-project.sh
```

The script will:
1. Validate your configuration
2. Scan all files in the project
3. Replace all occurrences of "neotool" references
4. Rename files and folders as needed
5. Provide a summary of changes

### Step 4: Review Changes

After the script completes, review the changes:

```bash
# See all changes
git diff

# Review specific files
git diff web/package.json
git diff service/kotlin/build.gradle.kts
```

### Step 5: Handle Manual Updates

Some items may require manual updates:

1. **Logo Files**: If you have custom logos, replace the files in `design/assets/logos/`
2. **Workspace File**: Rename `neotool.code-workspace` if desired
3. **Git Remote**: Update your git remote URL if needed:
   ```bash
   git remote set-url origin https://github.com/<your-org>/<your-repo>.git
   ```

### Step 6: Verify and Test

1. **Build the project:**
   ```bash
   # Web
   cd web
   npm install
   npm run build

   # Backend
   cd ../service/kotlin
   ./gradlew build
   ```

2. **Test the application:**
   ```bash
   # Start services
   docker-compose -f infra/docker/docker-compose.local.yml up -d

   # Run tests
   cd web && npm test
   cd ../service/kotlin && ./gradlew test
   ```

3. **Check for any remaining references:**
   ```bash
   # Search for any remaining "neotool" references (case-insensitive)
   grep -r -i "neotool" --exclude-dir=node_modules --exclude-dir=build --exclude-dir=.git .
   ```

### Step 7: Clean Up Example Code (Optional)

After renaming your project, you may want to remove the example customer and product code that comes with the starter template. This script removes all customer/product example code while keeping the boilerplate infrastructure intact.

**What gets removed:**
- Backend entities (`CustomerEntity`, `ProductEntity`)
- Backend domain models (`Customer`, `Product`)
- Backend DTOs (`CustomerDto`, `ProductDto`)
- GraphQL resolvers (`CustomerResolver`, `ProductResolver`)
- Database migration for customers/products
- Frontend customer/product pages
- Frontend hooks and GraphQL operations related to customers/products
- Example code from GraphQL schemas and wiring files

**What stays:**
- All infrastructure code
- Boilerplate setup
- GraphQL schema structure (without customer/product types)
- Example wiring patterns (cleaned of customer/product references)

**To clean up examples:**

1. **Run the clean examples command:**
   ```bash
   # Preview changes first (recommended)
   ./neotool clean-examples --dry-run
   
   # Apply changes
   ./neotool clean-examples
   ```
   
   Or run the script directly:
   ```bash
   ./scripts/clean-examples.sh --dry-run  # Preview
   ./scripts/clean-examples.sh             # Apply
   ```

2. **Review the changes:**
   ```bash
   # See all changes
   git diff
   
   # Review specific areas
   git diff web/src/app
   git diff service/kotlin/app/src/main/kotlin
   ```

3. **Commit the changes:**
   ```bash
   git add .
   git commit -m "Remove customer/product examples"
   ```

**Note:** This step is optional. You can keep the example code if you want to use it as a reference for building your own features. If you're unsure, you can always remove it later.

## Common Issues and Solutions

### Issue: Script fails with "command not found: jq" or similar

**Solution**: Install `jq` (JSON processor) which is required by the rename script:
```bash
# macOS
brew install jq

# Linux (Debian/Ubuntu)
sudo apt-get install jq

# Linux (RedHat/CentOS)
sudo yum install jq

# Verify installation
jq --version
```

### Issue: Script fails with "Configuration file not found"

**Solution**: Make sure you've created `project.config.json` in the project root:
```bash
cp project.config.example.json project.config.json
```

### Issue: Package name validation fails

**Solution**: Ensure package names follow kebab-case format:
- Use lowercase letters and hyphens only
- No spaces, underscores, or special characters
- Example: `my-project` ✅, `MyProject` ❌

### Issue: Some files still contain "neotool" after running

**Solution**: The script skips certain files:
- Binary files (images, fonts, etc.)
- Build artifacts (`node_modules`, `build`, `.gradle`)
- Git history (`.git`)

If you find references in source files, you can:
1. Manually update them
2. Check if the file was excluded (review the script output)
3. Re-run the script after fixing the configuration

### Issue: Git shows many file renames

**Solution**: This is expected behavior. Git tracks file renames, which preserves history. You can verify this with:
```bash
git log --follow -- <renamed-file-path>
```

### Issue: Build fails after renaming

**Solution**: 
1. Clean build artifacts:
   ```bash
   # Web
   cd web && rm -rf .next node_modules && npm install

   # Backend
   cd service/kotlin && ./gradlew clean
   ```

2. Rebuild the project
3. Check for any hardcoded references that weren't caught

## Best Practices

1. **Run the rename script early**: Do this before making significant changes to the codebase
2. **Commit before renaming**: Create a commit before running the rename script for easier rollback
3. **Test thoroughly**: After renaming, run all tests and verify the application works
4. **Update documentation**: Review and update any project-specific documentation
5. **Inform team members**: If working in a team, coordinate the rename to avoid conflicts

## Reverting Changes

If you need to revert the rename:

```bash
# Discard all changes
git reset --hard HEAD

# Or restore from a previous commit
git checkout <commit-before-rename> -- .
```

## Advanced Customization

### Custom Logo Files

After renaming, update logo files in `design/assets/logos/`:
- Replace `neotool-logo-blue.svg` with your logo
- Replace `neotool-logo-white.svg` with your white logo variant
- Update references in README and other documentation

### Environment Variables

The rename script updates configuration files, but you can also use environment variables for runtime configuration. See `web/src/shared/config/repo.constants.ts` for available environment variables.

### Partial Renaming

If you only want to rename specific aspects (e.g., only package names but not display names), you can:
1. Run the full rename script
2. Manually revert specific changes
3. Or modify the script to skip certain replacements

## Support

If you encounter issues:
1. Check this documentation
2. Review the script output for error messages
3. Verify your `project.config.json` format
4. Check for any validation errors in the configuration

## Next Steps

After renaming:
1. Update the README with your project information
2. Set up CI/CD with your new project names
3. Update any external documentation
4. Configure your deployment pipelines
5. Update your team's documentation

