---
id: template.reverse-spec-agent-prompt
status: canonical
kind: prompt-template
source_of_truth: code
last_verified: 2026-08-02
schema: spec-metadata-v1
---

# Reverse Spec Agent Prompt

Use this prompt with Cursor, Codex, or another agent to generate specs from current code.

```text
Generate reverse specs for the <domain> domain.

Goal:
- Write compact, evidence-based specs under spec/features/<domain>/.
- Optimize for future agent impact analysis with minimum token usage.

Source of truth, in order:
1. Backend Kotlin code.
2. Database migrations and constraints.
3. GraphQL schema and resolvers.
4. Automated tests.
5. Frontend and mobile code.
6. Historical docs only as historical references.

Rules:
- Do not copy removed historical docs as truth.
- Use removed historical docs only in legacy_refs when helpful.
- Backend is authoritative for permission, validation, idempotency, and invariants.
- Frontend/mobile permission claims are UX hints only.
- Cite evidence paths in frontmatter.
- Mark confidence as verified, partial, inferred, or stale-risk.
- Add Known Gaps instead of inventing missing behavior.
- Keep each spec short and focused on one stable behavior cluster.

Output:
- Create or update spec/features/<domain>/<feature>.md using spec/templates/feature-spec-template.md.
- Update spec/domains/<domain>.md with discovered feature groups and entrypoints.
- List specs created, confidence level, evidence used, and unresolved gaps.
```
