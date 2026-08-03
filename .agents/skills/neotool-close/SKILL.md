---
name: neotool-close
description: >
  Use this skill to close a completed Invistus delivery after implementation, review, QA, or bugfix
  work. It validates that delivered behavior matches the issue and accepted decisions, updates
  `spec/features` as durable feature memory, promotes long-lived architecture or convention changes to
  `docs/` or ADRs, invokes `neotool-retro` when workflow learning is useful, and produces a concise
  close summary. Trigger on requests like "close issue #N", "finish the work", "fecha a entrega",
  "rodar fechamento", "atualizar spec e docs", "closeout", "delivery close", or explicit requests to
  update specs/docs and run retro at the end of a task. An issue number is required when the work is
  issue-tracked.
---

# Close - Invistus

Close a delivery by reconciling implementation, tests, feature memory, durable docs, and workflow
learning. The close is the final quality gate after implementation, review, or QA, not a place to
redesign the feature.

## Non-negotiable rules

1. **Issue number is required for issue-tracked work.** If the user asks to close a tracked delivery
   without an issue number, ask for it before proceeding.
2. **Backend remains authoritative.** Feature specs may record frontend permission hints for UX, but
   authorization, validation, idempotency, and invariants must stay enforced by the Kotlin service.
3. **Update durable memory as delta only.** Do not paste chat history into specs or docs. Record final
   behavior, decisions, invariants, integration points, and meaningful deviations from the plan.
4. **Promote narrowly.** `spec/features` captures feature behavior. `docs/` or ADRs capture reusable
   architecture, conventions, patterns, and long-term decisions. Issue comments capture task-local
   execution history.
5. **Keep close token-cheap.** Read only the issue, accepted analysis/backlog/review comments, current
   diff, and directly relevant specs/docs. Use Semble/Graphify discipline from project instructions
   when locating code or architecture context.
6. **Invoke `neotool-retro` selectively.** Run retro during close when the work exposed process,
   tooling, skill, artifact-quality, or repeated-token-waste learning. Pass only the close evidence
   needed for the retro, and apply its approval rules for shared artifacts.

## Workflow

### 1. Identify close scope

Determine the delivery being closed:

- GitHub issue or PR explicitly referenced by the user
- Current branch when it clearly maps to an issue
- Current working diff only for untracked work

Read the minimum canonical sources:

- Issue title, description, acceptance criteria, labels, and relevant comments
- Technical analysis, backlog, review, and QA/E2E comments when present
- Current diff and test results relevant to the delivery
- Existing `spec/features` entry for the feature, if one exists
- Existing `docs/` or ADR only when the delivery changed durable architecture or conventions

### 2. Validate delivery state

Before updating memory, verify:

- Acceptance criteria are implemented or explicitly deferred
- Tests promised by backlog/review/QA were run or a gap is documented
- No unresolved blocker/major review finding remains
- Delivered behavior matches accepted decisions, or deviations are documented
- Backend authority is preserved for permissions, validation, idempotency, and invariants
- Frontend permission exposure is documented as UX gating only, not enforcement

If any item is materially unresolved, stop and report what blocks close.

### 3. Update `spec/features`

Create or update the smallest relevant feature spec. Prefer one feature spec per stable product
capability, not per PR.

Record these sections when applicable:

```markdown
## Behavior
[Current user-visible and service-visible behavior]

## Invariants to Preserve
- [Business/security/data invariant that future changes must not break]

## Permissions
- Backend authority: [service-side checks]
- UX hints: [token/me permissions used only to hide/disable UI]

## Impact Surface
- Backend: [services/resolvers/entities/migrations]
- Frontend: [pages/components/hooks]
- Mobile: [screens/hooks]
- Async/infra: [workers/jobs/config]

## Decisions
- `D-N` - [accepted decision and rationale]

## Validation
- [Tests/checks that prove this behavior]
```

Do not duplicate implementation details already obvious in code. Capture what a future maintenance or
evolution session needs for impact analysis.

### 4. Update durable docs only when warranted

Promote to `docs/` or ADR when the delivery establishes or changes:

- Cross-feature architecture
- Security or permission model
- Integration contracts
- Data lifecycle or migration policy
- Reusable frontend/backend/mobile pattern
- Long-term operational decision

Do not promote one-off implementation notes. When in doubt, keep the detail in `spec/features` and
mention the promotion question in the close summary.

### 5. Invoke `neotool-retro`

Invoke `neotool-retro` from this skill when there is concrete material to evaluate:

- Avoidable token waste or repeated context gathering
- Missing, stale, or ambiguous skill instructions
- Missing durable docs/specs that caused uncertainty
- Tooling or validation friction
- Requirement artifact ambiguity that caused rework

Retro should receive concise evidence: issue number, changed artifacts, notable friction, and any
proposed promotion candidate. Let `neotool-retro` classify findings and require approval for shared
skill/doc edits.

Skip retro when the close is routine and no reusable workflow learning is visible; say it was skipped
and why.

### 6. Publish close summary

When issue-tracked, post or prepare a concise close comment:

```markdown
## Delivery Close

**Status:** Closed | Blocked
**Spec updated:** [path or none]
**Docs updated:** [path or none]
**Validation:** [commands/checks]

### Delivered behavior
- [Final behavior]

### Decisions preserved
- `D-N` - [decision]

### Open follow-ups
- [Only real follow-ups; omit if none]

---
*Generated by neotool-close*
```

Tell the user what was updated, what was deliberately not promoted, and whether retro produced any
approved changes.

## Quality criteria

A good close leaves future agents with enough context to analyze impact without re-reading the whole
issue thread. It is concise, anchored in delivered behavior, and explicit about invariants and
permission boundaries. It should reduce future token consumption by making the next session read a
small spec and durable docs instead of reconstructing decisions from chat, commits, and comments.
