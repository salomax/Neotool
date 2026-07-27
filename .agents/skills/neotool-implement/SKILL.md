---
name: neotool-implement
description: >
  Orchestrates the full implementation of a GitHub issue in the Invistus project.
  Use when the task is to implement, build, code, or execute the work described in an issue.
  Triggers on: "implement issue #N", "implement #N", "code issue #N", "build issue #N",
  "start implementation of #N", "work on issue #N", "execute the backlog for #N",
  "let's implement #N", "start coding #N", or any prompt that combines an action verb
  (implement, build, code, develop, execute, work on) with a GitHub issue reference.
  An issue number is required — ask for it immediately if not provided.
compatibility: Requires GitHub MCP or gh CLI. Reads skills from .agents/skills/ at the project root.
metadata:
  author: invistus
  version: "1.3"
---

# Implementation Orchestrator — Invistus

Coordinate the full implementation of a GitHub issue by reading its backlog, loading the right
skills for the scope, and executing tasks one by one.

**Token discipline (v1.3):** apply
[neotool-conventions/references/token-discipline.md](../neotool-conventions/references/token-discipline.md)
for every implementation — narrowest tests first, bound CLI output, summarize before re-run,
broad-rerun cap, minimal-path GitHub lookups. Do not re-author those rules here.

## Non-negotiable rules

1. **All content written to GitHub must be in English** — comments, task updates, PR descriptions. No exceptions, regardless of the language used in the conversation.
2. **Issue number is required** — do not proceed without one. If not provided, stop and ask:
   > What is the issue number? e.g. `#42`
3. **Never implement without a backlog** — if no backlog exists on the issue, stop and instruct the user to run `neotool-solution-design` and `neotool-backlog-gen` first.
4. **Follow the backlog, not intuition** — implement exactly what the backlog specifies, in the order listed. If something seems wrong, flag it instead of silently deviating.
5. **Always leave a final issue comment in English** — before finishing, post a reviewer-oriented implementation summary on the GitHub issue covering the concrete file-level changes, validation status, and any remaining blockers or inconsistencies. This step is mandatory even when validation is partially blocked by unrelated branch issues.

## Workflow

### Step 1 — Read the issue and locate the backlog

**Minimal-path lookup** (see `token-discipline.md` §5): when the user already gave an issue URL
and/or backlog comment URL, fetch **only** those resources — one issue read + one comments
read (or the single comment if linked). Do **not** fan out exploratory project-board,
milestone, or label lookups unless the backlog explicitly requires board metadata.

Read the issue from GitHub:
- Title, description, labels, and current state
- All comments — look for a comment containing `📋 Development Backlog`

If no backlog is found, stop and respond:

```
No development backlog found on issue #N.

Please run neotool-solution-design and neotool-backlog-gen first, then come back to implement.
```

### Step 2 — Determine scope from the backlog

Parse the backlog to identify which layers are involved. Prefer deriving scope from the
backlog text already loaded in Step 1 — do not open extra GitHub/project calls just to
classify metadata.

| Backlog section present     | Load this skill          |
|-----------------------------|--------------------------|
| Backend tasks               | `neotool-kotlin-service` |
| Frontend tasks (web)        | `neotool-nextjs-app`     |
| Frontend tasks (mobile/RN)  | `neotool-expo-mobile`    |
| Personal finance domain     | `neotool-finance-domain` |
| Always                      | `neotool-conventions`    |

**Web vs mobile:** if the backlog touches `web/` (Next.js) load `neotool-nextjs-app`; if it touches
`mobile/` (React Native + Expo) load `neotool-expo-mobile`. A cross-platform issue may load both.

Load all skills that match before writing any code. Skills are located at `.agents/skills/` in the project root.
Always load `token-discipline.md` via `neotool-conventions` for implementation work.

### Step 3 — Confirm branch

The branch is typically created by `neotool-open-issue` before implementation starts.
Verify the expected branch name from the issue or backlog:

```
{type}/GH-{N}-{slug}   (feat/, fix/, refactor/, or chore/)
```

**Fail fast before editing any files:** if git is available and the current branch does **not**
match the expected issue branch, stop implementation work immediately and switch to the expected
branch before making changes. Do not start coding on an unrelated branch and plan to move the diff
later.

**If git is available and the branch does not exist locally:**
```bash
git checkout -b {type}/GH-{N}-{slug}
git push -u origin {type}/GH-{N}-{slug}
```

**If running in a sandboxed environment where git is restricted:**
Skip branch creation — proceed with implementation. The user will handle the git push and PR. Note the expected branch name in your final summary so the user knows where to push.

### Step 4 — Execute tasks in order

Work through the backlog checklist top to bottom. For each task:

1. Read the task description
2. Locate the relevant code area (explore the codebase — do not guess file paths)
3. Implement the change following the loaded skill conventions
4. Verify the change compiles / lints before moving on
5. Update the task checkbox on the issue comment to `[x]`

Treat each checkbox as a unit of work. Complete one before starting the next.

**If a task is blocked or unclear:**
- Do not skip silently
- Post a comment on the issue describing the blocker
- Ask the user how to proceed

### Step 5 — Run validation

After all tasks are complete, validate using **narrowing rings** from
`token-discipline.md`. Always pass `--console=plain` for Gradle. Prefer RTK-compressed
output when the hook is active; use `rtk proxy <cmd>` only when the filter hides a needed line.

