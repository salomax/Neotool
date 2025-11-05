#!/usr/bin/env bash

set -euo pipefail

# Project Renaming Script
# 
# This script maps all "neotool" references to new project values from project.config.json.
# Source patterns are hardcoded below; target values come from the config file.

# Get script directory and project root
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Color codes for terminal output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BRIGHT='\033[1m'
RESET='\033[0m'

log() {
    local message="$1"
    local color="${2:-$RESET}"
    echo -e "${color}${message}${RESET}"
}

log_error() {
    log "$1" "$RED"
}

# Load configuration from project.config.json
load_config() {
    local config_path="$PROJECT_ROOT/project.config.json"
    
    if [[ ! -f "$config_path" ]]; then
        log_error "Configuration file not found: $config_path"
        log_error "Please create project.config.json with your project values"
        exit 1
    fi
    
    # Validate required fields using jq
    local required_fields=(
        "displayName" "packageName" "packageNamespace" "databaseName"
        "databaseUser" "serviceName" "webPackageName" "dockerImagePrefix"
        "routeGroup" "githubOrg" "githubRepo"
    )
    
    for field in "${required_fields[@]}"; do
        local value
        value=$(jq -r ".$field" "$config_path" 2>/dev/null || echo "")
        if [[ -z "$value" || "$value" == "null" ]]; then
            log_error "Missing required configuration field: $field"
            exit 1
        fi
    done
    
    # Export config values
    export DISPLAY_NAME=$(jq -r '.displayName' "$config_path")
    export PACKAGE_NAME=$(jq -r '.packageName' "$config_path")
    export PACKAGE_NAMESPACE=$(jq -r '.packageNamespace' "$config_path")
    export DATABASE_NAME=$(jq -r '.databaseName' "$config_path")
    export DATABASE_USER=$(jq -r '.databaseUser' "$config_path")
    export SERVICE_NAME=$(jq -r '.serviceName' "$config_path")
    export WEB_PACKAGE_NAME=$(jq -r '.webPackageName' "$config_path")
    export DOCKER_IMAGE_PREFIX=$(jq -r '.dockerImagePrefix' "$config_path")
    export ROUTE_GROUP=$(jq -r '.routeGroup' "$config_path")
    export GITHUB_ORG=$(jq -r '.githubOrg' "$config_path")
    export GITHUB_REPO=$(jq -r '.githubRepo' "$config_path")
    
    # Optional fields with defaults
    local api_domain=$(jq -r '.apiDomain // empty' "$config_path")
    if [[ -z "$api_domain" ]]; then
        api_domain="api.$(echo "$PACKAGE_NAME" | tr '-' '.')"
    fi
    export API_DOMAIN="$api_domain"
    
    local logo_name=$(jq -r '.logoName // empty' "$config_path")
    if [[ -z "$logo_name" ]]; then
        logo_name="${PACKAGE_NAME}-logo"
    fi
    export LOGO_NAME="$logo_name"
    
    # Extract base package name from namespace
    if [[ -n "$PACKAGE_NAMESPACE" ]]; then
        export BASE_PACKAGE_NAME="${PACKAGE_NAMESPACE##*.}"
        
        # Get the last two parts
        local temp="${PACKAGE_NAMESPACE%.*}"
        if [[ "$temp" != "$PACKAGE_NAMESPACE" ]] && [[ -n "$temp" ]]; then
            local second_last="${temp##*.}"
            export NAMESPACE_LAST_TWO="${second_last}.${BASE_PACKAGE_NAME}"
        else
            export NAMESPACE_LAST_TWO="$PACKAGE_NAMESPACE"
        fi
    else
        export BASE_PACKAGE_NAME=""
        export NAMESPACE_LAST_TWO=""
    fi
    
    # Domain without api prefix
    export DOMAIN_WITHOUT_API=$(echo "$API_DOMAIN" | sed 's/^api\.//')
}

# Escape special characters for sed
escape_sed() {
    echo "$1" | sed 's/[[\.*^$()+?{|]/\\&/g'
}

# Replace content in a file
replace_in_file() {
    local file_path="$1"
    local old_value="$2"
    local new_value="$3"
    
    # Check if file contains the old value
    if grep -qF "$old_value" "$file_path" 2>/dev/null; then
        # Escape both old and new values for sed
        local escaped_old=$(escape_sed "$old_value")
        local escaped_new=$(escape_sed "$new_value")
        
        # Use sed for in-place replacement (works on both macOS and Linux)
        if [[ "$OSTYPE" == "darwin"* ]]; then
            sed -i '' "s|${escaped_old}|${escaped_new}|g" "$file_path"
        else
            sed -i "s|${escaped_old}|${escaped_new}|g" "$file_path"
        fi
        return 0
    fi
    return 1
}

