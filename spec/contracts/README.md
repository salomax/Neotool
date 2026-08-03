---
id: spec.contracts
status: canonical
kind: contract-index
source_of_truth: code
last_verified: 2026-08-02
schema: spec-metadata-v1
---

# Spec Contracts

Contracts define how specs are written, loaded, validated, and trusted by agents.

## Required Contracts

- `frontmatter.md`: metadata every spec file must expose.
- `confidence-model.md`: evidence quality classification.
- `evidence-model.md`: evidence path rules.
- `agent-loading-contract.md`: token-efficient loading behavior.

Specs that do not follow these contracts are not canonical for future agent sessions.
