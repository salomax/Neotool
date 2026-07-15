---
name: neotool-qa-e2e-branch-validation
description: >
  Use when Codex needs to validate the current Invistus git branch with
  Playwright end-to-end coverage: inspect the branch diff against
  `origin/main`, generate a test plan from the actual changes, map impacted
  behavior to existing `web/tests/e2e` specs, add missing E2E coverage, and
  run the selected Playwright tests until they pass. Trigger on requests such
  as "cobrir a branch atual com E2E", "gerar plano de teste da branch",
  "mapear mudanças para Playwright", "criar testes E2E faltantes", "rodar os
  testes da branch", or "validar a branch atual com QA E2E".
compatibility: Requires Playwright configured in web/ and an existing web/tests/e2e/ suite.
metadata:
  author: invistus
  version: "1.0"
---

# QA E2E Branch Validation

Leave the branch with user-visible changes covered by Playwright and the selected E2E suite passing.

## Outcome

Produce all of the following:

- A branch-aware test plan derived from the real git diff
- A mapping from changed behavior to existing or new Playwright specs
- New or updated E2E tests when coverage is missing
- Executed Playwright runs with results reported clearly

## Workflow

### 1. Build the branch delta first

Inspect committed and uncommitted work before deciding on tests.

```bash
base_ref=$(git symbolic-ref --quiet --short refs/remotes/origin/HEAD 2>/dev/null || echo origin/main)
merge_base=$(git merge-base "$base_ref" HEAD)
git diff --name-status "$merge_base"...HEAD
git diff --name-status
git diff --cached --name-status
git status --short
```

Use the merge-base diff as the primary view of branch changes. Include worktree and staged diffs when the branch is not clean.

### 2. Translate file changes into user-visible risk

Group the diff by behavior, not by file list. Identify:

- affected screens, drawers, forms, summaries, or imports
- permission or tenant-isolation impact
- async flows such as import, recalculation, pending actions, or SSE/polling
- backend or GraphQL changes that surface through an existing web journey

Do not claim E2E coverage from file proximity alone. Name the user journey that proves the change.

### 3. Write the test plan before editing tests

Keep the plan short and explicit. Include:

- changed areas and the user journeys at risk
- existing Playwright specs to run unchanged
- existing specs to extend
- scenarios that require new E2E coverage
- gaps that cannot be covered meaningfully by Playwright and why

If the change is not user-visible, say so explicitly instead of inventing a weak E2E test.

### 4. Reuse existing suite structure before creating new specs

Search the suite before adding coverage:

```bash
rg -n "feature term|data-testid|route segment|graphql field" web/tests/e2e
rg --files web/tests/e2e | sort
```

Prefer this order:

1. run an existing spec unchanged when it already proves the behavior
2. extend the nearest existing spec
3. add helpers, fixtures, or page objects when they remove duplication
4. create a new spec only when no existing file owns the flow

Follow local conventions in `web/tests/e2e/CONVENTIONS.md` before writing or changing tests.

### 5. Add only the missing coverage

When the plan reveals a real gap:

- cover the behavior through the UI path a user actually takes
- prefer stable selectors and existing page objects
- reuse shared helpers and timeout constants
- cover the directly affected regression edges before broadening scope

For personal-finance changes, read the shared coverage docs listed in [../_shared/testing/playwright-spec-map.md](../_shared/testing/playwright-spec-map.md) before adding another parallel spec.

### 6. Run tests in narrowing rings

Run the smallest reliable subset first, then broaden only as needed:

1. targeted spec or `--grep` for the changed scenario
2. the owning feature spec set
3. adjacent specs if shared helpers, fixtures, or cross-feature behavior changed

Use the repo-specific commands in [../_shared/testing/repo-playwright-workflow.md](../_shared/testing/repo-playwright-workflow.md).

### 7. Close only when coverage and execution are both clear

Do not finish with "tests added but not run". Report:

- which specs cover each changed behavior
- which specs were added or updated
- exact commands executed
- pass/fail outcome
- remaining blockers, if any

## Read On Demand

Open only what the current branch needs:

- Repo commands, environment, and execution strategy: [../_shared/testing/repo-playwright-workflow.md](../_shared/testing/repo-playwright-workflow.md)
- Existing spec ownership and feature-to-spec mapping: [../_shared/testing/playwright-spec-map.md](../_shared/testing/playwright-spec-map.md)

## Guardrails

- Prefer branch diff plus user journey reasoning over broad "run everything" behavior.
- Prefer extending the owning spec over creating duplicate coverage in a new file.
- Do not add cosmetic E2E tests for behavior already proven elsewhere.
- Do not ignore backend changes that alter a real UI flow.
- Do not mark the task complete while selected Playwright tests are still failing.
