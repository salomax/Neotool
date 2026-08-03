---
title: ADR-0021 Dual Metadata Schema for docs/ and spec/
type: adr
status: proposed
updated: 2026-08-03
owners: [platform]
source_of_truth:
  - docs/03-agent-workflow/context-contract.md
  - spec/contracts/frontmatter.md
  - scripts/docs/validate_docs.py
load_when:
  - explaining why docs/ and spec/ have different frontmatter
  - adding a new schema key to either corpus
supersedes:
schema: docs-metadata-v1
---

# ADR-0021: Dual Metadata Schema for docs/ and spec/

Purpose: make the intentional divergence between docs/ and spec/ frontmatter
schemas explicit and machine-checkable via a shared `schema` key.

## Status

proposed

## Context

docs/ and spec/ are two separate durable memory stores for AI-assisted
engineering. docs/ is navigational/durable engineering documentation, linted
mechanically by scripts/docs/validate_docs.py. spec/ is an evidence-graph memory
of current system behavior, governed by spec/contracts/frontmatter.md,
spec/contracts/confidence-model.md, and spec/contracts/evidence-model.md. They
evolved independently and use different required frontmatter keys, with no field
connecting them. This is intentional — they serve different purposes and should
not be merged into one schema — but the lack of an explicit link means a reader
or a validator cannot mechanically tell "this file follows schema A" from "this
file follows schema B" without inspecting the keys present.

## Decision

Keep docs/ and spec/ as two distinct frontmatter schemas. Do not unify their
required keys. Add one shared key, `schema`, to every frontmatter block in both
corpora, with a fixed value identifying which schema the file follows:

- Files under docs/**/*.md use `schema: docs-metadata-v1`
- Files under spec/**/*.md use `schema: spec-metadata-v1`

Both values point back to this ADR as the canonical explanation of the
divergence. This makes the schema explicit and lets tooling assert "every docs/
file declares docs-metadata-v1" and "every spec/ file declares
spec-metadata-v1" as one mechanical rule, instead of relying on directory
convention alone.

## Consequences

- scripts/docs/validate_docs.py should eventually check for
  `schema: docs-metadata-v1` on every docs/ file (do not implement that check in
  this pass — only add the field; a follow-up task wires the check).
- A future equivalent validator for spec/ (not yet built) should check for
  `schema: spec-metadata-v1` the same way.
- No existing required key in either schema changes meaning or is removed.
- If a third metadata corpus is introduced later, it must declare its own
  `schema` value and get an ADR of its own, following this same pattern.

## Evidence

- docs/03-agent-workflow/context-contract.md — docs-metadata-v1 required keys.
- spec/contracts/frontmatter.md — spec-metadata-v1 required keys.
- scripts/docs/validate_docs.py — current mechanical enforcement, docs/ only.

## Related

- [docs/03-agent-workflow/context-contract.md](../03-agent-workflow/context-contract.md)
- [spec/contracts/frontmatter.md](../../spec/contracts/frontmatter.md)
