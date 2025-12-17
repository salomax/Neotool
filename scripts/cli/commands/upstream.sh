#!/usr/bin/env bash

set -euo pipefail

# Upstream Command
# 
# Manages .gitattributes entries for files that should always use "ours" merge strategy
# when syncing with NeoTool upstream. This prevents merge conflicts for product-specific files.

# Get script directory and project root
COMMAND_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLI_DIR="$(cd "$COMMAND_DIR/.." && pwd)"
SCRIPTS_DIR="$(cd "$CLI_DIR/.." && pwd)"
PROJECT_ROOT="$(cd "$SCRIPTS_DIR/.." && pwd)"

# Source shared utilities
# shellcheck source=../utils.sh
source "$CLI_DIR/utils.sh"

# Find project root by searching for project.config.json or package.json
find_project_root() {
    local dir="$PWD"
    while [[ "$dir" != "/" ]]; do
        if [[ -f "$dir/project.config.json" ]] || [[ -f "$dir/package.json" ]]; then
            echo "$dir"
            return 0
        fi
        dir=$(dirname "$dir")
    done
    
    dir="$PROJECT_ROOT"
    while [[ "$dir" != "/" ]]; do
        if [[ -f "$dir/project.config.json" ]] || [[ -f "$dir/package.json" ]]; then
            echo "$dir"
            return 0
        fi
        dir=$(dirname "$dir")
    done
    
    return 1
}

PROJECT_ROOT=$(find_project_root)
if [[ -z "$PROJECT_ROOT" ]] || [[ ! -d "$PROJECT_ROOT" ]]; then
    log_error "Error: Could not find project root. Please ensure project.config.json or package.json exists in the project directory."
    exit 1
fi

GITATTRIBUTES_FILE="$PROJECT_ROOT/.gitattributes"
MERGE_STRATEGY_MARKER="# Upstream merge strategy - always keep our version"
MANAGED_BY_MARKER="# Managed by: ./neotool upstream command"

# Show help text
show_help() {
    cat << EOF
$(log "Upstream Merge Strategy Command" "$BRIGHT")

Usage: $0 <subcommand> [options]

Manages .gitattributes entries for files that should always use "ours" merge strategy
when syncing with NeoTool upstream. This prevents merge conflicts for product-specific files.

Subcommands:
  $(log "add" "$GREEN") <file|pattern>
    Add a file or pattern to use "ours" merge strategy.
    Examples:
      $0 add web/public/favicon.ico
      $0 add "web/src/app/product/**"
      $0 add project.config.json

  $(log "list" "$GREEN")
    List all files/patterns configured with "ours" merge strategy.

  $(log "remove" "$GREEN") <file|pattern>
    Remove a file or pattern from merge strategy configuration.
    Examples:
      $0 remove web/public/favicon.ico
      $0 remove "web/src/app/product/**"

Options:
  --help    Show this help message

Examples:
  $0 add web/public/favicon.ico
  $0 add "web/src/config/branding.ts"
  $0 list
  $0 remove web/public/favicon.ico
EOF
}

# Ensure .gitattributes exists with header
ensure_gitattributes() {
    if [[ ! -f "$GITATTRIBUTES_FILE" ]]; then
        log "Creating .gitattributes file..." "$BLUE"
        cat > "$GITATTRIBUTES_FILE" << EOF
$MERGE_STRATEGY_MARKER
$MANAGED_BY_MARKER

EOF
        log "✓ Created .gitattributes" "$GREEN"
    else
        # Check if header exists, if not add it
        if ! grep -q "$MERGE_STRATEGY_MARKER" "$GITATTRIBUTES_FILE" 2>/dev/null; then
            # Add header at the beginning
            {
                echo "$MERGE_STRATEGY_MARKER"
                echo "$MANAGED_BY_MARKER"
                echo ""
                cat "$GITATTRIBUTES_FILE"
            } > "$GITATTRIBUTES_FILE.tmp" && mv "$GITATTRIBUTES_FILE.tmp" "$GITATTRIBUTES_FILE"
        fi
    fi
}

