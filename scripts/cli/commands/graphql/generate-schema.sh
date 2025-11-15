#!/bin/bash
set -e

echo "Generating supergraph schemas..."

# Calculate paths from script location
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../../../" && pwd)"
CONTRACTS_DIR="$PROJECT_ROOT/contracts/graphql"
SUPERGRAPH_DIR="$CONTRACTS_DIR/supergraph"

# Function to generate a supergraph with rover in Docker
generate_with_docker() {
    local config_file="$1"
    local output_file="$2"
    local env_name="$3"
    
    echo "  Generating $env_name supergraph..."
    docker run --rm \
        -v "$SUPERGRAPH_DIR:/workspace" \
        -w /workspace \
        -e APOLLO_ELV2_LICENSE=accept \
        apollographql/rover:latest \
        supergraph compose \
        --config "./$config_file" \
        --output "./$output_file"
}

# Function to generate a supergraph with local rover
generate_with_local() {
    local config_file="$1"
    local output_file="$2"
    local env_name="$3"
    
    echo "  Generating $env_name supergraph..."
    cd "$SUPERGRAPH_DIR"
    rover supergraph compose \
        --config "./$config_file" \
        --output "./$output_file"
}

# Function to run rover in Docker (recommended for CI/CD)
run_rover_docker() {
    echo "Using Docker-based rover..."
    generate_with_docker "supergraph.yaml" "supergraph.graphql" "production"
    generate_with_docker "supergraph.dev.yaml" "supergraph.dev.graphql" "development"
}

# Function to run rover locally (for development)
run_rover_local() {
    echo "Using local rover..."
    
    # Check if rover is installed
    if ! command -v rover &> /dev/null; then
        echo "Error: rover is not installed or not in PATH"
        echo "Please install rover first:"
        echo "  curl -sSL https://rover.apollo.dev/nix/latest | sh"
        echo "  or visit: https://www.apollographql.com/docs/rover/getting-started/"
        echo ""
        echo "Alternatively, use Docker:"
        echo "  ./neotool graphql generate --docker"
        exit 1
    fi

    # Accept ELv2 license
    export APOLLO_ELV2_LICENSE=accept

    # Generate both supergraph schemas
    generate_with_local "supergraph.yaml" "supergraph.graphql" "production"
    generate_with_local "supergraph.dev.yaml" "supergraph.dev.graphql" "development"
}

# Check if we should use Docker (CI environment or explicit flag)
if [[ "${CI:-false}" == "true" ]] || [[ "${USE_DOCKER_ROVER:-false}" == "true" ]] || [[ "${1:-}" == "--docker" ]]; then
    run_rover_docker
else
    run_rover_local
fi

echo ""
echo "✅ Supergraph schemas generated successfully!"
if [[ -f "$SUPERGRAPH_DIR/supergraph.graphql" ]]; then
    echo "  📄 Production: $SUPERGRAPH_DIR/supergraph.graphql ($(wc -c < "$SUPERGRAPH_DIR/supergraph.graphql") bytes)"
fi
if [[ -f "$SUPERGRAPH_DIR/supergraph.dev.graphql" ]]; then
    echo "  📄 Development: $SUPERGRAPH_DIR/supergraph.dev.graphql ($(wc -c < "$SUPERGRAPH_DIR/supergraph.dev.graphql") bytes)"
fi

