/**
 * Shared helpers for personal-finance / finance-transactions E2E tests.
 *
 * The `transactions` GraphQL query defaults omitted `year`/`month` to the **current calendar
 * month** (server). Use `yearMonthFromIso` + explicit `getTransactions` options when asserting
 * rows in another month; use `navigateFinanceTransactionsToMonth` before table assertions.
 *
 * Related conventions: `tests/e2e/CONVENTIONS.md` and `transactions.e2e.spec.ts` file header.
 *
 * Previously documented only in `transactions.e2e.spec.ts`:
 * - Default `bankAccount.initialBalanceDate` is the first day of the current month — past-month API txs need a custom account.
 * - For calendar-**future** months, `getTransactions({ year, month })` is the robust way to assert
 *   POSTED rows; `monthSummary.totalExpenses` depends on banking version for future months.
 * - UI month summary: call `MonthSummarySection.waitForMetricsReady()` before baseline `getAll()`.
 */

import type { FinanceTransactionsPage } from "../pages/personal-finance/FinanceTransactionsPage";

export function yearMonthFromIso(iso: string): { year: number; month: number } {
  const [y, m] = iso.slice(0, 10).split("-").map(Number);
  return { year: y, month: m };
}

/** Local calendar year and month (1–12), defaulting to “today”. */
export function localCalendarYearMonth(d = new Date()): { year: number; month: number } {
  return { year: d.getFullYear(), month: d.getMonth() + 1 };
}

/** Statement month for `valid-transactions.csv` / `valid-transactions.ofx` fixtures. */
export const IMPORT_FIXTURE_YEAR = 2026;
export const IMPORT_FIXTURE_MONTH = 3;

export function importFixtureTransactionQuery(first = 50): {
  year: number;
  month: number;
  first: number;
} {
  return { year: IMPORT_FIXTURE_YEAR, month: IMPORT_FIXTURE_MONTH, first };
}

export function transactionsQueryFromIso(
  isoDate: string,
  first = 50,
): { year: number; month: number; first: number } {
  const { year, month } = yearMonthFromIso(isoDate);
  return { year, month, first };
}

/**
 * NFC-e receipts are dated in the receipt month; `getTransactions` defaults to the current month.
 */
export function nfceTxQuery(options?: {
  dateFrom?: string | null;
  /** Used when `dateFrom` is missing (default: April 2026 fixture). */
  defaultDate?: Date;
  first?: number;
}): { first: number; year: number; month: number } {
  const receiptMonth = options?.dateFrom
    ? new Date(options.dateFrom)
    : (options?.defaultDate ?? new Date(2026, 3, 1));
  return {
    first: options?.first ?? 100,
    year: receiptMonth.getFullYear(),
    month: receiptMonth.getMonth() + 1,
  };
}

/**
 * Drives the month carousel from the page’s initial month (expected: today’s month after `goto`).
 */
export async function navigateFinanceTransactionsToMonth(
  pfPage: FinanceTransactionsPage,
  target: { year: number; month: number },
): Promise<void> {
  const now = new Date();
  let y = now.getFullYear();
  let m = now.getMonth() + 1;

  while (y > target.year || (y === target.year && m > target.month)) {
    await pfPage.goToPreviousMonth();
    m -= 1;
    if (m === 0) {
      m = 12;
      y -= 1;
    }
  }
  while (y < target.year || (y === target.year && m < target.month)) {
    await pfPage.goToNextMonth();
    m += 1;
    if (m === 13) {
      m = 1;
      y += 1;
    }
  }
}
