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
- **Code discovery gate (mandatory):** Before any exploratory `Grep`/`Read`/`find`/`rg`, you
  MUST run a Semble call first — how: [semble-search.md](semble-search.md). Direct reads without
  Semble are allowed only when:
  - The exact file path is already known (from a diff, stack trace, issue, or a prior Semble
    result).
  - The file will be edited — read-before-edit is required and is not exploration.
  - Listing a directory for structural orientation.
  - Spot-checking a symbol whose owning file is already identified.
  After the first Semble hit, expand the neighborhood of a known point with `find-related`
  instead of opening adjacent files by hand.

## 2.5. Sandbox-friendly JVM / Gradle defaults

In restricted sandboxes, prefer a local temp-scoped home for Gradle / Kotlin before assuming the
failure is in the code:

- Set `HOME` to a writable temp dir when Kotlin / Gradle tries to write under `~/Library` or
  another blocked host path.
- Set `GRADLE_USER_HOME` to a writable temp dir when wrapper locks or caches under `~/.gradle`
  are blocked.
- Prefer `-Dkotlin.compiler.execution.strategy=in-process` when the Kotlin daemon cannot create
  its marker or socket files in the sandbox.

Treat these as execution hygiene, not code fixes. Apply them early so validation failures reflect
the diff, not the environment.

## 2.6. Do not parallelize KSP-backed Gradle tasks in the same module

For Kotlin modules that use KSP, do **not** run multiple Gradle tasks against the same module in
parallel (`testUnit`, `testIntegration`, `ktlintCheck`, compile tasks, etc.). KSP output cleanup
and regeneration can race on `build/generated/ksp/...`, producing artificial failures.

- Safe: parallelize across different modules when they do not share generated outputs.
- Unsafe: parallelize multiple Gradle invocations that touch the same module's build directory.

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

## 8. Prefer targeted validation before full-module reruns

When adding or adjusting a small number of tests:

1. Run the new or changed test class directly.
2. Run the closest HTTP / service-level test that proves the behavior end to end inside the module.
3. Only then broaden to the full module suite.

This reduces tokens and helps distinguish code failures from unrelated module noise.
