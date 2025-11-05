#!/usr/bin/env bash

set -euo pipefail

# Rename Project Command
# 
# Wraps the rename-project.sh script

# Get script directory and project root
# Resolve the actual script path, following symlinks if needed
SCRIPT_PATH="${BASH_SOURCE[0]}"
if [[ -L "$SCRIPT_PATH" ]]; then
    # If it's a symlink, resolve it
    if command -v realpath >/dev/null 2>&1; then
        SCRIPT_PATH=$(realpath "$SCRIPT_PATH")
    else
        # Fallback for systems without realpath
        SCRIPT_PATH=$(readlink "$SCRIPT_PATH" || echo "$SCRIPT_PATH")
        if [[ "$SCRIPT_PATH" != /* ]]; then
            # Relative symlink, resolve relative to symlink's directory
            SCRIPT_PATH="$(cd "$(dirname "${BASH_SOURCE[0]}")" && cd "$(dirname "$SCRIPT_PATH")" && pwd)/$(basename "$SCRIPT_PATH")"
        fi
    fi
fi

COMMAND_DIR="$(cd "$(dirname "$SCRIPT_PATH")" && pwd)"
CLI_DIR="$(cd "$COMMAND_DIR/.." && pwd)"
SCRIPTS_DIR="$(cd "$CLI_DIR/.." && pwd)"
PROJECT_ROOT="$(cd "$SCRIPTS_DIR/.." && pwd)"

# Source shared utilities
# shellcheck source=../utils.sh
source "$CLI_DIR/utils.sh"

main() {
    local script_path="$SCRIPTS_DIR/rename-project.sh"
    
    # If script not found at calculated path, try to find it relative to project root
    if [[ ! -f "$script_path" ]]; then
        # Try to find project root by looking for project.config.json or package.json
        local current_dir="$PWD"
        local found_project_root=""
        while [[ "$current_dir" != "/" ]]; do
            if [[ -f "$current_dir/project.config.json" ]] || [[ -f "$current_dir/package.json" ]]; then
                found_project_root="$current_dir"
                break
            fi
            current_dir=$(dirname "$current_dir")
        done
        
        # If we found a project root, try the script there
        if [[ -n "$found_project_root" ]] && [[ -f "$found_project_root/scripts/rename-project.sh" ]]; then
            script_path="$found_project_root/scripts/rename-project.sh"
            log "Found rename-project.sh at: $script_path" "$YELLOW"
        else
            log_error "Error: rename-project.sh not found at $script_path"
            log_error "Searched in: $SCRIPTS_DIR"
            if [[ -n "$found_project_root" ]]; then
                log_error "Also searched in: $found_project_root/scripts"
            fi
            exit 1
        fi
    fi
    
    log "\n🚀 Running rename-project...\n" "$BRIGHT"
    "$script_path" "$@"
}

# Run main function
main "$@"

