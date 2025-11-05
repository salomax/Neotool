#!/usr/bin/env bash

set -euo pipefail

# Clean Examples Script
# 
# This script removes all customer and product example code from the codebase,
# keeping only the boilerplate infrastructure.

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

# Files and directories to delete
files_to_delete=(
    # Backend entities
    'service/kotlin/app/src/main/kotlin/io/github/salomax/neotool/example/entity/CustomerEntity.kt'
    'service/kotlin/app/src/main/kotlin/io/github/salomax/neotool/example/entity/ProductEntity.kt'
    
    # Backend domain models
    'service/kotlin/app/src/main/kotlin/io/github/salomax/neotool/example/domain/Customer.kt'
    'service/kotlin/app/src/main/kotlin/io/github/salomax/neotool/example/domain/Product.kt'
    
    # Backend DTOs
    'service/kotlin/app/src/main/kotlin/io/github/salomax/neotool/example/dto/CustomerDto.kt'
    'service/kotlin/app/src/main/kotlin/io/github/salomax/neotool/example/dto/ProductDto.kt'
    
    # Backend resolvers
    'service/kotlin/app/src/main/kotlin/io/github/salomax/neotool/example/graphql/resolvers/CustomerResolver.kt'
    'service/kotlin/app/src/main/kotlin/io/github/salomax/neotool/example/graphql/resolvers/ProductResolver.kt'
    
    # Database migration
    'service/kotlin/app/src/main/resources/db/migration/V1_1__create_products_customers.sql'
)

directories_to_delete=(
    # Frontend customer/product pages
    'web/src/app/(neotool)/examples/customers'
    'web/src/app/(neotool)/examples/products'
    
    # Frontend hooks
    'web/src/lib/hooks/customer'
    
    # Frontend GraphQL operations
    'web/src/lib/graphql/operations/customer'
    'web/src/lib/graphql/operations/product'
)

# Files to modify (clean content)
files_to_modify=(
    'contracts/graphql/subgraphs/app/schema.graphqls'
    'service/kotlin/app/src/main/resources/graphql/schema.graphqls'
    'service/kotlin/app/src/main/kotlin/io/github/salomax/neotool/example/graphql/AppWiringFactory.kt'
    'service/kotlin/app/src/main/kotlin/io/github/salomax/neotool/example/graphql/dto/Inputs.kt'
    'service/kotlin/app/src/main/kotlin/io/github/salomax/neotool/example/service/Services.kt'
    'service/kotlin/app/src/main/kotlin/io/github/salomax/neotool/example/repo/Repositories.kt'
    'web/src/app/(neotool)/examples/page.tsx'
)

# Delete a file or directory
delete_path() {
    local relative_path="$1"
    local full_path="$PROJECT_ROOT/$relative_path"
    
    if [[ ! -e "$full_path" ]]; then
        echo "not found"
        return 1
    fi
    
    if [[ -d "$full_path" ]]; then
        rm -rf "$full_path"
    else
        rm -f "$full_path"
    fi
    echo "deleted"
    return 0
}

