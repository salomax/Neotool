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
    
    # Detect old database password
    OLD_DATABASE_USER="$OLD_PACKAGE_NAME"
    detect_database_password
    
    # Load new values
    NEW_DISPLAY_NAME=$(jq -r '.displayName' "$config_file")
    NEW_PACKAGE_NAME=$(jq -r '.packageName' "$config_file")
    NEW_PACKAGE_NAMESPACE=$(jq -r '.packageNamespace' "$config_file")
    NEW_DATABASE_NAME=$(jq -r '.databaseName' "$config_file")
    NEW_DATABASE_USER=$(jq -r '.databaseUser' "$config_file")
    NEW_DATABASE_PASSWORD=$(jq -r '.databasePassword // "'"$NEW_DATABASE_USER"'"' "$config_file")
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
    log "  Database: ${OLD_PACKAGE_NAME}_db → $NEW_DATABASE_NAME" "$CYAN"
    log "  Database User: $OLD_DATABASE_USER → $NEW_DATABASE_USER" "$CYAN"
    log "  GitHub: $OLD_GITHUB_ORG/$OLD_GITHUB_REPO → $NEW_GITHUB_ORG/$NEW_GITHUB_REPO" "$CYAN"
    log ""
}

# Global dry-run flag
DRY_RUN=false

# Parse command line arguments
parse_args() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            --dry-run)
                DRY_RUN=true
                shift
                ;;
            *)
                log_error "Unknown option: $1"
                exit 1
                ;;
        esac
    done
}

