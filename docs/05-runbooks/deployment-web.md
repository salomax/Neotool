---
title: Web Deployment Runbook
type: runbook
status: active
updated: 2026-08-03
owners: [platform]
source_of_truth:
  - web/Dockerfile
  - web/package.json
  - web/pnpm-lock.yaml
  - .github/workflows/ci.yml
load_when:
  - building or publishing the web container image
  - recovering a bad web deploy
supersedes:
schema: docs-metadata-v1
---

# Web Deployment Runbook

Purpose: build and publish the Next.js web image using the verified Dockerfile path.

## Symptoms

- Web pods/containers serving stale assets or failing health checks after a release.
- Need to rebuild `neotool-web` image for registry push.

## Authority

`web/Dockerfile` chooses pnpm when `pnpm-lock.yaml` is present (current web tree includes it). Root CI also uses `npm ci` for monorepo checks — do not assume a single package manager for every job.

## Commands

```bash
cd web
# Prefer the same tool the Dockerfile will pick for this tree:
corepack enable
pnpm install --frozen-lockfile
pnpm run typecheck
pnpm run lint
pnpm run test:unit
pnpm run build

docker build -t ghcr.io/<org>/neotool-web:<tag> .
docker push ghcr.io/<org>/neotool-web:<tag>
```

Cluster apply/rollout steps depend on the external GitOps/cluster inventory (not fully present under `infra/kubernetes/` in this repo). Use the active deploy pipeline for the environment.

## Rollback

- Redeploy the previous image tag via the environment’s GitOps/release process.
- Avoid manual `kubectl set image` unless that is the documented path for the target cluster.

## Verification

- Container starts and serves the app on the expected port.
- `pnpm run typecheck` / unit checks pass for the commit being released.
- Smoke the signed-in home path after rollout.

## Related

- [Frontend web](../02-development/frontend-web.md)
- [Kubernetes operations](kubernetes-operations.md)
- [Local development](local-development.md)
