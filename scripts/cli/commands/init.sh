#!/usr/bin/env bash

set -euo pipefail

# Init Command
# 
# Orchestrates rename-project and clean-examples

# Get script directory and project root
COMMAND_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLI_DIR="$(cd "$COMMAND_DIR/.." && pwd)"
SCRIPTS_DIR="$(cd "$CLI_DIR/.." && pwd)"
PROJECT_ROOT="$(cd "$SCRIPTS_DIR/.." && pwd)"

# Source shared utilities
# shellcheck source=../utils.sh
source "$CLI_DIR/utils.sh"

main() {
    log "\n🎯 Initializing project...\n" "$BRIGHT"
    
    # Step 1: Rename project
    log "Step 1: Renaming project..." "$BLUE"
    if "$COMMAND_DIR/rename-project.sh"; then
        log "✓ Project rename completed\n" "$GREEN"
    else
        log_error "✗ Project rename failed"
        exit 1
    fi
    
    # Step 2: Clean examples
    log "\nStep 2: Cleaning examples..." "$BLUE"
    log "Would you like to run clean-examples? (y/n) " "$YELLOW"
    read -r response
    
    if [[ "$response" =~ ^[Yy]$ ]]; then
        log "\nWould you like to run in dry-run mode first? (y/n) " "$YELLOW"
        read -r dry_run_response
        
        if [[ "$dry_run_response" =~ ^[Yy]$ ]]; then
            log "\nRunning clean-examples in dry-run mode..." "$BLUE"
            "$COMMAND_DIR/clean-examples.sh" --dry-run
            
            log "\nProceed with actual cleanup? (y/n) " "$YELLOW"
            read -r proceed_response
            
            if [[ "$proceed_response" =~ ^[Yy]$ ]]; then
                "$COMMAND_DIR/clean-examples.sh"
            else
                log "Skipped cleanup." "$YELLOW"
            fi
        else
            "$COMMAND_DIR/clean-examples.sh"
        fi
        
        if [[ $? -eq 0 ]]; then
            log "✓ Examples cleaned successfully\n" "$GREEN"
        else
            log_error "✗ Clean examples failed"
            exit 1
        fi
    else
        log "Skipped clean-examples.\n" "$YELLOW"
    fi
    
    log "\n✅ Project initialization completed!\n" "$BRIGHT"
}

# Run main function
main "$@"

