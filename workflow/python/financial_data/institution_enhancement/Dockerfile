# Multi-stage build for minimal image size
# Stage 1: Builder
FROM python:3.14-slim AS builder

WORKDIR /build

# Install build dependencies
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    gcc \
    g++ \
    libpq-dev \
    && rm -rf /var/lib/apt/lists/*

# Install neotool-common from local path
COPY common /tmp/neotool-common/
RUN pip install --no-cache-dir --user /tmp/neotool-common

# Copy application code and install
COPY financial_data/institution_enhancement /build/
RUN pip install --no-cache-dir --user /build

# Stage 2: Runtime with distroless
FROM gcr.io/distroless/python3-debian12:nonroot

# Copy Python packages from builder
COPY --from=builder /root/.local /home/nonroot/.local
COPY --from=builder /usr/local/lib/python3.14/site-packages /usr/local/lib/python3.14/site-packages

# Set Python path to include user site-packages
ENV PYTHONPATH=/home/nonroot/.local/lib/python3.14/site-packages:/usr/local/lib/python3.14/site-packages
ENV PATH=/home/nonroot/.local/bin:$PATH

# Expose health check port
EXPOSE 8080

# Run the consumer as non-root user (distroless nonroot user is 65532)
USER nonroot

# Run the consumer
ENTRYPOINT ["/home/nonroot/.local/bin/institution-enhancement-consumer"]

