#!/usr/bin/env bash

set -euo pipefail

# Clean Command
# 
# Removes customer/product example code, (neotool) route group, and generated lib files

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

DRY_RUN=false
if [[ "${1:-}" == "--dry-run" ]]; then
    DRY_RUN=true
    log "\n🔍 Running in DRY-RUN mode (no files will be modified)\n" "$YELLOW"
fi

# Files and directories to delete
declare -a FILES_TO_DELETE=(
    # Backend entities
    "service/kotlin/app/src/main/kotlin/io/github/salomax/neotool/example/entity/CustomerEntity.kt"
    "service/kotlin/app/src/main/kotlin/io/github/salomax/neotool/example/entity/ProductEntity.kt"
    
    # Backend domain models
    "service/kotlin/app/src/main/kotlin/io/github/salomax/neotool/example/domain/Customer.kt"
    "service/kotlin/app/src/main/kotlin/io/github/salomax/neotool/example/domain/Product.kt"
    
    # Backend DTOs
    "service/kotlin/app/src/main/kotlin/io/github/salomax/neotool/example/dto/CustomerDto.kt"
    "service/kotlin/app/src/main/kotlin/io/github/salomax/neotool/example/dto/ProductDto.kt"
    
    # Backend resolvers
    "service/kotlin/app/src/main/kotlin/io/github/salomax/neotool/example/graphql/resolvers/CustomerResolver.kt"
    "service/kotlin/app/src/main/kotlin/io/github/salomax/neotool/example/graphql/resolvers/ProductResolver.kt"
    
    # Backend repositories (if they only contain customer/product code)
    "service/kotlin/app/src/main/kotlin/io/github/salomax/neotool/example/repo/Repositories.kt"
    
    # Backend services (if they only contain customer/product code)
    "service/kotlin/app/src/main/kotlin/io/github/salomax/neotool/example/service/Services.kt"
    
    # Database migration
    "service/kotlin/app/src/main/resources/db/migration/V1_1__create_products_customers.sql"
    
    # Backend tests
    "service/kotlin/app/src/test/kotlin/io/github/salomax/neotool/example/test/integration/api/CustomerApiIntegrationTest.kt"
    "service/kotlin/app/src/test/kotlin/io/github/salomax/neotool/example/test/integration/api/ProductApiIntegrationTest.kt"
    "service/kotlin/app/src/test/kotlin/io/github/salomax/neotool/example/test/integration/api/GraphQLCustomerIntegrationTest.kt"
    "service/kotlin/app/src/test/kotlin/io/github/salomax/neotool/example/test/integration/api/GraphQLProductIntegrationTest.kt"
    "service/kotlin/app/src/test/kotlin/io/github/salomax/neotool/example/test/TestDataBuilders.kt"
    
    # Frontend hooks
    "web/src/lib/hooks/customer"
    "web/src/lib/hooks/product"
)

# Function to delete file or directory
delete_item() {
    local item_path="$PROJECT_ROOT/$1"
    
    if [[ ! -e "$item_path" ]]; then
        return 0
    fi
    
    if [[ "$DRY_RUN" == true ]]; then
        if [[ -d "$item_path" ]]; then
            log "  [DRY-RUN] Would delete directory: $1" "$CYAN"
        else
            log "  [DRY-RUN] Would delete file: $1" "$CYAN"
        fi
    else
        if [[ -d "$item_path" ]]; then
            rm -rf "$item_path"
            log "  ✓ Deleted directory: $1" "$GREEN"
        else
            rm -f "$item_path"
            log "  ✓ Deleted file: $1" "$GREEN"
        fi
    fi
}

# Function to remove lines containing pattern from file
remove_lines_from_file() {
    local file_path="$1"
    local pattern="$2"
    local description="$3"
    
    if [[ ! -f "$file_path" ]]; then
        return 0
    fi
    
    if [[ "$DRY_RUN" == true ]]; then
        local matching_lines=$(grep -c "$pattern" "$file_path" 2>/dev/null || echo "0")
        if [[ "$matching_lines" -gt 0 ]]; then
            log "  [DRY-RUN] Would remove $matching_lines line(s) from: $file_path ($description)" "$CYAN"
        fi
    else
        if grep -q "$pattern" "$file_path" 2>/dev/null; then
            # Use sed to remove lines (macOS compatible)
            if [[ "$OSTYPE" == "darwin"* ]]; then
                sed -i '' "/$pattern/d" "$file_path"
            else
                sed -i "/$pattern/d" "$file_path"
            fi
            log "  ✓ Removed $description from: $(basename "$file_path")" "$GREEN"
        fi
    fi
}

