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
    
    # Step 1: Setup project (rename from neotool to new name)
    log "Step 1: Setting up project (renaming from neotool)..." "$BLUE"
    log "Would you like to run setup in dry-run mode first? (y/n) " "$YELLOW"
    read -r dry_run_response
    
    if [[ "$dry_run_response" =~ ^[Yy]$ ]]; then
        log "\nRunning setup in dry-run mode..." "$BLUE"
        if "$COMMAND_DIR/setup.sh" --dry-run; then
            log "\nProceed with actual setup? (y/n) " "$YELLOW"
            read -r proceed_response
            
            if [[ "$proceed_response" =~ ^[Yy]$ ]]; then
                if "$COMMAND_DIR/setup.sh"; then
                    log "✓ Project setup completed\n" "$GREEN"
                else
                    log_error "✗ Project setup failed"
                    exit 1
                fi
            else
                log "Skipped setup." "$YELLOW"
            fi
        else
            log_error "✗ Setup dry-run failed"
            exit 1
        fi
    else
        if "$COMMAND_DIR/setup.sh"; then
            log "✓ Project setup completed\n" "$GREEN"
        else
            log_error "✗ Project setup failed"
            exit 1
        fi
    fi
    
    # Step 2: Clean neotool references (remove example code)
    log "\nStep 2: Cleaning example code..." "$BLUE"
    log "Would you like to run clean? (y/n) " "$YELLOW"
    read -r response
    
    if [[ "$response" =~ ^[Yy]$ ]]; then
        log "\nWould you like to run in dry-run mode first? (y/n) " "$YELLOW"
        read -r dry_run_response
        
        if [[ "$dry_run_response" =~ ^[Yy]$ ]]; then
            log "\nRunning clean in dry-run mode..." "$BLUE"
            "$COMMAND_DIR/clean.sh" --dry-run
            
            log "\nProceed with actual cleanup? (y/n) " "$YELLOW"
            read -r proceed_response
            
            if [[ "$proceed_response" =~ ^[Yy]$ ]]; then
                "$COMMAND_DIR/clean.sh"
            else
                log "Skipped cleanup." "$YELLOW"
            fi
        else
            "$COMMAND_DIR/clean.sh"
        fi
        
        if [[ $? -eq 0 ]]; then
            log "✓ Clean completed successfully\n" "$GREEN"
        else
            log_error "✗ Clean failed"
            exit 1
        fi
    else
        log "Skipped clean.\n" "$YELLOW"
    fi
    
    log "\n✅ Project initialization completed!\n" "$BRIGHT"
    log "Next steps:" "$CYAN"
    log "  1. Review the changes: git diff" "$CYAN"
    log "  2. Test your application to ensure everything works" "$CYAN"
    log "  3. Commit the changes" "$CYAN"
    log ""
}

# Run main function
main "$@"

