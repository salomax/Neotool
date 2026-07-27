---
name: neotool-open-issue
description: >
  Use this skill whenever Marcos wants to track any kind of change to the Invistus project as a GitHub
  issue — whether it's a new feature, an improvement to something that already exists, a bug fix,
  a design change, an infra task, or anything spanning backend, frontend (web or mobile), design, or
  multiple layers at once. Triggers on phrases like "new issue", "open an issue", "new requirement",
  "quero criar uma issue", "tem um bug", "melhoria", "preciso mudar", "quero adicionar", "something is
  broken", "I want to improve", "create an issue", "log a bug", "new fix", "new feature", or any
  description of a problem, improvement, or change that should become a tracked work item.
  Also use when Marcos describes a problem or idea in conversation and the natural next step is to
  formalize it as a GitHub issue — even if he doesn't explicitly say so.
compatibility: Requires GitHub MCP (preferred) or gh CLI for issue and branch creation.
metadata:
  author: invistus
  version: "1.0"
---

# Open Issue — Invistus

You are helping Marcos capture, structure, and create a GitHub issue for the Invistus project.
Follow this workflow exactly. It is designed to be fast and natural — no bureaucracy.

The default repository/project is https://github.com/invistus/invistus

## Non-negotiable rule

**All content written to GitHub must be in English** — title, body, labels, comments, branch name. No exceptions, regardless of the language used in the conversation.

## Your role

- Listen first, structure second. Let Marcos describe the requirement in his own words.
- Ask only one clarifying question at a time, only when truly needed.
- Fill the issue template based on what you learn.
- Confirm before creating anything on GitHub.

## Step 1 — Listen

If Marcos has already described the requirement, move to Step 2 immediately.
If the thread just started, ask: "What's the requirement or problem you want to capture?"

## Step 2 — Fill the issue template

Produce a structured draft using the fields below. Present it clearly in the thread.

```
## Context
[Why does this exist? What problem does it solve?]

## Requirement
[What needs to be built, in clear objective terms]

## Expected behavior
[What should happen when done?]

## Acceptance criteria
- [ ] [Criterion 1 — specific and verifiable]
- [ ] [Criterion 2]
- [ ] [Criterion 3]

## Scope
**Includes:** [what is in scope]
**Excludes:** [what is explicitly out — prevents scope creep]

## Technical info
- **Track:** Black (personal finance) | Silver (investments) | Infra | Cross-cutting
- **Type:** feat | fix | refactor | chore | docs | spike
- **Effort:** XS (<1h) | S (1-3h) | M (3-8h) | L (1d+) | XL (2d+)
- **Services:** backend | frontend | mobile | workers | infra
- **Backlog ref:** [BK-N if mapped, otherwise "new"]

## Dependencies
- Blocked by: [#issue or BK-N, or "none"]
- Blocks: [#issue or BK-N, or "none"]

## Branch
`{type}/GH-{number}-{short-slug}`
```

After presenting, ask: **"Is this scope correct? Any acceptance criteria missing?"**

## Step 3 — Refine

Update the template based on Marcos's feedback. Iterate until he approves.

Key things to confirm if not already clear:
- Priority (P0 critical / P1 next sprint / P2 planned / P3 future)
- Whether a full SDD spec is needed (`spec/features/name.md`) or the issue alone is enough
- Any dependencies on existing issues or [BK-N] items

## Step 4 — Confirm and create

Before creating, ask: **"Can I create the issue with this content?"**

On approval:
1. **Create the GitHub issue** via GitHub MCP with title, body, and labels
   - Title format: `[{type}] {brief description}`
   - Labels: type label + priority label + track label (e.g., `feat`, `P1`, `track:black`)
2. **Record the issue number** returned by GitHub
3. **Create the branch:**
   ```
   {type}/GH-{issue-number}-{descriptive-slug}
   ```
   Run via Bash or provide the command to Marcos.

## Step 5 — Suggest SDD spec (optional)

If this is a new feature or significant behavior change, suggest:
> "Should I create a spec file at `spec/features/{slug}.md` for the SDD workflow?"

Only suggest — don't create unless Marcos confirms.

---

## Acceptance criteria best practices

✅ Good — specific and verifiable:
```
- [ ] Password-protected PDF is processed when the correct password is provided
- [ ] Invalid password returns a clear, specific error (not a generic one)
```

❌ Avoid — vague:
```
- [ ] Works well
- [ ] Good UX
```

For complex flows, use Given/When/Then:
```
Given: user uploads a password-protected PDF
When: provides the correct password
Then: transactions are imported normally
```

---

## Branch convention

```
{type}/GH-{issue-number}-{slug}    # slug: English, kebab-case, max 5 words
```

Examples:
```
feat/GH-42-pdf-password-support
fix/GH-87-transaction-duplicate-import
refactor/GH-103-transaction-rules-service
chore/GH-12-update-kotlin-deps
```

Never create the branch before the issue number is known.

---

## Labels reference

**Type:** `feat` · `fix` · `refactor` · `chore` · `docs` · `spike`
**Priority:** `P0` · `P1` · `P2` · `P3`
**Track:** `track:black` · `track:silver` · `track:infra` · `track:cross`

**⚠️ P2/P3 label collision:** this repo already has lowercase `p2` ("Pillar 2 - Personal
Finance Control") and `p3` ("Pillar 3 - Wealth Management B2B") labels. GitHub labels are
case-insensitive, so `gh label create "P2"` / `"P3"` will fail with "label already exists"
— there is no separate priority label to attach. When priority is P2 or P3, state it in the
issue body's Technical info section instead of attempting the label, and don't spend a retry
trying to create it. (Confirmed via `gh label create "P2"` → 409 during issue #485.)

---

## Full workflow reference

For the complete workflow specification, see:
`docs/08-workflows/requirement-workflow.md`
`docs/91-templates/requirement-templates/github-issue-template.md`
