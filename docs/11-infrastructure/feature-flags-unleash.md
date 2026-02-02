---
title: Feature Flags with Unleash
type: infrastructure
category: deployment
status: current
version: 1.1.0
tags: [feature-flags, unleash, kubernetes, postgres, nextjs, kotlin, runtime-config]
ai_optimized: true
search_keywords: [feature flags, unleash, rollout, canary, ttl, edge, proxy, runtime config]
related:
  - 11-infrastructure/k8s-runbook.md
  - 11-infrastructure/runtime-configuration.md
  - 07-frontend/README.md
  - 05-backend/README.md
  - 10-observability/observability-overview.md
last_updated: 2026-01-26
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

### Current Deployment (Production)

#### Access URLs

| Component | Internal URL | External URL |
|-----------|-------------|--------------|
| Unleash Server (Admin UI) | `http://unleash-server.production.svc:4242` | `https://unleash.invistus.com.br` |
| Unleash Edge (API) | `http://unleash-edge.production.svc:3063` | `https://unleash-api.invistus.com.br` |

#### Network Flow Diagram

```
                    ┌─────────────────────────────┐
                    │       invistus.com.br       │
                    │     (Next.js Frontend)      │
                    └─────────────┬───────────────┘
                                  │
                                  │ Browser fetches flags
                                  ▼
┌───────────────────────────────────────────────────────────────┐
│                unleash-api.invistus.com.br                    │
│                    (Unleash Edge)                             │
│  - Caches flags (TTL: 30s)                                    │
│  - CORS: only invistus.com.br                                 │
│  - Read-only client token                                     │
└─────────────────────────┬─────────────────────────────────────┘
                          │
                          │ Internal sync
                          ▼
┌───────────────────────────────────────────────────────────────┐
│            unleash-server.production.svc:4242                 │
│                   (Unleash Server)                            │
│  - Admin UI: https://unleash.invistus.com.br                  │
│  - Feature flag management                                    │
│  - Strategy configuration                                     │
└─────────────────────────┬─────────────────────────────────────┘
                          │
                          │ Stores data
                          ▼
┌───────────────────────────────────────────────────────────────┐
│              pgbouncer.production.svc:6432                    │
│                      (PgBouncer)                              │
│                          ↓                                    │
│              postgres.production.svc:5432                     │
│                    Database: unleash                          │
└───────────────────────────────────────────────────────────────┘
```

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

**Current Implementation** (using Runtime Configuration pattern):

The frontend uses **window injection** to receive Unleash configuration at runtime, avoiding the need for build-time environment variables. See [Runtime Configuration](runtime-configuration.md) for details.

#### Configuration Flow

```
Vault (secret/web)
    ↓
External Secret (web-runtime-config)
    ↓
Pod Environment Variables
    ↓
Next.js Server (reads process.env)
    ↓
HTML (window.__RUNTIME_CONFIG__)
    ↓
Browser (FeatureFlagsProvider)
```

#### Key Files

| File | Purpose |
|------|---------|
| `web/src/shared/config/runtime-config.ts` | Config schema and loader |
| `web/src/shared/config/RuntimeConfigScript.tsx` | Injects config into HTML |
| `web/src/shared/providers/FeatureFlagsProvider.tsx` | Unleash client setup |
| `web/src/app/layout.tsx` | Includes RuntimeConfigScript |

#### Configuration Values

| Variable | Source | Example |
|----------|--------|---------|
| `UNLEASH_PROXY_URL` | Vault `secret/web` | `https://unleash-api.invistus.com.br` |
| `UNLEASH_CLIENT_TOKEN` | Vault `secret/web` | `default:production.xxx...` |
| `RUNTIME_ENV` | Vault `secret/web` | `production` |

#### Security

- CORS configured on Unleash Edge to accept only `invistus.com.br`
- Client token is read-only (cannot modify flags)
- Token scoped to `production` environment

#### Refresh Intervals

- **Client Proxy SDK**: 30 seconds (configured in FeatureFlagsProvider)
- **Edge Cache TTL**: 30 seconds (configured in helmrelease-edge.yaml)

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

## Token Types and Configuration

### Understanding Unleash Token Types

Unleash uses different token types for different purposes:

| Token Type | Purpose | Used By | Format Example |
|------------|---------|---------|----------------|
| **Client Token** | Backend services authenticate with Server | Unleash Edge, Backend SDKs | `default:production.xxx` or `*:environment.xxx` |
| **Frontend Token** | Browser/mobile apps fetch flags | Web browsers, Mobile apps | `*:production.xxx` or `project:environment.xxx` |
| **Admin Token** | Full access to Unleash API | CI/CD, Admin tools | `*:*.xxx` or `user:xxx` |

### Token Configuration Flow

```
┌─────────────────────────────────────────────────────────────┐
│                     Unleash Server (Admin UI)               │
│                                                             │
│  Create tokens:                                             │
│  1. Client Token   (for Edge → Server)                      │
│  2. Frontend Token (for Browser → Edge)                     │
└─────────────────────────────────────────────────────────────┘
                          ↓
                    Store in Vault
                          ↓
        ┌─────────────────┴─────────────────┐
        ↓                                   ↓
secret/unleash                        secret/web
├── server-token: <CLIENT-TOKEN>      ├── unleash-client-token: <FRONTEND-TOKEN>
└── (Edge uses this)                  └── (Browsers use this)
```

