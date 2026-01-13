# Flux/GitOps/K8s Infrastructure Code Review

**Date**: 2026-01-13
**Scope**: Complete Flux GitOps infrastructure review
**Status**: 24 issues identified

---

## Executive Summary

### Issues by Severity
- 🔴 **CRITICAL**: 4 issues (TLS, HA, Storage, Unsealing)
- 🟠 **HIGH**: 5 issues (RBAC, Refresh intervals, Resources)
- 🟡 **MEDIUM**: 10 issues (Network policies, Security contexts, Monitoring)
- 🟢 **LOW**: 5 issues (Documentation, Sync intervals)

### Immediate Action Required
1. ✅ **TLS for Vault** - Currently disabled (CRITICAL)
2. ✅ **Vault HA Mode** - Single point of failure (HIGH)
3. ✅ **Unsealing Strategy** - Manual unseal required (CRITICAL)
4. ✅ **Service Account Security** - Using default SA (HIGH)
5. ✅ **ExternalSecret Refresh** - Too slow (1h → 5m) (HIGH)

---

## 🔴 CRITICAL ISSUES

### 1. TLS Disabled in Vault Production
**File**: `infra/kubernetes/flux/infrastructure/vault/helmrelease.yaml:30`

```yaml
# CURRENT (INSECURE)
global:
  tlsDisable: true  # TODO: Enable TLS in production
```

**Risk**: All Vault communication is unencrypted, secrets transmitted in plaintext
**Impact**: Data breach, MITM attacks, compliance violations

**Fix Required**:
```yaml
global:
  tlsDisable: false
```

Plus TLS certificate configuration (see detailed plan below).

---

### 2. Vault Single-Instance Mode
**File**: `infra/kubernetes/flux/infrastructure/vault/helmrelease.yaml:40-42`

```yaml
# CURRENT (NOT PRODUCTION-READY)
ha:
  enabled: false
```

**Risk**: Single point of failure, no redundancy
**Impact**: System-wide outage if Vault pod fails

**Fix Required**:
```yaml
ha:
  enabled: true
  replicas: 3
  raft:
    enabled: true
    setNodeId: true
```

---

### 3. File-Based Storage (Non-HA)
**File**: `infra/kubernetes/flux/infrastructure/vault/helmrelease.yaml:85-87`

```yaml
# CURRENT (NOT SCALABLE)
storage "file" {
  path = "/vault/data"
}
```

**Risk**: Data loss on pod eviction, no backup strategy
**Impact**: Permanent loss of all secrets

**Fix Required**: Migrate to Raft storage with HA mode

---

### 4. No Automated Unsealing Strategy
**Risk**: Manual intervention required after every pod restart
**Impact**: Service disruption, operational overhead

**Fix Required**: Implement auto-unseal or documented unsealing automation

---

## 🟠 HIGH SEVERITY ISSUES

### 5. Default Service Account for Vault Auth
**File**: `infra/kubernetes/flux/infrastructure/external-secrets/secret-store.yaml:20`

```yaml
# CURRENT (INSECURE)
serviceAccountRef:
  name: default
```

**Risk**: Any pod with default SA can access Vault
**Fix**: Create dedicated service account

---

### 6. ExternalSecret Refresh Interval Too Long
**File**: `infra/kubernetes/flux/infrastructure/external-secrets/postgres-external-secret.yaml:9`

```yaml
# CURRENT (TOO SLOW)
refreshInterval: 1h
```

**Risk**: Secret rotation delays up to 1 hour
**Fix**: Reduce to 5 minutes for critical secrets

---

### 7. Missing Pod Disruption Budgets
**Risk**: All pods can be evicted simultaneously during maintenance
**Fix**: Add PDBs for Vault and External Secrets

---

### 8. No Backup Strategy for Vault
**Risk**: Data loss is permanent
**Fix**: Implement Velero or snapshot-based backups

---

### 9. Production Using Development Branch
**File**: `flux/clusters/production/flux-system/gotk-sync.yaml:11`

