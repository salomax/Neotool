---
title: Umami Web Analytics Runbook
type: runbook
category: observability
status: current
version: 1.0.0
tags: [runbook, analytics, umami, web-analytics, kubernetes, flux, vault, pgbouncer]
related:
  - 92-adr/0012-web-analytics-tooling.md
  - 10-observability/observability-overview.md
---

# Umami Web Analytics — Runbook

> **Purpose**: Operational guide for deploying, configuring, and maintaining the Umami self-hosted analytics instance.

## Quick Reference

| Item | Value |
|---|---|
| Dashboard | https://webanalytics.invistus.com.br |
| K8s namespace | `monitoring` |
| K8s deployment | `umami` |
| Image | `ghcr.io/umami-software/umami:postgresql-latest` |
| Database | `umami_db` (PostgreSQL via PgBouncer) |
| Vault path | `secret/umami` |
| Web config (Vault) | `secret/web` → `umami-website-id`, `umami-script-url` |
| Feature flag | `web-analytics-enabled` (Unleash) |
| Script URL | `https://webanalytics.invistus.com.br/script.js` |

---

## Architecture

```
Browser
  └── Next.js (invistus.com.br)
        └── UmamiAnalytics.tsx
              ├── Unleash flag: web-analytics-enabled  [on/off switch]
              └── RuntimeConfig: umamiWebsiteId        [empty = disabled]

Umami server (monitoring namespace)
  └── K8s Deployment/umami
        ├── ExternalSecret → Vault secret/umami
        │     ├── DATABASE_URL
        │     └── APP_SECRET
        └── Ingress → webanalytics.invistus.com.br (Traefik + cert-manager)

PostgreSQL (production namespace)
  └── umami_db (via PgBouncer port 6432)
```

**Security layers:**
- Umami credentials never touch git — synced via ExternalSecret from Vault
- Web website-id exposed only via runtime config (not build time)
- NetworkPolicy allows `monitoring/app=umami` → `production/pgbouncer:6432` explicitly

---

## Files

### `invistus` repo

| File | Purpose |
|---|---|
| `web/src/shared/analytics/UmamiAnalytics.tsx` | Script tag component (Unleash + websiteId gating) |
| `web/src/shared/analytics/umami.ts` | SSR-safe helpers: `identifyUser`, `trackEvent`, `trackPageView` |
| `web/src/shared/config/runtime-config.ts` | `RuntimeConfig` interface (`umamiWebsiteId`, `umamiScriptUrl`) |
| `web/src/shared/providers/AuthProvider.tsx` | Calls `identifyUser(user.id)` after `signIn` and `signInWithOAuth` |
| `web/src/app/layout.tsx` | Mounts `<UmamiAnalytics />` in `<head>` |
| `infra/postgres/init-databases.sh` | Creates `umami_db` on PostgreSQL container init |
| `infra/kubernetes/scripts/vault-setup.sh` | Configures Vault role to include `monitoring` namespace |

### `invistus-flux` repo