### CRITICAL: Token Assignment

**IMPORTANT:** Ensure tokens are assigned correctly:

- ✅ **Edge TOKENS env var** = Client Token (for Edge → Server auth)
- ✅ **Web app config** = Frontend Token (for Browser → Edge auth)
- ❌ **DO NOT swap these** - Edge with Frontend token will cause HTTP 403 errors

## Vault Secrets Structure

### Unleash Server/Edge Credentials

```
secret/unleash
├── username: unleash_app
├── password: <db-password>
├── database: unleash
├── server-token: <CLIENT-TOKEN>     # Edge uses this to auth with Server
└── proxy-token: <FRONTEND-TOKEN>    # (deprecated, use web.unleash-client-token)
```

### Web Frontend Configuration

```
secret/web
├── runtime-env: production
├── unleash-proxy-url: https://unleash-api.invistus.com.br
├── unleash-client-token: <FRONTEND-TOKEN>   # Browsers use this
└── graphql-endpoint: http://backend.svc/graphql
```

### External Secrets

| Secret Name | Source | Used By |
|-------------|--------|---------|
| `unleash-credentials` | `secret/unleash` | Unleash Server |
| `unleash-proxy-token` | `secret/unleash` | Unleash Edge |
| `web-runtime-config` | `secret/web` | Next.js Web |

## Troubleshooting

### HTTP 403 Forbidden from Edge

**Symptom:** Clients receive HTTP 403 when fetching flags from Edge.

**Common Causes:**

1. **Token Type Mismatch** (most common)
   - Check: Edge might have Frontend token instead of Client token
   - Fix: Verify `secret/unleash.server-token` contains the **Client token**
   ```bash
   # Check current token in Edge
   kubectl get secret unleash-server-token -n production -o jsonpath='{.data.UNLEASH_SERVER_API_TOKEN}' | base64 -d

   # Should start with 'default:production.' or similar Client token format
   ```

2. **Token Not Found in Unleash**
   - Check: Token might not exist or be disabled in Unleash UI
   - Fix: Go to Unleash UI → Settings → API Access, verify token exists and is enabled

3. **CORS Issues**
   - Check: Request missing Origin header or from unauthorized domain
   - Fix: Verify CORS middleware allows your domain in [middleware-cors.yaml](invistus-flux/infra/kubernetes/flux/apps/unleash/middleware-cors.yaml)

### Edge Cannot Connect to Server

**Symptom:** Edge logs show `Failed to send metrics: EdgeMetricsError`

**Common Causes:**

1. **Wrong Token Type**
   - Edge needs **Client token**, not Frontend token
   - Check token assignment (see Token Configuration Flow above)

2. **Network Connectivity**
   - Verify Edge can reach Server:
   ```bash
   kubectl run nettest --image=curlimages/curl --rm -i --restart=Never -n production \
     --overrides='{"spec":{"containers":[{"name":"test","image":"curlimages/curl","command":["curl","http://unleash-server.production.svc.cluster.local:4242/health"],"resources":{"limits":{"cpu":"100m","memory":"128Mi"},"requests":{"cpu":"50m","memory":"64Mi"}}}]}}'
   ```

3. **Token Invalid/Expired**
   - Verify token works directly against Server:
   ```bash
   kubectl run test-token --image=curlimages/curl --rm -i --restart=Never -n production \
     --overrides='{"spec":{"containers":[{"name":"test","image":"curlimages/curl","command":["curl","-H","Authorization: <CLIENT-TOKEN>","http://unleash-server.production.svc.cluster.local:4242/api/client/features"],"resources":{"limits":{"cpu":"100m","memory":"128Mi"},"requests":{"cpu":"50m","memory":"64Mi"}}}]}}'
   ```

### Verifying Configuration

```bash
# 1. Check Edge is using correct token
kubectl get deployment unleash-edge -n production -o jsonpath='{.spec.template.spec.containers[0].env[?(@.name=="TOKENS")]}' | jq '.'

# 2. Verify tokens in Vault
kubectl exec -n production vault-0 -- vault kv get -format=json secret/unleash | jq -r '.data.data | {"server-token": .["server-token"][:50], "proxy-token": .["proxy-token"][:50]}'

# 3. Test client connection
curl -H "Authorization: $(kubectl get secret web-runtime-config -n production -o jsonpath='{.data.UNLEASH_CLIENT_TOKEN}' | base64 -d)" \
  https://unleash-api.invistus.com.br/api/frontend

# Should return: {"toggles":[...]}
```

### Force Secret Refresh

If you update Vault but secrets aren't syncing:

```bash
# Force ExternalSecret to sync
kubectl annotate externalsecret unleash-server-token -n production force-sync="$(date +%s)" --overwrite
kubectl annotate externalsecret web-runtime-config -n production force-sync="$(date +%s)" --overwrite

# Restart Edge to pick up new secrets
kubectl rollout restart deployment unleash-edge -n production
```

## Acceptance Criteria
- Flags can be evaluated in both Next.js and Kotlin services.
- Flags update within configured TTL.
- Canary rollout works using context attributes.
- DB isolation is in place and credentials are managed via Vault.
- Frontend uses runtime configuration (no build-time env vars).
- Edge uses Client token, browsers use Frontend token (correct token assignment).
