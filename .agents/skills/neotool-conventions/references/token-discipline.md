# Token Discipline — Invistus Agent Workflows

Cross-cutting rules that every implementation / validation skill **applies**. This file is the
**definer** — do not re-author these caps inside individual skills; reference this document.

Goal: converge faster with fewer tokens when tests fail or metadata is incomplete. Measured cost
drivers to avoid: broad Gradle reruns, raw Micronaut/Flyway/Testcontainers/XML dumps, cascading
diagnostic loops, and exploratory GitHub/board fan-out.

## 1. Narrowest reliable test scope first

Run the smallest suite that can prove the change, then broaden only on green:

| Ring | Backend (Kotlin) | When |
|------|------------------|------|
| 1 | `./gradlew :<module>:testUnit --console=plain` | Inner loop after each task |
| 2 | `./gradlew :<module>:testIntegration --console=plain` | After unit green, or when IT is in scope |
| 3 | Full suite / multi-module (pre-PR gate only) | Final validation before opening a PR |

Frontend / E2E skills already use narrowing rings — keep that pattern; do not invent a parallel
broader default for backend.

**Module** = the Gradle project that owns the touched files (`banking`, `mcp`, `security`, …).
If multiple modules changed, run ring 1 per touched module before escalating any of them.

## 2. Bound every CLI read

- Prefer `--console=plain` (Gradle) and quiet / failures-only flags when available.
- Read failures via **tail / grep / filtered report**, never dump raw XML Surefire/Failsafe
  reports or full Micronaut startup logs into context.
- Prefer RTK-compressed shell output when the hook is active (see escape hatch below).
- Cap follow-up reads: summarize what failed (test name, assertion, first stack frame) before
  opening more files.
- For **code discovery**, prefer the Semble MCP / CLI over exploratory Grep+Read sweeps — see
  [semble-search.md](semble-search.md).

## 3. Summarize failure before re-running

Never re-run a broad suite “to see it again”.

1. Capture the failure summary (command, failing tests, one-line cause hypothesis).
2. Change code or fixtures based on that hypothesis.
3. Re-run **only** the narrowest command that can falsify the hypothesis
   (e.g. `./gradlew :banking:testUnit --tests '…FullyQualifiedClass…' --console=plain`).

## 4. Broad-rerun cap (then stop and ask)

- **Max 2 broad reruns** per implementation turn for the same failing scope.
  “Broad” = ring 2+ or an unscoped `./gradlew test` / full module IT without `--tests`.
- Targeted reruns with a **new hypothesis** (different `--tests`, different module) do not
  count toward the cap.
- After the second broad failure without a clear next hypothesis: **stop**, report the
  summarized failures to the user, and ask how to proceed. Do not burn a third broad cycle.

## 5. Minimal-path metadata lookups

When the user asked to implement / validate a known issue:

- Prefer **one** GitHub REST call for the issue (and one for comments if the backlog URL was
  not already given).
- Do not fan out exploratory project-board / Project V2 / label / milestone lookups unless the
  backlog or user explicitly requires board metadata.
- In the DevPod sandbox, if `gh` is broken, use a single authenticated REST path — do not
  retry multiple CLI variants before reading the backlog the user already linked.

## 6. RTK escape hatch (raw output)

RTK compresses shell output for Claude Code and Cursor (Tier-1 hooks) and is **best-effort**
for Codex (rules-file / `AGENTS.md` — depends on model compliance).

When compressed output hides a needed line:

```bash
rtk proxy <command>    # raw passthrough + tracking
```

Examples:

```bash
rtk proxy ./gradlew :banking:testUnit --console=plain
rtk proxy git diff
```

Do not disable RTK globally for routine work. Use `rtk proxy` only for the specific command
where the filter is insufficient, then return to the normal path.

## 7. When to stop exploring

Stop and ask the user when any of these hold:

- Broad-rerun cap reached.
- Failure cause is outside the issue scope (unrelated red suite, infra down, missing secrets).
- Next diagnostic step would re-read large logs already summarized, or re-run the same
  unscoped suite.
- Acceptance requires a measurement or environment rebuild you cannot complete in-session
  (e.g. DevPod image rebuild for a new binary) — report what is done and what is blocked.
