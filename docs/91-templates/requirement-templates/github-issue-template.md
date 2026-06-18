---
title: GitHub Issue Template
type: template
category: requirement-management
status: current
version: 1.1.0
date: 2026-06-16
tags: [template, github, issue, requirements, acceptance-criteria]
ai_optimized: true
search_keywords: [issue, github, requirement, acceptance criteria, backlog, branch]
---

# GitHub Issue Template

> **Purpose**: Standard for creating GitHub issues. Every issue must be refined with Marcos before entering the active backlog. Claude uses this template in requirement threads to produce issues ready to create via GitHub MCP or `gh` CLI.

---

## How to use

1. **Describe the requirement** in a Claude thread (informal, any language)
2. **Claude fills this template** based on the conversation
3. **Review and refine** with Marcos (scope, acceptance criteria)
4. **Claude creates the issue** via GitHub MCP or `gh` CLI
5. **Claude creates the branch** following the convention below

---

## Issue Template

```markdown
## Context

<!-- Why does this requirement exist? What problem does it solve? What is the business value? -->
[Describe the context and motivation]

## Requirement

<!-- What needs to be built, in clear and objective terms -->
[Describe what must be implemented]

## Expected behavior

<!-- What should happen when the requirement is complete? -->
[Describe the system behavior after implementation]

## Acceptance criteria

<!-- Objective and verifiable list. Use Given/When/Then or checkboxes. -->

- [ ] [Criterion 1]
- [ ] [Criterion 2]
- [ ] [Criterion 3]

## Scope

### Includes
- [What is within the scope of this issue]

### Excludes
- [What is explicitly out of scope — prevents scope creep]

## Technical info

- **Track**: <!-- Black (personal finance) | Silver (investments) | Infra | Cross-cutting -->
- **Type**: <!-- feat | fix | refactor | chore | docs -->
- **Estimated effort**: <!-- XS (<1h) | S (1-3h) | M (3-8h) | L (1d+) | XL (2d+) -->
- **Affected services**: <!-- backend | frontend | mobile | workers | infra -->
- **SDD spec**: <!-- Link to spec/features/feature-name.md (if exists) -->
- **Backlog ref**: <!-- [BK-N] (if mapped in backlog.md) -->

## Dependencies

- **Blocked by**: <!-- #issue or [BK-N] -->
- **Blocks**: <!-- #issue or [BK-N] -->

## Branch

<!-- Convention: {type}/GH-{number}-{short-slug} -->
<!-- Example: feat/GH-42-pdf-password-support -->
`{type}/GH-{number}-{slug}`

## Refinement notes

<!-- Decisions made during refinement with Marcos -->
<!-- Record any resolved ambiguity or adopted assumption here -->
```

---

## Issue types (labels)

| Label | Usage |
|-------|-------|
| `feat` | New feature |
| `fix` | Bug fix |
| `refactor` | Refactoring without behavior change |
| `chore` | Maintenance tasks, deps, configs |
| `docs` | Documentation |
| `spike` | Investigation / PoC |

## Priority labels

| Label | Criteria |
|-------|----------|
| `P0` | Critical / blocking — stop everything |
| `P1` | Next sprint |
| `P2` | Planned |
| `P3` | Future / backlog |

## Track labels

| Label | Domain |
|-------|--------|
| `track:black` | Personal finance, transactions, NFC-e |
| `track:silver` | Investments, portfolio |
| `track:infra` | Infrastructure, DevOps |
| `track:cross` | Cross-cutting (auth, billing, observability) |

---

## Branch convention

```
{type}/GH-{issue-number}-{descriptive-slug}
```

**Examples:**
```
feat/GH-42-pdf-password-support
fix/GH-87-transaction-duplicate-on-import
refactor/GH-103-transaction-rules-service
chore/GH-12-update-kotlin-deps
```

**Rules:**
- Always tied to the issue number created on GitHub
- Slug in English, kebab-case, max 5 words
- Never create a branch before the issue exists

---

## Acceptance criteria — best practices

Prefer **objective and verifiable** criteria:

✅ Good:
```
- [ ] Password-protected PDF is processed when the correct password is provided
- [ ] Invalid password returns a clear error to the user
- [ ] PDFs without password continue to work normally
```

❌ Avoid:
```
- [ ] Works well
- [ ] User-friendly interface
- [ ] Acceptable performance
```

For complex flows, use **Given/When/Then**:
```
Given: user uploads a password-protected PDF
When: provides the correct password
Then: transactions are imported normally

Given: user uploads a password-protected PDF
When: provides incorrect password
Then: a specific error message is displayed (not a generic error)
```

---

## Related

- [Requirement Workflow](../../../08-workflows/requirement-workflow.md) — full refinement workflow
- [Backlog](../../00-planning/backlog.md) — master backlog with [BK-N]
- [SDD Workflow](../../workflows/sdd-workflow.md) — how SDD specs connect to issues
- [Feature Form](../feature-templates/feature-creation/feature-form.md) — for larger features that need a full spec
