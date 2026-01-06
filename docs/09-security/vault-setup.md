---
title: Vault Setup Guide
type: security
category: infrastructure
status: current
version: 1.0.0
tags: [vault, security, secrets, jwt, hashicorp]
ai_optimized: true
search_keywords: [vault, hashicorp, secrets, jwt, keys, security, kubernetes]
related:
  - 09-security/authentication.md
  - 11-infrastructure/k8s-deployment-runbook.md
  - 93-reference/vault-setup.md
last_updated: 2026-01-02
---

# Vault Setup Guide

> **Purpose**: Guide for HashiCorp Vault deployment and configuration for JWT key management and secrets in Neotool.

## Overview

Vault is used for:
- JWT key storage and management
- Secret management for services
- Secure credential storage

## Development Setup

### Current Configuration

Vault runs in dev mode for development:
- Single pod deployment
- In-memory storage
- Root token: `myroot` (stored in Kubernetes Secret)

### Deployment

Vault is automatically deployed with the Kubernetes manifests:

```bash
kubectl apply -f infra/kubernetes/base/vault/
```

### Verify Vault

```bash
# Check Vault pod
kubectl get pods -n neotool-security

# Check Vault status
kubectl exec -n neotool-security deployment/vault -- vault status
```

## JWT Key Management

### Automatic Key Generation

The `vault-jwt-init` Job automatically:
1. Waits for Vault to be ready
2. Enables KV v2 secrets engine
3. Generates 4096-bit RSA key pair
4. Stores keys in `secret/jwt/keys/default`

### Manual Key Verification

```bash
# Check if keys exist
kubectl exec -n neotool-security deployment/vault -- \
  vault kv get secret/jwt/keys/default

# View private key
kubectl exec -n neotool-security deployment/vault -- \
  vault kv get -field=private secret/jwt/keys/default

# View public key
kubectl exec -n neotool-security deployment/vault -- \
  vault kv get -field=public secret/jwt/keys/default
```

### Key Path Structure

```
secret/jwt/keys/default
  ├── private: RSA private key (PEM format)
  └── public: RSA public key (PEM format)
```

## Service Integration

### Init Container Pattern

All Kotlin services use an init container that:
1. Checks if JWT keys exist in Vault
2. Generates keys if missing
3. Stores keys in Vault KV v2

### Service Configuration

Services read keys via `KeyManager`:
- Vault address: `http://vault.neotool-security.svc.cluster.local:8200`
- Secret path: `secret/jwt/keys/default`
- Token: From Kubernetes Secret `vault-token`

## Production Setup

### High Availability (HA) Mode

For production, Vault should run in HA mode:

1. **Raft Storage Backend**
   - Persistent storage for Vault data
   - Multi-node cluster for HA

2. **External Secrets Operator**
   - Syncs Vault secrets to Kubernetes
   - Automatic secret rotation

3. **Vault CSI Driver**
   - Secure secret injection into pods
   - No secret exposure in environment

### Migration from Dev Mode

1. Backup current Vault data
2. Deploy HA Vault configuration
3. Migrate secrets to new Vault
4. Update service configurations

## Security Best Practices

### Token Management

- **Development**: Root token in Kubernetes Secret (acceptable for dev)
- **Production**: Use service accounts with limited permissions
- **Rotation**: Implement token rotation policy

### Network Security

- Vault only accessible within cluster
- Network policies restrict access
- TLS encryption for production

### Access Control

- Use Vault policies for fine-grained access
- Service accounts with minimal permissions
- Audit logging enabled

## Troubleshooting

### Vault Not Starting

```bash
# Check pod logs
kubectl logs -n neotool-security deployment/vault

# Check pod events
kubectl describe pod -n neotool-security -l app=vault
```

### JWT Keys Not Generated

```bash
# Check init job status
kubectl get jobs -n neotool-security

# View job logs
kubectl logs -n neotool-security job/vault-jwt-init

# Manually trigger job
kubectl create job --from=job/vault-jwt-init vault-jwt-init-manual -n neotool-security
```

### Service Cannot Access Vault

1. Check service can reach Vault:
   ```bash
   kubectl exec -n neotool-app deployment/app -- \
     curl http://vault.neotool-security.svc.cluster.local:8200/v1/sys/health
   ```

2. Check Vault token:
   ```bash
   kubectl get secret vault-token -n neotool-security
   ```

3. Verify network policies allow access

## References

- [HashiCorp Vault Documentation](https://www.vaultproject.io/docs)
- [Vault Kubernetes Integration](https://www.vaultproject.io/docs/platform/k8s)
- [Vault KV Secrets Engine](https://www.vaultproject.io/docs/secrets/kv)
- [Vault Setup Reference](../93-reference/vault-setup.md)

