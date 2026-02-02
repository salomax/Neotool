.PHONY: help build build-consumer build-standalone build-dlq-consumer build-retry-worker build-dlq-inspector test lint fmt clean docker-build-consumer docker-build-standalone docker-build-dlq-consumer docker-build-all run-consumer run-standalone run-dlq-consumer run-retry-worker run-dlq-inspector

# Variables
PROJECT_NAME := institution-enhancement
CONSUMER_BINARY := bin/consumer
STANDALONE_BINARY := bin/standalone
DLQ_CONSUMER_BINARY := bin/dlq-consumer
RETRY_WORKER_BINARY := bin/retry-worker
DLQ_INSPECTOR_BINARY := bin/dlq-inspector
DOCKER_IMAGE_CONSUMER := $(PROJECT_NAME)-consumer-go
DOCKER_IMAGE_STANDALONE := $(PROJECT_NAME)-standalone-go
DOCKER_IMAGE_DLQ_CONSUMER := $(PROJECT_NAME)-dlq-consumer-go
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

build: build-consumer build-standalone build-dlq-consumer build-retry-worker ## Build all binaries

build-consumer: ## Build consumer binary
	@echo "Building consumer..."
	@mkdir -p bin
	CGO_ENABLED=0 GOOS=linux GOARCH=amd64 $(GOBUILD) -a -installsuffix cgo \
		-ldflags '-s -w' \
		-o $(CONSUMER_BINARY) \
		./cmd/consumer
	@echo "Consumer built: $(CONSUMER_BINARY)"

build-standalone: ## Build standalone binary
	@echo "Building standalone..."
	@mkdir -p bin
	CGO_ENABLED=0 GOOS=linux GOARCH=amd64 $(GOBUILD) -a -installsuffix cgo \
		-ldflags '-s -w' \
		-o $(STANDALONE_BINARY) \
		./cmd/standalone
	@echo "Standalone built: $(STANDALONE_BINARY)"

build-dlq-consumer: ## Build DLQ consumer binary
	@echo "Building DLQ consumer..."
	@mkdir -p bin
	CGO_ENABLED=0 GOOS=linux GOARCH=amd64 $(GOBUILD) -a -installsuffix cgo \
		-ldflags '-s -w' \
		-o $(DLQ_CONSUMER_BINARY) \
		./cmd/dlq-consumer
	@echo "DLQ consumer built: $(DLQ_CONSUMER_BINARY)"

build-retry-worker: ## Build retry worker binary
	@echo "Building retry worker..."
	@mkdir -p bin
	CGO_ENABLED=0 GOOS=linux GOARCH=amd64 $(GOBUILD) -a -installsuffix cgo \
		-ldflags '-s -w' \
		-o $(RETRY_WORKER_BINARY) \
		./cmd/retry-worker
	@echo "Retry worker built: $(RETRY_WORKER_BINARY)"

build-dlq-inspector: ## Build DLQ inspector binary
	@echo "Building DLQ inspector..."
	@mkdir -p bin
	CGO_ENABLED=0 GOOS=linux GOARCH=amd64 $(GOBUILD) -a -installsuffix cgo \
		-ldflags '-s -w' \
		-o $(DLQ_INSPECTOR_BINARY) \
		./cmd/dlq-inspector
	@echo "DLQ inspector built: $(DLQ_INSPECTOR_BINARY)"

test: ## Run tests
	@echo "Running tests..."
	$(GOTEST) -v -race -coverprofile=coverage.out ./...
	@echo "Coverage report: coverage.out"

test-integration: ## Run integration tests (requires testcontainers)
	@echo "Running integration tests..."
	$(GOTEST) -v -race -tags=integration ./test/integration/...

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

docker-build-consumer: ## Build consumer Docker image
	@echo "Building consumer Docker image..."
	docker build --target consumer \
		-t $(DOCKER_IMAGE_CONSUMER):$(DOCKER_TAG) \
		-f deployments/docker/Dockerfile .
	@echo "Consumer image built: $(DOCKER_IMAGE_CONSUMER):$(DOCKER_TAG)"

docker-build-standalone: ## Build standalone Docker image
	@echo "Building standalone Docker image..."
	docker build --target standalone \
		-t $(DOCKER_IMAGE_STANDALONE):$(DOCKER_TAG) \
		-f deployments/docker/Dockerfile .
	@echo "Standalone image built: $(DOCKER_IMAGE_STANDALONE):$(DOCKER_TAG)"

docker-build-all: docker-build-consumer docker-build-standalone ## Build all Docker images

run-consumer: build-consumer ## Run consumer locally (requires env vars)
	@echo "Running consumer..."
	./$(CONSUMER_BINARY)

run-standalone: build-standalone ## Run standalone locally (requires CNPJ argument and env vars)
	@echo "Running standalone..."
	@echo "Usage: make run-standalone CNPJ=12345678000190"
	@if [ -z "$(CNPJ)" ]; then \
		echo "Error: CNPJ argument required"; \
		echo "Example: make run-standalone CNPJ=12345678000190"; \
		exit 1; \
	fi
	./$(STANDALONE_BINARY) $(CNPJ)

run-dlq-consumer: build-dlq-consumer ## Run DLQ consumer locally (requires env vars)
	@echo "Running DLQ consumer..."
	./$(DLQ_CONSUMER_BINARY)

run-retry-worker: build-retry-worker ## Run retry worker locally (requires env vars)
	@echo "Running retry worker..."
	./$(RETRY_WORKER_BINARY)

run-dlq-inspector: build-dlq-inspector ## Inspect DLQ messages and show statistics
	@echo "Running DLQ inspector..."
	./$(DLQ_INSPECTOR_BINARY)

deps: ## Download dependencies
	@echo "Downloading dependencies..."
	$(GOMOD) download

verify: ## Verify dependencies
	@echo "Verifying dependencies..."
	$(GOMOD) verify

all: clean fmt lint build test ## Clean, format, lint, build, and test
