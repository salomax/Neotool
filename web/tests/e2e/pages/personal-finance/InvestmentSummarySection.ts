import { expect, Locator, Page } from '@playwright/test';
import { parseCurrencyBr, parsePercentBr } from '../../helpers/currency-dom';

export class InvestmentSummarySection {
  private readonly root: Locator;

  constructor(private readonly page: Page) {
    this.root = page.getByTestId('investment-summary-section');
  }

  async waitForLoad(): Promise<void> {
    await expect(this.root).toBeVisible({ timeout: 30000 });
  }

  async waitForMetricsReady(): Promise<void> {
    await expect(this.root.locator('.MuiSkeleton-root')).toHaveCount(0, { timeout: 30000 });
  }

  async getTotalPortfolioValue(): Promise<number> {
    const text = await this.page.getByTestId('investment-summary-total-portfolio-value').textContent();
    return parseCurrencyBr(text ?? '0');
  }

  async getGrossGainAmount(): Promise<number> {
    const text = await this.page.getByTestId('investment-summary-gross-gain-amount').textContent();
    return parseCurrencyBr(text ?? '0');
  }

  async getTotalContributedAmount(): Promise<number> {
    const text = await this.page.getByTestId('investment-summary-total-contributed-amount').textContent();
    return parseCurrencyBr(text ?? '0');
  }

  async getCashBalanceAmount(): Promise<number> {
    const text = await this.page.getByTestId('investment-summary-cash-balance-amount').textContent();
    return parseCurrencyBr(text ?? '0');
  }

  async getPerformanceTwr(): Promise<number | null> {
    const text = await this.page.getByTestId('investment-summary-performance-twr').textContent();
    return parsePercentBr(text ?? '');
  }

  async selectYearToDatePeriod(): Promise<void> {
    const ytdButton = this.page.getByRole('button', { name: /Este ano|This year/i });
    await ytdButton.click();
    await this.waitForMetricsReady();
  }
}

