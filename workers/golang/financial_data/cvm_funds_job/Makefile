.PHONY: help build clean test fmt vet lint tidy deps docker-build run

BINARY_DIR   := bin
BINARY       := $(BINARY_DIR)/cronjob
DOCKER_IMAGE ?= cvm-funds-job
VERSION      ?= latest

help: ## Show this help message
	@awk 'BEGIN {FS = ":.*?## "} /^[a-zA-Z_-]+:.*?## / {printf "  %-20s %s\n", $$1, $$2}' $(MAKEFILE_LIST)

build: ## Build cronjob binary
	@mkdir -p $(BINARY_DIR)
	CGO_ENABLED=0 go build -buildvcs=false -ldflags="-s -w" -o $(BINARY) ./cmd/cronjob
	@echo "Built: $(BINARY)"

clean: ## Remove build artifacts
	@rm -rf $(BINARY_DIR)

test: ## Run tests
	go test -v -race -cover ./...

test-coverage: ## Run tests with coverage report
	go test -v -race -coverprofile=coverage.out ./...
	go tool cover -html=coverage.out -o coverage.html

fmt: ## Format code
	go fmt ./...

vet: ## Run go vet
	go vet ./...

lint: ## Run golangci-lint
	golangci-lint run

tidy: ## Tidy go.mod
	go mod tidy

deps: ## Download dependencies
	go mod download

docker-build: ## Build Docker image (cronjob target)
	docker build -t $(DOCKER_IMAGE):$(VERSION) --target cronjob -f Dockerfile .

run: build ## Build and run cronjob locally
	./$(BINARY)

.DEFAULT_GOAL := help
