#!/bin/bash
set -e

# Schema synchronization script for NeoTool GraphQL contracts
# This script syncs schemas FROM service modules TO contracts (source of truth)

echo "🔄 GraphQL Schema Synchronization"
echo "================================="

# Define paths (calculate from script location)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../../../" && pwd)"
CONTRACTS_DIR="$PROJECT_ROOT/contracts/graphql"
SERVICE_DIR="$PROJECT_ROOT/service"

# Function to find schema sources in service modules (skip build directories)
find_schema_sources() {
    local schema_sources=()
    
    # Find all schema.graphqls files in service directory, excluding build directories
    while IFS= read -r -d '' file; do
        # Skip bin/ and build/ directories
        if [[ "$file" != *"/bin/"* ]] && [[ "$file" != *"/build/"* ]]; then
            schema_sources+=("$file")
        fi
    done < <(find "$SERVICE_DIR" -name "schema.graphqls" -type f -print0)
    
    echo "${schema_sources[@]}"
}

# Function to get base schema path
get_base_schema_path() {
    echo "$SERVICE_DIR/kotlin/common/src/main/resources/graphql/base-schema.graphqls"
}

# Function to list available subgraphs
list_available_subgraphs() {
    local subgraphs=()
    
    # Find existing subgraphs
    for subgraph_dir in "$CONTRACTS_DIR/subgraphs"/*; do
        if [[ -d "$subgraph_dir" ]]; then
            local subgraph_name=$(basename "$subgraph_dir")
            subgraphs+=("$subgraph_name")
        fi
    done
    
    echo "${subgraphs[@]}"
}

# Function to display schema sources menu
show_schema_sources_menu() {
    local schema_sources=($(find_schema_sources))
    
    if [[ ${#schema_sources[@]} -eq 0 ]]; then
        echo "❌ No schema sources found in $SERVICE_DIR"
        echo "   Expected pattern: */src/main/resources/graphql/schema.graphqls"
        echo "   (excluding bin/ and build/ directories)"
        return 1
    fi
    
    echo ""
    echo "📋 Found ${#schema_sources[@]} schema source(s):"
    echo ""
    
    # Display schema sources without numbers (auto-sync all)
    for source in "${schema_sources[@]}"; do
        local rel_path="${source#$SERVICE_DIR/}"
        echo "  - $rel_path"
    done
    
    echo ""
}

# Function to sync schema from service to contract
sync_to_contract() {
    local source_schema="$1"
    local subgraph_name="$2"
    local target_schema="$CONTRACTS_DIR/subgraphs/$subgraph_name/schema.graphqls"
    local base_schema=$(get_base_schema_path)
    
    echo "📋 Syncing schema to contract..."
    echo "   Source: ${source_schema#$SERVICE_DIR/}"
    echo "   Target: subgraphs/$subgraph_name/schema.graphqls"
    
    # Create subgraph directory if it doesn't exist
    mkdir -p "$(dirname "$target_schema")"
    
    # Create backup if target exists
    if [[ -f "$target_schema" ]]; then
        cp "$target_schema" "$target_schema.backup"
        echo "💾 Created backup: $target_schema.backup"
    fi
    
    # Merge base schema + service schema
    if [[ -f "$base_schema" ]]; then
        # Extract base scalars and filter out any that already exist in service schema
        local base_scalars=$(grep "^scalar " "$base_schema" || true)
        local service_scalars=$(grep "^scalar " "$source_schema" 2>/dev/null || true)
        
        # Filter out scalars that are already defined in service schema
        local scalars_to_add=""
        while IFS= read -r scalar_line; do
            if [[ -n "$scalar_line" ]]; then
                local scalar_name=$(echo "$scalar_line" | awk '{print $2}')
                # Check if this scalar is not already in service schema
                if ! echo "$service_scalars" | grep -q "^scalar $scalar_name$"; then
                    scalars_to_add="${scalars_to_add}${scalar_line}"$'\n'
                fi
            fi
        done <<< "$base_scalars"
        
        {
            echo "# ============================================================================"
            echo "# Base Schema - Common Scalars"
            echo "# Auto-included from: service/kotlin/common/src/main/resources/graphql/base-schema.graphqls"
            echo "# ============================================================================"
            echo ""
            # Only include scalars that aren't already in service schema
            # Directives like @key are provided by Apollo Federation, so we don't include them
            if [[ -n "$scalars_to_add" ]]; then
                echo -n "$scalars_to_add"
                echo ""
            fi
            echo "# ============================================================================"
            echo "# Service Schema - $subgraph_name"
            echo "# ============================================================================"
            echo ""
            cat "$source_schema"
        } > "$target_schema"
        echo "✅ Schema synchronized (with base scalars) to contract: $subgraph_name"
    else
        # Fallback: just copy if base schema not found
        echo "⚠️  Base schema not found at: $base_schema"
        echo "   Syncing without base schema (this may cause rover composition errors)"
        cp "$source_schema" "$target_schema"
        echo "✅ Schema synchronized to contract: $subgraph_name"
    fi
}

