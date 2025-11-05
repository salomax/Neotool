#!/usr/bin/env bash

set -euo pipefail

# Setup Command
# 
# Renames all project references from the current project name to your new project name
# based on project.config.json. Automatically detects the current project name.

# Get script directory and project root
COMMAND_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLI_DIR="$(cd "$COMMAND_DIR/.." && pwd)"
SCRIPTS_DIR="$(cd "$CLI_DIR/.." && pwd)"
PROJECT_ROOT="$(cd "$SCRIPTS_DIR/.." && pwd)"

# Source shared utilities
# shellcheck source=../utils.sh
source "$CLI_DIR/utils.sh"

# Find project root by searching for project.config.json or package.json
find_project_root() {
    # Try starting from current working directory first
    local dir="$PWD"
    while [[ "$dir" != "/" ]]; do
        if [[ -f "$dir/project.config.json" ]] || [[ -f "$dir/package.json" ]]; then
            echo "$dir"
            return 0
        fi
        dir=$(dirname "$dir")
    done
    
    # If not found, try starting from PROJECT_ROOT
    dir="$PROJECT_ROOT"
    while [[ "$dir" != "/" ]]; do
        if [[ -f "$dir/project.config.json" ]] || [[ -f "$dir/package.json" ]]; then
            echo "$dir"
            return 0
        fi
        dir=$(dirname "$dir")
    done
    
    return 1
}

PROJECT_ROOT=$(find_project_root)
if [[ -z "$PROJECT_ROOT" ]] || [[ ! -d "$PROJECT_ROOT" ]]; then
    log_error "Error: Could not find project root. Please ensure project.config.json or package.json exists in the project directory."
    exit 1
fi

# Check if jq is installed
check_jq() {
    if ! command -v jq &> /dev/null; then
        log_error "Error: jq is required but not installed."
        log "Install it with: brew install jq (macOS) or apt-get install jq (Linux)" "$YELLOW"
        exit 1
    fi
}

# Validate configuration file
validate_config() {
    local config_file="$PROJECT_ROOT/project.config.json"
    
    if [[ ! -f "$config_file" ]]; then
        log_error "Error: project.config.json not found at $config_file"
        exit 1
    fi
    
    # Check required fields (using gitRepo instead of githubOrg/githubRepo)
    local required_fields=("displayName" "packageName" "packageNamespace" "databaseName" "databaseUser" "serviceName" "webPackageName" "dockerImagePrefix" "routeGroup" "gitRepo")
    
    for field in "${required_fields[@]}"; do
        if ! jq -e ".$field" "$config_file" > /dev/null 2>&1; then
            log_error "Error: Required field '$field' is missing in project.config.json"
            exit 1
        fi
    done
    
    # Validate gitRepo format (should be org/repo)
    local git_repo=$(jq -r '.gitRepo' "$config_file")
    if [[ "$git_repo" != *"/"* ]]; then
        log_error "Error: gitRepo must be in format 'org/repo' (e.g., 'salomax/neotool')"
        exit 1
    fi
    
    log "✓ Configuration file validated" "$GREEN"
}

