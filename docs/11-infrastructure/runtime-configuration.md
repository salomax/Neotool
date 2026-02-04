---
title: Runtime Configuration
type: infrastructure
category: deployment
status: current
version: 1.0.0
tags: [runtime-config, next.js, kubernetes, vault, environment]
ai_optimized: true
search_keywords: [runtime config, window injection, environment variables, vault, external secrets]
related:
  - 11-infrastructure/feature-flags-unleash.md
  - 11-infrastructure/web-deployment-runbook.md
  - 09-security/vault-setup.md
last_updated: 2026-01-26
---

# Runtime Configuration

> **Purpose**: Document the runtime configuration pattern used by the Next.js frontend to inject environment-specific settings without requiring image rebuilds.

## Overview

The Runtime Configuration system allows the Next.js application to receive configuration values at **runtime** instead of **build time**. This follows industry best practices used by Netflix, Airbnb, and other large-scale applications.

### Benefits

| Aspect | Build-time (NEXT_PUBLIC_*) | Runtime (Window Injection) |
|--------|---------------------------|---------------------------|
| Config change | Requires rebuild | Just restart pod |
| Secret management | GitHub Secrets | Centralized in Vault |
| Environment parity | Different images per env | Same image, different config |
| Deployment speed | Slow (rebuild + push) | Fast (just restart) |

## Architecture

```
┌─────────────┐     ┌──────────────┐     ┌─────────────┐
│   Vault     │ ──► │ External     │ ──► │ K8s Secret  │
│  (source)   │     │ Secrets      │     │             │
└─────────────┘     └──────────────┘     └──────┬──────┘
                                                │
                                                ▼
                                    ┌───────────────────┐
                                    │ Pod Environment   │
                                    │ Variables         │
                                    └─────────┬─────────┘
                                              │
                                              ▼
                                    ┌───────────────────┐
                                    │ Next.js Server    │
                                    │ (reads env vars)  │
                                    └─────────┬─────────┘
                                              │
                                              ▼
                                    ┌───────────────────┐
                                    │ HTML Response     │
                                    │ <script>          │
                                    │   window.         │
                                    │   __RUNTIME_      │
                                    │   CONFIG__ = {}   │
                                    │ </script>         │
                                    └─────────┬─────────┘
                                              │
                                              ▼
                                    ┌───────────────────┐
                                    │ Browser           │
                                    │ (reads window.    │
                                    │  __RUNTIME_       │
                                    │  CONFIG__)        │
                                    └───────────────────┘
```

## Implementation

### 1. Configuration Schema

**File**: `web/src/shared/config/runtime-config.ts`

```typescript
export interface RuntimeConfig {
  env: string;                    // Environment name
  unleashProxyUrl: string;        // Feature flags proxy URL
  unleashClientToken: string;     // Feature flags client token
  graphqlEndpoint?: string;       // GraphQL API endpoint
  googleClientId?: string;        // OAuth client ID
}
```

### 2. Server-Side Injection

**File**: `web/src/shared/config/RuntimeConfigScript.tsx`

The server component reads environment variables and injects them into the HTML:

```typescript
export function RuntimeConfigScript() {
  const configScript = generateConfigScript();
  return (
    <script
      id="runtime-config"
      dangerouslySetInnerHTML={{ __html: configScript }}
    />
  );
}
```

### 3. Client-Side Access

**File**: `web/src/shared/hooks/useRuntimeConfig.ts`

```typescript
export function useRuntimeConfig(): RuntimeConfig {
  return useMemo(() => getRuntimeConfig(), []);
}
```

The `getRuntimeConfig()` function checks:
1. **Browser**: Returns `window.__RUNTIME_CONFIG__`
2. **Server**: Returns values from `process.env`

### 4. Layout Integration

**File**: `web/src/app/layout.tsx`

```tsx
<html>
  <head>
    <RuntimeConfigScript />  {/* Injected before any JS */}
  </head>
  <body>...</body>
</html>
```

## Vault Configuration

### Secret Path

```
secret/web
├── runtime-env: "production"
├── unleash-proxy-url: "https://unleash-api.invistus.com.br"
├── unleash-client-token: "default:production.xxx..."
└── graphql-endpoint: "http://backend.svc/graphql"
```

### Adding/Updating Values

```bash
kubectl exec -n production vault-0 -- sh -c '
  VAULT_TOKEN=<token> vault kv put secret/web \
    runtime-env="production" \
    unleash-proxy-url="https://unleash-api.invistus.com.br" \
    unleash-client-token="default:production.xxx" \
    graphql-endpoint="http://neotool-backend.production.svc.cluster.local:8080/graphql"
'
```

## External Secret