# Function to auto-detect subgraph name from schema source path
auto_detect_subgraph_name() {
    local source_path="$1"
    local rel_path="${source_path#$SERVICE_DIR/}"
    
    # Try to extract module name from path pattern: {language}/{module}/src/main/resources/graphql/schema.graphqls
    if [[ "$rel_path" =~ ^([^/]+)/([^/]+)/.*/schema\.graphqls$ ]]; then
        local language="${BASH_REMATCH[1]}"
        local module="${BASH_REMATCH[2]}"
        
        # First try: {language}_{module} (e.g., kotlin_app)
        if [[ -d "$CONTRACTS_DIR/subgraphs/${language}_${module}" ]]; then
            echo "${language}_${module}"
            return 0
        fi
        
        # Second try: just {module} (e.g., app) - this is the common pattern
        if [[ -d "$CONTRACTS_DIR/subgraphs/${module}" ]]; then
            echo "${module}"
            return 0
        fi
        
        # If no existing subgraph found, suggest the module name
        echo "${module}"
        return 0
    fi
    
    return 1
}

# Function to sync a single schema with auto-detected subgraph
sync_single_schema() {
    local source_schema="$1"
    local subgraph_name=""
    
    # Auto-detect subgraph name from service path
    if ! subgraph_name=$(auto_detect_subgraph_name "$source_schema"); then
        echo "❌ Could not auto-detect subgraph name for: ${source_schema#$SERVICE_DIR/}"
        return 1
    fi
    
    # Validate subgraph name
    if [[ ! "$subgraph_name" =~ ^[a-zA-Z][a-zA-Z0-9_-]*$ ]]; then
        echo "❌ Invalid subgraph name detected: $subgraph_name"
        echo "   Must start with letter and contain only letters, numbers, hyphens, and underscores"
        return 1
    fi
    
    # Sync the schema
    sync_to_contract "$source_schema" "$subgraph_name"
    return $?
}

# Function to run interactive sync
interactive_sync() {
    local schema_sources=($(find_schema_sources))
    
    show_schema_sources_menu
    
    echo "🔄 Syncing all schemas with auto-detected subgraphs..."
    echo ""
    
    local sync_errors=0
    local sync_success=0
    
    for source in "${schema_sources[@]}"; do
        local rel_path="${source#$SERVICE_DIR/}"
        local detected_subgraph=""
        
        if detected_subgraph=$(auto_detect_subgraph_name "$source"); then
            echo "📋 Processing: $rel_path → subgraphs/$detected_subgraph/schema.graphqls"
            if sync_single_schema "$source"; then
                ((sync_success++))
            else
                ((sync_errors++))
            fi
            echo ""
        else
            echo "❌ Could not auto-detect subgraph for: $rel_path"
            ((sync_errors++))
            echo ""
        fi
    done
    
    echo "================================="
    if [[ $sync_errors -eq 0 ]]; then
        echo "🎉 All schemas synchronized successfully! ($sync_success schema(s))"
        return 0
    else
        echo "⚠️  Completed with errors: $sync_success successful, $sync_errors failed"
        return 1
    fi
}

