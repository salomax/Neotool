#!/usr/bin/env bash

set -euo pipefail

# Vault Command
# 
# Manages HashiCorp Vault secrets for JWT keys

# Get script directory and project root
COMMAND_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLI_DIR="$(cd "$COMMAND_DIR/.." && pwd)"
SCRIPTS_DIR="$(cd "$CLI_DIR/.." && pwd)"
PROJECT_ROOT="$(cd "$SCRIPTS_DIR/.." && pwd)"

# Source shared utilities
# shellcheck source=../utils.sh
source "$CLI_DIR/utils.sh"

# Default values
DEFAULT_VAULT_ADDRESS="http://localhost:8200"
DEFAULT_VAULT_TOKEN="${VAULT_TOKEN:-myroot}"
DEFAULT_SECRET_PATH="secret/jwt/keys"
DEFAULT_KEY_BITS=4096
VAULT_CONTAINER_NAME="neotool-vault"

VAULT_ADDRESS="${VAULT_ADDRESS:-$DEFAULT_VAULT_ADDRESS}"
VAULT_TOKEN="${VAULT_TOKEN:-$DEFAULT_VAULT_TOKEN}"

# Show help text
show_help() {
    cat << EOF
$(log "Vault Management" "$BRIGHT")

Usage: $0 <subcommand> [options]

Subcommands:
  $(log "create-secret" "$GREEN") <key-name>
    Create a new JWT key pair in Vault with the specified key name.
    Generates a 4096-bit RSA key pair and stores it at secret/jwt/keys/<key-name>.
    
    Options:
      --key-bits <bits>     RSA key size in bits (default: 4096)
      --secret-path <path>   Vault secret path prefix (default: secret/jwt/keys)
      --vault-address <url> Vault server address (default: http://localhost:8200)
      --vault-token <token>  Vault authentication token (default: from VAULT_TOKEN env or 'myroot')
      --force                Overwrite existing secret if it exists

  $(log "backup" "$GREEN") [key-name]
    Backup JWT keys from Vault to local file (~/.vault-keys/backup.json).
    If key-name is provided, backs up only that key. Otherwise, backs up all keys.
    
    Options:
      --secret-path <path>   Vault secret path prefix (default: secret/jwt/keys)
      --vault-address <url> Vault server address (default: http://localhost:8200)
      --vault-token <token>  Vault authentication token (default: from VAULT_TOKEN env or 'myroot')
      --output <file>        Output file path (default: ~/.vault-keys/backup.json)

  $(log "restore" "$GREEN") [key-name]
    Restore JWT keys from local backup file to Vault.
    If key-name is provided, restores only that key. Otherwise, restores all keys from backup.
    
    Options:
      --secret-path <path>   Vault secret path prefix (default: secret/jwt/keys)
      --vault-address <url> Vault server address (default: http://localhost:8200)
      --vault-token <token>  Vault authentication token (default: from VAULT_TOKEN env or 'myroot')
      --input <file>          Input backup file path (default: ~/.vault-keys/backup.json)
      --force                 Overwrite existing keys in Vault

Environment variables:
  VAULT_ADDRESS              Vault server address
  VAULT_TOKEN                Vault authentication token

Examples:
  $0 create-secret kid-1
  $0 create-secret kid-2 --key-bits 2048
  $0 backup kid-1
  $0 backup                    # Backup all keys
  $0 restore kid-1
  $0 restore                  # Restore all keys
EOF
}

# Check if Vault is accessible
check_vault_connection() {
    local vault_addr="$1"
    local vault_token="$2"
    
    if ! command -v vault >/dev/null 2>&1 && ! docker ps --format '{{.Names}}' 2>/dev/null | grep -q "^${VAULT_CONTAINER_NAME}$"; then
        log_error "Error: Vault CLI not found and Vault container is not running"
        log_error "Options:"
        log_error "  1. Install Vault CLI: https://developer.hashicorp.com/vault/downloads"
        log_error "  2. Start Vault container: docker-compose -f infra/docker/docker-compose.local.yml --profile secrets up -d vault"
        return 1
    fi
    
    # Try to check Vault status
    if command -v vault >/dev/null 2>&1; then
        # Use local Vault CLI
        if ! VAULT_ADDR="$vault_addr" VAULT_TOKEN="$vault_token" vault status >/dev/null 2>&1; then
            log_error "Error: Cannot connect to Vault at ${vault_addr}"
            log_error "Make sure Vault is running and accessible"
            return 1
        fi
    elif docker ps --format '{{.Names}}' 2>/dev/null | grep -q "^${VAULT_CONTAINER_NAME}$"; then
        # Use Docker exec - need to pass token for authentication check
        if ! docker exec -e VAULT_ADDR="$vault_addr" -e VAULT_TOKEN="$vault_token" "$VAULT_CONTAINER_NAME" vault status >/dev/null 2>&1; then
            log_error "Error: Vault container is running but not responding"
            log_error "Check your VAULT_TOKEN environment variable or use --vault-token"
            return 1
        fi
    else
        log_error "Error: Cannot access Vault"
        return 1
    fi
    
    return 0
}

# Execute Vault command (via CLI or Docker)
execute_vault_command() {
    local command="$1"
    local vault_addr="$2"
    local vault_token="$3"
    
    if command -v vault >/dev/null 2>&1; then
        # Use local Vault CLI
        VAULT_ADDR="$vault_addr" VAULT_TOKEN="$vault_token" eval "$command"
    elif docker ps --format '{{.Names}}' 2>/dev/null | grep -q "^${VAULT_CONTAINER_NAME}$"; then
        # Use Docker exec - need to set env vars in container
        docker exec -e VAULT_ADDR="$vault_addr" -e VAULT_TOKEN="$vault_token" "$VAULT_CONTAINER_NAME" sh -c "$command"
    else
        log_error "Error: Cannot execute Vault command"
        return 1
    fi
}

# Store secret in Vault using HTTP API (more reliable for multiline values)
store_secret_via_api() {
    local secret_path="$1"
    local private_key="$2"
    local public_key="$3"
    local vault_addr="$4"
    local vault_token="$5"
    
    # Convert path to API path (secret/jwt/keys/kid-1 -> secret/data/jwt/keys/kid-1 for KV v2)
    local api_path="${secret_path}"
    # If path doesn't start with secret/data, assume it's a KV v2 path and convert
    if [[ ! "$api_path" =~ ^secret/data/ ]]; then
        # Replace first 'secret/' with 'secret/data/'
        if [[ "$api_path" =~ ^secret/ ]]; then
            api_path="${api_path/#secret\//secret\/data\/}"
        else
            # If path doesn't start with secret/, add it
            api_path="secret/data/${api_path}"
        fi
    fi
    
    # Ensure vault_addr doesn't have trailing slash
    local clean_vault_addr="${vault_addr%/}"
    local api_url="${clean_vault_addr}/v1/${api_path}"
    
    # Create JSON payload using jq if available, otherwise use manual escaping
    local temp_json
    temp_json=$(mktemp)
    
    if command -v jq >/dev/null 2>&1; then
        # Use jq for proper JSON encoding
        jq -n \
            --arg private "$private_key" \
            --arg public "$public_key" \
            '{data: {private: $private, public: $public}}' > "$temp_json"
    else
        # Manual JSON creation with basic escaping
        # This is a fallback if jq is not available
        local private_escaped
        private_escaped=$(printf '%s' "$private_key" | sed 's/\\/\\\\/g; s/"/\\"/g; s/$/\\n/' | tr -d '\n' | sed 's/\\n$//')
        local public_escaped
        public_escaped=$(printf '%s' "$public_key" | sed 's/\\/\\\\/g; s/"/\\"/g; s/$/\\n/' | tr -d '\n' | sed 's/\\n$//')
        
        cat > "$temp_json" << EOF
{
  "data": {
    "private": "${private_escaped}",
    "public": "${public_escaped}"
  }
}
EOF
    fi
    
    # Use curl to make API call
    local http_code
    local response
    response=$(curl -s -w "\n%{http_code}" \
        --header "X-Vault-Token: ${vault_token}" \
        --header "Content-Type: application/json" \
        --request POST \
        --data @"${temp_json}" \
        "${api_url}" 2>&1)
    
    http_code=$(echo "$response" | tail -n1)
    local exit_code=$?
    rm -f "$temp_json"
    
    if [[ $exit_code -ne 0 ]] || [[ "$http_code" != "200" ]]; then
        log_error "API call failed with HTTP code: ${http_code}"
        if [[ "$http_code" == "000" ]]; then
            log_error "Could not connect to Vault at ${vault_addr}"
        elif [[ "$http_code" == "404" ]]; then
            log_error "Secret path not found: ${api_path}"
            log_error "Make sure the KV v2 engine is enabled at 'secret' mount"
            log_error "Try: vault secrets enable -version=2 -path=secret kv"
        elif [[ "$http_code" == "403" ]]; then
            log_error "Permission denied. Check your Vault token has write access to ${api_path}"
        else
            # Show response body for debugging
            local response_body
            response_body=$(echo "$response" | head -n -1)
            if [[ -n "$response_body" ]]; then
                log_error "Response: $response_body"
            fi
        fi
        return 1
    fi
    
    return 0
}

# Generate RSA key pair
generate_key_pair() {
    local key_bits="$1"
    local temp_dir
    temp_dir=$(mktemp -d)
    local private_key_file="${temp_dir}/private.pem"
    local public_key_file="${temp_dir}/public.pem"
    
    # Generate private key
    if ! openssl genpkey -algorithm RSA -out "$private_key_file" -pkeyopt "rsa_keygen_bits:${key_bits}" 2>/dev/null; then
        log_error "Error: Failed to generate private key"
        log_error "Make sure OpenSSL is installed"
        rm -rf "$temp_dir"
        return 1
    fi
    
    # Extract public key
    if ! openssl rsa -pubout -in "$private_key_file" -out "$public_key_file" 2>/dev/null; then
        log_error "Error: Failed to extract public key"
        rm -rf "$temp_dir"
        return 1
    fi
    
    # Read keys
    local private_key
    private_key=$(cat "$private_key_file")
    local public_key
    public_key=$(cat "$public_key_file")
    
    # Clean up
    rm -rf "$temp_dir"
    
    # Return keys via global variables (bash limitation)
    GENERATED_PRIVATE_KEY="$private_key"
    GENERATED_PUBLIC_KEY="$public_key"
    
    return 0
}

# Check if secret exists
secret_exists() {
    local secret_path="$1"
    local vault_addr="$2"
    local vault_token="$3"
    
    local output
    output=$(execute_vault_command "vault kv get ${secret_path} 2>&1" "$vault_addr" "$vault_token" || true)
    
    # Check for common "not found" messages
    if echo "$output" | grep -qi "No value found\|secret not found\|no secret exists"; then
        return 1
    fi
    
    # If we got output that doesn't indicate an error, assume it exists
    # Check for common error patterns
    if echo "$output" | grep -qi "error\|failed\|permission denied"; then
        # Might be an error, but if it's not a "not found" error, assume it exists
        # (to be safe, we'll assume it doesn't exist on errors)
        return 1
    fi
    
    # If we have some output, assume secret exists
    if [[ -n "$output" ]]; then
        return 0
    fi
    
    return 1
}

# Create secret in Vault
create_secret() {
    local key_name="$1"
    local key_bits="$2"
    local secret_path_prefix="$3"
    local vault_addr="$4"
    local vault_token="$5"
    local force="$6"
    
    local secret_path="${secret_path_prefix}/${key_name}"
    
    # Validate key name
    if [[ -z "$key_name" ]]; then
        log_error "Error: Key name is required"
        echo ""
        show_help
        exit 1
    fi
    
    # Check if key name is valid (alphanumeric, dash, underscore)
    if [[ ! "$key_name" =~ ^[a-zA-Z0-9_-]+$ ]]; then
        log_error "Error: Invalid key name '${key_name}'"
        log_error "Key name must contain only alphanumeric characters, dashes, and underscores"
        exit 1
    fi
    
    # Check Vault connection
    log "Checking Vault connection...\n" "$BLUE"
    if ! check_vault_connection "$vault_addr" "$vault_token"; then
        exit 1
    fi
    log "Vault connection successful\n" "$GREEN"
    
    # Check if secret already exists
    if secret_exists "$secret_path" "$vault_addr" "$vault_token"; then
        if [[ "$force" != "true" ]]; then
            log_error "Error: Secret already exists at ${secret_path}"
            log_error "Use --force to overwrite existing secret"
            exit 1
        else
            log "Warning: Secret already exists at ${secret_path}, overwriting...\n" "$YELLOW"
        fi
    fi
    
    # Generate key pair
    log "Generating ${key_bits}-bit RSA key pair...\n" "$BLUE"
    if ! generate_key_pair "$key_bits"; then
        exit 1
    fi
    log "Key pair generated successfully\n" "$GREEN"
    
    # Store in Vault
    log "Storing keys in Vault at ${secret_path}...\n" "$BLUE"
    
    # Determine if we should use CLI or API
    # Prefer CLI when available, use API as fallback
    local use_api=false
    local cli_failed=false
    
    # Check if we have vault CLI available
    if command -v vault >/dev/null 2>&1; then
        # Try CLI first with file input
        local temp_private
        temp_private=$(mktemp)
        local temp_public
        temp_public=$(mktemp)
        
        echo "$GENERATED_PRIVATE_KEY" > "$temp_private"
        echo "$GENERATED_PUBLIC_KEY" > "$temp_public"
        
        # Try vault kv put with file input
        local vault_cmd="vault kv put ${secret_path} private=@${temp_private} public=@${temp_public}"
        local output
        output=$(VAULT_ADDR="$vault_addr" VAULT_TOKEN="$vault_token" eval "$vault_cmd" 2>&1)
        local exit_code=$?
        
        rm -f "$temp_private" "$temp_public"
        
        if [[ $exit_code -eq 0 ]]; then
            # CLI succeeded
            use_api=false
        else
            cli_failed=true
            log "Vault CLI approach failed, trying HTTP API...\n" "$YELLOW"
            log "CLI error: $output\n" "$GRAY"
        fi
    elif docker ps --format '{{.Names}}' 2>/dev/null | grep -q "^${VAULT_CONTAINER_NAME}$"; then
        # Docker mode - try using docker exec with vault CLI first
        log "Using Docker exec with Vault CLI...\n" "$BLUE"
        
        # Create temporary files in container
        local temp_private
        temp_private=$(mktemp)
        local temp_public
        temp_public=$(mktemp)
        
        echo "$GENERATED_PRIVATE_KEY" > "$temp_private"
        echo "$GENERATED_PUBLIC_KEY" > "$temp_public"
        
        # Copy files to container
        docker cp "$temp_private" "${VAULT_CONTAINER_NAME}:/tmp/private.pem" >/dev/null 2>&1
        docker cp "$temp_public" "${VAULT_CONTAINER_NAME}:/tmp/public.pem" >/dev/null 2>&1
        
        # Use vault kv put in container
        local vault_cmd="vault kv put ${secret_path} private=@/tmp/private.pem public=@/tmp/public.pem"
        local output
        output=$(docker exec -e VAULT_ADDR="$vault_addr" -e VAULT_TOKEN="$vault_token" "$VAULT_CONTAINER_NAME" sh -c "$vault_cmd" 2>&1)
        local exit_code=$?
        
        # Clean up container files
        docker exec "$VAULT_CONTAINER_NAME" sh -c "rm -f /tmp/private.pem /tmp/public.pem" >/dev/null 2>&1
        rm -f "$temp_private" "$temp_public"
        
        if [[ $exit_code -eq 0 ]]; then
            # Docker CLI succeeded
            use_api=false
        else
            cli_failed=true
            log "Docker CLI approach failed, trying HTTP API...\n" "$YELLOW"
            log "CLI error: $output\n" "$GRAY"
        fi
    else
        # No CLI available, must use API
        cli_failed=true
    fi
    
    # Store using API if CLI failed or not available
    if [[ "$cli_failed" == "true" ]]; then
        if ! command -v curl >/dev/null 2>&1; then
            log_error "Error: Neither Vault CLI nor curl is available"
            log_error "Please install Vault CLI or curl to use this command"
            exit 1
        fi
        
        if ! store_secret_via_api "$secret_path" "$GENERATED_PRIVATE_KEY" "$GENERATED_PUBLIC_KEY" "$vault_addr" "$vault_token"; then
            log_error "Error: Failed to store keys in Vault"
            exit 1
        fi
    fi
    
    log "Keys stored successfully in Vault!\n" "$GREEN"
    echo ""
    log "Secret Path: ${secret_path}" "$BRIGHT"
    log "Key Name: ${key_name}" "$BRIGHT"
    log "Key Size: ${key_bits} bits" "$BRIGHT"
    echo ""
    log "You can verify the secret with:" "$BLUE"
    log "  vault kv get ${secret_path}" "$GRAY"
    echo ""
    log "To use this key in your application, set:" "$BLUE"
    log "  export JWT_KEY_ID=${key_name}" "$GRAY"
}

# Backup keys from Vault to local file
backup_keys() {
    local key_name="$1"
    local secret_path_prefix="$2"
    local vault_addr="$3"
    local vault_token="$4"
    local output_file="$5"
    
    # Create backup directory if it doesn't exist
    local backup_dir
    backup_dir=$(dirname "$output_file")
    mkdir -p "$backup_dir"
    
    # Check Vault connection
    if ! check_vault_connection "$vault_addr" "$vault_token"; then
        exit 1
    fi
    
    log "Backing up keys from Vault..." "$BLUE"
    
    local temp_json
    temp_json=$(mktemp)
    
    if [[ -n "$key_name" ]]; then
        # Backup single key
        local secret_path="${secret_path_prefix}/${key_name}"
        
        if ! secret_exists "$secret_path" "$vault_addr" "$vault_token"; then
            log_error "Error: Secret not found at ${secret_path}"
            exit 1
        fi
        
        # Get keys from Vault
        local private_key
        private_key=$(execute_vault_command "vault kv get -field=private ${secret_path}" "$vault_addr" "$vault_token" 2>/dev/null || echo "")
        local public_key
        public_key=$(execute_vault_command "vault kv get -field=public ${secret_path}" "$vault_addr" "$vault_token" 2>/dev/null || echo "")
        
        if [[ -z "$private_key" ]] || [[ -z "$public_key" ]]; then
            log_error "Error: Failed to retrieve keys from Vault"
            exit 1
        fi
        
        # Create JSON backup
        if command -v jq >/dev/null 2>&1; then
            jq -n \
                --arg key_name "$key_name" \
                --arg private "$private_key" \
                --arg public "$public_key" \
                '{keys: [{name: $key_name, private: $private, public: $public}]}' > "$temp_json"
        else
            # Fallback without jq
            cat > "$temp_json" << EOF
{
  "keys": [
    {
      "name": "$key_name",
      "private": $(printf '%s' "$private_key" | sed 's/\\/\\\\/g; s/"/\\"/g' | awk '{printf "\"%s\"", $0}'),
      "public": $(printf '%s' "$public_key" | sed 's/\\/\\\\/g; s/"/\\"/g' | awk '{printf "\"%s\"", $0}')
    }
  ]
}
EOF
        fi
    else
        # Backup all keys - list all keys in the secret path
        log "Discovering all keys in ${secret_path_prefix}..." "$BLUE"
        
        local keys_list
        keys_list=$(execute_vault_command "vault kv list ${secret_path_prefix}" "$vault_addr" "$vault_token" 2>/dev/null || echo "")
        
        if [[ -z "$keys_list" ]] || echo "$keys_list" | grep -qi "no value\|not found"; then
            log_error "Error: No keys found at ${secret_path_prefix}"
            exit 1
        fi
        
        # Extract key names (skip header lines)
        local key_names
        key_names=$(echo "$keys_list" | grep -v "^Keys" | grep -v "^----" | grep -v "^$" | sed 's|/$||' | tr '\n' ' ')
        
        if [[ -z "$key_names" ]]; then
            log_error "Error: No keys found to backup"
            exit 1
        fi
        
        # Build JSON array
        local json_keys=""
        local first=true
        
        for key in $key_names; do
            local secret_path="${secret_path_prefix}/${key}"
            log "  Backing up key: ${key}..." "$GRAY"
            
            local private_key
            private_key=$(execute_vault_command "vault kv get -field=private ${secret_path}" "$vault_addr" "$vault_token" 2>/dev/null || echo "")
            local public_key
            public_key=$(execute_vault_command "vault kv get -field=public ${secret_path}" "$vault_addr" "$vault_token" 2>/dev/null || echo "")
            
            if [[ -z "$private_key" ]] || [[ -z "$public_key" ]]; then
                log_error "  Warning: Failed to retrieve key ${key}, skipping..."
                continue
            fi
            
            # Escape keys for JSON
            local private_escaped
            private_escaped=$(printf '%s' "$private_key" | sed 's/\\/\\\\/g; s/"/\\"/g; s/$/\\n/' | tr -d '\n' | sed 's/\\n$//')
            local public_escaped
            public_escaped=$(printf '%s' "$public_key" | sed 's/\\/\\\\/g; s/"/\\"/g; s/$/\\n/' | tr -d '\n' | sed 's/\\n$//')
            
            if [[ "$first" == "true" ]]; then
                json_keys="    {\"name\": \"${key}\", \"private\": \"${private_escaped}\", \"public\": \"${public_escaped}\"}"
                first=false
            else
                json_keys="${json_keys},\n    {\"name\": \"${key}\", \"private\": \"${private_escaped}\", \"public\": \"${public_escaped}\"}"
            fi
        done
        
        if [[ -z "$json_keys" ]]; then
            log_error "Error: No keys were successfully backed up"
            exit 1
        fi
        
        # Create JSON backup
        printf "{\n  \"keys\": [\n%s\n  ]\n}" "$json_keys" > "$temp_json"
    fi
    
    # Write to output file
    cp "$temp_json" "$output_file"
    rm -f "$temp_json"
    
    log "Backup completed successfully!" "$GREEN"
    log "Backup saved to: ${output_file}" "$BRIGHT"
}

# Restore keys from local file to Vault
restore_keys() {
    local key_name="$1"
    local secret_path_prefix="$2"
    local vault_addr="$3"
    local vault_token="$4"
    local input_file="$5"
    local force="$6"
    
    # Check if backup file exists
    if [[ ! -f "$input_file" ]]; then
        log_error "Error: Backup file not found: ${input_file}"
        log_error "Run 'vault backup' first to create a backup"
        exit 1
    fi
    
    # Check Vault connection
    if ! check_vault_connection "$vault_addr" "$vault_token"; then
        exit 1
    fi
    
    log "Restoring keys from backup..." "$BLUE"
    
    # Parse backup file
    local keys_to_restore
    
    if command -v jq >/dev/null 2>&1; then
        # Use jq to parse JSON
        if [[ -n "$key_name" ]]; then
            # Restore single key
            keys_to_restore=$(jq -r ".keys[] | select(.name == \"${key_name}\")" "$input_file")
            if [[ -z "$keys_to_restore" ]] || [[ "$keys_to_restore" == "null" ]]; then
                log_error "Error: Key '${key_name}' not found in backup file"
                exit 1
            fi
        else
            # Restore all keys
            keys_to_restore=$(jq -c ".keys[]" "$input_file")
        fi
    else
        # Fallback: simple grep/sed parsing (basic JSON parsing)
        log_error "Warning: jq not found, using basic JSON parsing"
        if [[ -n "$key_name" ]]; then
            # Try to extract single key (basic parsing)
            if ! grep -q "\"name\": \"${key_name}\"" "$input_file"; then
                log_error "Error: Key '${key_name}' not found in backup file"
                exit 1
            fi
            # Extract the key block (simplified)
            keys_to_restore=$(grep -A 3 "\"name\": \"${key_name}\"" "$input_file" | head -3)
        else
            # For all keys, we'll need to parse manually
            log_error "Error: jq is required for restoring all keys. Please install jq or restore keys individually."
            exit 1
        fi
    fi
    
    # Restore keys
    if command -v jq >/dev/null 2>&1; then
        if [[ -n "$key_name" ]]; then
            # Restore single key
            local name
            name=$(echo "$keys_to_restore" | jq -r '.name')
            local private
            private=$(echo "$keys_to_restore" | jq -r '.private')
            local public
            public=$(echo "$keys_to_restore" | jq -r '.public')
            
            local secret_path="${secret_path_prefix}/${name}"
            
            # Check if exists and force flag
            if secret_exists "$secret_path" "$vault_addr" "$vault_token" && [[ "$force" != "true" ]]; then
                log_error "Error: Key '${name}' already exists in Vault. Use --force to overwrite."
                exit 1
            fi
            
            log "  Restoring key: ${name}..." "$GRAY"
            store_secret_via_api "$secret_path" "$private" "$public" "$vault_addr" "$vault_token"
            log "  ✓ Restored ${name}" "$GREEN"
        else
            # Restore all keys - use process substitution to avoid subshell issues
            local restored_count=0
            local skipped_count=0
            
            while IFS= read -r key_json; do
                local name
                name=$(echo "$key_json" | jq -r '.name')
                local private
                private=$(echo "$key_json" | jq -r '.private')
                local public
                public=$(echo "$key_json" | jq -r '.public')
                
                local secret_path="${secret_path_prefix}/${name}"
                
                # Check if exists and force flag
                if secret_exists "$secret_path" "$vault_addr" "$vault_token" && [[ "$force" != "true" ]]; then
                    log "  ⚠ Skipping ${name} (already exists, use --force to overwrite)" "$YELLOW"
                    skipped_count=$((skipped_count + 1))
                    continue
                fi
                
                log "  Restoring key: ${name}..." "$GRAY"
                if store_secret_via_api "$secret_path" "$private" "$public" "$vault_addr" "$vault_token"; then
                    log "  ✓ Restored ${name}" "$GREEN"
                    restored_count=$((restored_count + 1))
                else
                    log_error "  ✗ Failed to restore ${name}"
                fi
            done < <(jq -c '.keys[]' "$input_file")
            
            log "Restored ${restored_count} key(s), skipped ${skipped_count} key(s)" "$BLUE"
        fi
    else
        # Fallback parsing (only works for single key)
        log_error "Error: jq is required for restore. Please install jq: brew install jq (macOS) or apt-get install jq (Linux)"
        exit 1
    fi
    
    log "Restore completed successfully!" "$GREEN"
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
        create-secret)
            local key_name=""
            local key_bits="$DEFAULT_KEY_BITS"
            local secret_path="$DEFAULT_SECRET_PATH"
            local vault_addr="$VAULT_ADDRESS"
            local vault_token="$VAULT_TOKEN"
            local force=false
            
            # Parse arguments
            while [[ $# -gt 0 ]]; do
                case "$1" in
                    --key-bits)
                        if [[ $# -lt 2 ]]; then
                            log_error "Error: --key-bits requires a value"
                            exit 1
                        fi
                        key_bits="$2"
                        shift 2
                        ;;
                    --secret-path)
                        if [[ $# -lt 2 ]]; then
                            log_error "Error: --secret-path requires a value"
                            exit 1
                        fi
                        secret_path="$2"
                        shift 2
                        ;;
                    --vault-address)
                        if [[ $# -lt 2 ]]; then
                            log_error "Error: --vault-address requires a value"
                            exit 1
                        fi
                        vault_addr="$2"
                        shift 2
                        ;;
                    --vault-token)
                        if [[ $# -lt 2 ]]; then
                            log_error "Error: --vault-token requires a value"
                            exit 1
                        fi
                        vault_token="$2"
                        shift 2
                        ;;
                    --force)
                        force=true
                        shift
                        ;;
                    --help|-h)
                        show_help
                        exit 0
                        ;;
                    *)
                        if [[ -z "$key_name" ]]; then
                            key_name="$1"
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
            
            if [[ -z "$key_name" ]]; then
                log_error "Error: Key name is required"
                echo ""
                show_help
                exit 1
            fi
            
            create_secret "$key_name" "$key_bits" "$secret_path" "$vault_addr" "$vault_token" "$force"
            ;;
        backup)
            local key_name=""
            local secret_path="$DEFAULT_SECRET_PATH"
            local vault_addr="$VAULT_ADDRESS"
            local vault_token="$VAULT_TOKEN"
            local output_file="${HOME}/.vault-keys/backup.json"
            
            # Parse arguments
            while [[ $# -gt 0 ]]; do
                case "$1" in
                    --secret-path)
                        if [[ $# -lt 2 ]]; then
                            log_error "Error: --secret-path requires a value"
                            exit 1
                        fi
                        secret_path="$2"
                        shift 2
                        ;;
                    --vault-address)
                        if [[ $# -lt 2 ]]; then
                            log_error "Error: --vault-address requires a value"
                            exit 1
                        fi
                        vault_addr="$2"
                        shift 2
                        ;;
                    --vault-token)
                        if [[ $# -lt 2 ]]; then
                            log_error "Error: --vault-token requires a value"
                            exit 1
                        fi
                        vault_token="$2"
                        shift 2
                        ;;
                    --output)
                        if [[ $# -lt 2 ]]; then
                            log_error "Error: --output requires a value"
                            exit 1
                        fi
                        output_file="$2"
                        shift 2
                        ;;
                    --help|-h)
                        show_help
                        exit 0
                        ;;
                    *)
                        if [[ -z "$key_name" ]]; then
                            key_name="$1"
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
            
            backup_keys "$key_name" "$secret_path" "$vault_addr" "$vault_token" "$output_file"
            ;;
        restore)
            local key_name=""
            local secret_path="$DEFAULT_SECRET_PATH"
            local vault_addr="$VAULT_ADDRESS"
            local vault_token="$VAULT_TOKEN"
            local input_file="${HOME}/.vault-keys/backup.json"
            local force=false
            
            # Parse arguments
            while [[ $# -gt 0 ]]; do
                case "$1" in
                    --secret-path)
                        if [[ $# -lt 2 ]]; then
                            log_error "Error: --secret-path requires a value"
                            exit 1
                        fi
                        secret_path="$2"
                        shift 2
                        ;;
                    --vault-address)
                        if [[ $# -lt 2 ]]; then
                            log_error "Error: --vault-address requires a value"
                            exit 1
                        fi
                        vault_addr="$2"
                        shift 2
                        ;;
                    --vault-token)
                        if [[ $# -lt 2 ]]; then
                            log_error "Error: --vault-token requires a value"
                            exit 1
                        fi
                        vault_token="$2"
                        shift 2
                        ;;
                    --input)
                        if [[ $# -lt 2 ]]; then
                            log_error "Error: --input requires a value"
                            exit 1
                        fi
                        input_file="$2"
                        shift 2
                        ;;
                    --force)
                        force=true
                        shift
                        ;;
                    --help|-h)
                        show_help
                        exit 0
                        ;;
                    *)
                        if [[ -z "$key_name" ]]; then
                            key_name="$1"
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
            
            restore_keys "$key_name" "$secret_path" "$vault_addr" "$vault_token" "$input_file" "$force"
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

