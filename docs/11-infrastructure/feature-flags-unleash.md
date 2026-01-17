---
title: Feature Flags with Unleash
type: infrastructure
category: deployment
status: draft
version: 1.0.0
tags: [feature-flags, unleash, kubernetes, postgres, nextjs, kotlin]
ai_optimized: true
search_keywords: [feature flags, unleash, rollout, canary, ttl, edge, proxy]
related:
  - 11-infrastructure/k8s-runbook.md
  - 07-frontend/README.md
  - 05-backend/README.md
  - 10-observability/observability-overview.md
last_updated: 2026-01-15
---

# Feature Flags with Unleash

> **Purpose**: Define requirements and implementation tasks for a scalable Unleash-based feature flag system across Next.js and Kotlin services.

## Requirements

### Functional
- Centralized feature flags for UI behavior toggles.
- Supports gradual rollout and canary releases.
- Targeting based on custom attributes (user, tenant, plan, role, region).
- Server-side evaluation for Kotlin services, Golang workflows and Next.js server code.
- Client-side evaluation for UI with bootstrap to avoid flicker.

### Non-Functional
- Low latency and minimal per-request overhead.
- Configurable TTL/polling for flag refresh.
- Secure tokens (server vs client) with least privilege.
- Auditability and environment separation (dev/staging/prod).

## Architecture Overview

### Components
- **Unleash Server**: Core API + feature management UI.
- **Unleash Edge/Proxy**: Public-facing endpoint for client SDKs.
- **Postgres**: Dedicated DB or schema for Unleash.
- **Next.js Web**: Server-side and client-side flag access.
- **Kotlin Services**: Server-side flag access with custom context.

### Recommended Network Flow
1. Admins manage flags via Unleash UI.
2. Unleash Server stores config in Postgres.
3. Edge/Proxy serves client tokens and caches flags.
4. Kotlin services use server SDK + server token.
5. Next.js uses server SDK for SSR and Proxy client for browser.

## Database Setup (Robust + Sleek)

### Recommendation
Use a **separate database** (or at minimum a **separate schema**) for Unleash. This isolates migrations and reduces blast radius.

### Tasks (K8S + Postgres)
1. **Create DB and user**:
   - DB: `unleash`
   - User: `unleash_app` with strong password
   - Privileges: `CONNECT`, `TEMP`, `CREATE` on DB, and ownership of Unleash schema
2. **Store credentials in Vault**:
   - `unleash.username`, `unleash.password`, `unleash.database`
3. **Sync secrets to K8S**:
   - Create an `ExternalSecret` for Unleash DB credentials.
4. **Update PgBouncer** (current config only exposes one DB):
   - Add a second entry for `unleash_db` in `pgbouncer.ini.tpl`, or use wildcard `*` if acceptable.
   - Add `unleash_app` to `userlist.txt`.
5. **Idempotent DB init job**:
   - Use a one-off K8S Job to run a SQL block that creates DB + user if missing.

### Example SQL (Idempotent)
```sql
DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'unleash_app') THEN
    CREATE ROLE unleash_app LOGIN PASSWORD 'REDACTED';
  END IF;
  IF NOT EXISTS (SELECT FROM pg_database WHERE datname = 'unleash') THEN
    CREATE DATABASE unleash OWNER unleash_app;
  END IF;
END $$;
```

## Implementation Tasks

### 1) Infrastructure (K8S)
- Deploy **Unleash Server** (Helm chart or manifests).
- Deploy **Unleash Edge/Proxy** for client access.
- Configure **network policies** to allow:
  - Unleash Server <-> Postgres/PgBouncer
  - Edge/Proxy <-> Unleash Server
  - Web/Kotlin services <-> Edge/Proxy and/or Server (server-only tokens)
- Create Kubernetes secrets from Vault for:
  - Unleash Server API token
  - Unleash Proxy/Edge client tokens
  - Unleash DB credentials

### 2) Next.js Web (Performance + TTL)
- Add a **FeatureFlagsProvider** to `web/src/app/providers.tsx`.
- Create a **server-only Unleash client** for SSR and route handlers.
- Use **bootstrap** so the first client render matches SSR.
- Configure refresh intervals:
  - Server SDK: 15s to 30s
  - Client Proxy SDK: 30s to 60s

### 3) Kotlin Services
- Add Unleash JVM SDK dependency.
- Configure Unleash client with:
  - `appName`, `instanceId`
  - `unleashApi` (server endpoint)
  - `apiKey` (server token)
  - `fetchTogglesInterval` for TTL
- Evaluate flags with **UnleashContext** per request:
  - `userId`, `tenantId`, `role`, `plan`, `region`, `environment`

## Rollout Strategy (Canary)
- Use Unleash strategies:
  - `flexibleRollout` with stickiness (`userId` or `sessionId`)
  - `constraint` rules for tenant or role gating
- Example rollout plan:
  1. Enable for internal users only
  2. 5% of tenants
  3. 25% rollout
  4. 100% rollout

## Observability
- Track:
  - Flag evaluation rate
  - Cache hit ratio (Edge/Proxy)
  - SDK refresh failures
- Add dashboards/alerts for Unleash Server and Edge/Proxy health.

## Acceptance Criteria
- Flags can be evaluated in both Next.js and Kotlin services.
- Flags update within configured TTL.
- Canary rollout works using context attributes.
- DB isolation is in place and credentials are managed via Vault.
