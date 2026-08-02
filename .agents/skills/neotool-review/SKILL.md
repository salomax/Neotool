---
name: neotool-review
description: >
  Use this skill to run a structured code review for a change tied to a GitHub issue in the Invistus
  project — a pull request, a branch, or a working diff. Trigger on requests like "review the code",
  "code review for issue #N", "review this PR", "review my branch", "faz o review", "revisa o código",
  "revisa a PR", "check my changes before merge", "is this ready to merge", or any request to evaluate
  implemented code against the issue's requirements and the project conventions.
  Use after implementation (typically after neotool-backlog-gen tasks were coded) and before merge.
  An issue number is required — ask for it immediately if not provided.
metadata:
  author: invistus
  version: "1.4"
---

# Code Review — Invistus

Run an objective, convention-grounded code review for a change linked to a GitHub issue, and publish
the result as an issue comment so the review history lives with the work item.

Perform the review in an independent, clean review context. Reconstruct the relevant facts from the
canonical issue, current diff, and current code. Technical analysis, backlog, and implementation
summary are navigation indices, not evidence; verify their claims before using them in a verdict.

The output of this skill must be good enough to become an issue comment with a `🔍 Code Review`
heading and give a clear, defensible merge decision.

## Non-negotiable rules

1. **All content posted to GitHub must be in English** — comments, headings, finding titles, labels. No exceptions, regardless of the language used in the conversation.
2. **Issue number is required** — do not proceed without one. If not provided, stop and ask:
   > What is the issue number? e.g. `#42`
3. **Never approve blind.** Every verdict must be anchored in the actual diff, a project convention, or an acceptance criterion. Do not rubber-stamp.

## Workflow

### 1. Identify what is being reviewed

Determine the change under review, in this order:

1. A pull request explicitly referenced by the user (read it via GitHub MCP)
2. A branch name (typically `{type}/GH-{issue-number}-{slug}` — see `neotool-open-issue`)
3. The current working diff (`git diff` / `git diff main...HEAD` via Bash)

If none is clear, ask which branch or PR to review. Never review from memory — always inspect the real diff.

**Re-reviews (round 2+):** when a prior `🔍 Code Review` comment already exists on the issue, review only the **delta** since it — the follow-up commits or the changed files listed in the author's response — plus the direct blast radius of those changes. Do not re-read and re-summarise the whole diff. Open the previous review's stable `CR-N` findings, confirm each as closed or still open with evidence, preserve its ID, and report only the delta. Assign a new `CR-N` only to a genuinely new finding. Follow the two-round cap in [token-discipline.md](../neotool-conventions/references/token-discipline.md) §9; unresolved blockers or majors after round 2 return to solution design.

### 2. Read the issue and its history

Use GitHub MCP to read the issue title, description, acceptance criteria, labels, and comments. Pay special attention to:

- The `🔍 Technical Analysis` comment (from `neotool-solution-design`) — the intended approach
- The `📋 Development Backlog` comment (from `neotool-backlog-gen`) — the tasks that should be delivered
- The acceptance criteria — the change must satisfy them

If the issue is ambiguous or the diff diverges from the technical analysis, flag it explicitly instead of assuming it is fine.

### 3. Load the project conventions

Read the relevant local skills based on what the diff touches:

- Kotlin/Micronaut backend → `neotool-kotlin-service`
- Web/mobile frontend → `neotool-nextjs-app`
- Personal finance domain → `neotool-finance-domain`
- Cross-cutting rules (auth, error handling, i18n) → `neotool-conventions`

Judge the code against these skills. A deviation from a documented convention is a finding, not a matter of taste.

### 4. Review the diff

When you need code outside the diff (blast radius, related flows), apply the code-discovery
gate in [token-discipline.md](../neotool-conventions/references/token-discipline.md) §2.

Evaluate the change across these dimensions. Be specific — cite file and, where possible, the concern:

