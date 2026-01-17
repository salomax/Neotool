#!/usr/bin/env bash

set -euo pipefail

# PgBouncer Command
# 
# Manages PgBouncer configuration and credential generation

# Get script directory and project root
COMMAND_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLI_DIR="$(cd "$COMMAND_DIR/.." && pwd)"
SCRIPTS_DIR="$(cd "$CLI_DIR/.." && pwd)"
PROJECT_ROOT="$(cd "$SCRIPTS_DIR/.." && pwd)"

# Source shared utilities
# shellcheck source=../utils.sh
source "$CLI_DIR/utils.sh"

# Default values
DEFAULT_USERLIST_TEMPLATE="${PROJECT_ROOT}/infra/pgbouncer/userlist.txt.template"
DEFAULT_USERLIST_FILE="${PROJECT_ROOT}/infra/pgbouncer/userlist.txt"
DEFAULT_ENV_FILE="${PROJECT_ROOT}/.env.local"

# Show help text
show_help() {
    cat << EOF
$(log "PgBouncer Management" "$BRIGHT")

Usage: $0 <subcommand> [options]

Subcommands:
  $(log "credential-md5" "$GREEN") <username> <password>
    Generate MD5 hash for PgBouncer userlist.txt format.
    Format: md5(password + username)
    
    Options:
      --output <file>        Append entry to userlist.txt file (default: infra/pgbouncer/userlist.txt)
      --template <file>       Generate from template file (default: infra/pgbouncer/userlist.txt.template)
      --from-env             Read credentials from .env.local file
      --verify <hash>        Verify that a hash matches the given password
      --quiet, -q            Minimal output (only print the hash line)

Environment variables:
  POSTGRES_USER              PostgreSQL username (used with --from-env)
  POSTGRES_PASSWORD           PostgreSQL password (used with --from-env)
  UNLEASH_USER                Unleash username (used with --from-env)
  UNLEASH_PASSWORD            Unleash password (used with --from-env)

Examples:
  $0 credential-md5 neotool neotool
  $0 credential-md5 neotool neotool --output infra/pgbouncer/userlist.txt
  $0 credential-md5 --from-env
  $0 credential-md5 --template infra/pgbouncer/userlist.txt.template
  $0 credential-md5 neotool neotool --verify md59cc94cc51f04f368a8a45a4f6ae30822
EOF
}

# Generate MD5 hash for PgBouncer
# Format: md5(password + username)
generate_md5_hash() {
    local password="$1"
    local username="$2"
    
    # Check for Python 3 (preferred method)
    if command -v python3 >/dev/null 2>&1; then
        python3 -c "import hashlib; print(hashlib.md5(b'${password}${username}').hexdigest())"
    # Fallback to OpenSSL
    elif command -v openssl >/dev/null 2>&1; then
        echo -n "${password}${username}" | openssl dgst -md5 | awk '{print $2}'
    # Fallback to md5sum (if available)
    elif command -v md5sum >/dev/null 2>&1; then
        echo -n "${password}${username}" | md5sum | awk '{print $1}'
    else
        log_error "Error: No MD5 tool available (python3, openssl, or md5sum required)"
        return 1
    fi
}

# Generate userlist entry
generate_userlist_entry() {
    local username="$1"
    local password="$2"
    local hash
    
    hash=$(generate_md5_hash "$password" "$username")
    if [[ $? -ne 0 ]]; then
        return 1
    fi
    
    echo "\"${username}\" \"md5${hash}\""
}

# Verify hash matches password
verify_hash() {
    local username="$1"
    local password="$2"
    local expected_hash="$3"
    
    local computed_hash
    computed_hash=$(generate_md5_hash "$password" "$username")
    if [[ $? -ne 0 ]]; then
        return 1
    fi
    
    # Remove 'md5' prefix if present
    expected_hash="${expected_hash#md5}"
    computed_hash="${computed_hash#md5}"
    
    if [[ "$expected_hash" == "$computed_hash" ]]; then
        return 0
    else
        return 1
    fi
}

