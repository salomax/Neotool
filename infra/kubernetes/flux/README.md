# Flux GitOps Setup

GitOps-based deployment using Flux CD.

## Architecture

```
GitHub Repository (Source of Truth)
         ↓
    Flux Controllers
         ↓
    K3S Cluster (Production)
```

## Directory Structure

```
flux/
├── clusters/
│   └── production/          # Cluster-specific configs
│       ├── flux-system/     # Flux components (auto-generated)
│       └── infrastructure.yaml
│
├── infrastructure/          # Platform services
│   ├── vault/
│   ├── external-secrets/
│   └── cert-manager/
│
└── apps/                   # Applications
    ├── postgres/
    ├── services/
    └── web/
```

## Bootstrap Process

### 1. Install Flux CLI

**macOS:**
```bash
brew install fluxcd/tap/flux
```

**Linux:**
```bash
curl -s https://fluxcd.io/install.sh | sudo bash
```

**Verify:**
```bash
flux --version
```

### 2. Pre-flight Check

```bash
export KUBECONFIG=~/.kube/config-hostinger

# Check cluster compatibility
flux check --pre
```

### 3. Bootstrap Flux

**With GitHub (Recommended):**
```bash
# Set GitHub token
export GITHUB_TOKEN=<your-github-token>

# Bootstrap
flux bootstrap github \
  --owner=salomax \
  --repository=Neotool \
  --branch=main \
  --path=infra/kubernetes/flux/clusters/production \
  --personal \
  --components-extra=image-reflector-controller,image-automation-controller
```

**What it does:**
- Installs Flux controllers in `flux-system` namespace
- Creates GitHub deploy key
- Commits Flux manifests to your repo
- Sets up reconciliation loop

### 4. Verify Installation

```bash
# Check Flux components
flux check

# Watch reconciliation
flux get all

# View logs
flux logs --all-namespaces --follow
```

## How It Works

### Git → Cluster Sync

```yaml
# flux/clusters/production/infrastructure.yaml
apiVersion: kustomize.toolkit.fluxcd.io/v1
kind: Kustomization
metadata:
  name: infrastructure
  namespace: flux-system
spec:
  interval: 10m
  path: ./infra/kubernetes/flux/infrastructure
  prune: true
  sourceRef:
    kind: GitRepository
    name: flux-system
```

**Workflow:**
1. You: `git push` changes
2. Flux: Detects changes (every 10min or webhook)
3. Flux: Applies to cluster
4. Flux: Reports status

### Dependencies

```yaml
spec:
  dependsOn:
    - name: vault
    - name: external-secrets
```

Flux respects dependencies - PostgreSQL won't deploy until Vault is ready!

## Managing Resources

### Deploy New Application

```bash
# 1. Add YAML to Git
git add apps/myapp/
git commit -m "Add myapp"
git push

# 2. Flux auto-deploys!
# Watch it:
flux get kustomizations --watch
```

### Manual Sync (Force)

```bash
# Trigger immediate reconciliation
flux reconcile kustomization infrastructure --with-source

# Reconcile specific app
flux reconcile helmrelease vault -n production
```

### Suspend/Resume

```bash
# Pause reconciliation
flux suspend kustomization apps

# Resume
flux resume kustomization apps
```

## Troubleshooting

### Check Flux Status

```bash
flux get sources git
flux get kustomizations
flux get helmreleases
```

### View Events

```bash
flux events --for Kustomization/infrastructure
flux events --for HelmRelease/vault
```

### Logs

```bash
# All Flux controllers
flux logs --all-namespaces --follow

# Specific controller
flux logs --kind=Kustomization --name=infrastructure
```

### Common Issues

**Kustomization not reconciling:**
```bash
# Check source
flux get sources git flux-system

# Force reconcile
flux reconcile source git flux-system
```

**HelmRelease stuck:**
```bash
# Check helm release
helm list -n production

# View Flux events
flux events --for HelmRelease/vault
```

## Notifications (Optional)

### Slack Integration

```yaml
apiVersion: notification.toolkit.fluxcd.io/v1beta1
kind: Provider
metadata:
  name: slack
  namespace: flux-system
spec:
  type: slack
  channel: deployments
  secretRef:
    name: slack-url
---
apiVersion: notification.toolkit.fluxcd.io/v1beta1
kind: Alert
metadata:
  name: infrastructure
  namespace: flux-system
spec:
  providerRef:
    name: slack
  eventSeverity: info
  eventSources:
    - kind: Kustomization
      name: infrastructure
```

## Best Practices

1. **Always commit to Git first** - Cluster changes are ephemeral
2. **Use dependencies** - Order matters
3. **Set proper intervals** - 5-10min for prod
4. **Monitor Flux health** - `flux check` regularly
5. **Use webhooks** - Faster than polling
6. **Encrypt secrets** - Use SOPS or Sealed Secrets

## Uninstall Flux

```bash
flux uninstall --keep-namespace
```

## References

- [Flux Documentation](https://fluxcd.io/docs/)
- [Flux GitHub](https://github.com/fluxcd/flux2)
- [Flux Slack](https://cloud-native.slack.com/#flux)
