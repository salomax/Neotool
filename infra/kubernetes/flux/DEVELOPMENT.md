# Development Workflow Options

You're currently on branch: `20260108_3` (development)

Since you're still developing, here are two approaches:

---

## Option 1: GitOps on Development Branch ⭐ RECOMMENDED

Bootstrap Flux to watch your development branch.

### Pros:
- ✅ Full GitOps workflow (test the real thing)
- ✅ Automatic deployments when you push
- ✅ Easy to switch to `main` later
- ✅ Can test the entire workflow before merging

### Cons:
- ⚠️ Requires GitHub token
- ⚠️ Every push triggers deployment (can be slow during rapid dev)

### How to:

```bash
cd /Users/salomax/src/Neotool/infra/kubernetes/flux
./bootstrap-dev.sh
```

This will:
1. Ask for your GitHub credentials
2. Bootstrap Flux to watch branch `20260108_3`
3. Create a deploy key in GitHub
4. Auto-sync every time you push

### Workflow:
```bash
# Edit files
vim flux/infrastructure/vault/helmrelease.yaml

# Commit and push
git add .
git commit -m "Update Vault config"
git push origin 20260108_3

# Flux auto-deploys in ~1 minute! ✨
flux get kustomizations --watch
```

### When ready for production:
```bash
# 1. Merge to main
git checkout main
git merge 20260108_3
git push origin main

# 2. Re-bootstrap to main
flux bootstrap github \
  --owner=salomax \
  --repository=Neotool \
  --branch=main \
  --path=infra/kubernetes/flux/clusters/production \
  --personal
```

---

## Option 2: Local Apply (No GitOps Yet)

Apply manifests directly without Git sync.

### Pros:
- ✅ No GitHub token needed
- ✅ Instant feedback (no waiting for Flux)
- ✅ No commits needed to test
- ✅ Good for rapid iteration

### Cons:
- ❌ Not testing the real GitOps workflow
- ❌ Need to manually apply every change
- ❌ Easy to forget what you deployed

### How to:

```bash
cd /Users/salomax/src/Neotool/infra/kubernetes/flux
./apply-local.sh
```

This applies all infrastructure directly (Vault, External Secrets, etc.)

### Workflow:
```bash
# Edit files
vim flux/infrastructure/vault/helmrelease.yaml

# Apply manually
kubectl apply -f flux/infrastructure/vault/helmrelease.yaml

# Or re-run the script
./apply-local.sh
```

### When ready for production:
```bash
# Merge to main
git checkout main
git merge 20260108_3
git push origin main

# Bootstrap Flux
./bootstrap.sh  # Choose option 1 (GitHub)
```

---

## Option 3: Hybrid Approach (Best of Both)

Use local apply for infrastructure (Vault, etc.) and GitOps for applications.

### How to:

```bash
# 1. Apply infrastructure manually (one time)
./apply-local.sh

# 2. Initialize Vault (one time)
./vault-init.sh
./vault-unseal.sh
./vault-configure.sh
./vault-store-postgres.sh

# 3. Bootstrap Flux for apps only
# Edit bootstrap to only watch flux/apps directory
flux bootstrap github \
  --owner=salomax \
  --repository=Neotool \
  --branch=20260108_3 \
  --path=infra/kubernetes/flux/apps \
  --personal
```

This way:
- Infrastructure is stable (applied once)
- Applications use GitOps (deployed automatically)

---

## My Recommendation

For your situation, I recommend **Option 1** (GitOps on dev branch):

**Why?**
1. You get to test the full GitOps workflow before production
2. It's easy to switch to `main` later
3. You can still iterate quickly (commits are fast)
4. You'll catch any issues with the GitOps setup early

**When to use Option 2?**
- If you're doing very rapid changes (multiple times per minute)
- If you don't want to commit incomplete work
- If you just want to test Vault configuration

---

## Current Status

```
✅ Flux installed (controllers running)
❌ Not bootstrapped (no Git sync)
✅ K3S cluster ready
✅ Production namespace exists
```

Choose your approach and run the corresponding script!

---

## Quick Commands

### Check what's running
```bash
kubectl get pods -n production
kubectl get pods -n flux-system
```

### Check Flux status
```bash
flux check
flux get all
```

### Watch for changes (after bootstrap)
```bash
flux get kustomizations --watch
flux logs --all-namespaces --follow
```

### Force sync (after bootstrap)
```bash
flux reconcile kustomization infrastructure --with-source
```
