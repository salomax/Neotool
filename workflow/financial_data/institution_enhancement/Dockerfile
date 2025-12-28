# Stage 1: Builder (if needed for compilation)
FROM python:3.14-slim AS builder

# Stage 2: Runtime
FROM python:3.14-slim

# Install system dependencies (psycopg2-binary needs libpq)
RUN apt-get update && apt-get install -y --no-install-recommends \
    libpq5 \
    && rm -rf /var/lib/apt/lists/*

# Create non-root user
RUN useradd -m -u 1000 consumer && \
    mkdir -p /app && \
    chown -R consumer:consumer /app

WORKDIR /app

# Copy requirements first for better layer caching
COPY --chown=consumer:consumer requirements.txt .

# Install Python dependencies
RUN pip install --no-cache-dir --upgrade pip && \
    pip install --no-cache-dir -r requirements.txt

# Copy application code
COPY --chown=consumer:consumer . .

# Switch to non-root user
USER consumer

# Expose health check port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8080/health')"

# Entry point
ENTRYPOINT ["python", "flows/consumer.py"]

