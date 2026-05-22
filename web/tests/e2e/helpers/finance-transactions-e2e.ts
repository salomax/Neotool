/**
 * Shared helpers for personal-finance / finance-transactions E2E tests.
 *
 * The `transactions` GraphQL query defaults omitted `year`/`month` to the **current calendar
 * month** (server). Use `yearMonthFromIso` + explicit `getTransactions` options when asserting
 * rows in another month; use `navigateFinanceTransactionsToMonth` before table assertions.
 *
 * Related conventions (see `transactions.e2e.spec.ts` file header):
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