# Function to validate schema consistency
validate_schemas() {
    echo "🔍 Validating schema consistency..."
    echo "   (Note: Contract schemas include base schema, so direct diff may show differences)"
    echo ""
    
    local schema_sources=($(find_schema_sources))
    local validation_errors=0
    local base_schema=$(get_base_schema_path)
    
    for source in "${schema_sources[@]}"; do
        local rel_path="${source#$SERVICE_DIR/}"
        
        # Try to determine subgraph name from path
        if [[ "$rel_path" =~ ^([^/]+)/([^/]+)/.*/schema\.graphqls$ ]]; then
            local language="${BASH_REMATCH[1]}"
            local module="${BASH_REMATCH[2]}"
            local subgraph_name="${language}_${module}"
            
            local contract_schema=""
            if [[ -f "$CONTRACTS_DIR/subgraphs/$subgraph_name/schema.graphqls" ]]; then
                contract_schema="$CONTRACTS_DIR/subgraphs/$subgraph_name/schema.graphqls"
            elif [[ -f "$CONTRACTS_DIR/subgraphs/$module/schema.graphqls" ]]; then
                contract_schema="$CONTRACTS_DIR/subgraphs/$module/schema.graphqls"
            fi
            
            if [[ -n "$contract_schema" ]]; then
                # Since contract schemas now include base schema, we do a simpler check:
                # Verify that the contract schema exists and contains a marker indicating it was synced
                # We check if the service schema's first non-comment line appears in the contract
                local service_first_line=$(grep -v '^#' "$source" | grep -v '^$' | head -n 1)
                local contract_content=$(cat "$contract_schema")
                
                if [[ -n "$service_first_line" ]] && echo "$contract_content" | grep -qF "$service_first_line"; then
                    echo "✅ Schema consistent: $rel_path"
                else
                    # Check if contract has been synced (contains base schema marker)
                    if echo "$contract_content" | grep -q "Base Schema - Common Types and Scalars"; then
                        echo "⚠️  Schema may be out of sync: $rel_path"
                        echo "   Service schema content not found in contract"
                        ((validation_errors++))
                    else
                        # Contract doesn't have base schema, might be old format
                        echo "⚠️  Contract schema missing base schema: $rel_path"
                        echo "   Run 'sync' to update with base schema"
                        ((validation_errors++))
                    fi
                fi
            else
                echo "⚠️  No contract found for: $rel_path"
                ((validation_errors++))
            fi
        fi
    done
    
    if [[ $validation_errors -eq 0 ]]; then
        echo ""
        echo "✅ All schemas are consistent"
        return 0
    else
        echo ""
        echo "❌ Found $validation_errors validation error(s)"
        echo "   Run 'sync' to fix these issues"
        return 1
    fi
}

# Function to generate supergraph schema
generate_supergraph() {
    echo "🚀 Generating supergraph schema..."
    
    cd "$CONTRACTS_DIR/supergraph"
    
    # Use the generate-schema.sh script from CLI commands
    local generate_script="$SCRIPT_DIR/generate-schema.sh"
    
    if [[ "${CI:-false}" == "true" ]] || [[ "${USE_DOCKER_ROVER:-false}" == "true" ]]; then
        USE_DOCKER_ROVER=true "$generate_script"
    else
        "$generate_script"
    fi
}

# Main execution
case "${1:-sync}" in
    "sync")
        interactive_sync
        ;;
    "validate")
        validate_schemas
        ;;
    "generate")
        generate_supergraph
        ;;
    "all")
        interactive_sync
        validate_schemas
        generate_supergraph
        echo "🎉 Full schema management completed!"
        ;;
    *)
        echo "Usage: $0 {sync|validate|generate|all}"
        echo ""
        echo "Commands:"
        echo "  sync     - Interactive sync from service modules to contracts"
        echo "  validate - Validate schema consistency between services and contracts"
        echo "  generate - Generate supergraph schema"
        echo "  all      - Run all operations"
        echo ""
        echo "Environment variables:"
        echo "  CI=true              - Use Docker for rover (CI environment)"
        echo "  USE_DOCKER_ROVER=true - Force Docker usage for rover"
        echo ""
        echo "Workflow:"
        echo "  1. Edit GraphQL schema in your service module"
        echo "  2. Run './neotool graphql sync' or '$0 sync'"
        echo "  3. Select the schema source (or ALL to sync all schemas)"
        echo "  4. Destination subgraph is automatically inferred from service name"
        echo "  5. Schema is copied from service → contract"
        echo ""
        echo "Schema Discovery:"
        echo "  Automatically discovers schema sources in service directory"
        echo "  Skips bin/ and build/ directories"
        echo "  Supports patterns: kotlin/app, kotlin/security, python/module_x, etc."
        exit 1
        ;;
esac
