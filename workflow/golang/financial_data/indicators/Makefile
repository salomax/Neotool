.PHONY: help build-cronjob clean test fmt vet tidy deps run-cronjob

BINARY_DIR := bin
CRONJOB_BINARY := $(BINARY_DIR)/cronjob

help: ## Show this help message
	@echo 'Usage: make [target]'
	@echo ''
	@echo 'Available targets:'
	@awk 'BEGIN {FS = ":.*?## "} /^[a-zA-Z_-]+:.*?## / {printf "  %-20s %s\n", $$1, $$2}' $(MAKEFILE_LIST)

build-cronjob: ## Build cronjob binary
	@echo "Building cronjob..."
	@mkdir -p $(BINARY_DIR)
	CGO_ENABLED=0 go build -ldflags="-s -w" -o $(CRONJOB_BINARY) ./cmd/cronjob
	@echo "Built: $(CRONJOB_BINARY)"

run-cronjob: build-cronjob ## Run cronjob locally (use INDICATOR_FILTER/VARIATION_FILTER)
	@echo "Running cronjob..."
	./$(CRONJOB_BINARY)

clean: ## Clean build artifacts
	@echo "Cleaning..."
	@rm -rf $(BINARY_DIR)
	@echo "Cleaned"

test: ## Run tests
	@echo "Running tests..."
	go test -v ./...

fmt: ## Format code
	@echo "Formatting code..."
	go fmt ./...

vet: ## Run go vet
	@echo "Running go vet..."
	go vet ./...

tidy: ## Tidy go.mod/go.sum
	@echo "Tidying go.mod..."
	go mod tidy

deps: ## Download dependencies
	@echo "Downloading dependencies..."
	go mod download

.DEFAULT_GOAL := help