# Function to clean GraphQL schema
clean_graphql_schema() {
    local schema_file="$PROJECT_ROOT/contracts/graphql/subgraphs/app/schema.graphqls"
    
    if [[ ! -f "$schema_file" ]]; then
        return 0
    fi
    
    log "Cleaning GraphQL schema..." "$BLUE"
    
    if [[ "$DRY_RUN" == true ]]; then
        local has_customer=$(grep -c "Customer\|customer" "$schema_file" 2>/dev/null || echo "0")
        local has_product=$(grep -c "Product\|product" "$schema_file" 2>/dev/null || echo "0")
        if [[ "$has_customer" -gt 0 ]] || [[ "$has_product" -gt 0 ]]; then
            log "  [DRY-RUN] Would remove customer/product types, queries, and mutations from schema" "$CYAN"
        fi
    else
        # Use Python or awk for more reliable block removal
        # For now, use a simpler sed-based approach and warn about manual cleanup
        local temp_file=$(mktemp)
        
        # Simple approach: remove lines that contain customer/product patterns
        # This won't perfectly handle multi-line blocks, but will catch most cases
        grep -v -E "(type Product|type Customer|input ProductInput|input CustomerInput|enum CustomerStatus|products:|product\(|customers:|customer\(|createProduct|updateProduct|deleteProduct|createCustomer|updateCustomer|deleteCustomer|# Product|# Customer|# Entity queries)" "$schema_file" > "$temp_file" || true
        
        # If the file became too small, something went wrong - don't overwrite
        local original_lines=$(wc -l < "$schema_file" | tr -d ' ')
        local new_lines=$(wc -l < "$temp_file" | tr -d ' ')
        
        if [[ "$new_lines" -gt 5 ]]; then
            mv "$temp_file" "$schema_file"
            log "  ✓ Cleaned GraphQL schema (may need manual review)" "$GREEN"
        else
            rm -f "$temp_file"
            log "  ⚠ GraphQL schema cleanup skipped (file would be too small - manual cleanup recommended)" "$YELLOW"
        fi
    fi
}

# Function to remove (neotool) route group
remove_neotool_route_group() {
    log "Removing (neotool) route group..." "$BLUE"
    
    # Find any route group directory
    local route_group_dir=$(find "$PROJECT_ROOT/web/src/app" -maxdepth 1 -type d -name '(*)' 2>/dev/null | head -1)
    
    if [[ -n "$route_group_dir" ]]; then
        local route_group_name=$(basename "$route_group_dir")
        
        # Check if it's (neotool)
        if [[ "$route_group_name" == "(neotool)" ]]; then
            delete_item "web/src/app/$route_group_name"
        else
            log "  ⚠ Found route group: $route_group_name (not (neotool), skipping)" "$YELLOW"
        fi
    fi
    
    # Also remove references to (neotool) in code files
    log "Removing (neotool) references in code..." "$BLUE"
    find "$PROJECT_ROOT/web/src" \
        -type f \
        \( -name "*.ts" -o -name "*.tsx" -o -name "*.js" -o -name "*.jsx" \) \
        ! -path "*/node_modules/*" \
        ! -path "*/.next/*" \
        ! -path "*/coverage/*" \
        -exec grep -l "(neotool)" {} + 2>/dev/null | while read -r file; do
            if [[ "$DRY_RUN" == true ]]; then
                local matches=$(grep -c "(neotool)" "$file" 2>/dev/null || echo "0")
                if [[ "$matches" -gt 0 ]]; then
                    log "  [DRY-RUN] Would remove $matches reference(s) from: $(basename "$file")" "$CYAN"
                fi
            else
                if [[ "$OSTYPE" == "darwin"* ]]; then
                    sed -i '' 's|@/app/(neotool)/|@/app/|g; s|from.*app/(neotool)/|from "@/app/|g; s|(neotool)||g' "$file"
                else
                    sed -i 's|@/app/(neotool)/|@/app/|g; s|from.*app/(neotool)/|from "@/app/|g; s|(neotool)||g' "$file"
                fi
                log "  ✓ Cleaned references from: $(basename "$file")" "$GREEN"
            fi
        done
}

# Function to remove generated lib files
remove_generated_lib_files() {
    log "Removing generated lib files..." "$BLUE"
    
    local generated_files_dir="$PROJECT_ROOT/web/src/lib/graphql"
    
    if [[ ! -d "$generated_files_dir" ]]; then
        return 0
    fi
    
    # Remove all .generated.ts files
    find "$generated_files_dir" \
        -type f \
        -name "*.generated.ts" \
        ! -path "*/node_modules/*" 2>/dev/null | while read -r file; do
            local rel_path="${file#$PROJECT_ROOT/}"
            delete_item "$rel_path"
        done
    
    # Also remove the types/__generated__ directory
    if [[ -d "$generated_files_dir/types/__generated__" ]]; then
        delete_item "web/src/lib/graphql/types/__generated__"
    fi
}

