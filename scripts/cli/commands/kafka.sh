#!/usr/bin/env bash

set -euo pipefail

# Kafka Command
# 
# Manages Kafka topics and consumer groups

# Get script directory and project root
COMMAND_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLI_DIR="$(cd "$COMMAND_DIR/.." && pwd)"
SCRIPTS_DIR="$(cd "$CLI_DIR/.." && pwd)"
PROJECT_ROOT="$(cd "$SCRIPTS_DIR/.." && pwd)"

# Source shared utilities
# shellcheck source=../utils.sh
source "$CLI_DIR/utils.sh"

# Default values
DEFAULT_BOOTSTRAP_SERVER="localhost:9092"
KAFKA_CONTAINER_NAME="neotool-kafka"
USE_DOCKER=false
BOOTSTRAP_SERVER="$DEFAULT_BOOTSTRAP_SERVER"

# Show help text
show_help() {
    cat << EOF
$(log "Kafka Management" "$BRIGHT")

Usage: $0 [options] <command> [arguments]

Commands:
  $(log "--topic" "$GREEN")
    List all topics (excluding internal topics like __consumer_offsets).

  $(log "--topic <name>" "$GREEN")
    Describe a specific topic. Shows partitions, replication factor, leader, ISR, etc.

  $(log "--consumer-group" "$GREEN")
    List all consumer groups.

  $(log "--consumer-group <name>" "$GREEN")
    Describe a specific consumer group. Shows partition assignments, offsets, lag, etc.

  $(log "--reset-offsets" "$GREEN")
    Reset consumer group offsets for reprocessing. Requires --group and --topic.
    Reset strategies:
      --to-earliest          Reset to beginning of topic (reprocess all messages)
      --to-latest            Reset to end of topic (skip all existing messages)
      --to-offset <offset>   Reset to specific offset (per partition)
      --to-datetime <time>   Reset to messages after datetime (format: YYYY-MM-DDTHH:mm:ss)
    Options:
      --group <name>         Consumer group name (required)
      --topic <name>         Topic name (required)
      --execute              Actually perform the reset (default is dry-run)
      --force                Skip confirmation prompt (use with caution)
    $(log "WARNING" "$YELLOW"): This is a destructive operation that will cause message reprocessing.

Options:
  --bootstrap-server <server>  Override default bootstrap server (default: localhost:9092)
  --docker                     Force using Docker exec (useful when Kafka tools aren't installed locally)
  --help                       Show this help message

Environment variables:
  KAFKA_BOOTSTRAP_SERVER       Default bootstrap server (overrides default)

Examples:
  $0 --topic
  $0 --topic swapi.people.v1
  $0 --consumer-group
  $0 --consumer-group swapi-people-consumer-group
  $0 --reset-offsets --group swapi-people-consumer-group --topic swapi.people.v1 --to-earliest
  $0 --reset-offsets --group swapi-people-consumer-group --topic swapi.people.v1 --to-earliest --execute
  $0 --reset-offsets --group swapi-people-consumer-group --topic swapi.people.v1 --to-latest --execute --force
  $0 --reset-offsets --group swapi-people-consumer-group --topic swapi.people.v1 --to-datetime 2024-01-01T00:00:00 --execute
  $0 --topic --bootstrap-server kafka.example.com:9092
  $0 --topic --docker
EOF
}

# Check if Kafka container is running
check_kafka_container() {
    if docker ps --format '{{.Names}}' 2>/dev/null | grep -q "^${KAFKA_CONTAINER_NAME}$"; then
        return 0
    else
        return 1
    fi
}

# Check if Kafka tools are available locally
check_kafka_tools_local() {
    if command -v kafka-topics >/dev/null 2>&1 && command -v kafka-consumer-groups >/dev/null 2>&1; then
        return 0
    else
        return 1
    fi
}

# Determine execution method (Docker or local)
determine_execution_method() {
    if [[ "$USE_DOCKER" == true ]]; then
        if check_kafka_container; then
            echo "docker"
            return 0
        else
            log_error "Error: --docker flag specified but Kafka container '${KAFKA_CONTAINER_NAME}' is not running"
            log_error "Start Kafka with: docker-compose -f infra/docker/docker-compose.local.yml up -d kafka"
            exit 1
        fi
    fi
    
    # Auto-detect: prefer Docker if container is running, otherwise try local
    if check_kafka_container; then
        echo "docker"
        return 0
    elif check_kafka_tools_local; then
        echo "local"
        return 0
    else
        log_error "Error: Cannot find Kafka tools"
        log_error "Options:"
        log_error "  1. Start Kafka container: docker-compose -f infra/docker/docker-compose.local.yml up -d kafka"
        log_error "  2. Install Kafka CLI tools locally"
        log_error "  3. Use --docker flag if container is running"
        exit 1
    fi
}

# Execute Kafka command via Docker or locally
execute_kafka_command() {
    local command="$1"
    local execution_method="$2"
    
    if [[ "$execution_method" == "docker" ]]; then
        # Replace kafka command names with full paths in Docker container
        local docker_command="$command"
        docker_command="${docker_command//kafka-topics /\/opt\/kafka\/bin\/kafka-topics.sh }"
        docker_command="${docker_command//kafka-consumer-groups /\/opt\/kafka\/bin\/kafka-consumer-groups.sh }"
        docker exec "$KAFKA_CONTAINER_NAME" /bin/bash -c "$docker_command"
    else
        eval "$command"
    fi
}

# Filter internal topics (those starting with __)
filter_internal_topics() {
    grep -v '^__' || true
}

# Get bootstrap server from args or environment
get_bootstrap_server() {
    local server="${KAFKA_BOOTSTRAP_SERVER:-$DEFAULT_BOOTSTRAP_SERVER}"
    echo "$server"
}

# List all topics (excluding internals)
list_topics() {
    local execution_method="$1"
    local bootstrap_server="$2"
    
    log "Listing topics (excluding internal topics)...\n" "$BLUE"
    
    local command="kafka-topics --bootstrap-server ${bootstrap_server} --list"
    local output
    output=$(execute_kafka_command "$command" "$execution_method" 2>&1)
    local exit_code=$?
    
    if [[ $exit_code -ne 0 ]]; then
        log_error "Failed to list topics"
        log_error "Output: $output"
        log_error "Check that Kafka is accessible at ${bootstrap_server}"
        return 1
    fi
    
    # Filter internal topics and display
    echo "$output" | filter_internal_topics | sort
    
    # Count topics
    local count
    count=$(echo "$output" | filter_internal_topics | wc -l | tr -d ' ')
    echo ""
    log "Found ${count} topic(s)" "$GREEN"
}

# Describe a specific topic
describe_topic() {
    local topic_name="$1"
    local execution_method="$2"
    local bootstrap_server="$3"
    
    log "Describing topic: ${topic_name}\n" "$BLUE"
    
    local command="kafka-topics --bootstrap-server ${bootstrap_server} --describe --topic ${topic_name}"
    local output
    output=$(execute_kafka_command "$command" "$execution_method" 2>&1)
    local exit_code=$?
    
    if [[ $exit_code -ne 0 ]]; then
        log_error "Failed to describe topic: ${topic_name}"
        log_error "Output: $output"
        if echo "$output" | grep -q "does not exist"; then
            log_error "Topic '${topic_name}' does not exist"
        else
            log_error "Check that Kafka is accessible at ${bootstrap_server}"
        fi
        return 1
    fi
    
    echo "$output"
}

# List all consumer groups
list_consumer_groups() {
    local execution_method="$1"
    local bootstrap_server="$2"
    
    log "Listing consumer groups...\n" "$BLUE"
    
    local command="kafka-consumer-groups --bootstrap-server ${bootstrap_server} --list"
    local output
    output=$(execute_kafka_command "$command" "$execution_method" 2>&1)
    local exit_code=$?
    
    if [[ $exit_code -ne 0 ]]; then
        log_error "Failed to list consumer groups"
        log_error "Output: $output"
        log_error "Check that Kafka is accessible at ${bootstrap_server}"
        return 1
    fi
    
    if [[ -z "$output" ]]; then
        log "No consumer groups found" "$YELLOW"
    else
        echo "$output" | sort
        local count
        count=$(echo "$output" | grep -v '^$' | wc -l | tr -d ' ')
        echo ""
        log "Found ${count} consumer group(s)" "$GREEN"
    fi
}

# Describe a specific consumer group
describe_consumer_group() {
    local group_name="$1"
    local execution_method="$2"
    local bootstrap_server="$3"
    
    log "Describing consumer group: ${group_name}\n" "$BLUE"
    
    local command="kafka-consumer-groups --bootstrap-server ${bootstrap_server} --group ${group_name} --describe"
    local output
    output=$(execute_kafka_command "$command" "$execution_method" 2>&1)
    local exit_code=$?
    
    if [[ $exit_code -ne 0 ]]; then
        log_error "Failed to describe consumer group: ${group_name}"
        log_error "Output: $output"
        if echo "$output" | grep -q "does not exist\|not found"; then
            log_error "Consumer group '${group_name}' does not exist"
        else
            log_error "Check that Kafka is accessible at ${bootstrap_server}"
        fi
        return 1
    fi
    
    # Check if output is empty or only whitespace
    local output_trimmed
    output_trimmed=$(echo "$output" | xargs 2>/dev/null || echo "")
    
    # Check if output contains actual partition/offset data (not just headers)
    # Data rows have format: TOPIC PARTITION CURRENT-OFFSET LOG-END-OFFSET LAG
    local data_rows
    data_rows=$(echo "$output" | grep -E '^[^[:space:]]+[[:space:]]+[0-9]+[[:space:]]+[0-9]+[[:space:]]+[0-9]+' 2>/dev/null || echo "")
    
    # If output is empty or has no data rows, show helpful message
    # Use simple check: if output is empty after trimming, or if no data rows found
    if [[ -z "$output_trimmed" ]] || [[ -z "$data_rows" ]]; then
        echo ""
        log "Consumer group '${group_name}' exists but has no partition assignments or committed offsets." "$YELLOW"
        echo ""
        log "This typically means:" "$YELLOW"
        log "  - The consumer has never started" "$YELLOW"
        log "  - The consumer has never consumed any messages" "$YELLOW"
        log "  - The consumer group is inactive" "$YELLOW"
        echo ""
        log "To see offsets, the consumer must have consumed at least one message." "$YELLOW"
        echo ""
        log "Try:" "$YELLOW"
        log "  - Start the consumer application" "$BLUE"
        log "  - Check if the topic has messages: ./neotool kafka --topic swapi.people.v1" "$BLUE"
        echo ""
        return 0
    fi
    
    # Output has actual data, display it
    echo "$output"
}

# Reset consumer group offsets
reset_offsets() {
    local group_name="$1"
    local topic_name="$2"
    local reset_strategy="$3"
    local execution_method="$4"
    local bootstrap_server="$5"
    local execute_flag="$6"
    local force_flag="$7"
    
    log "Resetting offsets for consumer group: ${group_name}\n" "$BLUE"
    log "Topic: ${topic_name}\n" "$BLUE"
    log "Strategy: ${reset_strategy}\n" "$BLUE"
    
    # First, show current state
    log "\nCurrent consumer group state:\n" "$YELLOW"
    describe_consumer_group "$group_name" "$execution_method" "$bootstrap_server"
    
    # Build the reset command
    local reset_command="kafka-consumer-groups --bootstrap-server ${bootstrap_server} --group ${group_name} --topic ${topic_name} --reset-offsets ${reset_strategy}"
    
    if [[ "$execute_flag" != "true" ]]; then
        log "\n$(log "DRY RUN MODE" "$YELLOW"): Would execute:\n" "$YELLOW"
        log "  ${reset_command}\n" "$BLUE"
        log "\nTo actually perform the reset, add --execute flag\n" "$YELLOW"
        log "Preview of what would happen:\n" "$YELLOW"
        # Show what would happen (dry-run)
        local preview_command="${reset_command} --dry-run"
        local preview_output
        preview_output=$(execute_kafka_command "$preview_command" "$execution_method" 2>&1)
        local preview_exit_code=$?
        
        # Check for specific error about consumer group being active (even if exit code is 0)
        if echo "$preview_output" | grep -qi "can only be reset if the group.*is inactive\|current state is"; then
            log_error "Cannot reset offsets: Consumer group is active"
            echo ""
            log "The consumer group '${group_name}' is currently active (consumers are running)." "$YELLOW"
            log "You must stop all consumers before resetting offsets." "$YELLOW"
            echo ""
            log "To fix this:" "$BRIGHT"
            log "  1. Stop the consumer application" "$BLUE"
            log "  2. Verify the group is inactive: ./neotool kafka --consumer-group ${group_name}" "$BLUE"
            log "  3. Then retry the reset command" "$BLUE"
            echo ""
            log "Full error output:" "$YELLOW"
            echo "$preview_output"
            return 1
        elif [[ $preview_exit_code -ne 0 ]]; then
            log_error "Failed to preview reset operation"
            log_error "Output: $preview_output"
            return 1
        else
            echo "$preview_output"
        fi
        return 0
    fi
    
    # Confirmation prompt (unless --force)
    if [[ "$force_flag" != "true" ]]; then
        log "\n$(log "WARNING" "$YELLOW"): This will reset offsets and cause message reprocessing!\n" "$YELLOW"
        log "Consumer Group: ${group_name}\n" "$BRIGHT"
        log "Topic: ${topic_name}\n" "$BRIGHT"
        log "Strategy: ${reset_strategy}\n" "$BRIGHT"
        echo ""
        read -p "Are you sure you want to proceed? (yes/no): " confirmation
        if [[ "$confirmation" != "yes" ]]; then
            log "Reset cancelled" "$YELLOW"
            return 0
        fi
    fi
    
    # Execute the reset
    log "\nExecuting offset reset...\n" "$BLUE"
    local output
    output=$(execute_kafka_command "$reset_command --execute" "$execution_method" 2>&1)
    local exit_code=$?
    
    # Check for specific error about consumer group being active (even if exit code is 0)
    if echo "$output" | grep -qi "can only be reset if the group.*is inactive\|current state is"; then
        log_error "Cannot reset offsets: Consumer group is active"
        echo ""
        log "The consumer group '${group_name}' is currently active (consumers are running)." "$YELLOW"
        log "You must stop all consumers before resetting offsets." "$YELLOW"
        echo ""
        log "To fix this:" "$BRIGHT"
        log "  1. Stop the consumer application" "$BLUE"
        log "  2. Verify the group is inactive: ./neotool kafka --consumer-group ${group_name}" "$BLUE"
        log "  3. Then retry the reset command with --execute flag" "$BLUE"
        echo ""
        log "Full error output:" "$YELLOW"
        echo "$output"
        return 1
    elif [[ $exit_code -ne 0 ]]; then
        log_error "Failed to reset offsets"
        log_error "Output: $output"
        return 1
    fi
    
    log "Offset reset completed successfully!\n" "$GREEN"
    echo "$output"
    
    # Show new state
    log "\nNew consumer group state:\n" "$YELLOW"
    describe_consumer_group "$group_name" "$execution_method" "$bootstrap_server"
}

# Main function
main() {
    # Check for help flag first
    if [[ $# -eq 0 ]] || [[ "$1" == "--help" ]] || [[ "$1" == "-h" ]]; then
        show_help
        exit 0
    fi
    
    local args=("$@")
    
    # First, check if --reset-offsets is present (it takes priority)
    local has_reset_offsets=false
    local i=0
    while [[ $i -lt ${#args[@]} ]]; do
        if [[ "${args[$i]}" == "--reset-offsets" ]]; then
            has_reset_offsets=true
            break
        fi
        i=$((i + 1))
    done
    
    # Identify the command
    local command=""
    local command_value=""
    i=0
    while [[ $i -lt ${#args[@]} ]]; do
        local arg="${args[$i]}"
        case "$arg" in
            --reset-offsets)
                if [[ -z "$command" ]]; then
                    command="$arg"
                fi
                ;;
            --topic|--consumer-group)
                # Only treat as command if --reset-offsets is not present
                if [[ "$has_reset_offsets" == false ]]; then
                    if [[ -z "$command" ]]; then
                        command="$arg"
                        # Check if next arg is a value (not an option)
                        if [[ $((i + 1)) -lt ${#args[@]} ]] && [[ ! "${args[$((i + 1))]}" =~ ^-- ]]; then
                            command_value="${args[$((i + 1))]}"
                        fi
                    else
                        log_error "Error: Multiple commands specified"
                        exit 1
                    fi
                fi
                ;;
        esac
        i=$((i + 1))
    done
    
    # Check if we found a command
    if [[ -z "$command" ]]; then
        log_error "Error: No command specified"
        echo ""
        show_help
        exit 1
    fi
    
    # Parse global options (can appear anywhere)
    i=0
    while [[ $i -lt ${#args[@]} ]]; do
        local arg="${args[$i]}"
        case "$arg" in
            --bootstrap-server)
                if [[ $((i + 1)) -ge ${#args[@]} ]]; then
                    log_error "Error: --bootstrap-server requires a value"
                    exit 1
                fi
                BOOTSTRAP_SERVER="${args[$((i + 1))]}"
                i=$((i + 2))
                ;;
            --docker)
                USE_DOCKER=true
                i=$((i + 1))
                ;;
            *)
                i=$((i + 1))
                ;;
        esac
    done
    
    # Handle reset-offsets command
    if [[ "$command" == "--reset-offsets" ]]; then
        # Parse reset-offsets specific options
        local reset_group=""
        local reset_topic=""
        local reset_strategy=""
        local reset_execute=false
        local reset_force=false
        
        i=0
        while [[ $i -lt ${#args[@]} ]]; do
            local arg="${args[$i]}"
            case "$arg" in
                --group)
                    if [[ $((i + 1)) -ge ${#args[@]} ]]; then
                        log_error "Error: --group requires a value"
                        exit 1
                    fi
                    reset_group="${args[$((i + 1))]}"
                    i=$((i + 2))
                    ;;
                --topic)
                    if [[ $((i + 1)) -ge ${#args[@]} ]]; then
                        log_error "Error: --topic requires a value"
                        exit 1
                    fi
                    reset_topic="${args[$((i + 1))]}"
                    i=$((i + 2))
                    ;;
                --to-earliest|--to-latest)
                    reset_strategy="$arg"
                    i=$((i + 1))
                    ;;
                --to-offset|--to-datetime)
                    if [[ $((i + 1)) -ge ${#args[@]} ]]; then
                        log_error "Error: $arg requires a value"
                        exit 1
                    fi
                    if [[ "$arg" == "--to-offset" ]]; then
                        reset_strategy="--to-offset ${args[$((i + 1))]}"
                    else
                        reset_strategy="--to-datetime ${args[$((i + 1))]}"
                    fi
                    i=$((i + 2))
                    ;;
                --execute)
                    reset_execute=true
                    i=$((i + 1))
                    ;;
                --force)
                    reset_force=true
                    i=$((i + 1))
                    ;;
                *)
                    i=$((i + 1))
                    ;;
            esac
        done
        
        # Validate required arguments
        if [[ -z "$reset_group" ]]; then
            log_error "Error: --reset-offsets requires --group <name>"
            echo ""
            show_help
            exit 1
        fi
        
        if [[ -z "$reset_topic" ]]; then
            log_error "Error: --reset-offsets requires --topic <name>"
            echo ""
            show_help
            exit 1
        fi
        
        if [[ -z "$reset_strategy" ]]; then
            log_error "Error: --reset-offsets requires a reset strategy: --to-earliest, --to-latest, --to-offset <offset>, or --to-datetime <datetime>"
            echo ""
            show_help
            exit 1
        fi
        
        # Get bootstrap server (from options or environment)
        BOOTSTRAP_SERVER=$(get_bootstrap_server)
        
        # Determine execution method
        local execution_method
        execution_method=$(determine_execution_method)
        
        # Execute reset
        reset_offsets "$reset_group" "$reset_topic" "$reset_strategy" "$execution_method" "$BOOTSTRAP_SERVER" "$reset_execute" "$reset_force"
        exit $?
    fi
    
    # Get bootstrap server (from options or environment)
    BOOTSTRAP_SERVER=$(get_bootstrap_server)
    
    # Determine execution method
    local execution_method
    execution_method=$(determine_execution_method)
    
    # Execute command
    case "$command" in
        --topic)
            if [[ -n "$command_value" ]]; then
                # Topic name provided
                describe_topic "$command_value" "$execution_method" "$BOOTSTRAP_SERVER"
            else
                # No topic name, list all topics
                list_topics "$execution_method" "$BOOTSTRAP_SERVER"
            fi
            ;;
        --consumer-group)
            if [[ -n "$command_value" ]]; then
                # Consumer group name provided
                describe_consumer_group "$command_value" "$execution_method" "$BOOTSTRAP_SERVER"
            else
                # No consumer group name, list all consumer groups
                list_consumer_groups "$execution_method" "$BOOTSTRAP_SERVER"
            fi
            ;;
        *)
            log_error "Unknown command: $command"
            echo ""
            show_help
            exit 1
            ;;
    esac
}

# Run main function
main "$@"
