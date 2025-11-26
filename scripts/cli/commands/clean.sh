#!/usr/bin/env bash

set -euo pipefail

# Clean Command
# 
# Removes feature/example implementations while keeping framework/shared code

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
    local dir="$PWD"
    while [[ "$dir" != "/" ]]; do
        if [[ -f "$dir/project.config.json" ]] || [[ -f "$dir/package.json" ]]; then
            echo "$dir"
            return 0
        fi
        dir=$(dirname "$dir")
    done
    
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

DRY_RUN=false
if [[ "${1:-}" == "--dry-run" ]]; then
    DRY_RUN=true
    log "\n🔍 Running in DRY-RUN mode (no files will be modified)\n" "$YELLOW"
fi

# Array to track deleted items
declare -a DELETED_ITEMS=()

# Function to delete file or directory
delete_item() {
    local item_path="$PROJECT_ROOT/$1"
    
    if [[ ! -e "$item_path" ]]; then
        return 0
    fi
    
    if [[ "$DRY_RUN" == true ]]; then
        if [[ -d "$item_path" ]]; then
            log "  [DRY-RUN] Would delete directory: $1" "$CYAN"
            DELETED_ITEMS+=("📁 $1")
        else
            log "  [DRY-RUN] Would delete file: $1" "$CYAN"
            DELETED_ITEMS+=("📄 $1")
        fi
    else
        if [[ -d "$item_path" ]]; then
            rm -rf "$item_path"
            log "  ✓ Deleted directory: $1" "$GREEN"
            DELETED_ITEMS+=("📁 $1")
        else
            rm -f "$item_path"
            log "  ✓ Deleted file: $1" "$GREEN"
            DELETED_ITEMS+=("📄 $1")
        fi
    fi
}

# Main function
main() {
    cd "$PROJECT_ROOT"
    
    log "\n🧹 Cleaning feature/example implementations...\n" "$BRIGHT"
    
    if [[ "$DRY_RUN" == true ]]; then
        log "DRY-RUN mode: No files will be modified\n" "$YELLOW"
    else
        log "⚠️  This will permanently remove example implementations\n" "$YELLOW"
        log "Make sure you have committed or backed up your changes.\n" "$YELLOW"
        log "Continue? (y/n) " "$YELLOW"
        read -r response
        if [[ ! "$response" =~ ^[Yy]$ ]]; then
            log "Aborted by user." "$YELLOW"
            exit 0
        fi
        log ""
    fi
    
    log "Removing files and directories...\n" "$BLUE"
    
    # Backend examples
    delete_item "service/kotlin/app/src/main/kotlin/io/github/salomax/neotool/example"
    delete_item "service/kotlin/app/src/main/resources/db/migration/V1_1__create_products_customers.sql"
    delete_item "service/kotlin/app/src/main/resources/graphql/schema.graphqls"
    delete_item "service/kotlin/app/src/test/kotlin/io/github/salomax/neotool/example"
    
    # Frontend examples
    delete_item "web/src/app/(neotool)"
    delete_item "web/src/lib/graphql/operations/customer"
    delete_item "web/src/lib/graphql/operations/product"
    delete_item "web/src/lib/hooks/customer"
    delete_item "web/src/lib/hooks/product"
    delete_item "web/src/lib/graphql/types/__generated__"
    
    # GraphQL contracts
    delete_item "contracts/graphql/subgraphs/app/schema.graphqls"
    delete_item "contracts/graphql/subgraphs/app/schema.graphqls.backup"
    delete_item "contracts/graphql/supergraph/supergraph.graphql"
    delete_item "contracts/graphql/supergraph/supergraph.local.graphql"
    
    # OpenAPI (delete the whole file)
    delete_item "service/kotlin/openapi/openapi.yaml"
    delete_item "service/kotlin/openapi/openapi.json"
    
    # Generated GraphQL files
    find "$PROJECT_ROOT/web/src/lib/graphql" \
        -type f \
        -name "*.generated.ts" \
        ! -path "*/node_modules/*" 2>/dev/null | while read -r file; do
        local rel_path="${file#$PROJECT_ROOT/}"
        delete_item "$rel_path"
    done || true
    
    log "\n✅ Clean completed!" "$BRIGHT"
    log ""
    
    # Display summary
    if [[ ${#DELETED_ITEMS[@]} -gt 0 ]]; then
        log "📋 Summary of deleted items (${#DELETED_ITEMS[@]} total):" "$BRIGHT"
        log ""
        for item in "${DELETED_ITEMS[@]}"; do
            log "  $item" "$CYAN"
        done
        log ""
    else
        log "No files to delete." "$YELLOW"
        log ""
    fi
    
    log "Framework/shared code preserved:" "$GREEN"
    log "  ✓ service/kotlin/common/ - Framework code" "$GREEN"
    log "  ✓ web/src/shared/ - Shared components and utilities" "$GREEN"
    log ""
    if [[ "$DRY_RUN" == true ]]; then
        log "Run without --dry-run to apply these changes." "$CYAN"
    else
        log "Next steps:" "$CYAN"
        log "  1. Review the changes: git diff" "$CYAN"
        log "  2. Test your application" "$CYAN"
        log "  3. Commit the changes" "$CYAN"
    fi
    log ""
}

# Run main function
main "$@"