# Clean GraphQL schema file
clean_graphql_schema() {
    local file_path="$1"
    local full_path="$PROJECT_ROOT/$file_path"
    
    if [[ ! -f "$full_path" ]]; then
        echo "not found"
        return 1
    fi
    
    # Create a temporary file for modifications
    local temp_file=$(mktemp)
    local modified=0
    
    # Read file and apply transformations
    local content
    content=$(cat "$full_path")
    local original_content="$content"
    
    # Remove Product type
    content=$(echo "$content" | sed -E '/^type Product @key\(fields: "id"\) \{/,/^}/d')
    
    # Remove Customer type
    content=$(echo "$content" | sed -E '/^type Customer @key\(fields: "id"\) \{/,/^}/d')
    
    # Remove ProductInput
    content=$(echo "$content" | sed -E '/^input ProductInput \{/,/^}/d')
    
    # Remove CustomerInput
    content=$(echo "$content" | sed -E '/^input CustomerInput \{/,/^}/d')
    
    # Remove CustomerStatus enum
    content=$(echo "$content" | sed -E '/^enum CustomerStatus \{/,/^}/d')
    
    # Clean Query type - remove customer/product queries (multi-line pattern using perl)
    if command -v perl >/dev/null 2>&1; then
        content=$(echo "$content" | perl -0pe 's/  # Entity queries\n  products: \[Product!\]!\n  product\(id: ID!\): Product\n  customers: \[Customer!\]!\n  customer\(id: ID!\): Customer\n//g')
    else
        content=$(echo "$content" | sed -E '/  # Entity queries/,/  customer\(id: ID!\): Customer/d')
    fi
    
    # Clean Mutation type - remove customer/product mutations (multi-line pattern using perl)
    if command -v perl >/dev/null 2>&1; then
        content=$(echo "$content" | perl -0pe 's/  # Product mutations\n  createProduct\(input: ProductInput!\): Product!\n  updateProduct\(id: ID!, input: ProductInput!\): Product!\n  deleteProduct\(id: ID!\): Boolean!\n  \n  # Customer mutations\n  createCustomer\(input: CustomerInput!\): Customer!\n  updateCustomer\(id: ID!, input: CustomerInput!\): Customer!\n  deleteCustomer\(id: ID!\): Boolean!\n//g')
    else
        content=$(echo "$content" | sed -E '/  # Product mutations/,/  deleteCustomer\(id: ID!\): Boolean!/d')
    fi
    
    # Clean up extra blank lines (3+ consecutive newlines become 2)
    content=$(echo "$content" | sed -E ':a;N;$!ba;s/\n{3,}/\n\n/g')
    
    # Check if content changed
    if [[ "$content" != "$original_content" ]]; then
        echo "$content" > "$full_path"
        echo "modified"
        return 0
    fi
    
    echo "unchanged"
    return 1
}

# Clean AppWiringFactory.kt
clean_app_wiring_factory() {
    local file_path="$1"
    local full_path="$PROJECT_ROOT/$file_path"
    
    if [[ ! -f "$full_path" ]]; then
        echo "not found"
        return 1
    fi
    
    local content
    content=$(cat "$full_path")
    local original_content="$content"
    
    # Remove customer/product resolver imports
    content=$(echo "$content" | sed '/^import io\.github\.salomax\.neotool\.example\.graphql\.resolvers\.CustomerResolver$/d')
    content=$(echo "$content" | sed '/^import io\.github\.salomax\.neotool\.example\.graphql\.resolvers\.ProductResolver$/d')
    
    # Remove customer/product resolver constructor parameters
    content=$(echo "$content" | sed -E 's/,\s*private val customerResolver: CustomerResolver//g')
    content=$(echo "$content" | sed -E 's/,\s*private val productResolver: ProductResolver//g')
    content=$(echo "$content" | sed -E 's/private val customerResolver: CustomerResolver,\s*//g')
    content=$(echo "$content" | sed -E 's/private val productResolver: ProductResolver,\s*//g')
    
    # Remove resolver registry registrations
    content=$(echo "$content" | sed '/resolverRegistry\.register("customer", customerResolver)/d')
    content=$(echo "$content" | sed '/resolverRegistry\.register("product", productResolver)/d')
    
    # Remove customer/product query resolvers
    content=$(echo "$content" | sed -E '/\.dataFetcher\("products",/,/\)/d')
    content=$(echo "$content" | sed -E '/\.dataFetcher\("product",/,/\)/d')
    content=$(echo "$content" | sed -E '/\.dataFetcher\("customers",/,/\)/d')
    content=$(echo "$content" | sed -E '/\.dataFetcher\("customer",/,/\)/d')
    
    # Remove customer/product mutation resolvers
    content=$(echo "$content" | sed -E '/\.dataFetcher\("createProduct",/,/\)/d')
    content=$(echo "$content" | sed -E '/\.dataFetcher\("updateProduct",/,/\)/d')
    content=$(echo "$content" | sed -E '/\.dataFetcher\("deleteProduct",/,/\)/d')
    content=$(echo "$content" | sed -E '/\.dataFetcher\("createCustomer",/,/\)/d')
    content=$(echo "$content" | sed -E '/\.dataFetcher\("updateCustomer",/,/\)/d')
    content=$(echo "$content" | sed -E '/\.dataFetcher\("deleteCustomer",/,/\)/d')
    
    # Remove customer/product subscription resolvers
    content=$(echo "$content" | sed -E '/\.dataFetcher\("productUpdated",/,/\)/d')
    content=$(echo "$content" | sed -E '/\.dataFetcher\("customerUpdated",/,/\)/d')
    
    # Remove customer/product type resolver methods (multi-line using perl)
    if command -v perl >/dev/null 2>&1; then
        content=$(echo "$content" | perl -0pe 's/\s*override fun registerCustomerTypeResolvers[^}]*\}\n\n?//gs')
        content=$(echo "$content" | perl -0pe 's/\s*override fun registerProductTypeResolvers[^}]*\}\n\n?//gs')
    else
        # Fallback to awk (less reliable for complex patterns)
        content=$(echo "$content" | awk '/override fun registerCustomerTypeResolvers/,/^    }/' | grep -v 'override fun registerCustomerTypeResolvers' | grep -v '^    }' || echo "$content")
        content=$(echo "$content" | awk '/override fun registerProductTypeResolvers/,/^    }/' | grep -v 'override fun registerProductTypeResolvers' | grep -v '^    }' || echo "$content")
    fi
    
    # Clean up extra blank lines
    content=$(echo "$content" | sed -E ':a;N;$!ba;s/\n{3,}/\n\n/g')
    
    if [[ "$content" != "$original_content" ]]; then
        echo "$content" > "$full_path"
        echo "modified"
        return 0
    fi
    
    echo "unchanged"
    return 1
}

