FROM python:3.14-alpine

WORKDIR /app

# Create non-root user
RUN addgroup -g 1000 consumer && \
    adduser -D -u 1000 -G consumer consumer

# Install system dependencies
RUN apk add --no-cache gcc musl-dev libpq-dev

# Install neotool-common from local path
COPY --chown=consumer:consumer common /tmp/neotool-common/
RUN pip install --no-cache-dir /tmp/neotool-common && \
    rm -rf /tmp/neotool-common

# Copy application code
COPY --chown=consumer:consumer financial_data/institution_enhancement /app/

# Install the application
RUN pip install --no-cache-dir /app

# Switch to non-root user
USER consumer

# Expose health check port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8080/health')"

# Run the consumer
ENTRYPOINT ["institution-enhancement-consumer"]