- **Correctness** — does it do what the issue asks? Any logic errors, missed edge cases, or broken invariants?
- **Acceptance criteria** — is each criterion actually met by the code? Map findings to criteria.
- **Conventions** — layering, naming, structure, and patterns consistent with the loaded skills.
- **Security** — auth enforced in the backend (never trust the frontend), input validation, trust boundaries, no leaked secrets. Extra scrutiny for any external input or third-party data.
- **Error handling & UX** — errors propagated and surfaced per `neotool-conventions`; sensible fallbacks.
- **Tests** — are the tests required by the backlog present and meaningful (`test:unit`, `test:integration`, `test:e2e`)? Do they cover the new business rules and flows, not just happy paths? **Check the fixtures, not just the assertions:** a test only proves something if it would fail when the bug is present. Watch for fixtures that make two distinct values coincidentally equal — e.g. a test where `resource` and `client_id` are the same string, so a check comparing the wrong two fields still passes. When a security-relevant comparison is under test, confirm the fixture uses *distinct* values for the things being distinguished, and mentally run the test against the buggy code: if it would still pass, it is not covering the property it claims to.

  **When the change ships a regression harness, do not simulate — reintroduce the defect and run it.** For any test whose stated purpose is to stop a class of bug from returning (a schema validator, a lint rule, a contract check), revert the fix in the real source, run the harness, confirm it fails, then restore. Reading cannot establish this. In issue #491 the round 1 harness validated GraphQL documents against the banking schema and every test passed — but it enumerated handlers from a hardcoded list, so it proved nothing about handlers added later; and the round 2 DI rewrite introduced the opposite failure mode, where an empty injected registry would iterate zero documents and still pass green. Reverting `ticker`→`symbol` and watching the build fail was the only thing that distinguished a working harness from a decorative one, in both rounds.

  **Treat enumeration-based tests as vacuous until proven otherwise.** A test shaped `collection.forEach { assert(...) }` passes trivially when the collection is empty, and passes incompletely when it is partial. Whenever coverage depends on a discovered set — injected beans, classpath scan, glob of files — check that something asserts the set is non-empty and of the expected size. Discovery that silently returns nothing is the failure mode these harnesses are least able to report.
- **Data & migrations** — migrations safe, reversible where expected, and consistent with entity changes.
- **Performance / data access** — for any function the diff adds or changes that is called from a loop, `.map`/`.forEach`, or a per-item/per-field resolver: does it perform I/O (DB query, HTTP call), and is that I/O batched once per request or does it repeat once per item (N+1)? Cite the call site and the loop/resolver invoking it. Issue #570 is the counterexample this dimension exists to catch: `FundValuationService.estimate()` fetched come-cotas checkpoints via a per-call repository query, even though the same service already had an established batch-once-per-request pattern (`InvestmentQueryService.loadHoldingTransactionsById()`) that the review should have flagged as the pattern to reuse. If an established batch-fetch sibling exists in the same service/module and the new code doesn't reuse it, that is at minimum a Major finding.
- **Simplicity** — no unnecessary complexity, dead code, or scope creep beyond the issue.

### 5. Classify findings by severity

Every finding gets a severity so the merge decision is unambiguous:

Assign each finding a stable `CR-N` ID, numbered independently within the issue. Link it to every
relevant upstream `AC-N`, `D-N`, `A-N`, or `IR-N`. Never renumber a published finding.

- **🔴 Blocker** — must be fixed before merge (bug, security hole, broken acceptance criterion, missing required test).
- **🟠 Major** — should be fixed before merge; convention violation or real risk without a clear justification.
- **🟡 Minor** — worth fixing but not blocking (readability, small inconsistencies).
- **🟢 Nit** — optional polish; state it is non-blocking.

Record positive signals only when they materially support the verdict or demonstrate that a risky
criterion or invariant was proven. Do not add ceremonial praise.

### 6. Decide the verdict

Reach one clear verdict:

- **✅ Approve** — no blockers, no unresolved majors.
- **🔧 Approve with comments** — mergeable once minors/nits are addressed at the author's discretion.
- **⛔ Request changes** — at least one blocker or unresolved major.

### 7. Post as a comment on the issue

Publish the review on the referenced issue. Use the following format:

```markdown
## 🔍 Code Review

**Reviewing:** [PR #N / branch `type/GH-N-slug` / working diff]
**Verdict:** [✅ Approve | 🔧 Approve with comments | ⛔ Request changes]

### Summary
[2–3 lines: what the change does and the overall assessment]

### Acceptance criteria
- [x] [Criterion met — where]
- [ ] [Criterion not met — why]

### Findings

**🔴 Blockers**
- `CR-1` (`AC-2`, `D-1`) — `path/to/file` — [what's wrong and why it blocks]

**🟠 Major**
- `CR-2` (`AC-N`, `D-N`, `A-N`, or `IR-N`) — `path/to/file` — [issue + suggested direction]

**🟡 Minor**
- `CR-3` (`AC-N`, `D-N`, `A-N`, or `IR-N`) — `path/to/file` — [issue]

**🟢 Nits**
- `CR-4` (`AC-N`, `D-N`, `A-N`, or `IR-N`) — `path/to/file` — [optional polish]

### Relevant signals
- [Optional evidence that materially supports the verdict; omit when absent]

### Tests
- [Coverage assessment — what's covered, what's missing]

---
*Generated by neotool-review*
```

If there are no findings in a severity bucket, omit that bucket rather than writing "none".
Omit `Relevant signals` when there is no decision-relevant positive evidence.

After commenting, tell the user the verdict, the blocker count, and whether the change is ready to merge.

## Quality criteria

Review the diff, not the whole repo — stay scoped to what changed and its direct blast radius. Every
blocker and major must be actionable: say what is wrong and point at a fix or a convention, never just
"this is bad". Prefer citing a project convention or an acceptance criterion over personal preference,
and mark true preferences as nits. Do not implement the fixes here — the goal is a clear, honest merge
decision recorded on the issue.
