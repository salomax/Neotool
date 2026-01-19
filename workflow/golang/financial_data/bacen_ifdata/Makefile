.PHONY: help build-all build-cronjob build-retry-worker build-cli clean test docker-build-all docker-build-cronjob docker-build-retry-worker docker-build-cli run-cronjob run-retry-worker run-cli

# Variables
BINARY_DIR := bin
CRONJOB_BINARY := $(BINARY_DIR)/cronjob
RETRY_WORKER_BINARY := $(BINARY_DIR)/retry-worker
CLI_BINARY := $(BINARY_DIR)/cli
DOCKER_REGISTRY ?= bacen-ifdata
VERSION ?= latest

help: ## Show this help message
	@echo 'Usage: make [target]'
	@echo ''
	@echo 'Available targets:'
	@awk 'BEGIN {FS = ":.*?## "} /^[a-zA-Z_-]+:.*?## / {printf "  %-20s %s\n", $$1, $$2}' $(MAKEFILE_LIST)

build-all: build-cronjob build-retry-worker build-cli ## Build all binaries

build-cronjob: ## Build cronjob binary
	@echo "Building cronjob..."
	@mkdir -p $(BINARY_DIR)
	CGO_ENABLED=0 go build -ldflags="-s -w" -o $(CRONJOB_BINARY) ./cmd/cronjob
	@echo "Built: $(CRONJOB_BINARY)"

build-retry-worker: ## Build retry worker binary
	@echo "Building retry worker..."
	@mkdir -p $(BINARY_DIR)
	CGO_ENABLED=0 go build -ldflags="-s -w" -o $(RETRY_WORKER_BINARY) ./cmd/retry-worker
	@echo "Built: $(RETRY_WORKER_BINARY)"

build-cli: ## Build CLI binary
	@echo "Building CLI..."
	@mkdir -p $(BINARY_DIR)
	CGO_ENABLED=0 go build -ldflags="-s -w" -o $(CLI_BINARY) ./cmd/cli
	@echo "Built: $(CLI_BINARY)"

clean: ## Clean build artifacts
	@echo "Cleaning..."
	@rm -rf $(BINARY_DIR)
	@echo "Cleaned"

test: ## Run tests
	@echo "Running tests..."
	go test -v -race -cover ./...

test-coverage: ## Run tests with coverage report
	@echo "Running tests with coverage..."
	go test -v -race -coverprofile=coverage.out ./...
	go tool cover -html=coverage.out -o coverage.html
	@echo "Coverage report generated: coverage.html"

fmt: ## Format code
	@echo "Formatting code..."
	go fmt ./...

vet: ## Run go vet
	@echo "Running go vet..."
	go vet ./...

lint: ## Run golangci-lint (requires golangci-lint installed)
	@echo "Running golangci-lint..."
	golangci-lint run

tidy: ## Tidy go.mod
	@echo "Tidying go.mod..."
	go mod tidy

deps: ## Download dependencies
	@echo "Downloading dependencies..."
	go mod download

docker-build-all: docker-build-cronjob docker-build-retry-worker docker-build-cli ## Build all Docker images

docker-build-cronjob: ## Build cronjob Docker image
	@echo "Building cronjob Docker image..."
	docker build -t $(DOCKER_REGISTRY)-cronjob:$(VERSION) --target cronjob -f Dockerfile .
	@echo "Built: $(DOCKER_REGISTRY)-cronjob:$(VERSION)"

docker-build-retry-worker: ## Build retry worker Docker image
	@echo "Building retry worker Docker image..."
	docker build -t $(DOCKER_REGISTRY)-retry-worker:$(VERSION) --target retry-worker -f Dockerfile .
	@echo "Built: $(DOCKER_REGISTRY)-retry-worker:$(VERSION)"

docker-build-cli: ## Build CLI Docker image
	@echo "Building CLI Docker image..."
	docker build -t $(DOCKER_REGISTRY)-cli:$(VERSION) --target cli -f Dockerfile .
	@echo "Built: $(DOCKER_REGISTRY)-cli:$(VERSION)"

run-cronjob: build-cronjob ## Run cronjob locally
	@echo "Running cronjob..."
	./$(CRONJOB_BINARY)

run-retry-worker: build-retry-worker ## Run retry worker locally
	@echo "Running retry worker..."
	./$(RETRY_WORKER_BINARY)

run-cli: build-cli ## Run CLI locally (use ARGS="--ano-mes 202403" to pass arguments)
	@echo "Running CLI..."
	./$(CLI_BINARY) $(ARGS)

install-tools: ## Install development tools
	@echo "Installing development tools..."
	go install github.com/golangci/golangci-lint/cmd/golangci-lint@latest
	@echo "Tools installed"

.DEFAULT_GOAL := help