# Detect current project name from codebase
detect_current_config() {
    log "Detecting current project configuration..." "$BLUE"
    
    # Try to detect package name from web/package.json
    if [[ -f "$PROJECT_ROOT/web/package.json" ]]; then
        OLD_PACKAGE_NAME=$(jq -r '.name // empty' "$PROJECT_ROOT/web/package.json" | sed 's/-web$//')
        if [[ -n "$OLD_PACKAGE_NAME" ]] && [[ "$OLD_PACKAGE_NAME" != "null" ]]; then
            log "  ✓ Detected package name from web/package.json: $OLD_PACKAGE_NAME" "$GREEN"
        fi
    fi
    
    # Try to detect from service build.gradle.kts if web package.json didn't work
    if [[ -z "$OLD_PACKAGE_NAME" ]] || [[ "$OLD_PACKAGE_NAME" == "null" ]]; then
        if [[ -f "$PROJECT_ROOT/service/kotlin/build.gradle.kts" ]]; then
            OLD_PACKAGE_NAME=$(grep -E '^rootProject\.name\s*=' "$PROJECT_ROOT/service/kotlin/build.gradle.kts" | head -1 | sed 's/.*=\s*"\([^"]*\)".*/\1/' | sed 's/-service$//')
            if [[ -n "$OLD_PACKAGE_NAME" ]]; then
                log "  ✓ Detected package name from build.gradle.kts: $OLD_PACKAGE_NAME" "$GREEN"
            fi
        fi
    fi
    
    # Fallback: try to detect from route group folder name
    if [[ -z "$OLD_PACKAGE_NAME" ]] || [[ "$OLD_PACKAGE_NAME" == "null" ]]; then
        local route_group_dir=$(find "$PROJECT_ROOT/web/src/app" -maxdepth 1 -type d -name '(*)' 2>/dev/null | head -1)
        if [[ -n "$route_group_dir" ]]; then
            OLD_PACKAGE_NAME=$(basename "$route_group_dir" | sed 's/[()]//g')
            log "  ✓ Detected package name from route group: $OLD_PACKAGE_NAME" "$GREEN"
        fi
    fi
    
    # Final fallback: use "neotool" if nothing found
    if [[ -z "$OLD_PACKAGE_NAME" ]] || [[ "$OLD_PACKAGE_NAME" == "null" ]]; then
        OLD_PACKAGE_NAME="neotool"
        log "  ⚠ Using default package name: $OLD_PACKAGE_NAME" "$YELLOW"
    fi
    
    # Detect namespace from Kotlin source files
    OLD_NAMESPACE=""
    if [[ -d "$PROJECT_ROOT/service/kotlin" ]]; then
        local namespace_file=$(find "$PROJECT_ROOT/service/kotlin" -name "*.kt" -type f ! -path "*/build/*" ! -path "*/test/*" ! -path "*/bin/*" 2>/dev/null | head -1 || true)
        if [[ -n "$namespace_file" ]] && [[ -f "$namespace_file" ]]; then
            local package_line=$(grep -E '^package\s+' "$namespace_file" 2>/dev/null | head -1 || echo "")
            if [[ -n "$package_line" ]]; then
                # Extract package name and remove example subpackages (like .example, .domain, .dto, etc.)
                local full_package=$(echo "$package_line" | sed 's/^package\s*\([^;]*\).*/\1/' || echo "")
                if [[ -n "$full_package" ]]; then
                    # Remove .example subpackage and everything after it
                    OLD_NAMESPACE=$(echo "$full_package" | sed 's/\.example\..*$//' | sed 's/\.example$//' || echo "")
                    # If we removed .example, we should have the base namespace
                    # If not, try to detect by removing common subpackages
                    if [[ "$OLD_NAMESPACE" == "$full_package" ]]; then
                        # Didn't match .example pattern, try removing other common subpackages
                        OLD_NAMESPACE=$(echo "$full_package" | sed 's/\.domain\..*$//' | sed 's/\.dto\..*$//' | sed 's/\.entity\..*$//' | sed 's/\.repo\..*$//' | sed 's/\.service\..*$//' | sed 's/\.graphql\..*$//' || echo "")
                        # If still has unexpected subpackages, try to get base (first 3-4 parts)
                        if [[ -n "$OLD_NAMESPACE" ]]; then
                            local dot_count=$(echo "$OLD_NAMESPACE" | tr -cd '.' | wc -c | tr -d ' ' || echo "0")
                            if [[ "$dot_count" -gt 3 ]]; then
                                # Take first 4 parts (e.g., io.github.salomax.neotool from io.github.salomax.neotool.example.dto)
                                OLD_NAMESPACE=$(echo "$OLD_NAMESPACE" | cut -d. -f1-4 || echo "$OLD_NAMESPACE")
                            fi
                        fi
                    fi
                    if [[ -n "$OLD_NAMESPACE" ]]; then
                        log "  ✓ Detected namespace from Kotlin files: $OLD_NAMESPACE" "$GREEN"
                    fi
                fi
            fi
        fi
    fi
    
    # Fallback namespace
    if [[ -z "$OLD_NAMESPACE" ]]; then
        OLD_NAMESPACE="io.github.salomax.$OLD_PACKAGE_NAME"
        log "  ⚠ Using default namespace pattern: $OLD_NAMESPACE" "$YELLOW"
    fi
    
    # Detect display name (capitalize first letter of package name and convert hyphens to spaces)
    # Convert hyphens to spaces and capitalize each word
    OLD_DISPLAY_NAME=$(echo "$OLD_PACKAGE_NAME" | sed 's/-/ /g' | awk '{for(i=1;i<=NF;i++){sub(/./,toupper(substr($i,1,1)),$i)};print}')
    
    # Detect GitHub org/repo from git remote or config
    OLD_GITHUB_ORG=""
    OLD_GITHUB_REPO=""
    if [[ -d "$PROJECT_ROOT/.git" ]]; then
        local git_remote=$(git -C "$PROJECT_ROOT" remote get-url origin 2>/dev/null || echo "")
        if [[ -n "$git_remote" ]]; then
            if [[ "$git_remote" =~ github.com[:/]([^/]+)/([^/]+) ]]; then
                OLD_GITHUB_ORG="${BASH_REMATCH[1]}"
                OLD_GITHUB_REPO="${BASH_REMATCH[2]%.git}"
                log "  ✓ Detected GitHub: $OLD_GITHUB_ORG/$OLD_GITHUB_REPO" "$GREEN"
            fi
        fi
    fi
    
    # Fallback GitHub values
    if [[ -z "$OLD_GITHUB_ORG" ]]; then
        OLD_GITHUB_ORG="salomax"
    fi
    if [[ -z "$OLD_GITHUB_REPO" ]]; then
        OLD_GITHUB_REPO="$OLD_PACKAGE_NAME"
    fi
    
    log ""
}

