#!/bin/bash

echo "🏥 Health Check for NeoTool Services"
echo "===================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to check service health
check_service() {
    local service_name=$1
    local url=$2
    local expected_status=${3:-200}
    
    echo -n "Checking $service_name... "
    
    if response=$(curl -s -o /dev/null -w "%{http_code}" "$url" 2>/dev/null); then
        if [ "$response" = "$expected_status" ]; then
            echo -e "${GREEN}✅ OK${NC} (HTTP $response)"
            return 0
        else
            echo -e "${YELLOW}⚠️  WARNING${NC} (HTTP $response, expected $expected_status)"
            return 1
        fi
    else
        echo -e "${RED}❌ FAILED${NC} (Connection refused)"
        return 1
    fi
}

# Function to check GraphQL endpoint
check_graphql() {
    local service_name=$1
    local url=$2
    
    echo -n "Checking $service_name GraphQL... "
    
    if response=$(curl -s -X POST "$url" \
        -H "Content-Type: application/json" \
        -d '{"query": "{ __schema { queryType { name } } }"}' 2>/dev/null); then
        
        if echo "$response" | grep -q '"data"'; then
            echo -e "${GREEN}✅ OK${NC} (GraphQL responding)"
            return 0
        else
            echo -e "${YELLOW}⚠️  WARNING${NC} (GraphQL error: $(echo "$response" | head -c 100))"
            return 1
        fi
    else
        echo -e "${RED}❌ FAILED${NC} (Connection refused)"
        return 1
    fi
}

# Function to check Docker container status
check_container() {
    local container_name=$1
    
    echo -n "Checking container $container_name... "
    
    if status=$(docker ps --filter "name=$container_name" --format "{{.Status}}" 2>/dev/null); then
        if echo "$status" | grep -q "Up"; then
            echo -e "${GREEN}✅ OK${NC} ($status)"
            return 0
        else
            echo -e "${RED}❌ FAILED${NC} ($status)"
            return 1
        fi
    else
        echo -e "${RED}❌ FAILED${NC} (Container not found)"
        return 1
    fi
}

echo ""
echo "📦 Container Status:"
echo "-------------------"
check_container "neotool-postgres"
check_container "neotool-redis"
check_container "neotool-kafka"
check_container "neotool-api"
check_container "neotool-graphql-router"

echo ""
echo "🌐 Service Health:"
echo "-----------------"
check_service "PostgreSQL" "http://localhost:5432" "000"  # PostgreSQL doesn't respond to HTTP
check_service "Redis" "http://localhost:6379" "000"        # Redis doesn't respond to HTTP
check_service "Kafka" "http://localhost:9092" "000"        # Kafka doesn't respond to HTTP
check_service "API Service" "http://localhost:8080/health" "200"
check_service "Router" "http://localhost:4000" "400"       # Router returns 400 for GET requests

echo ""
echo "🔍 GraphQL Endpoints:"
echo "-------------------"
check_graphql "API Service" "http://localhost:8080/graphql"
check_graphql "Router" "http://localhost:4000/"

echo ""
echo "📊 Summary:"
echo "----------"
echo "All services checked. Check the results above for any issues."
echo ""
echo "💡 Tips:"
echo "- If containers are not running, start them with: docker-compose up -d"
echo "- If services are unhealthy, check logs with: docker-compose logs [service-name]"
echo "- For GraphQL testing, visit: http://localhost:4000 (Router) or http://localhost:8080/graphql (API)"
