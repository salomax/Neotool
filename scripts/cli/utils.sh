#!/usr/bin/env bash

# Shared utilities for CLI commands
# 
# Provides common logging and utility functions

# Color codes for terminal output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
GRAY='\033[90m'
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