# Clean Inputs.kt
clean_inputs() {
    local file_path="$1"
    local full_path="$PROJECT_ROOT/$file_path"
    
    if [[ ! -f "$full_path" ]]; then
        echo "not found"
        return 1
    fi
    
    local content
    content=$(cat "$full_path")
    local original_content="$content"
    
    # Remove customer/product input classes (multi-line)
    content=$(echo "$content" | sed -E '/^data class CustomerInput/,/^}/d')
    content=$(echo "$content" | sed -E '/^data class ProductInput/,/^}/d')
    
    # Clean up extra blank lines
    content=$(echo "$content" | sed -E ':a;N;$!ba;s/\n{3,}/\n\n/g')
    
    if [[ "$content" != "$original_content" ]]; then
        echo "$content" > "$full_path"
        echo "modified"
        return 0
    fi
    
    echo "unchanged"
    return 1
}

# Clean Services.kt
clean_services() {
    local file_path="$1"
    local full_path="$PROJECT_ROOT/$file_path"
    
    if [[ ! -f "$full_path" ]]; then
        echo "not found"
        return 1
    fi
    
    local content
    content=$(cat "$full_path")
    local original_content="$content"
    
    # Remove customer/product imports
    content=$(echo "$content" | sed '/^import io\.github\.salomax\.neotool\.example\.domain\.Customer$/d')
    content=$(echo "$content" | sed '/^import io\.github\.salomax\.neotool\.example\.domain\.Product$/d')
    content=$(echo "$content" | sed '/^import io\.github\.salomax\.neotool\.example\.repo\.CustomerRepository$/d')
    content=$(echo "$content" | sed '/^import io\.github\.salomax\.neotool\.example\.repo\.ProductRepository$/d')
    
    # Remove ProductService class (multi-line using perl)
    if command -v perl >/dev/null 2>&1; then
        content=$(echo "$content" | perl -0pe 's/@Singleton\s+open class ProductService[^}]*\}\n\n?//gs')
        content=$(echo "$content" | perl -0pe 's/@Singleton\s+open class CustomerService[^}]*\}\n\n?//gs')
    else
        # Fallback to awk
        content=$(echo "$content" | awk '/@Singleton.*open class ProductService/,/^}/' || echo "$content")
        content=$(echo "$content" | awk '/@Singleton.*open class CustomerService/,/^}/' || echo "$content")
    fi
    
    # Clean up extra blank lines
    content=$(echo "$content" | sed -E ':a;N;$!ba;s/\n{3,}/\n\n/g')
    
    if [[ "$content" != "$original_content" ]]; then
        echo "$content" > "$full_path"
        echo "modified"
        return 0
    fi
    
    echo "unchanged"
    return 1
}