# Load configuration
load_config() {
    local config_file="$PROJECT_ROOT/project.config.json"
    
    # Detect current values first
    detect_current_config
    
    # Load new values
    NEW_DISPLAY_NAME=$(jq -r '.displayName' "$config_file")
    NEW_PACKAGE_NAME=$(jq -r '.packageName' "$config_file")
    NEW_PACKAGE_NAMESPACE=$(jq -r '.packageNamespace' "$config_file")
    NEW_DATABASE_NAME=$(jq -r '.databaseName' "$config_file")
    NEW_DATABASE_USER=$(jq -r '.databaseUser' "$config_file")
    NEW_SERVICE_NAME=$(jq -r '.serviceName' "$config_file")
    NEW_WEB_PACKAGE_NAME=$(jq -r '.webPackageName' "$config_file")
    NEW_DOCKER_IMAGE_PREFIX=$(jq -r '.dockerImagePrefix' "$config_file")
    NEW_ROUTE_GROUP=$(jq -r '.routeGroup' "$config_file")
    
    # Parse gitRepo (format: org/repo)
    local git_repo=$(jq -r '.gitRepo' "$config_file")
    NEW_GITHUB_ORG=$(echo "$git_repo" | cut -d'/' -f1)
    NEW_GITHUB_REPO=$(echo "$git_repo" | cut -d'/' -f2)
    
    NEW_API_DOMAIN=$(jq -r '.apiDomain // "api.'"$NEW_PACKAGE_NAME"'.com"' "$config_file")
    NEW_LOGO_NAME=$(jq -r '.logoName // "'"$NEW_PACKAGE_NAME"'-logo"' "$config_file")
    
    log "Configuration summary:" "$CYAN"
    log "  Display Name: $OLD_DISPLAY_NAME → $NEW_DISPLAY_NAME" "$CYAN"
    log "  Package Name: $OLD_PACKAGE_NAME → $NEW_PACKAGE_NAME" "$CYAN"
    log "  Namespace: $OLD_NAMESPACE → $NEW_PACKAGE_NAMESPACE" "$CYAN"
    log "  GitHub: $OLD_GITHUB_ORG/$OLD_GITHUB_REPO → $NEW_GITHUB_ORG/$NEW_GITHUB_REPO" "$CYAN"
    log ""
}

# Check if renaming is needed
check_if_renaming_needed() {
    local needs_rename=false
    
    # Check key values that would indicate a rename is needed
    if [[ "$OLD_PACKAGE_NAME" != "$NEW_PACKAGE_NAME" ]]; then
        needs_rename=true
    elif [[ "$OLD_NAMESPACE" != "$NEW_PACKAGE_NAMESPACE" ]]; then
        needs_rename=true
    elif [[ "$OLD_DISPLAY_NAME" != "$NEW_DISPLAY_NAME" ]]; then
        needs_rename=true
    elif [[ "$OLD_GITHUB_ORG" != "$NEW_GITHUB_ORG" ]] || [[ "$OLD_GITHUB_REPO" != "$NEW_GITHUB_REPO" ]]; then
        needs_rename=true
    elif [[ "${OLD_PACKAGE_NAME}-web" != "$NEW_WEB_PACKAGE_NAME" ]]; then
        needs_rename=true
    elif [[ "${OLD_PACKAGE_NAME}-service" != "$NEW_SERVICE_NAME" ]]; then
        needs_rename=true
    elif [[ "${OLD_PACKAGE_NAME}_db" != "$NEW_DATABASE_NAME" ]]; then
        needs_rename=true
    elif [[ "$OLD_PACKAGE_NAME" != "$NEW_ROUTE_GROUP" ]]; then
        needs_rename=true
    fi
    
    if [[ "$needs_rename" == false ]]; then
        log "✓ Project is already renamed to match configuration." "$GREEN"
        log "All detected values match the target configuration - no renaming needed.\n" "$GREEN"
        return 1  # Return false - no rename needed
    fi
    
    return 0  # Return true - rename needed
}

