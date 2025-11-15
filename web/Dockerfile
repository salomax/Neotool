# ----------------------------------------------------------------------
# Stage 1: Dependencies and Build
# ----------------------------------------------------------------------
FROM node:20-alpine AS builder
WORKDIR /app

# Copy package files
COPY package.json pnpm-lock.yaml* package-lock.json* yarn.lock* ./

# Install dependencies
RUN if [ -f pnpm-lock.yaml ]; then \
        npm i -g pnpm && pnpm i --frozen-lockfile; \
    elif [ -f yarn.lock ]; then \
        yarn install --frozen-lockfile; \
    elif [ -f package-lock.json ]; then \
        npm ci --legacy-peer-deps; \
    else \
        npm i --legacy-peer-deps; \
    fi

# Copy source code
COPY . .

# Set build environment
# --no-daemon to avoid zombie process
ENV NODE_ENV=production
# Disable Telemetry 
ENV NEXT_TELEMETRY_DISABLED=1
# Set max old space size to 4GB in order to avoid OOM errors
# Memory used by some libraries (e.g. ag-grid) is large
ENV NODE_OPTIONS="--max-old-space-size=4096"

# Build the application
RUN if [ -f pnpm-lock.yaml ]; then \
        pnpm build; \
    else \
        npm run build; \
    fi

# ----------------------------------------------------------------------
# Stage 2: Production Runner
# ----------------------------------------------------------------------
FROM node:20-alpine AS runner
WORKDIR /app

# Set production environment
ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1

# Create non-root user for security
RUN addgroup --system --gid 1001 nodejs
RUN adduser --system --uid 1001 nextjs

# Copy built application and necessary files
COPY --from=builder /app/.next/standalone ./
COPY --from=builder /app/.next/static ./.next/static
COPY --from=builder /app/public ./public

# Set ownership
RUN chown -R nextjs:nodejs /app
USER nextjs

EXPOSE 3000
ENV PORT=3000
ENV HOSTNAME="0.0.0.0"

CMD ["node", "server.js"]
