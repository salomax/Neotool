# E2E test conventions (Playwright)

Canonical helpers and timeouts live under `tests/e2e/helpers/`. Cursor rule: `.cursor/rules/e2e-playwright.mdc`.

## Infrastructure

- **Workers**: `playwright.config.ts` caps parallelism (`development`: 2). Override with `PLAYWRIGHT_WORKERS=1` when triaging flakes.
- **Timeouts**: Use shared constants from `helpers/timeouts.ts` — do not copy `90_000` / `120_000` literals.
- **Heavy suites**: `test.describe.configure({ mode: 'serial', timeout: E2E_* })` for import, NFC-e, investment TWR.

## Async backend (recalc, imports, pending actions)

| Work | Constant / helper |
|------|-------------------|
| Portfolio recalc idle | `waitForRecalculation()` in `investment-e2e.ts` (`E2E_RECALC_TIMEOUT_MS`) |
| TWR poll | `expectPortfolioTwrCloseTo()` |
| Pending actions / coupon UI | `E2E_PENDING_ACTION_POLL_MS` on `expect.poll` / `toBeVisible` |
| File import job | `E2E_IMPORT_JOB_TIMEOUT_MS` |
| Idempotent replay | `replayRecalculationRound()` — compare **two post-replay** snapshots |

Default `expect.timeout` (5s) is for stable DOM only.

## GraphQL list queries and dates

`getTransactions` defaults omitted `year`/`month` to the **current calendar month**.

- Fixture month: `importFixtureTransactionQuery()` (`finance-transactions-e2e.ts`).
- NFC-e receipt month: `nfceTxQuery({ dateFrom })`.
- Arbitrary ISO date: `transactionsQueryFromIso(iso)` or `yearMonthFromIso(iso)`.

Always pass the same period you used when creating data.

## Locators

- Scope to **row, drawer, or section** before `toHaveCount(1)` or role queries.
- Avoid page-global counts for repeatable entities (holdings, pending actions).
- After **account switch**, re-open the drawer/panel under test.
- Clear persisted UI: `clearInvestmentGridClassSelection(page)` when grid tabs stick in `sessionStorage`.

## Test data vs domain rules

- Validate asset type × coupon frequency × issuer before recording (e.g. Treasury ≠ monthly coupon).
- Time-gated UI: use `couponDaySoon()` + `couponPendingPurchaseDate()` from `fixed-income-e2e.ts` for monthly coupon pending actions (do not use ~28-day purchases — they fall outside the 7-day visibility window).
- Split scenarios when one instrument cannot satisfy all legs (treasury form UI vs debenture pending action).

## Assertions

- Money / rates from API: `toBeCloseTo`, not exact equality.
- Forms: assert submit **disabled** + drawer open instead of flaky blur/submit.
- Idempotency: warm-up replay → snapshot A → replay → snapshot B → assert A ≈ B.
- Prefer `data-testid`; poll API/grid until data exists before opening drawers.

## Date helpers

Use `fixed-income-e2e.ts`: `isoDaysAgo`, `isoDaysFromNow`, `todayIso`, `couponDaySoon` — do not duplicate in spec files.
