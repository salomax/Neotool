---
id: contract.frontmatter
status: canonical
kind: metadata-contract
source_of_truth: code
last_verified: 2026-08-02
schema: spec-metadata-v1
---

# Frontmatter Contract

## Required Fields For Feature Specs

```yaml
---
id: feature.<domain>.<feature>
domain: security | transactions | investment-portfolio | market-data | financial-planning
status: draft | canonical | deprecated
confidence: verified | partial | inferred | stale-risk
source_of_truth: code
last_verified: YYYY-MM-DD
evidence:
  backend: []
  graphql: []
  database: []
  tests: []
  frontend: []
  mobile: []
  workers: []
legacy_refs: []
related_specs: []
---
```

## Rules

- `id` must be stable and unique.
- `source_of_truth` must be `code` for reverse specs.
- `last_verified` is the last date the cited evidence was checked.
- Empty evidence arrays are allowed, but they reduce confidence.
- `legacy_refs` may point to `removed historical docs`, but legacy refs never justify `verified`.
- `related_specs` should contain spec IDs, not prose descriptions.