# Clean Repositories.kt
clean_repositories() {
    local file_path="$1"
    local full_path="$PROJECT_ROOT/$file_path"
    
    if [[ ! -f "$full_path" ]]; then
        echo "not found"
        return 1
    fi
    
    local content
    content=$(cat "$full_path")
    local original_content="$content"
    
    # Remove customer/product imports
    content=$(echo "$content" | sed '/^import io\.github\.salomax\.neotool\.example\.entity\.CustomerEntity$/d')
    content=$(echo "$content" | sed '/^import io\.github\.salomax\.neotool\.example\.entity\.ProductEntity$/d')
    
    # Remove ProductRepository interface (multi-line)
    content=$(echo "$content" | sed -E '/^@Repository.*interface ProductRepository/,/^}/d')
    
    # Remove CustomerRepository interface (multi-line)
    content=$(echo "$content" | sed -E '/^@Repository.*interface CustomerRepository/,/^}/d')
    
    # Clean up extra blank lines
    content=$(echo "$content" | sed -E ':a;N;$!ba;s/\n{3,}/\n\n/g')
    
    if [[ "$content" != "$original_content" ]]; then
        echo "$content" > "$full_path"
        echo "modified"
        return 0
    fi
    
    echo "unchanged"
    return 1
}

# Clean examples page
clean_examples_page() {
    local file_path="$1"
    local full_path="$PROJECT_ROOT/$file_path"
    
    if [[ ! -f "$full_path" ]]; then
        echo "not found"
        return 1
    fi
    
    local content
    content=$(cat "$full_path")
    local original_content="$content"
    
    # Remove customer example object (multi-line pattern using perl)
    if command -v perl >/dev/null 2>&1; then
        content=$(echo "$content" | perl -0pe 's/\s*{\s*title: "Customer Management",[^}]*\}[^}]*\},\n?//gs')
        content=$(echo "$content" | perl -0pe 's/\s*{\s*title: "Product Catalog",[^}]*\}[^}]*\},\n?//gs')
    else
        # Fallback to sed (less reliable)
        content=$(echo "$content" | sed -E '/\s*{\s*title: "Customer Management",/,/},\n?/d')
        content=$(echo "$content" | sed -E '/\s*{\s*title: "Product Catalog",/,/},\n?/d')
    fi
    
    # Clean up trailing commas before closing brackets
    content=$(echo "$content" | sed -E 's/,(\s*\])/\1/g')
    
    if [[ "$content" != "$original_content" ]]; then
        echo "$content" > "$full_path"
        echo "modified"
        return 0
    fi
    
    echo "unchanged"
    return 1
}

