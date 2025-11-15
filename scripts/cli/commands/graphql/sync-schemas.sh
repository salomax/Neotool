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
    
    # Display schema sources with numbers
    for i in "${!schema_sources[@]}"; do
        local rel_path="${schema_sources[$i]#$SERVICE_DIR/}"
        echo "  [$((i+1))] $rel_path"
    done
    
    echo ""
}

# Function to sync schema from service to contract
sync_to_contract() {
    local source_schema="$1"
    local subgraph_name="$2"
    local target_schema="$CONTRACTS_DIR/subgraphs/$subgraph_name/schema.graphqls"
    
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
    
    # Copy schema
    cp "$source_schema" "$target_schema"
    echo "✅ Schema synchronized to contract: $subgraph_name"
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

# Function to get suggested subgraphs for a schema source
get_suggested_subgraphs() {
    local source_path="$1"
    local suggested_subgraphs=()
    local available_subgraphs=($(list_available_subgraphs))
    
    # Auto-detect the primary suggestion
    local primary_suggestion=""
    if primary_suggestion=$(auto_detect_subgraph_name "$source_path"); then
        # Add the primary suggestion first if it exists
        for subgraph in "${available_subgraphs[@]}"; do
            if [[ "$subgraph" == "$primary_suggestion" ]]; then
                suggested_subgraphs+=("$subgraph")
                break
            fi
        done
        
        # If primary suggestion doesn't exist yet, add it as a new option
        local exists=false
        for subgraph in "${available_subgraphs[@]}"; do
            if [[ "$subgraph" == "$primary_suggestion" ]]; then
                exists=true
                break
            fi
        done
        
        if [[ "$exists" == false ]]; then
            suggested_subgraphs+=("$primary_suggestion (new)")
        fi
    fi
    
    # Add other available subgraphs that don't match
    for subgraph in "${available_subgraphs[@]}"; do
        local already_added=false
        for suggested in "${suggested_subgraphs[@]}"; do
            if [[ "$suggested" == "$subgraph" ]] || [[ "$suggested" == "$subgraph (new)" ]]; then
                already_added=true
                break
            fi
        done
        if [[ "$already_added" == false ]]; then
            suggested_subgraphs+=("$subgraph")
        fi
    done
    
    # Return the array
    echo "${suggested_subgraphs[@]}"
}

# Function to run interactive sync
interactive_sync() {
    local schema_sources=($(find_schema_sources))
    
    show_schema_sources_menu
    
    # Get schema source selection
    echo -n "Select schema source (1-${#schema_sources[@]}): "
    read -r source_choice
    
    if ! [[ "$source_choice" =~ ^[0-9]+$ ]] || [[ "$source_choice" -lt 1 ]] || [[ "$source_choice" -gt ${#schema_sources[@]} ]]; then
        echo "❌ Invalid selection: $source_choice"
        return 1
    fi
    
    local selected_source="${schema_sources[$((source_choice-1))]}"
    
    # Get suggested subgraphs
    local suggested_subgraphs=($(get_suggested_subgraphs "$selected_source"))
    
    if [[ ${#suggested_subgraphs[@]} -eq 0 ]]; then
        echo "❌ No subgraph suggestions available"
        return 1
    fi
    
    # Display suggested subgraphs
    echo ""
    echo "📁 Suggested subgraphs:"
    echo ""
    for i in "${!suggested_subgraphs[@]}"; do
        echo "  [$((i+1))] ${suggested_subgraphs[$i]}"
    done
    echo ""
    
    # Get subgraph selection
    echo -n "Select subgraph (1-${#suggested_subgraphs[@]}): "
    read -r subgraph_choice
    
    if ! [[ "$subgraph_choice" =~ ^[0-9]+$ ]] || [[ "$subgraph_choice" -lt 1 ]] || [[ "$subgraph_choice" -gt ${#suggested_subgraphs[@]} ]]; then
        echo "❌ Invalid selection: $subgraph_choice"
        return 1
    fi
    
    local selected_subgraph="${suggested_subgraphs[$((subgraph_choice-1))]}"
    
    # Remove "(new)" suffix if present
    selected_subgraph="${selected_subgraph% (new)}"
    
    # Validate subgraph name
    if [[ ! "$selected_subgraph" =~ ^[a-zA-Z][a-zA-Z0-9_-]*$ ]]; then
        echo "❌ Invalid subgraph name: $selected_subgraph"
        echo "   Must start with letter and contain only letters, numbers, hyphens, and underscores"
        return 1
    fi
    
    # Confirm and sync
    echo ""
    echo "🔄 Ready to sync:"
    echo "   From: ${selected_source#$SERVICE_DIR/}"
    echo "   To:   subgraphs/$selected_subgraph/schema.graphqls"
    echo ""
    echo -n "Continue? (y/N): "
    read -r confirm
    
    if [[ "$confirm" =~ ^[Yy]$ ]]; then
        sync_to_contract "$selected_source" "$selected_subgraph"
        echo "🎉 Schema synchronization completed!"
    else
        echo "❌ Synchronization cancelled"
    fi
}

# Function to validate schema consistency
validate_schemas() {
    echo "🔍 Validating schema consistency..."
    
    local schema_sources=($(find_schema_sources))
    local validation_errors=0
    
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
                if ! diff -q "$source" "$contract_schema" > /dev/null; then
                    echo "⚠️  Schema mismatch: $rel_path"
                    echo "   Service: $source"
                    echo "   Contract: $contract_schema"
                    ((validation_errors++))
                else
                    echo "✅ Schema consistent: $rel_path"
                fi
            else
                echo "⚠️  No contract found for: $rel_path"
                ((validation_errors++))
            fi
        fi
    done
    
    if [[ $validation_errors -eq 0 ]]; then
        echo "✅ All schemas are consistent"
        return 0
    else
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
        echo "  3. Select the schema source and target subgraph"
        echo "  4. Schema is copied from service → contract"
        echo ""
        echo "Schema Discovery:"
        echo "  Automatically discovers schema sources in service directory"
        echo "  Skips bin/ and build/ directories"
        echo "  Supports patterns: kotlin/app, kotlin/security, python/module_x, etc."
        exit 1
        ;;
esac