# Comprehensive search utility to find all references
search_references() {
    local search_term="$1"
    local category="$2"
    
    log "Searching for $category references..." "$BLUE"
    
    local count=0
    local files_found=()
    
    # Use find to locate files, excluding certain directories
    while IFS= read -r file; do
        if [[ -f "$file" ]]; then
            local matches=$(grep -c "$search_term" "$file" 2>/dev/null || echo "0")
            if [[ "$matches" -gt 0 ]]; then
                files_found+=("$file ($matches matches)")
                count=$((count + matches))
            fi
        fi
    done < <(find "$PROJECT_ROOT" \
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
        2>/dev/null)
    
    if [[ $count -gt 0 ]]; then
        log "  Found $count references in ${#files_found[@]} files" "$CYAN"
        if [[ "$DRY_RUN" == true ]] && [[ ${#files_found[@]} -le 10 ]]; then
            for file_info in "${files_found[@]}"; do
                log "    - $file_info" "$CYAN"
            done
        fi
    else
        log "  No references found" "$YELLOW"
    fi
    
    return $count
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
    local file_filter="${4:-}"  # Optional file filter pattern
    
    if [[ "$DRY_RUN" == true ]]; then
        log "[DRY-RUN] Would replace $description..." "$BLUE"
    else
        log "Replacing $description..." "$BLUE"
    fi
    
    local files_modified=0
    local search_path="$PROJECT_ROOT"
    
    # If file_filter is provided, use it to limit search
    if [[ -n "$file_filter" ]]; then
        search_path="$PROJECT_ROOT/$file_filter"
    fi
    
    # Use find to locate files, excluding certain directories
    while IFS= read -r file; do
        if [[ -f "$file" ]] && grep -q "$pattern" "$file" 2>/dev/null; then
            if [[ "$DRY_RUN" == true ]]; then
                local match_count=$(grep -c "$pattern" "$file" 2>/dev/null || echo "0")
                log "  [DRY-RUN] Would modify: $file ($match_count matches)" "$CYAN"
            else
                # Use sed for in-place replacement
                if [[ "$OSTYPE" == "darwin"* ]]; then
                    # macOS requires empty string after -i
                    sed -i '' "s|$pattern|$replacement|g" "$file"
                else
                    # Linux
                    sed -i "s|$pattern|$replacement|g" "$file"
                fi
            fi
            files_modified=$((files_modified + 1))
        fi
    done < <(find "$search_path" \
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
        2>/dev/null)
    
    if [[ "$DRY_RUN" == true ]]; then
        log "  [DRY-RUN] Would modify $files_modified files" "$CYAN"
    else
        log "  ✓ $description replaced in $files_modified files" "$GREEN"
    fi
}

# Replace in specific file
replace_in_file() {
    local file_path="$1"
    local pattern="$2"
    local replacement="$3"
    local description="$4"
    
    if [[ ! -f "$file_path" ]]; then
        return 0
    fi
    
    if [[ "$DRY_RUN" == true ]]; then
        if grep -q "$pattern" "$file_path" 2>/dev/null; then
            log "  [DRY-RUN] Would modify: $file_path" "$CYAN"
        fi
    else
        if grep -q "$pattern" "$file_path" 2>/dev/null; then
            if [[ "$OSTYPE" == "darwin"* ]]; then
                sed -i '' "s|$pattern|$replacement|g" "$file_path"
            else
                sed -i "s|$pattern|$replacement|g" "$file_path"
            fi
            log "  ✓ $description replaced in $file_path" "$GREEN"
        fi
    fi
}

# Detect old database password from pgbouncer userlist.txt
detect_database_password() {
    OLD_DATABASE_PASSWORD=""
    local userlist_file="$PROJECT_ROOT/infra/pgbouncer/userlist.txt"
    
    if [[ -f "$userlist_file" ]]; then
        # Extract password hash from userlist.txt format: "username" "md5hash"
        local line=$(grep "\"$OLD_DATABASE_USER\"" "$userlist_file" 2>/dev/null | head -1)
        if [[ -n "$line" ]]; then
            # Extract the second quoted string (the password hash)
            # Format: "username" "md5hash" -> extract md5hash
            OLD_DATABASE_PASSWORD=$(echo "$line" | sed -E 's/.*"[^"]*"[[:space:]]+"([^"]*)".*/\1/')
            # Remove md5 prefix if present (we'll add it back when generating)
            OLD_DATABASE_PASSWORD="${OLD_DATABASE_PASSWORD#md5}"
        fi
    fi
    
    # If not found, try to detect from environment or use default
    if [[ -z "$OLD_DATABASE_PASSWORD" ]]; then
        OLD_DATABASE_PASSWORD="$OLD_DATABASE_USER"  # Default fallback
    fi
}

# Generate new database password hash for pgbouncer
generate_password_hash() {
    local username="$1"
    local password="$2"
    # pgbouncer uses md5(username + password + username)
    if command -v md5sum &> /dev/null; then
        echo -n "${username}${password}${username}" | md5sum | cut -d' ' -f1
    elif command -v md5 &> /dev/null; then
        echo -n "${username}${password}${username}" | md5 | cut -d' ' -f1
    else
        log_error "Error: Neither md5sum nor md5 command found. Cannot generate password hash."
        exit 1
    fi
}

# Replace Docker container names and environment variables
replace_docker_configs() {
    log "\n📦 Replacing Docker configurations..." "$BRIGHT"
    
    # Replace container names (neotool-* -> NEW_PACKAGE_NAME-*)
    replace_in_files "container_name: ${OLD_PACKAGE_NAME}-" "container_name: ${NEW_PACKAGE_NAME}-" "Docker container names" "infra/docker"
    
    # Replace environment variable defaults in docker-compose files
    replace_in_files "POSTGRES_USER:-${OLD_DATABASE_USER}" "POSTGRES_USER:-${NEW_DATABASE_USER}" "PostgreSQL user defaults" "infra/docker"
    replace_in_files "POSTGRES_PASSWORD:-${OLD_DATABASE_PASSWORD}" "POSTGRES_PASSWORD:-${NEW_DATABASE_PASSWORD}" "PostgreSQL password defaults" "infra/docker"
    replace_in_files "POSTGRES_DB:-${OLD_PACKAGE_NAME}_db" "POSTGRES_DB:-${NEW_DATABASE_NAME}" "PostgreSQL database defaults" "infra/docker"
    
    # Replace DATA_SOURCE_NAME in postgres-exporter
    replace_in_files "postgresql://\${POSTGRES_USER:-${OLD_DATABASE_USER}}:\${POSTGRES_PASSWORD:-${OLD_DATABASE_PASSWORD}}@pgbouncer:6432/\${POSTGRES_DB:-${OLD_PACKAGE_NAME}_db}" \
        "postgresql://\${POSTGRES_USER:-${NEW_DATABASE_USER}}:\${POSTGRES_PASSWORD:-${NEW_DATABASE_PASSWORD}}@pgbouncer:6432/\${POSTGRES_DB:-${NEW_DATABASE_NAME}}" \
        "PostgreSQL exporter data source" "infra/docker"
}

# Replace pgbouncer configurations
replace_pgbouncer_configs() {
    log "\n🗄️  Replacing pgbouncer configurations..." "$BRIGHT"
    
    local pgbouncer_ini="$PROJECT_ROOT/infra/pgbouncer/pgbouncer.ini"
    local userlist_txt="$PROJECT_ROOT/infra/pgbouncer/userlist.txt"
    
    # Replace in pgbouncer.ini
    if [[ -f "$pgbouncer_ini" ]]; then
        # Replace database connection string
        replace_in_file "$pgbouncer_ini" \
            "${OLD_PACKAGE_NAME}_db = host=postgres port=5432 user=${OLD_DATABASE_USER} password=${OLD_DATABASE_PASSWORD} dbname=${OLD_PACKAGE_NAME}_db" \
            "${NEW_DATABASE_NAME} = host=postgres port=5432 user=${NEW_DATABASE_USER} password=${NEW_DATABASE_PASSWORD} dbname=${NEW_DATABASE_NAME}" \
            "pgbouncer database configuration"
        
        # Also handle if database name appears elsewhere in the file
        replace_in_file "$pgbouncer_ini" "$OLD_PACKAGE_NAME"_db "$NEW_DATABASE_NAME" "database name in pgbouncer.ini"
        replace_in_file "$pgbouncer_ini" "user=$OLD_DATABASE_USER" "user=$NEW_DATABASE_USER" "database user in pgbouncer.ini"
        replace_in_file "$pgbouncer_ini" "password=$OLD_DATABASE_PASSWORD" "password=$NEW_DATABASE_PASSWORD" "database password in pgbouncer.ini"
    fi
    
    # Replace in userlist.txt
    if [[ -f "$userlist_txt" ]]; then
        local new_password_hash=$(generate_password_hash "$NEW_DATABASE_USER" "$NEW_DATABASE_PASSWORD")
        # Replace the username first, then the password hash
        # Format: "username" "md5hash"
        # We need to replace the entire line, so we'll use a more flexible approach
        if [[ "$DRY_RUN" == true ]]; then
            if grep -q "\"${OLD_DATABASE_USER}\"" "$userlist_txt" 2>/dev/null; then
                log "  [DRY-RUN] Would replace user credentials in userlist.txt" "$CYAN"
                log "    Old: \"${OLD_DATABASE_USER}\" \"md5...\"" "$CYAN"
                log "    New: \"${NEW_DATABASE_USER}\" \"md5${new_password_hash}\"" "$CYAN"
            fi
        else
            # Use sed to replace the entire line matching the old username
            if [[ "$OSTYPE" == "darwin"* ]]; then
                sed -i '' "s|\"${OLD_DATABASE_USER}\" \".*\"|\"${NEW_DATABASE_USER}\" \"md5${new_password_hash}\"|g" "$userlist_txt"
            else
                sed -i "s|\"${OLD_DATABASE_USER}\" \".*\"|\"${NEW_DATABASE_USER}\" \"md5${new_password_hash}\"|g" "$userlist_txt"
            fi
            log "  ✓ Replaced pgbouncer user credentials in userlist.txt" "$GREEN"
        fi
    fi
}

# Replace logback XML configurations
replace_logback_configs() {
    log "\n📋 Replacing logback configurations..." "$BRIGHT"
    
    # Find all logback XML files
    find "$PROJECT_ROOT/service/kotlin" -name "logback*.xml" -type f 2>/dev/null | while read -r logback_file; do
        # Replace MDCFilter class reference
        replace_in_file "$logback_file" \
            "io.github.salomax.${OLD_PACKAGE_NAME}.logging.MDCFilter" \
            "${NEW_PACKAGE_NAMESPACE}.logging.MDCFilter" \
            "MDCFilter class in logback"
        
        # Replace logger names
        replace_in_file "$logback_file" \
            "io.github.salomax.${OLD_PACKAGE_NAME}" \
            "$NEW_PACKAGE_NAMESPACE" \
            "logger names in logback"
    done
}

# Replace application test configurations
replace_application_test_configs() {
    log "\n🧪 Replacing application test configurations..." "$BRIGHT"
    
    # Find all application-test.yml files
    find "$PROJECT_ROOT/service/kotlin" -name "application-test.yml" -type f 2>/dev/null | while read -r test_config; do
        # Replace service name
        replace_in_file "$test_config" \
            "name: ${OLD_PACKAGE_NAME}-service-test" \
            "name: ${NEW_PACKAGE_NAME}-service-test" \
            "service name in test config"
        
        # Replace logging level namespace
        replace_in_file "$test_config" \
            "io.github.salomax.${OLD_PACKAGE_NAME}:" \
            "${NEW_PACKAGE_NAMESPACE}:" \
            "logging namespace in test config"
    done
}

# Replace GraphQL supergraph configurations
replace_graphql_configs() {
    log "\n🔗 Replacing GraphQL configurations..." "$BRIGHT"
    
    local supergraph_graphql="$PROJECT_ROOT/contracts/graphql/supergraph/supergraph.graphql"
    local supergraph_yaml="$PROJECT_ROOT/contracts/graphql/supergraph/supergraph.yaml"
    
    # Replace in supergraph.graphql
    if [[ -f "$supergraph_graphql" ]]; then
        replace_in_file "$supergraph_graphql" \
            "http://${OLD_PACKAGE_NAME}-app:8080/graphql" \
            "http://${NEW_PACKAGE_NAME}-app:8080/graphql" \
            "app service URL in supergraph.graphql"
        
        replace_in_file "$supergraph_graphql" \
            "http://${OLD_PACKAGE_NAME}-security:8080/graphql" \
            "http://${NEW_PACKAGE_NAME}-security:8080/graphql" \
            "security service URL in supergraph.graphql"
        
        replace_in_file "$supergraph_graphql" \
            "http://${OLD_PACKAGE_NAME}-assistant:8080/graphql" \
            "http://${NEW_PACKAGE_NAME}-assistant:8080/graphql" \
            "assistant service URL in supergraph.graphql"
    fi
    
    # Replace in supergraph.yaml
    if [[ -f "$supergraph_yaml" ]]; then
        replace_in_file "$supergraph_yaml" \
            "http://${OLD_PACKAGE_NAME}-api:8080/graphql" \
            "http://${NEW_PACKAGE_NAME}-api:8080/graphql" \
            "API routing URL in supergraph.yaml"
    fi
}

# Replace frontend configurations
replace_frontend_configs() {
    log "\n🌐 Replacing frontend configurations..." "$BRIGHT"
    
    # Replace metadata in web/src/shared/seo/metadata.ts
    local metadata_file="$PROJECT_ROOT/web/src/shared/seo/metadata.ts"
    if [[ -f "$metadata_file" ]]; then
        # Replace all lowercase neotool references
        replace_in_file "$metadata_file" "'${OLD_PACKAGE_NAME}'" "'${NEW_PACKAGE_NAME}'" "app name in metadata"
        replace_in_file "$metadata_file" "\"${OLD_PACKAGE_NAME}\"" "\"${NEW_PACKAGE_NAME}\"" "app name in metadata (double quotes)"
    fi
    
    # Replace constants in web/src/shared/config/repo.constants.ts
    local constants_file="$PROJECT_ROOT/web/src/shared/config/repo.constants.ts"
    if [[ -f "$constants_file" ]]; then
        replace_in_file "$constants_file" \
            "github.com/${OLD_GITHUB_ORG}/${OLD_GITHUB_REPO}" \
            "github.com/${NEW_GITHUB_ORG}/${NEW_GITHUB_REPO}" \
            "GitHub URL in constants"
        
        replace_in_file "$constants_file" \
            "\"${OLD_GITHUB_ORG}/${OLD_GITHUB_REPO}\"" \
            "\"${NEW_GITHUB_ORG}/${NEW_GITHUB_REPO}\"" \
            "GitHub repo in constants"
        
        replace_in_file "$constants_file" \
            "\"${OLD_PACKAGE_NAME}\"" \
            "\"${NEW_PACKAGE_NAME}\"" \
            "repo name in constants"
    fi
    
    # Replace storage keys (e.g., neotool_cart)
    replace_in_files "${OLD_PACKAGE_NAME}_cart" "${NEW_PACKAGE_NAME}_cart" "storage keys" "web"
    
    # Replace logo references in Logo.tsx and LogoMark.tsx
    local logo_file="$PROJECT_ROOT/web/src/shared/ui/brand/Logo.tsx"
    local logomark_file="$PROJECT_ROOT/web/src/shared/ui/brand/LogoMark.tsx"
    
    if [[ -f "$logo_file" ]]; then
        replace_in_file "$logo_file" \
            "/images/logos/${OLD_PACKAGE_NAME}-logo" \
            "/images/logos/${NEW_LOGO_NAME}" \
            "logo paths in Logo.tsx"
        
        replace_in_file "$logo_file" \
            "alt=\"${OLD_PACKAGE_NAME}\"" \
            "alt=\"${NEW_PACKAGE_NAME}\"" \
            "logo alt text in Logo.tsx"
    fi
    
    if [[ -f "$logomark_file" ]]; then
        replace_in_file "$logomark_file" \
            "/images/logos/${OLD_PACKAGE_NAME}-logo" \
            "/images/logos/${NEW_LOGO_NAME}" \
            "logo paths in LogoMark.tsx"
        
        replace_in_file "$logomark_file" \
            "alt=\"${OLD_PACKAGE_NAME}\"" \
            "alt=\"${NEW_PACKAGE_NAME}\"" \
            "logo alt text in LogoMark.tsx"
    fi
}

# Replace Grafana dashboard configurations
replace_grafana_configs() {
    log "\n📊 Replacing Grafana configurations..." "$BRIGHT"
    
    # Replace database name in Grafana dashboard JSON files
    find "$PROJECT_ROOT/infra/observability/grafana/dashboards" -name "*.json" -type f 2>/dev/null | while read -r dashboard_file; do
        # Replace various database name patterns in Prometheus queries
        replace_in_file "$dashboard_file" \
            "datname=\"${OLD_PACKAGE_NAME}_db\"" \
            "datname=\"${NEW_DATABASE_NAME}\"" \
            "database name in Grafana dashboard"
        
        # Also replace without quotes (for JSON string values)
        replace_in_file "$dashboard_file" \
            "${OLD_PACKAGE_NAME}_db" \
            "$NEW_DATABASE_NAME" \
            "database name references in Grafana dashboard"
    done
    
    # Replace dashboard folder names in provisioning config
    local dashboard_yml="$PROJECT_ROOT/infra/observability/grafana/provisioning/dashboards/dashboard.yml"
    if [[ -f "$dashboard_yml" ]]; then
        replace_in_file "$dashboard_yml" \
            "${OLD_PACKAGE_NAME}-dashboards" \
            "${NEW_PACKAGE_NAME}-dashboards" \
            "dashboard folder name"
        
        replace_in_file "$dashboard_yml" \
            "folder: '${OLD_PACKAGE_NAME}'" \
            "folder: '${NEW_PACKAGE_NAME}'" \
            "dashboard folder"
    fi
}

# Rename files and directories
rename_files() {
    log "Renaming files and directories..." "$BLUE"
    
    # Rename Next.js route group folder
    local old_route_group="$PROJECT_ROOT/web/src/app/($OLD_PACKAGE_NAME)"
    local new_route_group="$PROJECT_ROOT/web/src/app/($NEW_ROUTE_GROUP)"
    
    if [[ -d "$old_route_group" ]] && [[ "$old_route_group" != "$new_route_group" ]]; then
        if [[ "$DRY_RUN" == true ]]; then
            log "  [DRY-RUN] Would rename route group: ($OLD_PACKAGE_NAME) → ($NEW_ROUTE_GROUP)" "$CYAN"
        else
            mv "$old_route_group" "$new_route_group"
            log "  ✓ Renamed route group: ($OLD_PACKAGE_NAME) → ($NEW_ROUTE_GROUP)" "$GREEN"
        fi
    fi
    
    # Rename logo files in design/assets/logos
    if [[ -d "$PROJECT_ROOT/design/assets/logos" ]]; then
        find "$PROJECT_ROOT/design/assets/logos" -type f \( -name "*${OLD_PACKAGE_NAME}-logo*" -o -name "*${OLD_PACKAGE_NAME}logo*" \) | while read -r file; do
            local dir=$(dirname "$file")
            local basename=$(basename "$file")
            local new_basename="${basename//${OLD_PACKAGE_NAME}-logo/$NEW_LOGO_NAME}"
            new_basename="${new_basename//${OLD_PACKAGE_NAME}logo/${NEW_LOGO_NAME}}"
            if [[ "$basename" != "$new_basename" ]]; then
                if [[ "$DRY_RUN" == true ]]; then
                    log "  [DRY-RUN] Would rename: $basename → $new_basename" "$CYAN"
                else
                    mv "$file" "$dir/$new_basename"
                    log "  ✓ Renamed: $basename → $new_basename" "$GREEN"
                fi
            fi
        done
    fi
    
    # Rename logo files in web/public/images/logos
    if [[ -d "$PROJECT_ROOT/web/public/images/logos" ]]; then
        find "$PROJECT_ROOT/web/public/images/logos" -type f \( -name "*${OLD_PACKAGE_NAME}-logo*" -o -name "*${OLD_PACKAGE_NAME}logo*" \) | while read -r file; do
            local dir=$(dirname "$file")
            local basename=$(basename "$file")
            local new_basename="${basename//${OLD_PACKAGE_NAME}-logo/$NEW_LOGO_NAME}"
            new_basename="${new_basename//${OLD_PACKAGE_NAME}logo/${NEW_LOGO_NAME}}"
            if [[ "$basename" != "$new_basename" ]]; then
                if [[ "$DRY_RUN" == true ]]; then
                    log "  [DRY-RUN] Would rename: $basename → $new_basename" "$CYAN"
                else
                    mv "$file" "$dir/$new_basename"
                    log "  ✓ Renamed: $basename → $new_basename" "$GREEN"
                fi
            fi
        done
    fi
    
    # Rename workspace file if it exists
    local old_workspace="$PROJECT_ROOT/${OLD_PACKAGE_NAME}.code-workspace"
    local new_workspace="$PROJECT_ROOT/$NEW_PACKAGE_NAME.code-workspace"
    if [[ -f "$old_workspace" ]] && [[ "$old_workspace" != "$new_workspace" ]]; then
        if [[ "$DRY_RUN" == true ]]; then
            log "  [DRY-RUN] Would rename workspace file: $(basename "$old_workspace") → $(basename "$new_workspace")" "$CYAN"
        else
            mv "$old_workspace" "$new_workspace"
            log "  ✓ Renamed workspace file" "$GREEN"
        fi
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
                if [[ "$DRY_RUN" == true ]]; then
                    log "  [DRY-RUN] Would rename: $basename → $new_basename" "$CYAN"
                else
                    mv "$item" "$dir/$new_basename"
                    log "  ✓ Renamed: $basename → $new_basename" "$GREEN"
                fi
            fi
        done
}

# Main function
main() {
    cd "$PROJECT_ROOT"
    
    # Parse command line arguments
    parse_args "$@"
    
    if [[ "$DRY_RUN" == true ]]; then
        log "\n🔍 Running in DRY-RUN mode (no files will be modified)\n" "$YELLOW"
    fi
    
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
    
    # Confirm before proceeding (skip in dry-run mode)
    if [[ "$DRY_RUN" != true ]]; then
        log "\n⚠️  This will rename all project references from '$OLD_PACKAGE_NAME' to '$NEW_PACKAGE_NAME'." "$YELLOW"
        log "Make sure you have committed or backed up your changes before proceeding.\n" "$YELLOW"
        log "Continue? (y/n) " "$YELLOW"
        read -r response
        if [[ ! "$response" =~ ^[Yy]$ ]]; then
            log "Aborted by user." "$YELLOW"
            exit 0
        fi
    fi
    
    log "\n📝 Performing replacements...\n" "$BRIGHT"
    
    # IMPORTANT: Order matters! Do specific (longer) replacements first, then general ones
    
    # Replace package namespaces (most specific - contains dots)
    replace_in_files "$OLD_NAMESPACE" "$NEW_PACKAGE_NAMESPACE" "package namespaces"
    
    # Replace Docker configurations
    replace_docker_configs
    
    # Replace pgbouncer configurations
    replace_pgbouncer_configs
    
    # Replace logback configurations
    replace_logback_configs
    
    # Replace application test configurations
    replace_application_test_configs
    
    # Replace GraphQL configurations
    replace_graphql_configs
    
    # Replace frontend configurations
    replace_frontend_configs
    
    # Replace Grafana configurations
    replace_grafana_configs
    
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
    
    # Replace API service names in supergraph.yaml and supergraph.graphql files
    replace_in_files "${OLD_PACKAGE_NAME}-api" "${NEW_PACKAGE_NAME}-api" "API service names in supergraph"
    replace_in_files "neotool-api" "${NEW_PACKAGE_NAME}-api" "API service names in supergraph"
    # Also replace in GraphQL files (the join__Graph enum URL)
    replace_in_files "http://${OLD_PACKAGE_NAME}-api:8080/graphql" "http://${NEW_PACKAGE_NAME}-api:8080/graphql" "GraphQL API URLs in supergraph"
    replace_in_files "http://neotool-api:8080/graphql" "http://${NEW_PACKAGE_NAME}-api:8080/graphql" "GraphQL API URLs in supergraph"
    
    # Replace display names
    replace_in_files "$OLD_DISPLAY_NAME" "$NEW_DISPLAY_NAME" "display names"
    
    # Replace general package name instances (do this last, after specific patterns)
    # After all specific patterns are replaced, remaining "neotool" should become the package name
    # Note: This is a broad replacement, but safe because we've already handled compound names
    replace_in_files "$OLD_PACKAGE_NAME" "$NEW_PACKAGE_NAME" "remaining package name references"
    
    log "\n📁 Renaming files and directories...\n" "$BRIGHT"
    rename_files
    
    if [[ "$DRY_RUN" == true ]]; then
        log "\n✅ Dry-run completed! Review the changes above." "$BRIGHT"
        log "Run without --dry-run to apply these changes.\n" "$CYAN"
    else
        log "\n✅ Project setup completed successfully!\n" "$BRIGHT"
        log "Next steps:" "$CYAN"
        log "  1. Review the changes: git diff" "$CYAN"
        log "  2. Test your application to ensure everything works" "$CYAN"
        log "  3. Commit the changes: git add . && git commit -m 'Setup project: rename from $OLD_PACKAGE_NAME to $NEW_PACKAGE_NAME'" "$CYAN"
        log ""
    fi
}

# Run main function
main "$@"
