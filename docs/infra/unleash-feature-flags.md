# Unleash Feature Flags - Complete Guide

## Table of Contents
- [Philosophy](#philosophy-flags-are-data-not-code)
- [Architecture](#architecture)
- [Environment Configuration](#environment-configuration)
- [How to Create a New Feature Flag](#how-to-create-a-new-feature-flag)
- [Usage in Code](#usage-in-code)
- [Single Source of Truth](#single-source-of-truth)
- [Troubleshooting](#troubleshooting)
- [References](#references)

---

## Philosophy: Flags are Data, not Code

Feature flags are **dynamic data** that live in the Unleash database, not static code in YAML files or ConfigMaps.

### Why?

- ✅ **Progressive rollouts**: Enable for 5% → 25% → 100% of users
- ✅ **Different states per environment**: Dev can have different flags than staging/prod
- ✅ **Complete audit trail**: Track who changed what and when via UI
- ✅ **No drift**: State in database is the source of truth, not files in Git
- ✅ **Dynamic management**: Change flags without deploy or restart

---

## Architecture

### Components

```
┌─────────────────────────────────────────────────────────────┐
│ Frontend (Next.js Client)                                   │
│ ├─ useFeatureFlag()           → hooks/useFeatureFlag.ts    │
│ └─ FeatureFlagsProvider        → providers/...Provider.tsx │
└────────────────┬────────────────────────────────────────────┘
                 │
                 │ HTTP (Browser)
                 ↓
┌─────────────────────────────────────────────────────────────┐
│ Unleash Edge/Proxy (port 3063)                              │
│ └─ Read-only API for client SDKs                           │
└────────────────┬────────────────────────────────────────────┘
                 │
                 │ Internal
                 ↓
┌─────────────────────────────────────────────────────────────┐
│ Unleash Server (port 4242)                                  │
│ ├─ Admin UI                                                 │
│ ├─ Admin API (create/update/delete flags)                  │
│ └─ Server API (evaluate flags)                             │
└────────────────┬────────────────────────────────────────────┘
                 │
                 │ PostgreSQL
                 ↓
┌─────────────────────────────────────────────────────────────┐
│ PostgreSQL Database                                         │
│ └─ unleash schema (flags, strategies, events)              │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│ Backend Services (Kotlin/Micronaut)                        │
│ └─ UnleashService                  → UnleashService.kt     │
│    └─ Direct connection to Unleash Server (port 4242)     │
└─────────────────────────────────────────────────────────────┘
```

### Data Flow

**Client-side (Browser)**:
```
Frontend → Unleash Edge (Proxy) → Unleash Server → PostgreSQL
```

**Server-side (SSR/API)**:
```
Next.js/Kotlin → Unleash Server → PostgreSQL
```

---

## Environment Configuration

### 🏠 Local Development (Docker Compose)

**Goal**: Quick spin-up with known initial state

#### Start environment

```bash
cd infra/docker
docker-compose --profile feature-flags up
```

This will start:
- `unleash` (Unleash Server) - http://localhost:4242
- `unleash-edge` (Unleash Proxy) - http://localhost:3063
- `unleash-init` (Automatic flag initialization)

#### How it works

1. `unleash-init` container waits for Unleash Server to be ready
2. Reads configuration from `infra/unleash/flags.yaml` (or legacy `flags-init.json`)
3. Script `scripts/unleash-init-flags.mjs` creates flags via Unleash API
4. Flags are created **idempotently** (does not overwrite existing ones)
5. Applies defaults specific to the `development` environment

**Alternative: Use CLI tool**

You can also manage flags using the Neotool CLI:

```bash
# Import flags from YAML file
./neotool flags import infra/unleash/flags.yaml

# List all flags
./neotool flags list

# Enable/disable flags
./neotool flags enable security/login/enabled
./neotool flags disable assistant/enable --env production
```

See [CLI Management](#cli-management) section for details.

#### Required environment variables

Create a `.env.local` file based on `.env.unleash.example`:

```bash
# Unleash Server (backend/SSR)
UNLEASH_URL=http://unleash:4242
UNLEASH_SERVER_API_TOKEN=default:development.unleash-insecure-api-token
UNLEASH_APP_NAME=neotool-service

# Unleash Edge/Proxy (frontend)
NEXT_PUBLIC_UNLEASH_PROXY_URL=http://localhost:3063/proxy
NEXT_PUBLIC_UNLEASH_CLIENT_TOKEN=default:development.unleash-insecure-api-token
NEXT_PUBLIC_ENV=development

# Database Unleash
UNLEASH_USER=unleash_app
UNLEASH_PASSWORD=unleash_password
UNLEASH_DB=unleash
```

**Source of truth in development**: `infra/unleash/flags.yaml` (versioned in Git)

> **Note**: The legacy `flags-init.json` format is still supported but `flags.yaml` is the preferred format.

---

### ☁️ Staging/Production (Kubernetes)

**Goal**: State managed via database, not files

#### Infrastructure deployment

```bash
# Deploy via Flux
kubectl apply -k infra/kubernetes/flux/apps/unleash/
```

This will create:
- Unleash Server (HelmRelease)
- Unleash Edge (HelmRelease)
- PostgreSQL database initialization job (once)
- ConfigMap with service URLs

#### Flag Initialization (RECOMMENDED: Manual)

**First deployment**:

1. **Access Unleash UI**
   ```bash
   # Port-forward (if ingress is not configured)
   kubectl port-forward -n production svc/unleash-server 4242:4242

   # Access
   open http://localhost:4242
   ```

2. **Create flags manually via UI** (recommended)
   - Navigate to Projects → default → Create feature toggle
   - Configure strategies (gradual rollout, constraints, etc.)
   - Enable in desired environments

3. **OR use Neotool CLI** (recommended for bulk operations)
   ```bash
   # Export token from Vault
   export UNLEASH_SERVER_API_TOKEN=$(vault kv get -field=server-token secret/unleash)

   # Import flags from YAML file
   ./neotool flags import infra/unleash/flags.yaml \
     --url https://unleash.example.com

   # Or enable/disable individual flags
   ./neotool flags enable security/login/enabled \
     --url https://unleash.example.com \
     --env production
   ```

4. **OR create via API** (one time only)
   ```bash
   # Export token from Vault
   export UNLEASH_TOKEN=$(vault kv get -field=server-token secret/unleash)

   # Create each flag individually
   curl -X POST http://localhost:4242/api/admin/projects/default/features \
     -H "Authorization: $UNLEASH_TOKEN" \
     -H "Content-Type: application/json" \
     -d '{
       "name": "security/login/enabled",
       "description": "Enable/disable login functionality",
       "type": "release",
       "impressionData": false
     }'
   ```

**Why is manual initialization recommended?**

- ✅ Flags live in database, not in files
- ✅ Allows progressive rollouts via UI (5% → 25% → 100%)
- ✅ Different states per environment
- ✅ Complete audit trail of changes
- ✅ No risk of drift between file and reality
- ✅ No risk of overwriting changes during deploys

#### Option B: Automatic Initialization Job (NOT recommended for production)

The file `infra/kubernetes/flux/apps/unleash/flags-init-job.yaml` exists but is **NOT** included in `kustomization.yaml` by design.

If you **really** need automatic initialization (e.g., staging environment):

```bash
# 1. Edit kustomization.yaml and uncomment
# - flags-init-job.yaml

# 2. Apply
kubectl apply -k infra/kubernetes/flux/apps/unleash/

# 3. Verify
kubectl logs -n production job/unleash-flags-init

# 4. IMPORTANT: After first execution, remove from kustomization.yaml
# From here on, manage flags via UI
```

**Environment variables (Kubernetes)**

Variables are managed via ExternalSecrets (Vault):

```yaml
# ExternalSecret syncs from Vault
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: unleash-tokens
spec:
  secretStoreRef:
    name: vault-backend
  target:
    name: unleash-tokens
  data:
    - secretKey: UNLEASH_SERVER_API_TOKEN
      remoteRef:
        key: secret/unleash
        property: server-token
    - secretKey: UNLEASH_PROXY_CLIENT_TOKEN
      remoteRef:
        key: secret/unleash
        property: client-token
```

**Source of truth in production**: PostgreSQL database (managed via Unleash UI)

---

## How to Create a New Feature Flag

### 1. Plan the Flag

Before creating, define:

- **Name**: Use namespacing, e.g., `security/mfa/enabled`, `assistant/chat/enabled`
- **Description**: Clearly explain what the flag controls
- **Type**:
  - `release`: Toggle functionality on/off (most common)
  - `experiment`: A/B testing
  - `operational`: Operational control (kill switch)
  - `permission`: Access control
- **Initial strategy**: What will be the default state?
  - Disabled in all environments?
  - Enabled only in dev?
  - Gradual rollout (5%, 25%, 50%, 100%)?

### 2. Create the Flag in Development Environment

#### Option A: Via CLI (recommended for dev)

1. **Edit** `infra/unleash/flags.yaml`:

```yaml
flags:
  - name: feature/new-dashboard/enabled
    description: Enable new dashboard UI redesign
    type: release
    enabled: false
```

2. **Import** flags using the CLI:

```bash
./neotool flags import infra/unleash/flags.yaml
```

3. **Verify** that the flag was created:

```bash
./neotool flags list
```

Or access http://localhost:4242 and navigate to Projects → default → Feature toggles

#### Option B: Via file (legacy, still supported)

1. **Edit** `infra/unleash/flags-init.json` (legacy JSON format):

```json
{
  "flags": [
    {
      "name": "feature/new-dashboard/enabled",
      "description": "Enable new dashboard UI redesign",
      "type": "release",
      "impressionData": false,
      "enabled": false,
      "strategies": []
    }
  ]
}
```

2. **Restart** the initialization container:

```bash
cd infra/docker
docker-compose restart unleash-init

# Or if you want to recreate everything
docker-compose --profile feature-flags down
docker-compose --profile feature-flags up
```

3. **Verify** that the flag was created:
   - Access http://localhost:4242
   - Navigate to Projects → default → Feature toggles
   - Confirm that `feature/new-dashboard/enabled` is present

#### Option C: Via UI (always works)

1. **Access** http://localhost:4242
2. **Navigate** to Projects → default → Feature toggles
3. **Click** "New feature toggle"
4. **Fill in**:
   - Name: `feature/new-dashboard/enabled`
   - Description: `Enable new dashboard UI redesign`
   - Type: `Release`
5. **Configure strategy** (or leave default: standard)
6. **Enable/Disable** in desired environments:
   - Development: ✅ ON
   - Production: ❌ OFF

### 3. Create the Flag in Staging/Production

**ALWAYS use Unleash UI**, never file:

1. **Access** Unleash UI in staging/production
   ```bash
   # Port-forward if necessary
   kubectl port-forward -n production svc/unleash-server 4242:4242
   open http://localhost:4242
   ```

2. **Create the flag** exactly as in development (same name!)

3. **Configure rollout strategy**:

   **Example: Gradual Rollout**
   - Strategy: Gradual rollout
   - Percentage: 5% (start with 5% of users)
   - Stickiness: userId (same users always see same state)

   **Example: User Targeting**
   - Strategy: UserIDs
   - User IDs: `user-123, user-456` (beta testers)

   **Example: Tenant-based**
   - Strategy: Constraints
   - Constraint: `tenantId IN (tenant-alpha, tenant-beta)`

4. **Enable gradually**:
   - Day 1: 5% of users
   - Day 3: 25% of users
   - Day 5: 50% of users
   - Day 7: 100% of users

### 4. Use the Flag in Code

#### Frontend (React/Next.js)

**Client-side (hooks)**:

```typescript
import { useFeatureFlag } from '@/shared/hooks/useFeatureFlag';

export function DashboardPage() {
  const { enabled, loading, error } = useFeatureFlag('feature/new-dashboard/enabled');

  if (loading) {
    return <Skeleton />;
  }

  if (error) {
    console.error('Error loading feature flag:', error);
    // Fall back to default behavior
  }

  return enabled ? <NewDashboard /> : <OldDashboard />;
}
```

**Simple boolean check** (when loading state is not needed):

```typescript
import { useFeatureFlagEnabled } from '@/shared/hooks/useFeatureFlag';

export function SettingsMenu() {
  const hasNewSettings = useFeatureFlagEnabled('feature/new-settings/enabled');

  return (
    <Menu>
      {hasNewSettings && <MenuItem>New Settings</MenuItem>}
    </Menu>
  );
}
```

**Server-side (SSR/API routes)**:

```typescript
import { isFeatureEnabled } from '@/lib/feature-flags/server';

export async function getServerSideProps(context) {
  const userId = context.req.session?.userId;

  const newDashboardEnabled = await isFeatureEnabled(
    'feature/new-dashboard/enabled',
    { userId },
    false // default value if Unleash is not available
  );

  return {
    props: {
      newDashboardEnabled,
    },
  };
}
```

**With targeting context** (user, tenant, role, etc.):

```typescript
import { isFeatureEnabled } from '@/lib/feature-flags/server';
import type { UnleashEvaluationContext } from '@/shared/types/unleash';

const context: UnleashEvaluationContext = {
  userId: user.id,
  tenantId: user.tenantId,
  role: user.role,
  plan: user.subscription.plan,
  region: user.region,
  environment: 'production',
};

const hasFeature = await isFeatureEnabled(
  'feature/premium/analytics',
  context,
  false
);
```

#### Backend (Kotlin/Micronaut)

```kotlin
import io.github.salomax.neotool.common.featureflags.UnleashService
import io.getunleash.UnleashContext

@Singleton
class DashboardService(
    private val unleashService: UnleashService
) {
    fun getDashboard(userId: String, tenantId: String): Dashboard {
        val context = UnleashContext.builder()
            .userId(userId)
            .addProperty("tenantId", tenantId)
            .build()

        val useNewDashboard = unleashService.isEnabled(
            "feature/new-dashboard/enabled",
            context
        )

        return if (useNewDashboard) {
            getNewDashboard(userId)
        } else {
            getOldDashboard(userId)
        }
    }
}
```

**With variant** (for A/B testing):

```kotlin
val variant = unleashService.getVariant("experiment/button-color", context)

val buttonColor = when (variant?.name) {
    "blue" -> Color.BLUE
    "green" -> Color.GREEN
    else -> Color.DEFAULT
}
```

### 5. Test the Flag

#### Development

```bash
# 1. Make sure Unleash is running
docker-compose --profile feature-flags up

# 2. Access UI
open http://localhost:4242

# 3. Toggle the flag ON/OFF

# 4. Refresh your application and observe behavior
```

#### Staging/Production

```bash
# 1. Access environment UI
kubectl port-forward -n production svc/unleash-server 4242:4242
open http://localhost:4242

# 2. Use gradual rollout or user targeting to test
# Example: Enable only for your userID first

# 3. Monitor logs and metrics

# 4. Gradually increase percentage
```

### 6. Remove the Flag (Cleanup)

When the feature is 100% stable and no longer needs the flag:

1. **Make sure** the flag is 100% ON in all environments for at least 1 week
2. **Remove** conditional code:
   ```typescript
   // Before
   const enabled = useFeatureFlag('feature/new-dashboard/enabled');
   return enabled ? <NewDashboard /> : <OldDashboard />;

   // After (simplify)
   return <NewDashboard />;
   ```
3. **Delete** `<OldDashboard />` and related code
4. **Deploy** simplified code
5. **Wait** at least 1 week to ensure stability
6. **Delete the flag** via Unleash UI:
   - UI → Feature toggle → Archive
   - After 30 days, delete permanently

---

## CLI Management

The Neotool CLI provides a convenient way to manage feature flags from the command line.

### Prerequisites

- `yq` installed (for YAML parsing): https://github.com/mikefarah/yq
- `jq` installed (for JSON output): https://stedolan.github.io/jq/
- `curl` installed (for API calls)

### Basic Usage

#### List All Flags

```bash
# Default format (table)
./neotool flags list

# JSON format
./neotool flags list --format json

# YAML format
./neotool flags list --format yaml

# Custom Unleash server
./neotool flags list --url https://unleash.example.com
```

#### Import Flags from YAML

```bash
# Import from default file
./neotool flags import infra/unleash/flags.yaml

# Dry run (see what would be created)
./neotool flags import infra/unleash/flags.yaml --dry-run

# Force update existing flags
./neotool flags import infra/unleash/flags.yaml --force

# Custom server
./neotool flags import infra/unleash/flags.yaml \
  --url https://unleash.example.com \
  --token $UNLEASH_SERVER_API_TOKEN
```

#### Enable/Disable Flags

```bash
# Enable in default environment (development)
./neotool flags enable security/login/enabled

# Enable in specific environment
./neotool flags enable assistant/enable --env production

# Disable flag
./neotool flags disable security/login/enabled --env staging

# Skip confirmation prompt (useful for scripts)
./neotool flags enable feature/new-dashboard --env production --yes
```

### YAML File Format

The CLI uses a simplified YAML format compared to the legacy JSON:

```yaml
# Feature flags definition
# Import with: neotool flags import infra/unleash/flags.yaml

flags:
  - name: security/login/enabled
    description: Enable/disable login functionality
    type: release  # release | experiment | operational | permission
    enabled: true  # initial state when importing

  - name: assistant/enable
    description: Enable AI assistant features
    type: release
    enabled: false
```

**Key differences from legacy JSON:**
- No `environmentDefaults` section (manage per-environment via enable/disable commands)
- No `impressionData` or `strategies` (use UI for advanced configuration)
- Simpler structure focused on flag definition

### Environment Variables

The CLI respects the following environment variables:

```bash
# Unleash server URL (default: http://unleash:4242 or http://localhost:4242)
export UNLEASH_SERVER_URL=https://unleash.example.com

# API token (required if not using --token option)
export UNLEASH_SERVER_API_TOKEN=your-token-here
```

### Production Safety

When operating on production environments, the CLI will prompt for confirmation:

```bash
# This will prompt for confirmation
./neotool flags enable feature/new-dashboard --url https://unleash-prod.example.com

# Skip confirmation with --yes
./neotool flags enable feature/new-dashboard \
  --url https://unleash-prod.example.com \
  --yes
```

### Error Handling

The CLI provides clear error messages:

- **404 errors**: Flag not found (check flag name)
- **5xx errors**: Server error (check Unleash server status)
- **Connection errors**: Cannot reach Unleash (check URL and network)
- **Authentication errors**: Invalid token (check token permissions)

### Examples

**Complete workflow:**

```bash
# 1. List existing flags
./neotool flags list

# 2. Import new flags from YAML
./neotool flags import infra/unleash/flags.yaml --dry-run  # Preview
./neotool flags import infra/unleash/flags.yaml            # Import

# 3. Enable a flag in development
./neotool flags enable feature/new-dashboard

# 4. Enable in production (with confirmation)
./neotool flags enable feature/new-dashboard --env production

# 5. Verify
./neotool flags list --format json | jq '.features[] | select(.name == "feature/new-dashboard")'
```

---

## Single Source of Truth

### Development (Docker Compose)

```
infra/unleash/flags.yaml (Git) [preferred]
  OR
infra/unleash/flags-init.json (Git) [legacy]
  ↓
  neotool flags import (CLI) OR scripts/unleash-init-flags.mjs (idempotent)
  ↓
  Unleash API (http://unleash:4242)
  ↓
  PostgreSQL (unleash database)
```

**When to modify**:
- New flags for development
- Change environment defaults
- Add default strategies

**Never**:
- Don't use to manage production state
- Don't expect automatic sync with prod

### Staging/Production (Kubernetes)

```
Humans via Unleash UI/API
  ↓
  Unleash Server (http://unleash-server.production.svc:4242)
  ↓
  PostgreSQL (unleash database) ← SOURCE OF TRUTH
```

**When to modify**:
- Create new flags
- Change rollout strategies
- Adjust percentages
- Enable/disable flags
- Configure targeting (user, tenant, region)

**Export for backup** (optional):
```bash
export UNLEASH_TOKEN=$(vault kv get -field=server-token secret/unleash)

curl https://unleash.example.com/api/admin/features-export \
  -H "Authorization: $UNLEASH_TOKEN" \
  > backup-flags-$(date +%Y%m%d).json
```

---

## Usage in Code

### TypeScript/React (Frontend)

#### Available hooks

```typescript
// Full hook (with loading and error states)
import { useFeatureFlag } from '@/shared/hooks/useFeatureFlag';
const { enabled, loading, error } = useFeatureFlag('flag-name');

// Simple hook (boolean only)
import { useFeatureFlagEnabled } from '@/shared/hooks/useFeatureFlag';
const enabled = useFeatureFlagEnabled('flag-name');

// All flags
import { useFeatureFlags } from '@/shared/hooks/useFeatureFlag';
const flags = useFeatureFlags(); // Record<string, boolean>
```

#### Server-side (Next.js)

```typescript
import { isFeatureEnabled, getFeatureFlags } from '@/lib/feature-flags/server';

// Check a flag
const enabled = await isFeatureEnabled('flag-name', context, defaultValue);

// Get all flags (for bootstrap)
const allFlags = await getFeatureFlags(context);
```

### Kotlin (Backend)

```kotlin
@Inject
lateinit var unleashService: UnleashService

// Simple
val enabled = unleashService.isEnabled("flag-name")

// With context
val context = UnleashContext.builder()
    .userId("user-123")
    .addProperty("tenantId", "tenant-456")
    .build()
val enabled = unleashService.isEnabled("flag-name", context)

// With variant
val variant = unleashService.getVariant("flag-name", context)
```

### Shared types

```typescript
// web/src/shared/types/unleash.ts

interface UnleashEvaluationContext {
  userId?: string;
  tenantId?: string;
  role?: string;
  plan?: string;
  region?: string;
  environment?: string;
}

interface FeatureFlagEvaluationResult {
  enabled: boolean;
  loading: boolean;
  error: Error | null;
}
```

---

## Why NOT Use ConfigMaps for Flags?

### ❌ Anti-Pattern

```yaml
# DON'T DO THIS
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
configMapGenerator:
  - name: unleash-flags-config
    files:
      - flags-init.json
```

### Problems

1. **Drift**: File in Git ≠ State in database
2. **Overwrite**: Kubernetes deploy can revert changes made via UI
3. **Rollouts**: Doesn't allow progressive rollouts (5% → 100%)
4. **Audit**: Loses history of who changed what and when
5. **Duplication**: File duplicated in multiple places
6. **Rigidity**: Requires deploy to change flag

### ✅ Correct Approach

| Resource | Management | Versioning | Where it lives |
|----------|------------|------------|----------------|
| Deployment | Kubernetes YAML | Git | Cluster |
| ConfigMap | Kubernetes YAML | Git | Cluster |
| Secret | ExternalSecret → Vault | Vault | Vault |
| **Feature Flags** | **Unleash UI/API** | **PostgreSQL** | **Database** |

**Feature flags are stateful, not stateless**. Treat them as data, not code.

---

## Troubleshooting

### "My flag doesn't appear in environment X"

**Cause**: Flags are created per environment (development, staging, production)

**Solution**:
1. Access Unleash UI
2. Navigate to Projects → default → Feature toggles → [your-flag]
3. Go to "Environment" tab
4. Enable the flag in the desired environment

### "Flag was reverted after Kubernetes deploy"

**Cause**: You're using ConfigMap or initialization job that overwrites state

**Solution**:
1. **Remove** flags-init-job from kustomization.yaml
2. **Delete** the job if it exists: `kubectl delete job unleash-flags-init`
3. **Recreate** flags via UI
4. **Never again** run initialization job in production

### "Flag doesn't update in frontend"

**Cause**: Unleash Edge cache or refresh interval

**Solution**:
1. Check `UNLEASH_REFRESH_INTERVAL` (default: 15 seconds)
2. Wait for refresh interval
3. Force reload in browser (Cmd+Shift+R)
4. Verify that `NEXT_PUBLIC_UNLEASH_PROXY_URL` is correct

### "Unleash doesn't connect in Kubernetes"

**Cause**: Incorrect environment variables or secrets

**Solution**:
1. Verify ExternalSecret is synced:
   ```bash
   kubectl get externalsecrets -n production
   kubectl get secrets unleash-tokens -n production -o yaml
   ```
2. Check pod logs:
   ```bash
   kubectl logs -n production deployment/unleash-server
   ```
3. Verify PostgreSQL connectivity:
   ```bash
   kubectl exec -n production deployment/unleash-server -- \
     pg_isready -h postgres -U unleash_app
   ```

### "DisabledUnleash is being used (Kotlin)"

**Cause**: `UNLEASH_URL` or `UNLEASH_SERVER_API_TOKEN` variables not configured

**Solution**:
1. Check ConfigMap/Secret with environment variables
2. Check service logs:
   ```bash
   kubectl logs -n production deployment/seu-servico | grep Unleash
   ```
3. Confirm that ExternalSecret is populating the token correctly

### "Loading state never ends (React)"

**Cause**: FeatureFlagsProvider is not initializing or timeout

**Solution**:
1. Verify that `<FeatureFlagsProvider>` is wrapping your application
2. Check browser console for errors
3. Confirm that `NEXT_PUBLIC_UNLEASH_PROXY_URL` is accessible:
   ```bash
   curl http://localhost:3063/proxy/health
   ```
4. Increase timeout if necessary in `lib/feature-flags/server.ts`

---

## References

### Official Documentation
- [Unleash Documentation](https://docs.getunleash.io/)
- [Unleash Best Practices](https://docs.getunleash.io/topics/feature-flags/best-practices)
- [Unleash Strategies](https://docs.getunleash.io/reference/activation-strategies)
- [Unleash SDKs](https://docs.getunleash.io/reference/sdks)

### Concepts
- [Feature Toggles (Martin Fowler)](https://martinfowler.com/articles/feature-toggles.html)
- [GitOps for Config, Not Data](https://www.gitops.tech/#what-is-gitops)
- [12-Factor Apps - Config vs State](https://12factor.net/config)

### Project Files

**Infrastructure**:
- `infra/unleash/flags-init.json` - Initial flag configuration (dev only)
- `infra/docker/docker-compose.local.yml` - Docker Compose with Unleash
- `infra/kubernetes/flux/apps/unleash/` - Kubernetes deployment
- `scripts/unleash-init-flags.mjs` - Idempotent initialization script

**Frontend (TypeScript/React)**:
- `web/src/shared/hooks/useFeatureFlag.ts` - React hooks
- `web/src/shared/providers/FeatureFlagsProvider.tsx` - Provider
- `web/src/lib/feature-flags/server.ts` - Server-side evaluation
- `web/src/shared/types/unleash.ts` - TypeScript types

**Backend (Kotlin)**:
- `service/kotlin/common/src/main/kotlin/io/github/salomax/neotool/common/featureflags/UnleashService.kt`

**Configuration**:
- `.env.unleash.example` - Environment variables example
- `infra/kubernetes/flux/apps/unleash/README.md` → **This file**

---

## Summary: Quick Reference

### Create new flag

**Dev**:
1. Edit `infra/unleash/flags-init.json`
2. `docker-compose restart unleash-init`

**Prod**:
1. Access UI: http://unleash.example.com
2. Create flag manually
3. Configure rollout strategy

### Use in code

**React**:
```typescript
const { enabled, loading } = useFeatureFlag('flag-name');
```

**Next.js SSR**:
```typescript
const enabled = await isFeatureEnabled('flag-name', context, false);
```

**Kotlin**:
```kotlin
val enabled = unleashService.isEnabled("flag-name", context)
```

### Environments

| Environment | Unleash URL | Initialization | Management |
|-------------|-------------|----------------|------------|
| Dev | http://localhost:4242 | Automatic (file) | UI or file |
| Staging | https://unleash-staging.example.com | Manual (UI) | UI only |
| Prod | https://unleash.example.com | Manual (UI) | UI only |

### Useful commands

```bash
# Development
docker-compose --profile feature-flags up
open http://localhost:4242

# Kubernetes
kubectl port-forward -n production svc/unleash-server 4242:4242
kubectl logs -n production deployment/unleash-server

# Vault
vault kv get -field=server-token secret/unleash
```