**File**: `infra/kubernetes/flux/infrastructure/external-secrets-config/web-external-secret.yaml`

```yaml
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: web-runtime-config
  namespace: production
spec:
  refreshInterval: 1h
  secretStoreRef:
    name: vault-backend
    kind: SecretStore
  target:
    name: web-runtime-config
    creationPolicy: Owner
  data:
    - secretKey: UNLEASH_PROXY_URL
      remoteRef:
        key: web
        property: unleash-proxy-url
    - secretKey: UNLEASH_CLIENT_TOKEN
      remoteRef:
        key: web
        property: unleash-client-token
    - secretKey: RUNTIME_ENV
      remoteRef:
        key: web
        property: runtime-env
    - secretKey: GRAPHQL_ENDPOINT
      remoteRef:
        key: web
        property: graphql-endpoint
```

## Deployment Configuration

**File**: `infra/kubernetes/flux/apps/web/nextjs/deployment.yaml`

```yaml
env:
  - name: RUNTIME_ENV
    valueFrom:
      secretKeyRef:
        name: web-runtime-config
        key: RUNTIME_ENV
  - name: UNLEASH_PROXY_URL
    valueFrom:
      secretKeyRef:
        name: web-runtime-config
        key: UNLEASH_PROXY_URL
  - name: UNLEASH_CLIENT_TOKEN
    valueFrom:
      secretKeyRef:
        name: web-runtime-config
        key: UNLEASH_CLIENT_TOKEN
```

## Local Development

For local development, create `.env.local`:

```bash
RUNTIME_ENV=development
UNLEASH_PROXY_URL=http://localhost:4242/api/frontend
UNLEASH_CLIENT_TOKEN=your-dev-token
GRAPHQL_ENDPOINT=http://localhost:4000/graphql
```

## Security Considerations

### What's Safe to Expose

The runtime config is injected into the HTML and visible in browser DevTools. Only include:

- **Public URLs** (API endpoints, CDN URLs)
- **Feature flag tokens** (read-only, scoped)
- **Environment identifiers**
- **Public OAuth client IDs**

### What NOT to Include

Never include in runtime config:

- API keys with write access
- Database credentials
- JWT signing keys
- Admin tokens
- Any server-only secrets

### CORS Protection

For additional security, configure CORS on external services (like Unleash Edge) to only accept requests from your domain:

```yaml
env:
  - name: CORS_ORIGINS
    value: "https://invistus.com.br,https://www.invistus.com.br"
```

## Troubleshooting

### Config Not Loading

1. Check if script is in HTML:
   ```bash
   curl -s https://invistus.com.br | grep "__RUNTIME_CONFIG__"
   ```

2. Check pod environment:
   ```bash
   kubectl exec -n production deploy/neotool-web -- env | grep -E "UNLEASH|RUNTIME"
   ```

3. Check External Secret status:
   ```bash
   kubectl get externalsecret -n production web-runtime-config
   ```

### Config Values Empty

1. Check Vault has values:
   ```bash
   kubectl exec -n production vault-0 -- vault kv get secret/web
   ```

2. Check secret was created:
   ```bash
   kubectl get secret -n production web-runtime-config -o yaml
   ```

### Changes Not Reflecting

1. Force External Secret refresh:
   ```bash
   kubectl annotate externalsecret -n production web-runtime-config \
     force-sync=$(date +%s) --overwrite
   ```

2. Restart deployment:
   ```bash
   kubectl rollout restart deployment/neotool-web -n production
   ```

## Adding New Configuration Values

1. **Add to Vault**:
   ```bash
   vault kv patch secret/web new-key="new-value"
   ```

2. **Update External Secret** (`web-external-secret.yaml`):
   ```yaml
   - secretKey: NEW_KEY
     remoteRef:
       key: web
       property: new-key
   ```

3. **Update Deployment** (`deployment.yaml`):
   ```yaml
   - name: NEW_KEY
     valueFrom:
       secretKeyRef:
         name: web-runtime-config
         key: NEW_KEY
   ```

4. **Update TypeScript type** (`runtime-config.ts`):
   ```typescript
   export interface RuntimeConfig {
     // ... existing
     newKey?: string;
   }
   ```

5. **Update `getRuntimeConfig()`**:
   ```typescript
   return {
     // ... existing
     newKey: process.env.NEW_KEY || '',
   };
   ```

## References

- [Next.js Runtime Configuration](https://nextjs.org/docs/app/building-your-application/configuring/environment-variables)
- [External Secrets Operator](https://external-secrets.io/latest/)
- [HashiCorp Vault KV](https://developer.hashicorp.com/vault/docs/secrets/kv)
- [Vault Setup Guide](../09-security/vault-setup.md)
