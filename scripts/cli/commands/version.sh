#!/usr/bin/env bash

set -euo pipefail

# Version/Verification Command
# 
# Checks system requirements (Node.js, Docker, JVM)

# Get script directory and project root
COMMAND_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLI_DIR="$(cd "$COMMAND_DIR/.." && pwd)"
SCRIPTS_DIR="$(cd "$CLI_DIR/.." && pwd)"
PROJECT_ROOT="$(cd "$SCRIPTS_DIR/.." && pwd)"

# Source shared utilities
# shellcheck source=../utils.sh
source "$CLI_DIR/utils.sh"

main() {
    log "\n🔍 Checking system requirements...\n" "$BRIGHT"
    
    local all_ok=1
    local missing_tools=()
    
    # Check Node.js
    log "Node.js:" "$BLUE"
    if command -v node >/dev/null 2>&1; then
        local node_version
        node_version=$(node --version 2>/dev/null || echo "unknown")
        log "  ✓ Installed: $node_version" "$GREEN"
    else
        log "  ✗ Not installed" "$RED"
        all_ok=0
        missing_tools+=("Node.js")
    fi
    
    # Check Docker
    log "\nDocker:" "$BLUE"
    if command -v docker >/dev/null 2>&1; then
        local docker_version
        docker_version=$(docker --version 2>/dev/null | sed 's/Docker version //' | sed 's/,.*//' || echo "unknown")
        log "  ✓ Installed: $docker_version" "$GREEN"
        
        # Check if Docker daemon is running
        if docker info >/dev/null 2>&1; then
            log "  ✓ Docker daemon is running" "$GREEN"
        else
            log "  ⚠ Docker daemon is not running" "$YELLOW"
        fi
    else
        log "  ✗ Not installed" "$RED"
        all_ok=0
        missing_tools+=("Docker")
    fi
    
    # Check JVM (Java)
    log "\nJVM (Java):" "$BLUE"
    if command -v java >/dev/null 2>&1; then
        local java_version
        java_version=$(java -version 2>&1 | head -n 1 | sed 's/.*"\(.*\)".*/\1/' || echo "unknown")
        log "  ✓ Installed: $java_version" "$GREEN"
        
        # Check JAVA_HOME
        if [[ -n "${JAVA_HOME:-}" ]]; then
            log "  ✓ JAVA_HOME: $JAVA_HOME" "$GREEN"
        else
            log "  ⚠ JAVA_HOME is not set" "$YELLOW"
        fi
    else
        log "  ✗ Not installed" "$RED"
        all_ok=0
        missing_tools+=("Java")
    fi
    
    # Summary
    echo ""
    if [[ $all_ok -eq 1 ]]; then
        log "✅ All required tools are installed!" "$GREEN"
        echo ""
        exit 0
    else
        log "⚠️  Some required tools are missing:" "$YELLOW"
        for tool in "${missing_tools[@]}"; do
            log "  • $tool" "$YELLOW"
        done
        echo ""
        log "Please install the missing tools to use all neotool features." "$YELLOW"
        echo ""
        exit 1
    fi
}

# Run main function
main "$@"