# Normalize file path to relative from project root
normalize_path() {
    local path="$1"
    local abs_path
    
    # Convert to absolute path if relative
    if [[ "$path" != /* ]]; then
        abs_path="$PROJECT_ROOT/$path"
    else
        abs_path="$path"
    fi
    
    # Normalize path
    abs_path=$(cd "$(dirname "$abs_path")" 2>/dev/null && pwd)/$(basename "$abs_path" 2>/dev/null) || echo "$path"
    
    # Convert to relative from project root
    local rel_path="${abs_path#$PROJECT_ROOT/}"
    
    # If it's still absolute, return original
    if [[ "$rel_path" == "$abs_path" ]]; then
        echo "$path"
    else
        echo "$rel_path"
    fi
}

# Validate file path exists (for specific files, not patterns)
validate_file_path() {
    local path="$1"
    local full_path="$PROJECT_ROOT/$path"
    
    # If it's a pattern (contains * or **), skip validation
    if [[ "$path" == *"*"* ]]; then
        return 0
    fi
    
    # Check if file or directory exists
    if [[ ! -e "$full_path" ]]; then
        log_error "Error: File or directory not found: $path"
        log "Note: Patterns (with *) are allowed even if files don't exist yet" "$YELLOW"
        return 1
    fi
    
    return 0
}

# Check if entry already exists
entry_exists() {
    local pattern="$1"
    # Escape special regex characters but keep * and ** for glob matching
    local escaped_pattern=$(printf '%s\n' "$pattern" | sed 's/[[\.*^$()+?{|]/\\&/g' | sed 's/\\\*\\\*/\\*\\*/g' | sed 's/\\\*/\\*/g')
    
    if grep -q "^${escaped_pattern}[[:space:]]*merge=ours" "$GITATTRIBUTES_FILE" 2>/dev/null; then
        return 0
    fi
    return 1
}

# Add merge strategy entry
add_merge_strategy() {
    local file_pattern="$1"
    
    # Normalize path
    file_pattern=$(normalize_path "$file_pattern")
    
    # Validate file exists (for non-pattern paths)
    if ! validate_file_path "$file_pattern"; then
        return 1
    fi
    
    ensure_gitattributes
    
    # Check if entry already exists
    if entry_exists "$file_pattern"; then
        log "Entry already exists: $file_pattern" "$YELLOW"
        return 0
    fi
    
    # Simply append the entry to the file
    # The ensure_gitattributes function already creates the header with a blank line
    echo "${file_pattern} merge=ours" >> "$GITATTRIBUTES_FILE"
    
    log "✓ Added: $file_pattern merge=ours" "$GREEN"
}

# Remove merge strategy entry
remove_merge_strategy() {
    local file_pattern="$1"
    
    # Normalize path
    file_pattern=$(normalize_path "$file_pattern")
    
    if [[ ! -f "$GITATTRIBUTES_FILE" ]]; then
        log_error "Error: .gitattributes file not found"
        return 1
    fi
    
    # Escape pattern for sed
    local escaped_pattern=$(printf '%s\n' "$file_pattern" | sed 's/[[\.*^$()+?{|]/\\&/g' | sed 's/\\\*\\\*/\\*\\*/g' | sed 's/\\\*/\\*/g')
    
    # Check if entry exists
    if ! entry_exists "$file_pattern"; then
        log_error "Error: Entry not found: $file_pattern"
        return 1
    fi
    
    # Remove the line
    sed -i.bak "/^${escaped_pattern}[[:space:]]*merge=ours$/d" "$GITATTRIBUTES_FILE"
    rm -f "$GITATTRIBUTES_FILE.bak"
    
    log "✓ Removed: $file_pattern" "$GREEN"
}

# List all merge strategy entries
list_merge_strategies() {
    if [[ ! -f "$GITATTRIBUTES_FILE" ]]; then
        log "No .gitattributes file found. Run '$0 init' to create one." "$YELLOW"
        return 0
    fi
    
    # Extract merge=ours entries
    local entries=$(grep "merge=ours" "$GITATTRIBUTES_FILE" 2>/dev/null | grep -v "^#" | sed 's/[[:space:]]*merge=ours.*$//' | sed 's/^[[:space:]]*//' | sed '/^$/d')
    
    if [[ -z "$entries" ]]; then
        log "No merge strategy entries found." "$YELLOW"
        log "Run '$0 init' to add default files, or '$0 add <file>' to add specific files." "$YELLOW"
        return 0
    fi
    
    log "Files/patterns configured with 'ours' merge strategy:" "$BRIGHT"
    echo ""
    while IFS= read -r entry; do
        if [[ -n "$entry" ]]; then
            log "  • $entry" "$CYAN"
        fi
    done <<< "$entries"
}

# Main command handler
main() {
    local subcommand="${1:-}"
    
    case "$subcommand" in
        add)
            if [[ -z "${2:-}" ]]; then
                log_error "Error: File or pattern required"
                echo ""
                show_help
                exit 1
            fi
            add_merge_strategy "$2"
            ;;
        list)
            list_merge_strategies
            ;;
        remove)
            if [[ -z "${2:-}" ]]; then
                log_error "Error: File or pattern required"
                echo ""
                show_help
                exit 1
            fi
            remove_merge_strategy "$2"
            ;;
        help|--help|-h|"")
            show_help
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