# Replace text in files
replace_in_files() {
    local pattern="$1"
    local replacement="$2"
    local description="$3"
    
    log "Replacing $description..." "$BLUE"
    
    # Use find to locate files, excluding certain directories
    find "$PROJECT_ROOT" \
        -type f \
        ! -path "*/node_modules/*" \
        ! -path "*/build/*" \
        ! -path "*/.git/*" \
        ! -path "*/coverage/*" \
        ! -path "*/storybook-static/*" \
        ! -path "*/gradle/*" \
        ! -path "*/bin/*" \
        ! -name "*.backup" \
        ! -name "*.swp" \
        ! -name "*.swo" \
        ! -path "*/scripts/*" \
        -exec grep -l "$pattern" {} + 2>/dev/null | while read -r file; do
            # Use sed for in-place replacement
            if [[ "$OSTYPE" == "darwin"* ]]; then
                # macOS requires empty string after -i
                sed -i '' "s|$pattern|$replacement|g" "$file"
            else
                # Linux
                sed -i "s|$pattern|$replacement|g" "$file"
            fi
        done
    
    log "  ✓ $description replaced" "$GREEN"
}

# Rename files and directories
rename_files() {
    log "Renaming files and directories..." "$BLUE"
    
    # Rename Next.js route group folder
    local old_route_group="$PROJECT_ROOT/web/src/app/($OLD_PACKAGE_NAME)"
    local new_route_group="$PROJECT_ROOT/web/src/app/($NEW_ROUTE_GROUP)"
    
    if [[ -d "$old_route_group" ]] && [[ "$old_route_group" != "$new_route_group" ]]; then
        mv "$old_route_group" "$new_route_group"
        log "  ✓ Renamed route group: ($OLD_PACKAGE_NAME) → ($NEW_ROUTE_GROUP)" "$GREEN"
    fi
    
    # Rename logo files
    if [[ -d "$PROJECT_ROOT/design/assets/logos" ]]; then
        find "$PROJECT_ROOT/design/assets/logos" -type f \( -name "*${OLD_PACKAGE_NAME}-logo*" -o -name "*${OLD_PACKAGE_NAME}logo*" \) | while read -r file; do
            local dir=$(dirname "$file")
            local basename=$(basename "$file")
            local new_basename="${basename//${OLD_PACKAGE_NAME}-logo/$NEW_LOGO_NAME}"
            new_basename="${new_basename//${OLD_PACKAGE_NAME}logo/${NEW_LOGO_NAME}}"
            if [[ "$basename" != "$new_basename" ]]; then
                mv "$file" "$dir/$new_basename"
                log "  ✓ Renamed: $basename → $new_basename" "$GREEN"
            fi
        done
    fi
    
    # Rename workspace file if it exists
    local old_workspace="$PROJECT_ROOT/${OLD_PACKAGE_NAME}.code-workspace"
    local new_workspace="$PROJECT_ROOT/$NEW_PACKAGE_NAME.code-workspace"
    if [[ -f "$old_workspace" ]] && [[ "$old_workspace" != "$new_workspace" ]]; then
        mv "$old_workspace" "$new_workspace"
        log "  ✓ Renamed workspace file" "$GREEN"
    fi
    
    # Rename any other files/directories with the old package name in the name
    # This is done carefully to avoid renaming the script itself or important files
    find "$PROJECT_ROOT" \
        -depth \
        -name "*${OLD_PACKAGE_NAME}*" \
        ! -path "*/node_modules/*" \
        ! -path "*/build/*" \
        ! -path "*/.git/*" \
        ! -path "*/coverage/*" \
        ! -path "*/storybook-static/*" \
        ! -path "*/gradle/*" \
        ! -path "*/bin/*" \
        ! -name "*.backup" \
        ! -name "*.swp" \
        ! -name "*.swo" \
        ! -path "*/scripts/*" \
        ! -name "test-init-local.sh" \
        | while read -r item; do
            local dir=$(dirname "$item")
            local basename=$(basename "$item")
            local new_basename="${basename//${OLD_PACKAGE_NAME}/$NEW_PACKAGE_NAME}"
            if [[ "$basename" != "$new_basename" ]]; then
                mv "$item" "$dir/$new_basename"
                log "  ✓ Renamed: $basename → $new_basename" "$GREEN"
            fi
        done
}

