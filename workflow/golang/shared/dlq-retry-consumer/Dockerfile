# Build stage
FROM golang:1.23-alpine AS builder

# Install build dependencies
RUN apk add --no-cache git make

# Set working directory
WORKDIR /build

# Copy go mod files
COPY go.mod go.sum ./

# Download dependencies
RUN go mod download

# Copy source code
COPY . .

# Build the binary
RUN make build-retry-consumer

# Runtime stage
FROM alpine:3.19

# Install ca-certificates for HTTPS
RUN apk add --no-cache ca-certificates tzdata

# Create non-root user
RUN addgroup -g 1000 dlqretry && \
    adduser -D -u 1000 -G dlqretry dlqretry

# Set working directory
WORKDIR /app

# Copy binary from builder
COPY --from=builder /build/bin/dlq-retry-consumer /app/dlq-retry-consumer

# Copy config template (optional, can be overridden via volume)
COPY --from=builder /build/config/dlq-mappings.yaml /app/config/dlq-mappings.yaml

# Change ownership
RUN chown -R dlqretry:dlqretry /app

# Switch to non-root user
USER dlqretry

# Set environment variables
ENV DLQ_CONFIG_PATH=/app/config/dlq-mappings.yaml
ENV KAFKA_BROKERS=kafka:9092

# Health check (optional - checks if binary exists and is executable)
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD [ -x /app/dlq-retry-consumer ] || exit 1

# Run the binary
ENTRYPOINT ["/app/dlq-retry-consumer"]