# Define all source-to-target mappings
# Source patterns are hardcoded "neotool" references
# Target values come from project.config.json
apply_replacements() {
    local file_path="$1"
    local modified=0
    
    # Order matters! More specific replacements first
    
    # Package namespaces (longest first)
    replace_in_file "$file_path" "io.github.salomax.neotool" "$PACKAGE_NAMESPACE" && modified=1
    replace_in_file "$file_path" "salomax.neotool" "$NAMESPACE_LAST_TWO" && modified=1
    
    # Docker/Container names (specific container names first)
    replace_in_file "$file_path" "neotool-postgres-exporter" "${DOCKER_IMAGE_PREFIX}-postgres-exporter" && modified=1
    replace_in_file "$file_path" "neotool-redis-exporter" "${DOCKER_IMAGE_PREFIX}-redis-exporter" && modified=1
    replace_in_file "$file_path" "neotool-kafka-exporter" "${DOCKER_IMAGE_PREFIX}-kafka-exporter" && modified=1
    replace_in_file "$file_path" "neotool-graphql-router" "${DOCKER_IMAGE_PREFIX}-graphql-router" && modified=1
    replace_in_file "$file_path" "neotool-postgres" "${DOCKER_IMAGE_PREFIX}-postgres" && modified=1
    replace_in_file "$file_path" "neotool-prometheus" "${DOCKER_IMAGE_PREFIX}-prometheus" && modified=1
    replace_in_file "$file_path" "neotool-promtail" "${DOCKER_IMAGE_PREFIX}-promtail" && modified=1
    replace_in_file "$file_path" "neotool-grafana" "${DOCKER_IMAGE_PREFIX}-grafana" && modified=1
    replace_in_file "$file_path" "neotool-loki" "${DOCKER_IMAGE_PREFIX}-loki" && modified=1
    replace_in_file "$file_path" "neotool-redis" "${DOCKER_IMAGE_PREFIX}-redis" && modified=1
    replace_in_file "$file_path" "neotool-kafka" "${DOCKER_IMAGE_PREFIX}-kafka" && modified=1
    replace_in_file "$file_path" "neotool-api" "${DOCKER_IMAGE_PREFIX}-api" && modified=1
    
    # Docker images with tags (more specific first)
    replace_in_file "$file_path" "neotool-web:latest" "${WEB_PACKAGE_NAME}:latest" && modified=1
    replace_in_file "$file_path" "neotool-backend:latest" "${DOCKER_IMAGE_PREFIX}-backend:latest" && modified=1
    replace_in_file "$file_path" "neotool-backend" "${DOCKER_IMAGE_PREFIX}-backend" && modified=1
    
    # Package names
    replace_in_file "$file_path" "neotool-web" "$WEB_PACKAGE_NAME" && modified=1
    replace_in_file "$file_path" "neotool-mobile" "${PACKAGE_NAME}-mobile" && modified=1
    replace_in_file "$file_path" "neotool-service" "$SERVICE_NAME" && modified=1
    
    # Database names
    replace_in_file "$file_path" "neotool_db" "$DATABASE_NAME" && modified=1
    
    # Database user in environment variable patterns
    replace_in_file "$file_path" ":-neotool}" ":-${DATABASE_USER}}" && modified=1
    replace_in_file "$file_path" ":neotool}" ":${DATABASE_USER}}" && modified=1
    replace_in_file "$file_path" "-neotool}" "-${DATABASE_USER}}" && modified=1
    replace_in_file "$file_path" "=neotool}" "=${DATABASE_USER}}" && modified=1
    
    # GitHub URLs
    replace_in_file "$file_path" "https://github.com/salomax/neotool" "https://github.com/${GITHUB_ORG}/${GITHUB_REPO}" && modified=1
    replace_in_file "$file_path" "github.com/salomax/neotool" "github.com/${GITHUB_ORG}/${GITHUB_REPO}" && modified=1
    replace_in_file "$file_path" "salomax/neotool" "${GITHUB_ORG}/${GITHUB_REPO}" && modified=1
    
    # Route groups (Next.js route groups)
    replace_in_file "$file_path" "/((neotool))/" "/(${ROUTE_GROUP})/" && modified=1
    replace_in_file "$file_path" "((neotool))" "(${ROUTE_GROUP})" && modified=1
    
    # Logo files
    replace_in_file "$file_path" "neotool-logo" "$LOGO_NAME" && modified=1
    
    # API domains
    replace_in_file "$file_path" "api.neotool.com" "$API_DOMAIN" && modified=1
    replace_in_file "$file_path" "neotool.com" "$DOMAIN_WITHOUT_API" && modified=1
    
    # Grafana dashboard
    replace_in_file "$file_path" "neotool-dashboards" "${PACKAGE_NAME}-dashboards" && modified=1
    replace_in_file "$file_path" "neotool-overview" "${PACKAGE_NAME}-overview" && modified=1
    replace_in_file "$file_path" "NeoTool - System Overview" "${DISPLAY_NAME} - System Overview" && modified=1
    replace_in_file "$file_path" "Neotool - System Overview" "${DISPLAY_NAME} - System Overview" && modified=1
    
    # ArgoCD
    replace_in_file "$file_path" "neotool-apps" "${PACKAGE_NAME}-apps" && modified=1
    
    # Display names (case variations)
    replace_in_file "$file_path" "NeoTool" "$DISPLAY_NAME" && modified=1
    replace_in_file "$file_path" "Neotool" "$DISPLAY_NAME" && modified=1
    
    # Generic 'neotool' replacement (must be last to catch remaining instances)
    replace_in_file "$file_path" "neotool" "$PACKAGE_NAME" && modified=1
    
    [[ $modified -eq 1 ]] && return 0 || return 1
}