# Main function
main() {
    cd "$PROJECT_ROOT"
    
    log "\n🧹 Cleaning neotool references and examples...\n" "$BRIGHT"
    
    if [[ "$DRY_RUN" == true ]]; then
        log "DRY-RUN mode: No files will be modified\n" "$YELLOW"
    else
        log "⚠️  This will permanently delete:" "$YELLOW"
        log "  - Customer/product example code" "$YELLOW"
        log "  - (neotool) route group and references" "$YELLOW"
        log "  - Generated lib files" "$YELLOW"
        log "Make sure you have committed or backed up your changes.\n" "$YELLOW"
        log "Continue? (y/n) " "$YELLOW"
        read -r response
        if [[ ! "$response" =~ ^[Yy]$ ]]; then
            log "Aborted by user." "$YELLOW"
            exit 0
        fi
        log ""
    fi
    
    log "📁 Deleting files and directories...\n" "$BRIGHT"
    for item in "${FILES_TO_DELETE[@]}"; do
        delete_item "$item"
    done
    
    # Delete frontend pages (detect route group dynamically)
    local route_group_dir=$(find "$PROJECT_ROOT/web/src/app" -maxdepth 1 -type d -name '(*)' 2>/dev/null | head -1)
    if [[ -n "$route_group_dir" ]]; then
        local route_group_name=$(basename "$route_group_dir")
        delete_item "web/src/app/$route_group_name/examples/customers"
        delete_item "web/src/app/$route_group_name/examples/products"
    else
        # Fallback: try common patterns
        delete_item "web/src/app/(neotool)/examples/customers"
        delete_item "web/src/app/(neotool)/examples/products"
        delete_item "web/src/app/examples/customers"
        delete_item "web/src/app/examples/products"
    fi
    
    # Remove (neotool) route group
    log "\n🗑️  Removing (neotool) route group...\n" "$BRIGHT"
    remove_neotool_route_group
    
    # Remove generated lib files
    log "\n🗑️  Removing generated lib files...\n" "$BRIGHT"
    remove_generated_lib_files
    
    log "\n📝 Cleaning references in files...\n" "$BRIGHT"
    
    # Clean GraphQL schema
    clean_graphql_schema
    
    # Clean OpenAPI spec (remove customer/product examples and schemas)
    local openapi_file="$PROJECT_ROOT/service/kotlin/openapi/openapi.yaml"
    if [[ -f "$openapi_file" ]]; then
        log "Cleaning OpenAPI spec..." "$BLUE"
        remove_lines_from_file "$openapi_file" "Product\|Customer\|product\|customer" "customer/product references"
    fi
    
    # Clean any other files that might reference customers/products
    # Look for imports or references in Kotlin files
    log "Cleaning Kotlin import references..." "$BLUE"
    find "$PROJECT_ROOT/service/kotlin" \
        -name "*.kt" \
        -type f \
        ! -path "*/build/*" \
        ! -path "*/test/*" \
        -exec grep -l "Customer\|Product" {} + 2>/dev/null | while read -r file; do
            if [[ "$file" != *"Customer"* ]] && [[ "$file" != *"Product"* ]]; then
                # File references customers/products but isn't a customer/product file
                # Remove import lines
                if [[ "$DRY_RUN" == true ]]; then
                    local imports=$(grep -c "import.*Customer\|import.*Product" "$file" 2>/dev/null || echo "0")
                    if [[ "$imports" -gt 0 ]]; then
                        log "  [DRY-RUN] Would remove $imports import(s) from: $(basename "$file")" "$CYAN"
                    fi
                else
                    if [[ "$OSTYPE" == "darwin"* ]]; then
                        sed -i '' '/import.*Customer/d; /import.*Product/d' "$file"
                    else
                        sed -i '/import.*Customer/d; /import.*Product/d' "$file"
                    fi
                    log "  ✓ Cleaned imports from: $(basename "$file")" "$GREEN"
                fi
            fi
        done
    
    log "\n✅ Clean completed!" "$BRIGHT"
    if [[ "$DRY_RUN" == true ]]; then
        log "\nRun without --dry-run to apply these changes." "$CYAN"
    else
        log "\nNext steps:" "$CYAN"
        log "  1. Review the changes: git diff" "$CYAN"
        log "  2. Test your application to ensure everything still works" "$CYAN"
        log "  3. Commit the changes: git add . && git commit -m 'Remove neotool references and examples'" "$CYAN"
    fi
    log ""
}

# Run main function
main "$@"