# Read credentials from .env.local
read_env_credentials() {
    local env_file="${1:-$DEFAULT_ENV_FILE}"
    
    if [[ ! -f "$env_file" ]]; then
        log_error "Error: Environment file not found: ${env_file}"
        return 1
    fi
    
    # Source the env file (handle comments and empty lines)
    set -a
    # shellcheck source=/dev/null
    source <(grep -v '^#' "$env_file" | grep -v '^$' || true)
    set +a
    
    # Extract credentials
    local postgres_user="${POSTGRES_USER:-neotool}"
    local postgres_password="${POSTGRES_PASSWORD:-neotool}"
    local unleash_user="${UNLEASH_USER:-unleash_app}"
    local unleash_password="${UNLEASH_PASSWORD:-unleash_password}"
    
    # Return via global variables (bash limitation)
    ENV_POSTGRES_USER="$postgres_user"
    ENV_POSTGRES_PASSWORD="$postgres_password"
    ENV_UNLEASH_USER="$unleash_user"
    ENV_UNLEASH_PASSWORD="$unleash_password"
}

# Generate from template
generate_from_template() {
    local template_file="$1"
    local output_file="$2"
    local quiet="${3:-false}"
    
    if [[ ! -f "$template_file" ]]; then
        log_error "Error: Template file not found: ${template_file}"
        return 1
    fi
    
    # Read .env.local for credentials
    if [[ ! -f "$DEFAULT_ENV_FILE" ]]; then
        log_error "Error: .env.local file not found: ${DEFAULT_ENV_FILE}"
        log_error "Required to read credentials for template generation"
        return 1
    fi
    
    read_env_credentials "$DEFAULT_ENV_FILE"
    
    # Generate entries for each user in template
    local temp_output
    temp_output=$(mktemp)
    
    # Process template line by line
    while IFS= read -r line || [[ -n "$line" ]]; do
        # Skip comments and empty lines
        if [[ "$line" =~ ^[[:space:]]*# ]] || [[ -z "${line// }" ]]; then
            echo "$line" >> "$temp_output"
            continue
        fi
        
        # Check if line contains a placeholder
        if [[ "$line" =~ PLACEHOLDER_FOR_NEOTOOL ]]; then
            local entry
            entry=$(generate_userlist_entry "$ENV_POSTGRES_USER" "$ENV_POSTGRES_PASSWORD")
            echo "$entry" >> "$temp_output"
        elif [[ "$line" =~ PLACEHOLDER_FOR_UNLEASH ]]; then
            local entry
            entry=$(generate_userlist_entry "$ENV_UNLEASH_USER" "$ENV_UNLEASH_PASSWORD")
            echo "$entry" >> "$temp_output"
        else
            # Keep original line
            echo "$line" >> "$temp_output"
        fi
    done < "$template_file"
    
    # Write to output file
    if [[ "$output_file" == "-" ]]; then
        cat "$temp_output"
    else
        cp "$temp_output" "$output_file"
        if [[ "$quiet" != "true" ]]; then
            log "Generated userlist.txt: ${output_file}\n" "$GREEN"
        fi
    fi
    
    rm -f "$temp_output"
}

# Execute credential-md5 command
execute_credential_md5() {
    local username=""
    local password=""
    local output_file=""
    local template_file=""
    local from_env=false
    local verify_hash=""
    local quiet=false
    
    # Parse arguments
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --output)
                if [[ $# -lt 2 ]]; then
                    log_error "Error: --output requires a value"
                    exit 1
                fi
                output_file="$2"
                shift 2
                ;;
            --template)
                if [[ $# -lt 2 ]]; then
                    log_error "Error: --template requires a value"
                    exit 1
                fi
                template_file="$2"
                shift 2
                ;;
            --from-env)
                from_env=true
                shift
                ;;
            --verify)
                if [[ $# -lt 2 ]]; then
                    log_error "Error: --verify requires a hash value"
                    exit 1
                fi
                verify_hash="$2"
                shift 2
                ;;
            --quiet|-q)
                quiet=true
                shift
                ;;
            --help|-h)
                show_help
                exit 0
                ;;
            *)
                if [[ -z "$username" ]]; then
                    username="$1"
                elif [[ -z "$password" ]]; then
                    password="$1"
                else
                    log_error "Error: Unknown option: $1"
                    echo ""
                    show_help
                    exit 1
                fi
                shift
                ;;
        esac
    done
    
    # Handle template generation
    if [[ -n "$template_file" ]]; then
        local out_file="${output_file:-$DEFAULT_USERLIST_FILE}"
        if ! generate_from_template "$template_file" "$out_file" "$quiet"; then
            exit 1
        fi
        exit 0
    fi
    
    # Handle --from-env
    if [[ "$from_env" == "true" ]]; then
        if ! read_env_credentials "$DEFAULT_ENV_FILE"; then
            exit 1
        fi
        
        # Generate entries for both users
        local postgres_entry
        local unleash_entry
        
        postgres_entry=$(generate_userlist_entry "$ENV_POSTGRES_USER" "$ENV_POSTGRES_PASSWORD")
        unleash_entry=$(generate_userlist_entry "$ENV_UNLEASH_USER" "$ENV_UNLEASH_PASSWORD")
        
        if [[ -n "$output_file" ]]; then
            # Write to file
            {
                echo "$postgres_entry"
                echo "$unleash_entry"
            } > "$output_file"
            if [[ "$quiet" != "true" ]]; then
                log "Generated userlist.txt: ${output_file}\n" "$GREEN"
            fi
        else
            # Print to stdout
            echo "$postgres_entry"
            echo "$unleash_entry"
        fi
        exit 0
    fi
    
    # Validate username and password
    if [[ -z "$username" ]] || [[ -z "$password" ]]; then
        log_error "Error: Username and password are required"
        echo ""
        show_help
        exit 1
    fi
    
    # Handle verification
    if [[ -n "$verify_hash" ]]; then
        if verify_hash "$username" "$password" "$verify_hash"; then
            if [[ "$quiet" != "true" ]]; then
                log "✅ Hash verification successful\n" "$GREEN"
            fi
            exit 0
        else
            log_error "❌ Hash verification failed"
            exit 1
        fi
    fi
    
    # Generate hash
    local entry
    entry=$(generate_userlist_entry "$username" "$password")
    
    if [[ $? -ne 0 ]]; then
        exit 1
    fi
    
    # Output result
    if [[ -n "$output_file" ]]; then
        # Append to file
        if [[ -f "$output_file" ]]; then
            # Check if entry already exists
            if grep -q "\"${username}\"" "$output_file" 2>/dev/null; then
                if [[ "$quiet" != "true" ]]; then
                    log "Warning: Entry for '${username}' already exists in ${output_file}\n" "$YELLOW"
                    log "Skipping append. Use --force to overwrite (not yet implemented)\n" "$YELLOW"
                fi
            else
                echo "$entry" >> "$output_file"
                if [[ "$quiet" != "true" ]]; then
                    log "Appended entry to: ${output_file}\n" "$GREEN"
                fi
            fi
        else
            # Create new file
            echo "$entry" > "$output_file"
            if [[ "$quiet" != "true" ]]; then
                log "Created userlist.txt: ${output_file}\n" "$GREEN"
            fi
        fi
    else
        # Print to stdout
        echo "$entry"
    fi
}

# Main function
main() {
    # Check for help flag first
    if [[ $# -eq 0 ]] || [[ "$1" == "--help" ]] || [[ "$1" == "-h" ]]; then
        show_help
        exit 0
    fi
    
    local subcommand="${1:-}"
    shift 2>/dev/null || true
    
    case "$subcommand" in
        credential-md5)
            execute_credential_md5 "$@"
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
