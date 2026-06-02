/**
 * Shared E2E timeouts for async backend work (recalc, imports, pending actions).
 * UI assertions should use Playwright defaults unless waiting on jobs listed here.
 *
 * @see tests/e2e/CONVENTIONS.md
 */

/** Mirrors `playwright.config.ts` `expect.timeout` — DOM-only assertions. */
export const E2E_EXPECT_TIMEOUT_MS = 5_000;

/** Portfolio TWR / Kafka recalculation idle wait. */
export const E2E_RECALC_TIMEOUT_MS = 120_000;

/** Pending-action rows, coupon maturity UI, similar investment grid polls. */
export const E2E_PENDING_ACTION_POLL_MS = 90_000;

/** File import / duplicate-review / SSE completion. */
export const E2E_IMPORT_JOB_TIMEOUT_MS = 60_000;

/** Serial describe blocks: NFC-e, transaction import sections. */
export const E2E_HEAVY_DESCRIBE_TIMEOUT_MS = 120_000;

/** Long investment TWR / phase-3 / stress suites. */
export const E2E_INVESTMENT_DESCRIBE_TIMEOUT_MS = 240_000;

/** Pause between forced recalculation rounds in idempotency stress tests. */
export const E2E_RECALC_REPLAY_PAUSE_MS = 1_500;