# Get all files recursively, excluding certain directories
get_all_files() {
    find "$PROJECT_ROOT" -type f \
        -not -path "*/node_modules/*" \
        -not -path "*/.git/*" \
        -not -path "*/build/*" \
        -not -path "*/.gradle/*" \
        -not -path "*/.next/*" \
        -not -path "*/coverage/*" \
        -not -path "*/storybook-static/*" \
        -not -path "*/bin/*" \
        -not -path "*/dist/*" \
        -not -name "*.jar" \
        -not -name "*.class" \
        -not -name "*.png" \
        -not -name "*.jpg" \
        -not -name "*.jpeg" \
        -not -name "*.gif" \
        -not -name "*.ico" \
        -not -name "*.woff" \
        -not -name "*.woff2" \
        -not -name "*.ttf" \
        -not -name "*.eot" \
        -not -name "package-lock.json" \
        -not -name ".*"
}

# Rename files and directories
rename_files_and_dirs() {
    local files_renamed=0
    local dirs_renamed=0
    
    # Find directories to rename (deepest first)
    local dirs_to_rename=()
    while IFS= read -r -d '' dir; do
        local dir_name=$(basename "$dir")
        local relative_dir=$(realpath --relative-to="$PROJECT_ROOT" "$dir" 2>/dev/null || echo "${dir#$PROJECT_ROOT/}")
        
        # Skip build directories
        if [[ "$relative_dir" == *"node_modules"* ]] || \
           [[ "$relative_dir" == *"build"* ]] || \
           [[ "$relative_dir" == *".gradle"* ]] || \
           [[ "$relative_dir" == *".next"* ]] || \
           [[ "$relative_dir" == *"coverage"* ]]; then
            continue
        fi
        
        if [[ "$dir_name" == "((neotool))" ]]; then
            dirs_to_rename+=("$dir|(${ROUTE_GROUP})")
        elif [[ "$dir_name" == *"neotool"* ]]; then
            local new_name="${dir_name//neotool/$PACKAGE_NAME}"
            # Handle special case for route groups
            if [[ "$new_name" == "($PACKAGE_NAME)" ]]; then
                new_name="(${ROUTE_GROUP})"
            fi
            dirs_to_rename+=("$dir|$new_name")
        fi
    done < <(find "$PROJECT_ROOT" -type d -print0 | sort -rz)
    
    # Rename directories
    for entry in "${dirs_to_rename[@]}"; do
        local old_path="${entry%%|*}"
        local new_name="${entry##*|}"
        local parent_dir=$(dirname "$old_path")
        local new_path="$parent_dir/$new_name"
        
        if [[ -d "$old_path" ]] && [[ "$old_path" != "$new_path" ]]; then
            mv "$old_path" "$new_path" 2>/dev/null || {
                log_error "Error renaming directory $old_path"
                continue
            }
            local rel_path=$(realpath --relative-to="$PROJECT_ROOT" "$old_path" 2>/dev/null || echo "${old_path#$PROJECT_ROOT/}")
            local rel_new=$(realpath --relative-to="$PROJECT_ROOT" "$new_path" 2>/dev/null || echo "${new_path#$PROJECT_ROOT/}")
            log "Renamed directory: $rel_path -> $rel_new" "$CYAN"
            ((dirs_renamed++))
        fi
    done
    
    # Find files to rename
    while IFS= read -r -d '' file; do
        local file_name=$(basename "$file")
        local relative_file=$(realpath --relative-to="$PROJECT_ROOT" "$file" 2>/dev/null || echo "${file#$PROJECT_ROOT/}")
        
        # Skip build directories
        if [[ "$relative_file" == *"node_modules"* ]] || \
           [[ "$relative_file" == *"build"* ]] || \
           [[ "$relative_file" == *".gradle"* ]] || \
           [[ "$relative_file" == *".next"* ]] || \
           [[ "$relative_file" == *"coverage"* ]]; then
            continue
        fi
        
        if [[ "$file_name" == *"neotool"* ]]; then
            local new_name
            if [[ "$file_name" == *"neotool-logo"* ]]; then
                new_name="${file_name//neotool-logo/$LOGO_NAME}"
            else
                new_name="${file_name//neotool/$PACKAGE_NAME}"
            fi
            
            local parent_dir=$(dirname "$file")
            local new_path="$parent_dir/$new_name"
            
            if [[ "$file" != "$new_path" ]]; then
                mv "$file" "$new_path" 2>/dev/null || {
                    log_error "Error renaming file $file"
                    continue
                }
                local rel_path=$(realpath --relative-to="$PROJECT_ROOT" "$file" 2>/dev/null || echo "${file#$PROJECT_ROOT/}")
                local rel_new=$(realpath --relative-to="$PROJECT_ROOT" "$new_path" 2>/dev/null || echo "${new_path#$PROJECT_ROOT/}")
                log "Renamed file: $rel_path -> $rel_new" "$CYAN"
                ((files_renamed++))
            fi
        fi
    done < <(find "$PROJECT_ROOT" -type f -print0)
    
    echo "$files_renamed|$dirs_renamed"
}

