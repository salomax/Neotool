"use client";

import React from "react";
import {
  Box,
  Typography,
  Container,
  Paper,
  Button,
  Stack,
  List,
  ListItem,
  ListItemText,
  Alert,
  Chip,
} from "@mui/material";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import Link from "next/link";
import { APP_CONFIG } from "@/shared/config/repo.constants";

export default function DeploymentPage() {
  return (
    <Container maxWidth="lg" sx={{ py: 4 }}>
      <Box sx={{ mb: 4 }}>
        <Button
          component={Link}
          href="/documentation"
          startIcon={<ArrowBackIcon />}
          sx={{ mb: 3 }}
        >
          Back to Documentation
        </Button>
        <Typography variant="h3" component="h1" gutterBottom>
          Deployment
        </Typography>
        <Typography variant="h6" color="text.secondary" sx={{ maxWidth: 800 }}>
          Deployment strategies and CI/CD pipelines for production applications.
        </Typography>
      </Box>

      <Stack spacing={4}>
        {/* Overview */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Deployment Overview
          </Typography>
          <Typography variant="body1" paragraph>
            This guide covers deployment strategies, build configuration, and best practices for
            deploying Next.js applications to production.
          </Typography>
          <Alert severity="info" sx={{ mt: 2 }}>
            Next.js supports multiple deployment targets including Vercel, Docker, and traditional
            hosting platforms.
          </Alert>
        </Paper>

        {/* Build Configuration */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Build Configuration
          </Typography>
          <Typography variant="body1" paragraph>
            Configure your Next.js application for production builds.
          </Typography>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Production Build
          </Typography>
          <Box
            component="pre"
            sx={{
              bgcolor: "background.default",
              p: 2,
              borderRadius: 1,
              overflow: "auto",
            }}
          >
            <code>{`# Build for production
npm run build

# Start production server
npm start

# Build output
.next/              # Build output directory
  ├── server/      # Server-side code
  ├── static/      # Static assets
  └── ...`}</code>
          </Box>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Next.js Configuration
          </Typography>
          <Box
            component="pre"
            sx={{
              bgcolor: "background.default",
              p: 2,
              borderRadius: 1,
              overflow: "auto",
            }}
          >
            <code>{`// next.config.mjs
/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'standalone', // For Docker deployments
  compress: true, // Enable gzip compression
  poweredByHeader: false, // Remove X-Powered-By header
  reactStrictMode: true,
  swcMinify: true, // Use SWC for minification
};

export default nextConfig;`}</code>
          </Box>
        </Paper>

        {/* Environment Variables */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Environment Variables
          </Typography>
          <Typography variant="body1" paragraph>
            Configure environment variables for different deployment environments.
          </Typography>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Environment Files
          </Typography>
          <List>
            <ListItem>
              <ListItemText
                primary=".env.local"
                secondary="Local development (never commit to git)"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary=".env.development"
                secondary="Development environment variables"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary=".env.production"
                secondary="Production environment variables"
              />
            </ListItem>
          </List>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Public Variables
          </Typography>
          <Typography variant="body2" paragraph>
            Variables prefixed with <code>NEXT_PUBLIC_</code> are exposed to the browser:
          </Typography>
          <Box
            component="pre"
            sx={{
              bgcolor: "background.default",
              p: 2,
              borderRadius: 1,
              overflow: "auto",
            }}
          >
            <code>{`# .env.production
NEXT_PUBLIC_API_URL=https://api.example.com
NEXT_PUBLIC_APP_NAME=${APP_CONFIG.name}

# Server-side only (not prefixed)
DATABASE_URL=postgresql://...
API_SECRET_KEY=...`}</code>
          </Box>
        </Paper>

        {/* Docker Deployment */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Docker Deployment
          </Typography>
          <Typography variant="body1" paragraph>
            Deploy Next.js applications using Docker containers for consistent deployments.
          </Typography>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Dockerfile Example
          </Typography>
          <Box
            component="pre"
            sx={{
              bgcolor: "background.default",
              p: 2,
              borderRadius: 1,
              overflow: "auto",
            }}
          >
            <code>{`# Dockerfile
FROM node:${APP_CONFIG.nodeVersionShort}-alpine AS base

# Install dependencies
FROM base AS deps
WORKDIR /app
COPY package*.json ./
RUN npm ci

# Build application
FROM base AS builder
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN npm run build

# Production image
FROM base AS runner
WORKDIR /app
ENV NODE_ENV production

COPY --from=builder /app/public ./public
COPY --from=builder /app/.next/standalone ./
COPY --from=builder /app/.next/static ./.next/static

EXPOSE ${new URL(APP_CONFIG.devUrl).port}
ENV PORT ${new URL(APP_CONFIG.devUrl).port}

CMD ["node", "server.js"]`}</code>
          </Box>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Build and Run Docker Image
          </Typography>
          <Box
            component="pre"
            sx={{
              bgcolor: "background.default",
              p: 2,
              borderRadius: 1,
              overflow: "auto",
            }}
          >
            <code>{`# Build Docker image
docker build -t ${APP_CONFIG.dockerImageName} .

# Run container
docker run -p ${new URL(APP_CONFIG.devUrl).port}:${new URL(APP_CONFIG.devUrl).port} ${APP_CONFIG.dockerImageName}`}</code>
          </Box>
        </Paper>

        {/* CI/CD Setup */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            CI/CD Pipeline
          </Typography>
          <Typography variant="body1" paragraph>
            Set up continuous integration and deployment pipelines for automated testing and deployment.
          </Typography>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            GitHub Actions Example
          </Typography>
          <Box
            component="pre"
            sx={{
              bgcolor: "background.default",
              p: 2,
              borderRadius: 1,
              overflow: "auto",
            }}
          >
            <code>{`# .github/workflows/deploy.yml
name: Deploy

on:
  push:
    branches: [main]

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Setup Node.js
        uses: actions/setup-node@v3
        with:
          node-version: '${APP_CONFIG.nodeVersionShort}'
          
      - name: Install dependencies
        run: npm ci
        
      - name: Run tests
        run: npm test
        
      - name: Build
        run: npm run build
        env:
          NEXT_PUBLIC_API_URL: \${{ secrets.API_URL }}
          
      - name: Deploy
        run: |
          # Your deployment commands here
          echo "Deploying to production..."`}</code>
          </Box>
        </Paper>

        {/* Production Checklist */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Production Checklist
          </Typography>
          <Typography variant="body1" paragraph>
            Before deploying to production, ensure the following:
          </Typography>

          <Stack spacing={2} sx={{ mt: 2 }}>
            <Box sx={{ display: "flex", gap: 2 }}>
              <CheckCircleIcon color="success" />
              <Box>
                <Typography variant="body1" fontWeight="bold">
                  Environment Variables
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  All required environment variables are configured
                </Typography>
              </Box>
            </Box>
            <Box sx={{ display: "flex", gap: 2 }}>
              <CheckCircleIcon color="success" />
              <Box>
                <Typography variant="body1" fontWeight="bold">
                  Build Success
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Application builds successfully without errors
                </Typography>
              </Box>
            </Box>
            <Box sx={{ display: "flex", gap: 2 }}>
              <CheckCircleIcon color="success" />
              <Box>
                <Typography variant="body1" fontWeight="bold">
                  Tests Pass
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  All tests pass in CI/CD pipeline
                </Typography>
              </Box>
            </Box>
            <Box sx={{ display: "flex", gap: 2 }}>
              <CheckCircleIcon color="success" />
              <Box>
                <Typography variant="body1" fontWeight="bold">
                  Security Headers
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Security headers are configured (CSP, HSTS, etc.)
                </Typography>
              </Box>
            </Box>
            <Box sx={{ display: "flex", gap: 2 }}>
              <CheckCircleIcon color="success" />
              <Box>
                <Typography variant="body1" fontWeight="bold">
                  Monitoring
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Error tracking and monitoring are set up
                </Typography>
              </Box>
            </Box>
            <Box sx={{ display: "flex", gap: 2 }}>
              <CheckCircleIcon color="success" />
              <Box>
                <Typography variant="body1" fontWeight="bold">
                  Performance
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Performance metrics meet targets (Lighthouse, Web Vitals)
                </Typography>
              </Box>
            </Box>
          </Stack>
        </Paper>

        {/* Resources */}
        <Paper sx={{ p: 4, bgcolor: "primary.main", color: "primary.contrastText" }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Additional Resources
          </Typography>
          <Stack spacing={2} sx={{ mt: 2 }}>
            <Link
              href="https://nextjs.org/docs/deployment"
              target="_blank"
              rel="noopener noreferrer"
              style={{ color: "inherit", textDecoration: "underline" }}
            >
              Next.js Deployment Documentation
            </Link>
            <Link
              href="https://vercel.com/docs"
              target="_blank"
              rel="noopener noreferrer"
              style={{ color: "inherit", textDecoration: "underline" }}
            >
              Vercel Deployment Guide
            </Link>
          </Stack>
        </Paper>
      </Stack>
    </Container>
  );
}

