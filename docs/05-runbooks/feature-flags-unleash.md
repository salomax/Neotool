---
title: Feature Flags Unleash Runbook
type: runbook
status: active
updated: 2026-08-03
owners: [platform]
source_of_truth:
  - infra/unleash/flags.yaml
  - infra/unleash/flags-init.json
  - infra/kubernetes/scripts/unleash-setup.sh
load_when:
  - importing or changing Unleash flags
  - bootstrapping Unleash secrets in a cluster
supersedes:
  - historical Unleash docs
schema: docs-metadata-v1
---

# Feature Flags Unleash Runbook

Purpose: manage Unleash feature flags from the canonical in-repo definitions.

## Symptoms

- Flag missing in an environment.
- Login/assistant (or other) behavior not toggling as expected.
- Flag definitions disagree with runtime state.

## Authority

Canonical flag files: `infra/unleash/flags.yaml` and `flags-init.json`. Cluster secret helper: `infra/kubernetes/scripts/unleash-setup.sh`.

## Commands

```bash
# Review declared flags
cat infra/unleash/flags.yaml

# Import using the project CLI if available in your environment:
# neotool flags import infra/unleash/flags.yaml

# Cluster secrets bootstrap
infra/kubernetes/scripts/unleash-setup.sh
```

Change flags deliberately; prefer environment UI/API for runtime toggles after initial import.

## Rollback

- Re-import previous `flags.yaml` revision or toggle the flag off/on in Unleash UI.
- Restore Vault/Unleash API tokens from backup if setup script rotated them.

## Verification

- Flag appears in Unleash with expected default.
- Client/service reads the new state after cache TTL/restart as applicable.
- Runtime state matches the canonical flag definition or an explicit environment override.

## Related

- [Infrastructure](../01-architecture/infrastructure.md)
- [Kubernetes operations](kubernetes-operations.md)
- [Vault](vault.md)
