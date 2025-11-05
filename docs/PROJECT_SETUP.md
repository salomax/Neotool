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

1. Edit `project.config.json` with your project details

2. Run the rename script:
   ```bash
   ./scripts/rename-project.sh
   ```

3. Review and commit the changes:
   ```bash
   git diff
   git add .
   git commit -m "Rename project from neotool to <your-project-name>"
   ```

4. (Optional) Clean up example code:
   ```bash
   node scripts/clean-examples.mjs
   ```
   
   This removes customer/product example code, keeping only the boilerplate infrastructure. Review changes and commit:
   ```bash
   git diff
   git add .
   git commit -m "Remove customer/product examples"
   ```
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

Execute the rename script from the project root:

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

1. **Run the clean examples script:**
   ```bash
   node scripts/clean-examples.mjs
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

