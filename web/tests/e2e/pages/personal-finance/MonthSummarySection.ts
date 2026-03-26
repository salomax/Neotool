/**
 * Page Object for the MonthSummary component.
 *
 * Selectors use data-testid attributes.
 * `openingBalance` is rendered as a hidden element (aria-hidden) for test access.
 */

import { Page, Locator } from '@playwright/test';

export class MonthSummarySection {
  private readonly root: Locator;

  constructor(private readonly page: Page) {
    this.root = page.getByTestId('month-summary-section');
  }

  async waitForLoad(): Promise<void> {
    await this.root.waitFor({ state: 'visible' });
  }

  /** Returns the totalIncome value as a number (strips currency formatting). */
  async getTotalIncome(): Promise<number> {
    const text = await this.page
      .getByTestId('month-summary-total-income')
      .textContent();
    return parseCurrencyBr(text ?? '0');
  }

  /** Returns the totalExpenses value as a number. */
  async getTotalExpenses(): Promise<number> {
    const text = await this.page
      .getByTestId('month-summary-total-expenses')
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

/**
 * Parses a pt-BR formatted currency string into a float.
 * Examples: "R$ 1.500,75" → 1500.75 | "-R$ 200,00" → -200 | "1.234" → 1234
 */
function parseCurrencyBr(text: string): number {
  const cleaned = text
    .replace(/R\$\s*/g, '')
    .replace(/\./g, '')
    .replace(',', '.')
    .trim();
  const value = parseFloat(cleaned);
  return isNaN(value) ? 0 : value;
}
