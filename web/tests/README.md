# Tests Conventions

- **Unit/Component tests (Vitest/RTL)**: colocated near the code under `__tests__` folders.
  - Prefer `data-testid` (avoid `getByText` to not break with i18n).
  - Always `import React from "react"` at the top of `*.test.tsx`.
- **E2E tests (Playwright)**: keep under `tests/e2e/*.e2e.spec.mjs`.
- **Separation**: Vitest config ignores any `*.e2e.*` and Playwright runs only in `tests/e2e`.

