.PHONY: help build build-retry-consumer test lint fmt clean run deps verify all

# Variables
PROJECT_NAME := dlq-retry-consumer
BINARY := bin/dlq-retry-consumer
DOCKER_IMAGE := $(PROJECT_NAME)
DOCKER_TAG := latest

# Go parameters
GOCMD := go
GOBUILD := $(GOCMD) build
GOTEST := $(GOCMD) test
GOCLEAN := $(GOCMD) clean
GOMOD := $(GOCMD) mod
GOFMT := $(GOCMD) fmt

help: ## Show this help message
	@echo 'Usage: make [target]'
	@echo ''
	@echo 'Available targets:'
	@awk 'BEGIN {FS = ":.*?## "} /^[a-zA-Z_-]+:.*?## / {printf "  \033[36m%-20s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST)

build: build-retry-consumer ## Build all binaries

build-retry-consumer: ## Build DLQ retry consumer binary
	@echo "Building DLQ retry consumer..."
	@mkdir -p bin
	CGO_ENABLED=0 GOOS=linux GOARCH=amd64 $(GOBUILD) -a -installsuffix cgo \
		-ldflags '-s -w' \
		-o $(BINARY) \
		./cmd/dlq-retry-consumer
	@echo "Binary built: $(BINARY)"

test: ## Run tests
	@echo "Running tests..."
	$(GOTEST) -v -race -coverprofile=coverage.out ./...
	@echo "Coverage report: coverage.out"

test-coverage: test ## Run tests and show coverage
	@echo "Coverage summary:"
	$(GOCMD) tool cover -func=coverage.out

test-integration: ## Run integration tests (requires Docker)
	@echo "Running integration tests..."
	$(GOTEST) -v -race -tags=integration ./internal/consumer/...

lint: ## Run linter (requires golangci-lint)
	@echo "Running linter..."
	golangci-lint run ./...

fmt: ## Format code
	@echo "Formatting code..."
	$(GOFMT) ./...

clean: ## Clean build artifacts
	@echo "Cleaning..."
	$(GOCLEAN)
	rm -rf bin/
	rm -f coverage.out

tidy: ## Tidy go.mod
	@echo "Tidying go.mod..."
	$(GOMOD) tidy

deps: ## Download dependencies
	@echo "Downloading dependencies..."
	$(GOMOD) download

verify: ## Verify dependencies
	@echo "Verifying dependencies..."
	$(GOMOD) verify

run: build-retry-consumer ## Run DLQ retry consumer locally (requires env vars)
	@echo "Running DLQ retry consumer..."
	@echo "Make sure KAFKA_BROKERS and DLQ_CONFIG_PATH are set"
	./$(BINARY)

docker-build: ## Build Docker image
	@echo "Building Docker image..."
	docker build -t $(DOCKER_IMAGE):$(DOCKER_TAG) .
	@echo "Image built: $(DOCKER_IMAGE):$(DOCKER_TAG)"

all: clean fmt lint test build ## Clean, format, lint, test, and build

.DEFAULT_GOAL := help
