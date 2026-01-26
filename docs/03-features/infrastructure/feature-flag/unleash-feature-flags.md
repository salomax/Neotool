# Unleash Feature Flags Implementation

**Type**: Full-Stack Feature (Infrastructure + Frontend + Backend)  
**Module**: infrastructure  
**Status**: Draft

## Problem/Context

The application needs a centralized feature flag system to enable:
- Gradual rollouts and canary releases
- Context-based targeting (user, tenant, plan, role, region)
- Server-side evaluation for Kotlin services and Next.js SSR
- Client-side evaluation for browser UI with bootstrap to avoid flicker
- Low latency and minimal per-request overhead

Unleash provides a production-ready solution with Edge/Proxy for client access and server SDKs for backend services.

## Solution/Approach

Deploy Unleash Server and Edge/Proxy in Kubernetes, configure separate Postgres database, integrate with Next.js (server + client SDKs), and add Unleash JVM SDK to Kotlin services. Use Vault for secret management and ExternalSecrets for K8s secret sync.

## Success Criteria

### Infrastructure
- [ ] Unleash Server deployed in K8s (Helm chart or manifests)
- [ ] Unleash Edge/Proxy deployed and accessible
- [ ] Separate `unleash` database created with dedicated user
- [ ] Database credentials stored in Vault and synced via ExternalSecret
- [ ] PgBouncer configured to support `unleash_db`
- [ ] Idempotent DB init Job creates database/user if missing
- [ ] Network policies allow required traffic flows
- [ ] Unleash tokens (server, client) stored in Vault and synced to K8s

### Next.js Frontend
- [ ] `FeatureFlagsProvider` added to `web/src/app/providers.tsx`
- [ ] Server-only Unleash client for SSR and route handlers
- [ ] Client Proxy SDK with bootstrap to match SSR
- [ ] Refresh intervals configured (server: 15-30s, client: 30-60s)
- [ ] Flags update within configured TTL
- [ ] No flicker on initial client render (bootstrap working)

### Kotlin Backend
- [ ] Unleash JVM SDK dependency added to services
- [ ] Unleash client configured with server endpoint and token
- [ ] Context evaluation per request (userId, tenantId, role, plan, region, environment)
- [ ] TTL configured for flag refresh
- [ ] Flags can be evaluated in all Kotlin services

### Observability
- [ ] Flag evaluation rate tracked
- [ ] Cache hit ratio (Edge/Proxy) monitored
- [ ] SDK refresh failures logged
- [ ] Dashboards/alerts for Unleash Server and Edge/Proxy health

### Functional
- [ ] Flags can be evaluated in both Next.js and Kotlin services
- [ ] Canary rollout works using context attributes (userId, tenantId, etc.)
- [ ] Gradual rollout with flexibleRollout strategy works
- [ ] Constraint rules for tenant/role gating work

## Integration Points

### Provider Pattern (Next.js)
- **Path**: `web/src/app/providers.tsx`
- **Purpose**: Learn how providers are structured and nested
- **Pattern**: Follow existing provider pattern (AppThemeProvider, AuthProvider, etc.)

### ExternalSecret Pattern
- **Path**: `infra/kubernetes/flux/infrastructure/external-secrets-config/postgres-external-secret.yaml`
- **Purpose**: Follow pattern for syncing secrets from Vault to K8s
- **Pattern**: Use same SecretStore (`vault-backend`), similar data mapping structure

### HelmRelease Pattern
- **Path**: `infra/kubernetes/flux/infrastructure/external-secrets/helmrelease.yaml`
- **Purpose**: Learn HelmRelease structure for deploying Unleash via Helm
- **Pattern**: Similar structure with dependencies, values, resources

### Micronaut SDK Configuration
- **Path**: `service/kotlin/common/src/main/kotlin/io/github/salomax/neotool/common/security/service/ServiceTokenClient.kt`
- **Purpose**: Learn how external SDKs are configured in Micronaut services
- **Pattern**: Use `@Property` annotations, `@Singleton`, dependency injection

