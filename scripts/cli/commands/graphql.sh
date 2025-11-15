#!/usr/bin/env bash

set -euo pipefail

# GraphQL Command
# 
# Manages GraphQL schema synchronization and supergraph generation

# Get script directory and project root
COMMAND_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLI_DIR="$(cd "$COMMAND_DIR/.." && pwd)"
SCRIPTS_DIR="$(cd "$CLI_DIR/.." && pwd)"
PROJECT_ROOT="$(cd "$SCRIPTS_DIR/.." && pwd)"

# Source shared utilities
# shellcheck source=../utils.sh
source "$CLI_DIR/utils.sh"

# Path to GraphQL scripts (now in CLI commands directory)
GRAPHQL_SCRIPTS_DIR="$COMMAND_DIR/graphql"
SYNC_SCRIPT="$GRAPHQL_SCRIPTS_DIR/sync-schemas.sh"
GENERATE_SCRIPT="$GRAPHQL_SCRIPTS_DIR/generate-schema.sh"

# Show help text
show_help() {
    cat << EOF
$(log "GraphQL Schema Management" "$BRIGHT")

Usage: $0 <subcommand> [options]

Subcommands:
  $(log "sync" "$GREEN")
    Interactive sync from service modules to contracts.
    Prompts to select schema source and target subgraph.

  $(log "validate" "$GREEN")
    Validate schema consistency between services and contracts.

  $(log "generate" "$GREEN")
    Generate supergraph schema from subgraph schemas.

  $(log "all" "$GREEN")
    Run sync, validate, and generate in sequence.

Options:
  --docker    Use Docker for rover (useful for CI/CD)
  --help      Show this help message

Environment variables:
  CI=true              - Use Docker for rover (CI environment)
  USE_DOCKER_ROVER=true - Force Docker usage for rover

Examples:
  $0 sync
  $0 validate
  $0 generate
  $0 generate --docker
  $0 all

For more information, see: scripts/cli/commands/graphql/sync-schemas.sh
EOF
}

# Execute sync command
execute_sync() {
    if [[ ! -f "$SYNC_SCRIPT" ]]; then
        log_error "Error: sync-schemas.sh not found at $SYNC_SCRIPT"
        exit 1
    fi
    
    if [[ ! -x "$SYNC_SCRIPT" ]]; then
        log_error "Error: sync-schemas.sh is not executable"
        exit 1
    fi
    
    "$SYNC_SCRIPT" sync
}

# Execute validate command
execute_validate() {
    if [[ ! -f "$SYNC_SCRIPT" ]]; then
        log_error "Error: sync-schemas.sh not found at $SYNC_SCRIPT"
        exit 1
    fi
    
    if [[ ! -x "$SYNC_SCRIPT" ]]; then
        log_error "Error: sync-schemas.sh is not executable"
        exit 1
    fi
    
    "$SYNC_SCRIPT" validate
}

# Execute generate command
execute_generate() {
    local use_docker=false
    
    # Check for --docker flag
    for arg in "$@"; do
        if [[ "$arg" == "--docker" ]]; then
            use_docker=true
            break
        fi
    done
    
    if [[ ! -f "$GENERATE_SCRIPT" ]]; then
        log_error "Error: generate-schema.sh not found at $GENERATE_SCRIPT"
        exit 1
    fi
    
    if [[ ! -x "$GENERATE_SCRIPT" ]]; then
        log_error "Error: generate-schema.sh is not executable"
        exit 1
    fi
    
    if [[ "$use_docker" == true ]]; then
        USE_DOCKER_ROVER=true "$GENERATE_SCRIPT"
    else
        "$GENERATE_SCRIPT"
    fi
}

# Execute all command
execute_all() {
    local use_docker=false
    
    # Check for --docker flag
    for arg in "$@"; do
        if [[ "$arg" == "--docker" ]]; then
            use_docker=true
            break
        fi
    done
    
    log "\n🔄 Running full GraphQL schema management workflow...\n" "$BRIGHT"
    
    # Sync
    log "Step 1/3: Syncing schemas..." "$BLUE"
    execute_sync
    echo ""
    
    # Validate
    log "Step 2/3: Validating schemas..." "$BLUE"
    if execute_validate; then
        log "✅ Validation passed\n" "$GREEN"
    else
        log "⚠️  Validation found issues (continuing anyway)\n" "$YELLOW"
    fi
    
    # Generate
    log "Step 3/3: Generating supergraph..." "$BLUE"
    if [[ "$use_docker" == true ]]; then
        execute_generate --docker
    else
        execute_generate
    fi
    
    log "\n🎉 Full GraphQL schema management completed!" "$BRIGHT"
}

# Main function
main() {
    local subcommand="${1:-}"
    
    # Handle help flags
    if [[ "$subcommand" == "--help" || "$subcommand" == "-h" || -z "$subcommand" ]]; then
        show_help
        exit 0
    fi
    
    # Shift to remove subcommand from arguments
    shift 2>/dev/null || true
    
    case "$subcommand" in
        sync)
            execute_sync
            ;;
        validate)
            execute_validate
            ;;
        generate)
            execute_generate "$@"
            ;;
        all)
            execute_all "$@"
            ;;
        *)
            log_error "Unknown subcommand: $subcommand"
            echo ""
            show_help
            exit 1
            ;;
    esac
}

# Run main function
main "$@"

