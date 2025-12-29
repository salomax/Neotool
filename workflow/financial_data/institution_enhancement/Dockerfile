# Stage 1: Builder (if needed for compilation)
FROM python:3.14-alpine AS builder

# Stage 2: Runtime
FROM python:3.14-alpine

# Install system dependencies (psycopg2-binary needs libpq)
RUN apk add --no-cache \
    postgresql-libs

# Create non-root user and group
RUN addgroup --system --gid 1000 consumer && \
    adduser --system --uid 1000 --ingroup consumer consumer && \
    mkdir -p /app && \
    chown -R consumer:consumer /app

WORKDIR /app

# Set PYTHONPATH so Python can find the workflow package
ENV PYTHONPATH=/app

# Copy requirements first for better layer caching
COPY --chown=consumer:consumer financial_data/institution_enhancement/requirements.txt .

# Install Python dependencies
RUN pip install --no-cache-dir --upgrade pip && \
    pip install --no-cache-dir -r requirements.txt

# Copy workflow common module (needed for consumer_base)
COPY --chown=consumer:consumer common/ ./workflow/common/

# Copy application code
COPY --chown=consumer:consumer financial_data/institution_enhancement/ .

# Switch to non-root user
USER consumer

# Expose health check port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8080/health')"

# Entry point
ENTRYPOINT ["python", "flows/consumer.py"]

