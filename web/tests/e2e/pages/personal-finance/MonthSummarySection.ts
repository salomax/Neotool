/**
 * Page Object for the MonthSummary component.
 *
 * Selectors use data-testid attributes.
 * `openingBalance` is rendered as a hidden element (aria-hidden) for test access.
 * Income/expense totals use `*-amount` test ids (value only) — see `helpers/currency-dom.ts`.
 */

import { Page, Locator, expect } from '@playwright/test';
import { parseCurrencyBr } from '../../helpers/currency-dom';

function escapeRegex(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

export class MonthSummarySection {
  private readonly root: Locator;

  constructor(private readonly page: Page) {
    this.root = page.getByTestId('month-summary-section');
  }

  async waitForLoad(): Promise<void> {
    await this.root.waitFor({ state: 'visible' });
  }

  /**
   * While the summary query is in its first load (`loading && !summary`), the closing balance
   * node renders a MUI Skeleton with no parseable amount — {@link getClosingBalance} becomes 0
   * and balance assertions fail intermittently (e.g. expected 100 vs received 1000).
   *
   * `FinanceTransactionsPage.selectAccount` / month carousel already wait, but a **refetch**
   * can briefly show Skeleton again — call this immediately before **baseline** reads of
   * {@link getAll}, not only after mutations (see TX-H-01/H-02/H-06/H-09 in transactions E2E).
   */
  async waitForMetricsReady(): Promise<void> {
    await expect(
      this.page.getByTestId('month-summary-closing-balance').locator('.MuiSkeleton-root'),
    ).toHaveCount(0, { timeout: 30000 });
  }

  /**
   * After mutations, Apollo often refetches with `cache-and-network` while keeping the previous
   * summary on screen (no Skeleton). Poll until the displayed closing balance matches.
   */
  async waitForClosingBalance(expected: number, precision = 2): Promise<void> {
    await expect
      .poll(
        async () => {
          await this.waitForMetricsReady();
          return this.getClosingBalance();
        },
        { timeout: 30_000 },
      )
      .toBeCloseTo(expected, precision);
  }

  /** Returns the totalIncome value as a number (strips currency formatting). */
  async getTotalIncome(): Promise<number> {
    const text = await this.page
      .getByTestId('month-summary-total-income-amount')
      .textContent();
    return parseCurrencyBr(text ?? '0');
  }

  /** Returns the totalExpenses value as a number. */
  async getTotalExpenses(): Promise<number> {
    const text = await this.page
      .getByTestId('month-summary-total-expenses-amount')
      .textContent();
    return parseCurrencyBr(text ?? '0');
  }

  /** Returns the closingBalance value as a number. */
  async getClosingBalance(): Promise<number> {
    const text = await this.page
      .getByTestId('month-summary-closing-balance')
      .textContent();
    return parseCurrencyBr(text ?? '0');
  }

  /** Returns the openingBalance value as a number. */
  async getOpeningBalance(): Promise<number> {
    const text = await this.page
      .getByTestId('month-summary-opening-balance')
      .textContent();
    return parseCurrencyBr(text ?? '0');
  }

  /**
   * Activates a category filter from the month-summary breakdown and waits for the
   * filtered `transactions` query to finish (chip click alone is not enough for table assertions).
   */
  async filterByCategoryName(categoryName: string, categoryId?: string): Promise<void> {
    const transactionsResponse = this.page.waitForResponse(
      (response) => {
        const req = response.request();
        if (req.method() !== 'POST' || !/graphql/i.test(req.url())) return false;
        const body = req.postData() ?? '';
        if (!body.includes('transactions') || !response.ok()) return false;
        return categoryId ? body.includes(categoryId) : true;
      },
      { timeout: 30_000 },
    );

    const chip = this.page.getByRole('button', {
      name: new RegExp(`categoria:\\s*${escapeRegex(categoryName)}`, 'i'),
    });
    await expect(chip).toBeVisible({ timeout: 15_000 });
    await chip.click();
    await transactionsResponse;
  }

  /** Activates a tag filter chip (`#tag`) and waits for the filtered transactions query. */
  async filterByTag(tag: string): Promise<void> {
    const transactionsResponse = this.page.waitForResponse(
      (response) => {
        const req = response.request();
        if (req.method() !== 'POST' || !/graphql/i.test(req.url())) return false;
        const body = req.postData() ?? '';
        return body.includes('transactions') && body.includes(tag) && response.ok();
      },
      { timeout: 30_000 },
    );

    const chip = this.root.getByText(`#${tag}`, { exact: true });
    await expect(chip).toBeVisible({ timeout: 15_000 });
    await chip.click();
    await transactionsResponse;
  }

  /** Snapshot of all summary values. */
  async getAll(): Promise<{
    totalIncome: number;
    totalExpenses: number;
    closingBalance: number;
    openingBalance: number;
  }> {
    return {
      totalIncome: await this.getTotalIncome(),
      totalExpenses: await this.getTotalExpenses(),
      closingBalance: await this.getClosingBalance(),
      openingBalance: await this.getOpeningBalance(),
    };
  }
}