| File | Purpose |
|---|---|
| `apps/monitoring/umami/external-secret.yaml` | Syncs `DATABASE_URL` and `APP_SECRET` from Vault |
| `apps/monitoring/umami/deployment.yaml` | Umami deployment in `monitoring` namespace |
| `apps/monitoring/umami/service.yaml` | ClusterIP service port 3000 |
| `apps/monitoring/umami/ingress.yaml` | Traefik ingress → `webanalytics.invistus.com.br` |
| `apps/monitoring/kustomization.yaml` | Registers Umami resources with Flux |
| `infrastructure/external-secrets-config/monitoring-secret-store.yaml` | ServiceAccount + SecretStore in `monitoring` namespace |
| `infrastructure/external-secrets-config/web-external-secret.yaml` | Adds `UMAMI_WEBSITE_ID` and `UMAMI_SCRIPT_URL` to web secret |
| `infrastructure/namespace/monitoring-resource-quota.yaml` | ResourceQuota (1200Mi mem to fit Umami's 128Mi request) |
| `infrastructure/network-policies/pgbouncer-allow-apps.yaml` | Cross-namespace policy: monitoring/umami → production/pgbouncer |
| `apps/database/pgbouncer/deployment.yaml` | PgBouncer template includes `umami_db` entry |
| `apps/web/nextjs/deployment.yaml` | Adds `UMAMI_WEBSITE_ID` and `UMAMI_SCRIPT_URL` env vars |
| `apps/web/nextjs/middleware-security-headers.yaml` | CSP allows `webanalytics.invistus.com.br` in `script-src` and `connect-src` |

---

## Initial Setup (first deploy)

### 1. Create the database

The `infra/postgres/init-databases.sh` script will create `umami_db` automatically on the next
PostgreSQL container restart. For an already-running cluster, create it manually:

```bash
kubectl exec -n production postgres-0 -- \
  psql -U neotool -d neotool_db -c "CREATE DATABASE umami_db OWNER neotool;"
```

### 2. Populate Vault secrets

```bash
# Connect to Vault
kubectl exec -it -n production vault-0 -- sh

# Inside the pod:
vault kv put secret/umami \
  database-url="postgresql://neotool:<POSTGRES_PASSWORD>@pgbouncer.production.svc.cluster.local:6432/umami_db" \
  app-secret="$(openssl rand -base64 32)"
```

> The `POSTGRES_PASSWORD` is the same credential in `secret/postgres` → `password`.

### 3. Update Vault role for monitoring namespace

If re-running vault-setup.sh from scratch, the role already includes `monitoring`.
For an existing cluster, apply the update once:

```bash
kubectl exec -n production vault-0 -- sh -c "
  vault write auth/kubernetes/role/app \
    bound_service_account_names=external-secrets-vault-auth \
    bound_service_account_namespaces=production,monitoring \
    policies=app-policy \
    ttl=24h
"
```

### 4. Deploy via Flux

```bash
flux reconcile source git flux-system
flux reconcile kustomization infrastructure
flux reconcile kustomization monitoring
```

### 5. First login and site creation

1. Access https://webanalytics.invistus.com.br
2. Login: `admin` / `umami` — **change immediately** in Settings → Profile
3. Go to Settings → Websites → Add website
   - Name: `Invistus`
   - Domain: `invistus.com.br`
4. Copy the **Website ID** (UUID)

### 6. Activate analytics in the web app

```bash
# Store website-id and script URL in Vault
kubectl exec -n production vault-0 -- sh -c "
  vault kv patch secret/web \
    umami-website-id='<WEBSITE_ID_FROM_STEP_5>' \
    umami-script-url='https://webanalytics.invistus.com.br/script.js'
"

# Force ExternalSecret re-sync
kubectl annotate externalsecret web-runtime-config -n production \
  reconcile.external-secrets.io/requested-at="$(date -u +%Y-%m-%dT%H:%M:%SZ)" --overwrite

# Restart web to pick up new runtime config
kubectl rollout restart deployment/neotool-web -n production
```

### 7. Enable the feature flag

In Unleash at https://unleash.invistus.com.br, enable the flag `web-analytics-enabled`
for the production environment. Analytics will start loading on the next page visit.

---

## Day-to-Day Operations

### Check pod health

```bash
kubectl get pods -n monitoring -l app=umami
kubectl logs -n monitoring deployment/umami --tail=50
```

### Verify ExternalSecret sync

```bash
kubectl get externalsecret umami-credentials -n monitoring
# Expected: STATUS=SecretSynced, READY=True
```

### Check ingress and TLS

```bash
kubectl get ingress umami -n monitoring
kubectl get certificate umami-tls -n monitoring
# Expected: certificate READY=True
```

### Enable / disable analytics without redeploy

Toggle the `web-analytics-enabled` flag in Unleash. Changes take effect on the next
page load — no restart required.

---

## Maintenance

### Rotate APP_SECRET

```bash
kubectl exec -n production vault-0 -- sh -c "
  vault kv patch secret/umami app-secret=\"$(openssl rand -base64 32)\"
"

# Force ExternalSecret re-sync (happens automatically within 1h, or manually:)
kubectl annotate externalsecret umami-credentials -n monitoring \
  reconcile.external-secrets.io/requested-at="$(date -u +%Y-%m-%dT%H:%M:%SZ)" --overwrite

kubectl rollout restart deployment/umami -n monitoring
```

> Rotating APP_SECRET invalidates all active Umami sessions. Users will need to log in again.

### Update Umami version

Edit the image tag in [apps/monitoring/umami/deployment.yaml](../../invistus-flux/infra/kubernetes/flux/apps/monitoring/umami/deployment.yaml),
commit and push. Flux will apply the rollout automatically.

```bash
# Check available tags at: https://github.com/umami-software/umami/pkgs/container/umami
# Current pinning: postgresql-latest (tracks latest PostgreSQL-compatible build)
```

### Add a second tracked website (e.g. staging)

In the Umami dashboard: Settings → Websites → Add website → copy the new Website ID.
The same Umami instance can track multiple sites. No infrastructure change needed.

---

## Troubleshooting

### Pod not starting — `no such database: umami_db`

The database doesn't exist in PostgreSQL.

```bash
kubectl exec -n production postgres-0 -- \
  psql -U neotool -d neotool_db -c "CREATE DATABASE umami_db OWNER neotool;"
kubectl rollout restart deployment/umami -n monitoring
```

### Pod not starting — `ExternalSecret status: InProgress`

The Vault key doesn't exist or the monitoring namespace isn't in the Vault role.

```bash
# Check ExternalSecret status
kubectl describe externalsecret umami-credentials -n monitoring

# Verify Vault has the secret
kubectl exec -n production vault-0 -- vault kv get secret/umami

# If missing, populate it (see Initial Setup → Step 2)
# If role issue, update the role (see Initial Setup → Step 3)
```

### Pod failing — quota exceeded

```bash
kubectl describe resourcequota monitoring-quota -n monitoring
# If requests.memory is near limit, update monitoring-resource-quota.yaml in invistus-flux
```

### Umami pod running but analytics not loading on web

1. Check the feature flag: `web-analytics-enabled` must be `true` in Unleash for production
2. Check the website-id in Vault: `kubectl exec -n production vault-0 -- vault kv get secret/web | grep umami`
3. Check web runtime config: inspect `window.__RUNTIME_CONFIG__` in browser console — `umamiWebsiteId` must be non-empty
4. Check ingress is accessible: `curl -I https://webanalytics.invistus.com.br/script.js`

### PgBouncer not routing `umami_db` connections

```bash
# Verify PgBouncer config has umami_db
kubectl exec -n production deployment/pgbouncer -- cat /etc/pgbouncer/pgbouncer.ini | grep umami

# If missing (e.g. after PgBouncer redeploy from stale ConfigMap), restart PgBouncer:
kubectl rollout restart deployment/pgbouncer -n production
```

### Flux monitoring kustomization stuck

```bash
flux get kustomizations
# If infrastructure is not ready, it blocks monitoring
flux reconcile kustomization infrastructure --with-source
flux reconcile kustomization monitoring
```

---

## Known Issues & Decisions

| Issue | Decision |
|---|---|
| `umami-enabled` config was redundant with Unleash flag | Removed — `umamiWebsiteId` being non-empty + Unleash flag is sufficient |
| Grafana in `monitoring` uses plain K8s Secret (`admin/admin`) | Known debt — change password manually post-deploy; Umami uses Vault instead |
| `umami_db` uses `neotool` user (shared) | Acceptable for now — Umami only reads/writes its own schema tables |
| `postgresql-latest` image tag is mutable | Acceptable for `monitoring` tools; pin to a specific version if stricter control is needed |