**Backend — ring 1 (inner loop, required):**
```bash
./gradlew :<module>:testUnit --console=plain
./gradlew :<module>:ktlintCheck --console=plain   # if the module exposes it; else root ktlintCheck on touched paths
```
`<module>` = Gradle project owning the diff (`banking`, `mcp`, `security`, …). Run once per
touched module.

**Backend — ring 2 (escalate only after ring 1 is green, or when IT is in backlog scope):**
```bash
./gradlew :<module>:testIntegration --console=plain
```

**Backend — ring 3 (pre-PR gate only — not the diagnostic loop):**
```bash
./gradlew ktlintCheck --console=plain
./gradlew test --console=plain
```

**Failure handling (mandatory):**
1. Summarize the failure (command, failing tests, one-line hypothesis) **before** any re-run.
2. Re-run only the narrowest command that falsifies the hypothesis
   (add `--tests '…FullyQualifiedClass…'` when possible).
3. **Broad-rerun cap:** at most **2** broad reruns (ring 2+ / unscoped suite) for the same
   failing scope. After the second, **stop and ask the user** — do not burn a third broad cycle.
4. Never dump raw Surefire/Failsafe XML or full Micronaut/Flyway/Testcontainers startup logs;
   use tail/grep/filtered excerpts.

**Frontend (web):**
```bash
npm run lint
npm run type-check
npm test
```

**Frontend (mobile — from `mobile/`):**
```bash
pnpm lint
pnpm typecheck
pnpm test
```

Fix any failures before proceeding (within the rerun-cap rules above).

### Step 5.5 — Self-review before requesting review

Run this checklist before writing the summary. Each item maps to a round-1 blocker seen repeatedly on this project; catching them here removes an entire review round (the single most expensive part of the loop).

- [ ] **Every `test:*` in the backlog exists** as an actual test, and each one would **fail without the fix** — not just pass with it. For any security comparison, confirm the fixture uses distinct values for the things being distinguished (see `neotool-kotlin-service` → tests).
- [ ] **The backlog's rejection/edge matrix is covered**, not only the happy path — expired, revoked, unauthorized, missing-claim, wrong-tenant, empty-input, as the backlog lists.
- [ ] **Every trust-sensitive value has a fail-closed test.** If the change moves a value across a service/network boundary (token claim, `X-Mcp-*` or other header, scope, provenance, tenant/account binding), there is a test that a **forged or stripped** value fails closed — the call is rejected, ignored, or defaults to the safe value, never trusted. Trace it end to end: confirm it actually arrives on the real path (through the router / propagation, not only when injected directly in the test), and that a spoofed value on the untrusted path does not take effect. The router propagates only `authorization`, `x-request-id`, `traceparent`, `tracestate`, `baggage` — a custom header that rides the router is silently dropped, so a test that injects it directly proves nothing about production.
- [ ] **No claim the design made about existing code went unverified in the diff.** If the design said a method "already does X" and you found it did not, that divergence is in the summary, not silently worked around.
- [ ] **A contract this issue applies but another defines** (scope map, shared claim, error taxonomy) is injected, not re-authored — and if you had to duplicate it for sequencing reasons, the summary says so and names the follow-up.
- [ ] **Validation actually ran** for the touched scope (`ktlintCheck`, `testUnit`, and the relevant integration tests), and the summary reports exactly what passed and what was not run.

Fix what this surfaces before summarising. A blocker you catch here costs nothing; the same blocker caught in review costs a full round trip.

### Step 6 — Confirm completion

Before responding to the user, post a final GitHub issue comment in English with:

- A concrete list of changed files and what changed in each one
- What was implemented behaviorally
- Which validations passed
- Which validations were blocked or failed
- Any remaining inconsistencies, branch-local breakages, or follow-up risks

After posting that comment, respond with a summary:

```
Implementation complete for issue #N.

Tasks completed: [N]/[N]
Layers touched: [backend | frontend | both]
Branch: {type}/GH-{N}-{slug}

Next steps:
- Open a PR targeting main
- Reference the issue in the PR description: "Closes #N"
```

Do not open the PR automatically — let the user review the changes first.

---

## Scope reference

| Task keyword            | Layer      | Skill to load            |
|-------------------------|------------|--------------------------|
| migration, entity, JPA  | Backend    | `neotool-kotlin-service` |
| service, repository     | Backend    | `neotool-kotlin-service` |
| resolver, GraphQL, DTO  | Backend    | `neotool-kotlin-service` |
| component, page, hook (web)  | Frontend (web)    | `neotool-nextjs-app`     |
| mutation, query, Apollo (web)| Frontend (web)    | `neotool-nextjs-app`     |
| screen, Expo Router, RN Paper, native | Frontend (mobile) | `neotool-expo-mobile` |
| React Native, Expo, iOS, Android | Frontend (mobile) | `neotool-expo-mobile` |
| Transaction, CreditCard | Domain     | `neotool-finance-domain` |
| MonthSummary, balance   | Domain     | `neotool-finance-domain` |
| auth, error, i18n       | Cross-cut  | `neotool-conventions`    |

---

## Handling edge cases

**No backlog, but a technical analysis exists:**
Run `neotool-backlog-gen` to generate the backlog from the existing analysis, then return here.

**Partial backlog (some tasks already checked):**
Skip completed tasks (already `[x]`) and continue from the first unchecked task.

**Large scope / milestones:**
If the backlog is split into milestones, ask the user which milestone to implement first. Implement one milestone at a time.

**Unexpected complexity mid-task:**
Stop, post a comment on the issue describing what was discovered, and ask for guidance. Do not improvise architecture decisions.