# Main execution
main() {
    log "\n🚀 Starting project renaming process...\n" "$BRIGHT"
    
    # Load configuration
    log "📋 Loading configuration..." "$BLUE"
    load_config
    log "   ✓ Display Name: $DISPLAY_NAME" "$GREEN"
    log "   ✓ Package Name: $PACKAGE_NAME" "$GREEN"
    log "   ✓ Package Namespace: $PACKAGE_NAMESPACE" "$GREEN"
    log "   ✓ Route Group: $ROUTE_GROUP\n" "$GREEN"
    
    # Warn if still using default "neotool" values
    if [[ "$PACKAGE_NAME" == "neotool" ]] && [[ "$GITHUB_ORG" == "salomax" ]]; then
        log "⚠️  Warning: Configuration still contains default \"neotool\" values." "$YELLOW"
        log "   If you want to rename the project, please edit project.config.json first.\n" "$YELLOW"
        log "   Proceeding with current configuration...\n" "$YELLOW"
    fi
    
    # Get all files
    log "📁 Scanning files..." "$BLUE"
    local all_files
    all_files=$(get_all_files)
    local file_count=$(echo "$all_files" | wc -l | tr -d ' ')
    log "   ✓ Found $file_count files to process\n" "$GREEN"
    
    # Process files
    log "✏️  Processing files..." "$BLUE"
    local files_modified=0
    local file_num=0
    
    while IFS= read -r file; do
        [[ -z "$file" ]] && continue
        if apply_replacements "$file"; then
            ((files_modified++))
            ((file_num++))
            if [[ $((file_num % 50)) -eq 0 ]]; then
                echo -n "."
            fi
        fi
    done <<< "$all_files"
    
    echo ""
    log "\n   ✓ Modified $files_modified files\n" "$GREEN"
    
    # Rename files and directories
    log "📝 Renaming files and directories..." "$BLUE"
    local rename_results
    rename_results=$(rename_files_and_dirs)
    local files_renamed="${rename_results%%|*}"
    local dirs_renamed="${rename_results##*|}"
    log "   ✓ Renamed $files_renamed files and $dirs_renamed directories\n" "$GREEN"
    
    # Summary
    log "\n✅ Project renaming completed!\n" "$BRIGHT"
    log "📊 Summary:" "$BLUE"
    log "   • Files modified: $files_modified" "$GREEN"
    log "   • Files renamed: $files_renamed" "$GREEN"
    log "   • Directories renamed: $dirs_renamed" "$GREEN"
    
    log "\n⚠️  Next steps:" "$YELLOW"
    log "   1. Review the changes with: git diff" "$YELLOW"
    log "   2. Update any remaining references manually" "$YELLOW"
    log "   3. Test the application to ensure everything works" "$YELLOW"
    log "   4. Update logo files in design/assets/logos/ if needed" "$YELLOW"
    log "   5. Update neotool.code-workspace filename if desired" "$YELLOW"
    log "   6. Commit the changes: git add . && git commit -m \"Rename project from neotool to $PACKAGE_NAME\"" "$YELLOW"
    log ""
}

# Run the script
main "$@"