### Database Init Job Pattern
- **Path**: `infra/kubernetes/flux/apps/database/postgres/statefulset.yaml`
- **Purpose**: Reference for K8s Job structure (create similar for DB init)
- **Pattern**: Use init containers or separate Jobs for one-time setup

### PgBouncer Configuration
- **Path**: `infra/pgbouncer/pgbouncer.ini`
- **Purpose**: Understand current PgBouncer setup to add `unleash_db` entry
- **Pattern**: Add second database entry or use wildcard pattern

## Technical Constraints

- **Database Isolation**: Use separate database (not just schema) for Unleash
- **Secret Management**: All credentials must go through Vault → ExternalSecret → K8s Secret
- **Network Security**: Network policies must restrict access appropriately
- **Performance**: Server SDK refresh: 15-30s, Client Proxy: 30-60s
- **Bootstrap Requirement**: Client must bootstrap flags to match SSR (no flicker)
- **Context Attributes**: Must support userId, tenantId, role, plan, region, environment

## Implementation Tasks

### 1. Infrastructure (K8S)
1. Create HelmRelease for Unleash Server (or use manifests)
2. Create HelmRelease/Deployment for Unleash Edge/Proxy
3. Create ExternalSecret for Unleash DB credentials
4. Create ExternalSecret for Unleash Server API token
5. Create ExternalSecret for Unleash Proxy/Edge client tokens
6. Create K8s Job for idempotent DB initialization
7. Update PgBouncer config to support `unleash_db`
8. Create NetworkPolicy resources for required traffic flows
9. Store secrets in Vault (unleash.username, unleash.password, unleash.database, unleash.server-token, unleash.proxy-token)

### 2. Next.js Web
1. Install `@unleash/nextjs` and `@unleash/proxy-client-react` packages
2. Create server-only Unleash client utility (`web/src/lib/feature-flags/server.ts`)
3. Create FeatureFlagsProvider component (`web/src/shared/providers/FeatureFlagsProvider.tsx`)
4. Add FeatureFlagsProvider to `web/src/app/providers.tsx`
5. Create hook for client-side flag access (`web/src/shared/hooks/useFeatureFlag.ts`)
6. Configure refresh intervals in provider

### 3. Kotlin Services
1. Add `io.getunleash:unleash-client-java` dependency to `service/kotlin/common/build.gradle.kts`
2. Create Unleash configuration class (`service/kotlin/common/src/main/kotlin/io/github/salomax/neotool/common/featureflags/UnleashConfig.kt`)
3. Create Unleash client factory/service (`service/kotlin/common/src/main/kotlin/io/github/salomax/neotool/common/featureflags/UnleashService.kt`)
4. Add configuration properties to `application.yml` files
5. Create helper for building UnleashContext from request context

### 4. Observability
1. Add Prometheus metrics for flag evaluation rate
2. Add metrics for Edge/Proxy cache hit ratio
3. Create Grafana dashboard for Unleash health
4. Add alerts for SDK refresh failures

## Open Decisions

- [ ] Use Helm chart or raw K8s manifests for Unleash Server?
- [ ] Which namespace for Unleash components? (suggest: `neotool-observability` or new `neotool-feature-flags`)
- [ ] Should Edge/Proxy be exposed via Ingress or only internal?
- [ ] Resource limits for Unleash Server and Edge/Proxy?

## For LLM Implementation

When implementing:
1. Start with infrastructure (database, Vault secrets, ExternalSecrets)
2. Deploy Unleash Server and Edge/Proxy
3. Integrate Next.js (server client first, then client provider)
4. Integrate Kotlin services (common module first, then service-specific usage)
5. Add observability last

Reference the integration points above to follow existing patterns. Use the requirements document (`docs/11-infrastructure/feature-flags-unleash.md`) for detailed requirements.