# Main function
main() {
    cd "$PROJECT_ROOT"
    
    log "\n🚀 Starting project setup...\n" "$BRIGHT"
    
    # Pre-flight checks
    check_jq
    validate_config
    load_config
    
    # Check if renaming is actually needed
    if ! check_if_renaming_needed; then
        log "Skipping setup - project is already configured correctly.\n" "$CYAN"
        exit 0
    fi
    
    # Confirm before proceeding
    log "\n⚠️  This will rename all project references from '$OLD_PACKAGE_NAME' to '$NEW_PACKAGE_NAME'." "$YELLOW"
    log "Make sure you have committed or backed up your changes before proceeding.\n" "$YELLOW"
    log "Continue? (y/n) " "$YELLOW"
    read -r response
    if [[ ! "$response" =~ ^[Yy]$ ]]; then
        log "Aborted by user." "$YELLOW"
        exit 0
    fi
    
    log "\n📝 Performing replacements...\n" "$BRIGHT"
    
    # IMPORTANT: Order matters! Do specific (longer) replacements first, then general ones
    
    # Replace package namespaces (most specific - contains dots)
    replace_in_files "$OLD_NAMESPACE" "$NEW_PACKAGE_NAMESPACE" "package namespaces"
    
    # Replace compound names (specific patterns)
    replace_in_files "${OLD_PACKAGE_NAME}-web" "$NEW_WEB_PACKAGE_NAME" "web package names"
    replace_in_files "${OLD_PACKAGE_NAME}-service" "$NEW_SERVICE_NAME" "service names"
    replace_in_files "${OLD_PACKAGE_NAME}_db" "$NEW_DATABASE_NAME" "database names"
    replace_in_files "${OLD_PACKAGE_NAME}-logo" "$NEW_LOGO_NAME" "logo names"
    
    # Replace GitHub references (specific patterns)
    replace_in_files "github.com/${OLD_GITHUB_ORG}/${OLD_GITHUB_REPO}" "github.com/$NEW_GITHUB_ORG/$NEW_GITHUB_REPO" "GitHub URLs"
    replace_in_files "${OLD_GITHUB_ORG}/${OLD_GITHUB_REPO}" "$NEW_GITHUB_ORG/$NEW_GITHUB_REPO" "GitHub repository references"
    
    # Replace API domains (specific patterns) - detect from current codebase if possible
    # Try to find current API domain from config files
    local old_api_domain=""
    if [[ -f "$PROJECT_ROOT/web/next.config.mjs" ]] || [[ -f "$PROJECT_ROOT/web/next.config.js" ]]; then
        local next_config=$(find "$PROJECT_ROOT/web" -maxdepth 1 -name "next.config.*" | head -1)
        if [[ -n "$next_config" ]]; then
            old_api_domain=$(grep -oE "api\.[a-zA-Z0-9.-]+\.com" "$next_config" | head -1 || echo "")
        fi
    fi
    if [[ -n "$old_api_domain" ]]; then
        replace_in_files "$old_api_domain" "$NEW_API_DOMAIN" "API domains"
        # Extract base domain
        local old_base_domain="${old_api_domain#api.}"
        local new_base_domain="${NEW_API_DOMAIN#api.}"
        if [[ "$old_base_domain" != "$old_api_domain" ]] && [[ "$old_base_domain" != "$new_base_domain" ]]; then
            replace_in_files "$old_base_domain" "$new_base_domain" "domain references"
        fi
    fi
    
    # Replace route groups in paths (specific pattern with parentheses)
    replace_in_files "($OLD_PACKAGE_NAME)" "($NEW_ROUTE_GROUP)" "route groups"
    
    # Replace display names
    replace_in_files "$OLD_DISPLAY_NAME" "$NEW_DISPLAY_NAME" "display names"
    
    # Replace general package name instances (do this last, after specific patterns)
    # After all specific patterns are replaced, remaining "neotool" should become the package name
    # Note: This is a broad replacement, but safe because we've already handled compound names
    replace_in_files "$OLD_PACKAGE_NAME" "$NEW_PACKAGE_NAME" "remaining package name references"
    
    log "\n📁 Renaming files and directories...\n" "$BRIGHT"
    rename_files
    
    log "\n✅ Project setup completed successfully!\n" "$BRIGHT"
    log "Next steps:" "$CYAN"
    log "  1. Review the changes: git diff" "$CYAN"
    log "  2. Test your application to ensure everything works" "$CYAN"
    log "  3. Commit the changes: git add . && git commit -m 'Setup project: rename from $OLD_PACKAGE_NAME to $NEW_PACKAGE_NAME'" "$CYAN"
    log ""
}

# Run main function
main "$@"
