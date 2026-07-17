import { expect, Locator, Page } from '@playwright/test';
import { InvestmentSummarySection } from './InvestmentSummarySection';

export class InvestmentsPage {
  readonly summary: InvestmentSummarySection;

  constructor(private readonly page: Page) {
    this.summary = new InvestmentSummarySection(page);
  }

  private async anyMainStateVisible(): Promise<boolean> {
    const checks = await Promise.all([
      this.page.getByTestId('account-selector').isVisible().catch(() => false),
      this.page.getByTestId('create-investment-account-empty-btn').isVisible().catch(() => false),
      this.page.getByTestId('investment-summary-section').isVisible().catch(() => false),
      this.page.getByTestId('investment-table').isVisible().catch(() => false),
      this.page.getByTestId('investment-grid-record-cta').isVisible().catch(() => false),
    ]);
    return checks.some(Boolean);
  }

  async goto(): Promise<void> {
    await this.page.setViewportSize({ width: 1720, height: 1100 });
    await this.page.goto('/personal-finance/investments');
    const urlAfterNavigation = this.page.url();
    if (urlAfterNavigation.includes('/signin')) {
      throw new Error(`Unexpected redirect to signin while opening investments page: ${urlAfterNavigation}`);
    }
    await expect
      .poll(async () => this.anyMainStateVisible(), { timeout: 180000 })
      .toBe(true);
    const emptyStateCta = this.page.getByTestId('create-investment-account-empty-btn');
    // While financial accounts are loading, the page treats the portfolio as non-empty and may
    // render InvestmentSummary in the sidebar even for users who end up with zero accounts.
    // Wait for a stable main layout before touching the summary section.
    await expect
      .poll(
        async () =>
          (await emptyStateCta.isVisible().catch(() => false)) ||
          (await this.page.getByTestId('investment-grid-card-cash').isVisible().catch(() => false)),
        { timeout: 180000 },
      )
      .toBe(true);
    const portfolioLayout = await this.page
      .getByTestId('investment-grid-card-cash')
      .isVisible()
      .catch(() => false);
    if (portfolioLayout) {
      await this.summary.waitForLoad();
      await this.summary.waitForMetricsReady();
    }
  }

  async selectAccount(accountName: string, accountId?: string): Promise<void> {
    const selector = this.page.getByTestId('account-selector');
    const hasSelector = await selector.isVisible().catch(() => false);
    if (!hasSelector) {
      // Some layouts hide the sidebar account selector; keep current scope.
      return;
    }

    await selector.click();
    const popover = this.page.getByTestId('account-selector-popover');
    const popoverVisible = await popover.isVisible().catch(() => false);
    if (!popoverVisible) {
      await expect
        .poll(
          async () => ((await selector.textContent()) ?? '').includes(accountName),
          { timeout: 10000 },
        )
        .toBe(true);
      if (await this.page.getByTestId('investment-summary-section').isVisible().catch(() => false)) {
        await this.summary.waitForMetricsReady();
      }
      return;
    }
    const row = accountId
      ? popover.locator(`[data-account-id="${accountId}"]`).first()
      : popover.getByText(accountName, { exact: true }).locator('xpath=ancestor::*[@role="button"][1]');
    await row.waitFor({ state: 'visible', timeout: 10000 });
    const summaryResponse = this.waitForInvestmentSummaryResponse();
    await row.click({ force: true });
    if (await popover.isVisible().catch(() => false)) {
      await this.page.keyboard.press('Escape');
    }
    await expect
      .poll(
        async () => ((await selector.textContent()) ?? '').includes(accountName),
        { timeout: 10000 },
      )
      .toBe(true);
    await summaryResponse.catch(() => null);
    await this.summary.waitForMetricsReady();
  }

  async selectConsolidatedView(): Promise<void> {
    const selector = this.page.getByTestId('account-selector');
    await selector.click();
    const popover = this.page.getByTestId('account-selector-popover');
    await popover.waitFor({ state: 'visible', timeout: 10000 });
    const summaryResponse = this.waitForInvestmentSummaryResponse();
    await popover.locator('[role="button"]:not([data-account-id])').first().click({ force: true });
    await summaryResponse.catch(() => null);
    await this.summary.waitForMetricsReady();
  }

  private waitForInvestmentSummaryResponse(): Promise<void> {
    return this.page
      .waitForResponse(
        (response) =>
          response.request().method() === 'POST' &&
          response.ok() &&
          response.request().postData()?.includes('InvestmentPortfolioSummary') === true,
        { timeout: 10000 },
      )
      .then(() => undefined);
  }
}
