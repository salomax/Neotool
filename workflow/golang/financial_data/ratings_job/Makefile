.PHONY: build build-cronjob build-standalone test clean fmt vet lint run-cronjob run-standalone

# Build configuration
BINARY_CRONJOB=ratings-cronjob
BINARY_STANDALONE=ratings-standalone
BUILD_DIR=bin

# Go configuration
GOCMD=go
GOBUILD=$(GOCMD) build
GOTEST=$(GOCMD) test
GOCLEAN=$(GOCMD) clean
GOVET=$(GOCMD) vet
GOFMT=$(GOCMD) fmt

# Build flags
LDFLAGS=-ldflags "-s -w"

# Default target
all: build

# Build all binaries
build: build-cronjob build-standalone

# Build cronjob binary
build-cronjob:
	@echo "Building cronjob..."
	@mkdir -p $(BUILD_DIR)
	$(GOBUILD) $(LDFLAGS) -o $(BUILD_DIR)/$(BINARY_CRONJOB) ./cmd/cronjob

# Build standalone binary
build-standalone:
	@echo "Building standalone..."
	@mkdir -p $(BUILD_DIR)
	$(GOBUILD) $(LDFLAGS) -o $(BUILD_DIR)/$(BINARY_STANDALONE) ./cmd/standalone

# Run tests
test:
	@echo "Running tests..."
	$(GOTEST) -v -race ./...

# Run tests with coverage
test-coverage:
	@echo "Running tests with coverage..."
	$(GOTEST) -v -race -coverprofile=coverage.out ./...
	$(GOCMD) tool cover -html=coverage.out -o coverage.html

# Clean build artifacts
clean:
	@echo "Cleaning..."
	$(GOCLEAN)
	rm -rf $(BUILD_DIR)
	rm -f coverage.out coverage.html

# Format code
fmt:
	@echo "Formatting code..."
	$(GOFMT) ./...

# Vet code
vet:
	@echo "Vetting code..."
	$(GOVET) ./...

# Lint code (requires golangci-lint)
lint:
	@echo "Linting code..."
	golangci-lint run

# Download dependencies
deps:
	@echo "Downloading dependencies..."
	$(GOCMD) mod download
	$(GOCMD) mod tidy

# Run cronjob locally (requires POSTGRES_PASSWORD env var)
run-cronjob: build-cronjob
	@echo "Running cronjob..."
	./$(BUILD_DIR)/$(BINARY_CRONJOB)

# Run standalone locally (requires POSTGRES_PASSWORD env var and --issuer flag)
run-standalone: build-standalone
	@echo "Running standalone (use --issuer=FITCH)..."
	./$(BUILD_DIR)/$(BINARY_STANDALONE) --issuer=FITCH --dry-run --verbose

# Docker build targets
docker-build-cronjob:
	@echo "Building cronjob Docker image..."
	docker build -t ratings-cronjob:latest --target cronjob .

docker-build-standalone:
	@echo "Building standalone Docker image..."
	docker build -t ratings-standalone:latest --target standalone .

docker-build-all: docker-build-cronjob docker-build-standalone

# Help
help:
	@echo "Available targets:"
	@echo "  build           - Build all binaries"
	@echo "  build-cronjob   - Build cronjob binary"
	@echo "  build-standalone - Build standalone binary"
	@echo "  test            - Run tests"
	@echo "  test-coverage   - Run tests with coverage report"
	@echo "  clean           - Clean build artifacts"
	@echo "  fmt             - Format code"
	@echo "  vet             - Vet code"
	@echo "  lint            - Lint code (requires golangci-lint)"
	@echo "  deps            - Download and tidy dependencies"
	@echo "  run-cronjob     - Run cronjob locally"
	@echo "  run-standalone  - Run standalone locally with dry-run"
	@echo "  docker-build-all - Build all Docker images"
