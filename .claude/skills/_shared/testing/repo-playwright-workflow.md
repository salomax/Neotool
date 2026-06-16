# Repo Playwright Workflow

Use these commands and repo facts when executing branch-focused Playwright validation skills.

## Branch inspection

Determine the default remote branch and compare the current branch to its merge base:

```bash
base_ref=$(git symbolic-ref --quiet --short refs/remotes/origin/HEAD 2>/dev/null || echo origin/main)
merge_base=$(git merge-base "$base_ref" HEAD)
git diff --name-status "$merge_base"...HEAD
git status --short
```

If the worktree is dirty, include unstaged and staged diffs in the plan.

## Relevant repo paths

- Playwright config: `web/playwright.config.ts`
- E2E specs: `web/tests/e2e/**/*.e2e.spec.ts`
- Shared E2E conventions: `web/tests/e2e/CONVENTIONS.md`
- Personal-finance E2E plan: `docs/03-features/personal-finance/e2e-test-spec.md`
- Personal-finance coverage map: `docs/03-features/personal-finance/testing/finance-transactions-e2e-coverage-map.md`
- Broader E2E pattern docs: `docs/07-frontend/patterns/e2e-testing-pattern.md`

## Execution commands

Run from `web/` unless there is a reason to stay at repo root.

### Target one spec

```bash
cd web
pnpm exec playwright test tests/e2e/path/to/spec.e2e.spec.ts --project=chromium
```

### Target one scenario

```bash
cd web
pnpm exec playwright test tests/e2e/path/to/spec.e2e.spec.ts --project=chromium --grep "scenario name"
```

### Run a feature slice

```bash
cd web
pnpm exec playwright test tests/e2e/personal-finance --project=chromium
```

### Run the repo script

```bash
cd web
pnpm test:e2e
```

## Local environment facts

- `webServer.command` is `pnpm run dev`
- default local base URL is `http://localhost:3000`
- default local GraphQL endpoint is `http://localhost:4000/graphql`
- local workers default to 1 in `local` and 2 in `development`
- set `PLAYWRIGHT_WORKERS=1` when triaging flakes or backend pressure

## Reliability rules

- Use `--project=chromium` for focused local runs unless cross-browser coverage is the point.
- Re-run the owning feature slice after changing shared helpers, fixtures, or page objects.
- Respect shared timeout helpers instead of copying long numeric literals.
- Prefer `data-testid` and existing page objects over brittle CSS or text-only selectors.

## Setup fallback

If Playwright browsers are missing:

```bash
cd web
pnpm exec playwright install --with-deps
```

If the app is already running locally, allow Playwright to reuse the server configured in `web/playwright.config.ts`.
