# Multi-stage Dockerfile for institution enhancement consumer and standalone

# Stage 1: Build
FROM golang:1.22-alpine AS builder

WORKDIR /build

# Install build dependencies
RUN apk add --no-cache git ca-certificates tzdata

# Copy go.mod and go.sum for dependency caching
COPY go.mod go.sum ./
RUN go mod download

# Copy source code
COPY . .

# Build consumer binary
RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build \
    -a -installsuffix cgo \
    -ldflags '-s -w -extldflags "-static"' \
    -o /bin/consumer \
    ./cmd/consumer

# Build standalone binary
RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build \
    -a -installsuffix cgo \
    -ldflags '-s -w -extldflags "-static"' \
    -o /bin/standalone \
    ./cmd/standalone

# Build DLQ consumer binary
RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build \
    -a -installsuffix cgo \
    -ldflags '-s -w -extldflags "-static"' \
    -o /bin/dlq-consumer \
    ./cmd/dlq-consumer

# Build DLQ inspector binary
RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build \
    -a -installsuffix cgo \
    -ldflags '-s -w -extldflags "-static"' \
    -o /bin/dlq-inspector \
    ./cmd/dlq-inspector

# Stage 2: Consumer Runtime
FROM alpine:3.19 AS consumer

# Create non-root user
RUN addgroup -g 1000 consumer && \
    adduser -D -u 1000 -G consumer consumer

# Install runtime dependencies
RUN apk add --no-cache ca-certificates tzdata wget

# Copy binary from builder
COPY --from=builder --chown=consumer:consumer /bin/consumer /app/consumer

# Switch to non-root user
USER consumer

WORKDIR /app

# Expose health check port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

# Run consumer
ENTRYPOINT ["/app/consumer"]

# Stage 3: Standalone Runtime
FROM alpine:3.19 AS standalone

# Create non-root user
RUN addgroup -g 1000 consumer && \
    adduser -D -u 1000 -G consumer consumer

# Install runtime dependencies
RUN apk add --no-cache ca-certificates tzdata

# Copy binary from builder
COPY --from=builder --chown=consumer:consumer /bin/standalone /app/standalone

# Switch to non-root user
USER consumer

WORKDIR /app

# Run standalone
ENTRYPOINT ["/app/standalone"]

# Stage 4: DLQ Consumer Runtime
FROM alpine:3.19 AS dlq-consumer

# Create non-root user
RUN addgroup -g 1000 consumer && \
    adduser -D -u 1000 -G consumer consumer

# Install runtime dependencies
RUN apk add --no-cache ca-certificates tzdata wget

# Copy binary from builder
COPY --from=builder --chown=consumer:consumer /bin/dlq-consumer /app/dlq-consumer

# Switch to non-root user
USER consumer

WORKDIR /app

# Expose health check port (use different port from main consumer)
EXPOSE 8081

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8081/health || exit 1

# Run DLQ consumer
ENTRYPOINT ["/app/dlq-consumer"]

# Stage 5: DLQ Inspector Runtime
FROM alpine:3.19 AS dlq-inspector

# Create non-root user
RUN addgroup -g 1000 consumer && \
    adduser -D -u 1000 -G consumer consumer

# Install runtime dependencies
RUN apk add --no-cache ca-certificates tzdata

# Copy binary from builder
COPY --from=builder --chown=consumer:consumer /bin/dlq-inspector /app/dlq-inspector

# Switch to non-root user
USER consumer

WORKDIR /app

# Run DLQ inspector
ENTRYPOINT ["/app/dlq-inspector"]