```yaml
# CURRENT (WRONG BRANCH)
ref:
  branch: "20260108_3"
```

**Risk**: Untested changes deployed to production
**Fix**: Switch to `main` branch

---

## 🟡 MEDIUM SEVERITY ISSUES

10. Missing Network Policies for production namespace
11. No Security Contexts defined (containers run as root)
12. Missing ResourceQuota for production namespace
13. No RBAC configuration for External Secrets
14. Vault listener misconfiguration (IPv6 + no TLS)
15. Missing liveness/readiness probes for External Secrets
16. Memory locking disabled in Vault (secrets can swap to disk)
17. No dependency health checks (apps.yaml has `wait: false`)
18. HTTP-based Vault health probes without authentication
19. Missing monitoring/observability configuration

---

## 🟢 LOW SEVERITY ISSUES

20. Helm repository sync interval too long (24h)
21. Kustomization retry interval suboptimal
22. Missing documentation for Linkerd service mesh expectations
23. No ServiceMonitor for Flux metrics
24. No documented secret rotation policy

---

## ✅ POSITIVE FINDINGS

- Flux version is recent (v2.7.5)
- Proper dependency ordering (infrastructure → apps)
- Kustomization with prune enabled
- Resource limits defined for Vault
- GitOps workflow properly configured
- Linkerd service mesh integration

---

## 📋 REMEDIATION PLAN

### Phase 1: Critical Security Fixes (Week 1)
- [ ] Enable TLS for Vault with cert-manager
- [ ] Implement Vault HA mode (3 replicas)
- [ ] Migrate to Raft storage
- [ ] Configure auto-unseal
- [ ] Create dedicated service accounts
- [ ] Switch to main branch for production

### Phase 2: High Priority Improvements (Week 2)
- [ ] Reduce ExternalSecret refresh interval to 5m
- [ ] Add PodDisruptionBudgets
- [ ] Implement backup strategy with Velero
- [ ] Add network policies for production
- [ ] Configure security contexts

### Phase 3: Production Hardening (Week 3)
- [ ] Add ResourceQuota for production namespace
- [ ] Configure monitoring with Prometheus
- [ ] Add proper RBAC for all components
- [ ] Enable Vault memory locking
- [ ] Add dependency health checks

### Phase 4: Operational Excellence (Week 4)
- [ ] Document secret rotation procedures
- [ ] Add Grafana dashboards
- [ ] Implement automated secret rotation
- [ ] Add comprehensive testing strategy
- [ ] Create runbooks for common scenarios

---

## 🎯 Quick Wins (Can Implement Today)

1. ✅ Reduce ExternalSecret refresh interval (2 min fix)
2. ✅ Create dedicated service account for Vault auth (5 min fix)
3. ✅ Add resource quotas to production namespace (5 min fix)
4. ✅ Enable health check waits in apps kustomization (1 min fix)
5. ✅ Add security contexts to HelmReleases (10 min fix)

---

## 📊 Risk Assessment

**Current Production Readiness**: 60%

**Blockers for Production**:
1. TLS disabled (data security)
2. Single Vault instance (availability)
3. No backup strategy (data durability)
4. Manual unsealing (operational overhead)

**Recommended Timeline**:
- Fix critical issues: 1-2 weeks
- Achieve production-ready: 3-4 weeks
- Full operational maturity: 6-8 weeks

---

## 🔗 Related Documentation

- [Vault Production Hardening](https://developer.hashicorp.com/vault/tutorials/kubernetes/kubernetes-hardening)
- [Flux Security Best Practices](https://fluxcd.io/flux/security/)
- [Kubernetes Pod Security Standards](https://kubernetes.io/docs/concepts/security/pod-security-standards/)
- [External Secrets Security](https://external-secrets.io/latest/guides/security-best-practices/)

---

## Next Steps

Review this document and decide:
1. Which issues to fix immediately?
2. What's the acceptable timeline?
3. Do we need to stay on dev branch for testing?
4. What's the priority: security vs. availability vs. operability?
