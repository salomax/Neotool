FROM python:3.14-slim

# Set working directory
WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc \
    && rm -rf /var/lib/apt/lists/*

# Install neotool-common from local path
COPY --chown=nobody:nogroup ../../common /tmp/neotool-common/
RUN pip install --no-cache-dir /tmp/neotool-common && rm -rf /tmp/neotool-common

# Copy application code
COPY --chown=nobody:nogroup . /app/

# Install the application
RUN pip install --no-cache-dir /app

# Run as non-root user
USER nobody

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
  CMD python -c "import sys; sys.exit(0)"

# Default command - can be overridden
CMD ["python", "-m", "bacen_ifdata.flows.main"]