# Main execution
main() {
    local dry_run=0
    local regenerate=0
    
    # Parse arguments
    for arg in "$@"; do
        case "$arg" in
            --dry-run)
                dry_run=1
                ;;
            --regenerate)
                regenerate=1
                ;;
        esac
    done
    
    log "\n🧹 Starting example cleanup process...\n" "$BRIGHT"
    
    if [[ $dry_run -eq 1 ]]; then
        log "🔍 DRY RUN MODE - No files will be modified\n" "$YELLOW"
    fi
    
    local deleted_count=0
    local modified_count=0
    local errors=()
    
    # Delete files
    log "🗑️  Deleting files..." "$BLUE"
    for file in "${files_to_delete[@]}"; do
        if [[ $dry_run -eq 1 ]]; then
            if [[ -e "$PROJECT_ROOT/$file" ]]; then
                log "   ✓ Would delete: $file" "$GREEN"
            fi
        else
            local result
            result=$(delete_path "$file" 2>&1 || echo "error")
            if [[ "$result" == "deleted" ]]; then
                ((deleted_count++))
                log "   ✓ Deleted: $file" "$GREEN"
            elif [[ "$result" != "not found" ]]; then
                errors+=("Failed to delete $file: $result")
                log_error "   ✗ Failed to delete: $file - $result"
            fi
        fi
    done
    
    # Delete directories
    log "\n📁 Deleting directories..." "$BLUE"
    for dir in "${directories_to_delete[@]}"; do
        if [[ $dry_run -eq 1 ]]; then
            if [[ -e "$PROJECT_ROOT/$dir" ]]; then
                log "   ✓ Would delete: $dir" "$GREEN"
            fi
        else
            local result
            result=$(delete_path "$dir" 2>&1 || echo "error")
            if [[ "$result" == "deleted" ]]; then
                ((deleted_count++))
                log "   ✓ Deleted: $dir" "$GREEN"
            elif [[ "$result" != "not found" ]]; then
                errors+=("Failed to delete $dir: $result")
                log_error "   ✗ Failed to delete: $dir - $result"
            fi
        fi
    done
    
    # Modify files
    log "\n✏️  Modifying files..." "$BLUE"
    for file in "${files_to_modify[@]}"; do
        if [[ $dry_run -eq 1 ]]; then
            if [[ -f "$PROJECT_ROOT/$file" ]]; then
                log "   ✓ Would modify: $file" "$GREEN"
            fi
        else
            local result=""
            
            if [[ "$file" == *"schema.graphqls" ]]; then
                result=$(clean_graphql_schema "$file" 2>&1 || echo "error")
            elif [[ "$file" == *"AppWiringFactory.kt" ]]; then
                result=$(clean_app_wiring_factory "$file" 2>&1 || echo "error")
            elif [[ "$file" == *"Inputs.kt" ]]; then
                result=$(clean_inputs "$file" 2>&1 || echo "error")
            elif [[ "$file" == *"Services.kt" ]]; then
                result=$(clean_services "$file" 2>&1 || echo "error")
            elif [[ "$file" == *"Repositories.kt" ]]; then
                result=$(clean_repositories "$file" 2>&1 || echo "error")
            elif [[ "$file" == *"examples/page.tsx" ]]; then
                result=$(clean_examples_page "$file" 2>&1 || echo "error")
            else
                result="unknown file type"
            fi
            
            if [[ "$result" == "modified" ]]; then
                ((modified_count++))
                log "   ✓ Modified: $file" "$GREEN"
            elif [[ "$result" != "not found" && "$result" != "unchanged" && "$result" != "error" ]]; then
                errors+=("Failed to modify $file: $result")
                log_error "   ✗ Failed to modify: $file - $result"
            fi
        fi
    done
    
    # Summary
    log "\n✅ Cleanup completed!\n" "$BRIGHT"
    log "📊 Summary:" "$BLUE"
    log "   • Files/directories deleted: $deleted_count" "$GREEN"
    log "   • Files modified: $modified_count" "$GREEN"
    
    if [[ ${#errors[@]} -gt 0 ]]; then
        log "   • Errors: ${#errors[@]}" "$RED"
        log "\n⚠️  Errors encountered:" "$YELLOW"
        for error in "${errors[@]}"; do
            log_error "   • $error"
        done
    fi
    
    if [[ $dry_run -eq 1 ]]; then
        log "\n💡 This was a dry run. Run without --dry-run to apply changes." "$YELLOW"
    else
        log "\n⚠️  Next steps:" "$YELLOW"
        log "   1. Review the changes with: git diff" "$YELLOW"
        log "   2. Check for any remaining customer/product references" "$YELLOW"
        log "   3. Regenerate GraphQL types:" "$YELLOW"
        log "      cd web && npm run codegen" "$YELLOW"
        log "   4. Regenerate supergraph schema:" "$YELLOW"
        log "      cd contracts/graphql && ./sync-schemas.sh" "$YELLOW"
        log "   5. Test the application builds:" "$YELLOW"
        log "      cd service/kotlin && ./gradlew build" "$YELLOW"
        log "      cd web && npm run build" "$YELLOW"
        log "   6. Commit the changes:" "$YELLOW"
        log '      git add . && git commit -m "Remove customer/product examples"' "$YELLOW"
        
        if [[ $regenerate -eq 1 ]]; then
            log "\n🔄 Regenerating artifacts..." "$BLUE"
            log "   Please run the regeneration commands manually:" "$YELLOW"
            log "   cd web && npm run codegen" "$YELLOW"
            log "   cd contracts/graphql && ./sync-schemas.sh" "$YELLOW"
        fi
    fi
    
    log ""
    
    if [[ ${#errors[@]} -gt 0 ]]; then
        exit 1
    fi
}

# Run the script
main "$@"

