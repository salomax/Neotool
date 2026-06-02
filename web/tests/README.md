# Tests Conventions

- **Unit/Component tests (Vitest/RTL)**: colocated near the code under `__tests__` folders.
  - Prefer `data-testid` (avoid `getByText` to not break with i18n).
  - Always `import React from "react"` at the top of `*.test.tsx`.
- **E2E tests (Playwright)**: keep under `tests/e2e/**/*.spec.ts`.
- **Separation**: Vitest config ignores any `*.e2e.*` and Playwright runs only in `tests/e2e`.

## E2E flow split

- `@dev`: local development helpers. These flows optimize for speed while building screens and forms, and are excluded from the default `pnpm test:e2e` run.
- `@smoke`: short regression checks for quick confidence.
- `@spec`: canonical spec-driven scenarios. New regression suites should add this tag even while legacy suites are still being migrated.

## E2E commands

- `pnpm test:e2e`: default regression run, excluding `@dev`.
- `pnpm test:e2e:spec`: runs only `@spec` regression coverage, excluding `@dev` and `@smoke`.
- `pnpm test:e2e:smoke`: quick smoke coverage, restricted to smoke-tagged tests.
- `pnpm test:e2e:dev`: dev-assist flows, meant for local iteration, running only the dedicated Playwright `dev` project.
- `pnpm test:e2e:dev:ui`: interactive UI Mode focused on the `dev` project.
- `pnpm test:e2e:dev:debug`: step-through debugging for the `dev` project.
- `pnpm test:e2e:ui`: Playwright UI Mode for interactive debugging without `@dev` flows mixed into the main suite.
- `pnpm test:e2e:codegen`: bootstrap selectors and interactions with Playwright codegen; generated steps should be refined before committing.

## Authentication

- Personal-finance fixtures now provide authenticated Playwright `storageState` per worker.
- Prefer fixture-level auth state over repeating `localStorage` injection in every spec.
- Legacy personal-finance specs now bootstrap from the fixture-provided auth state instead of writing tokens into `localStorage` inside each test file.
