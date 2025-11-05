#!/usr/bin/env bash

set -euo pipefail

# Clean Examples Command
# 
# Wraps the clean-examples.sh script

# Get script directory and project root
COMMAND_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLI_DIR="$(cd "$COMMAND_DIR/.." && pwd)"
SCRIPTS_DIR="$(cd "$CLI_DIR/.." && pwd)"
PROJECT_ROOT="$(cd "$SCRIPTS_DIR/.." && pwd)"

# Source shared utilities
# shellcheck source=../utils.sh
source "$CLI_DIR/utils.sh"

main() {
    local script_path="$SCRIPTS_DIR/clean-examples.sh"
    
    if [[ ! -f "$script_path" ]]; then
        log_error "Error: clean-examples.sh not found at $script_path"
        exit 1
    fi
    
    log "\n🧹 Running clean-examples...\n" "$BRIGHT"
    "$script_path" "$@"
}

# Run main function
main "$@"

