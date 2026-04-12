/**
 * Page Object for the main Finance Transactions page
 * (/personal-finance/finance-transactions).
 *
 * Acts as the root entry point for all PF E2E tests. Exposes child section
 * objects (MonthSummarySection, TransactionTablePage, CreateAccountDrawer).
 */

import { Page, Locator, expect } from '@playwright/test';
import { navigateFinanceTransactionsToMonth } from '../../helpers/finance-transactions-e2e';
import { MonthSummarySection } from './MonthSummarySection';
import { TransactionTablePage } from './TransactionTablePage';
import { CreateAccountDrawer } from './CreateAccountDrawer';

export class FinanceTransactionsPage {
  readonly summary: MonthSummarySection;
  readonly transactionTable: TransactionTablePage;
  readonly createAccountDrawer: CreateAccountDrawer;

  constructor(private readonly page: Page) {
    this.summary = new MonthSummarySection(page);
    this.transactionTable = new TransactionTablePage(page);
    this.createAccountDrawer = new CreateAccountDrawer(page);
  }

  private emptyStateTypeButton(type: 'BANK' | 'CARD'): Locator {
    return this.page.getByTestId('empty-accounts-state').getByTestId(`account-type-${type}`).first();
  }

  private async anyMainStateVisible(): Promise<boolean> {
    const checks = await Promise.all([
      this.page.getByTestId('empty-accounts-state').isVisible().catch(() => false),
      this.page.getByTestId('account-selector').isVisible().catch(() => false),
      this.page.getByTestId('btn-new-account').isVisible().catch(() => false),
      this.page.getByTestId('account-type-BANK').isVisible().catch(() => false),
      this.page.getByText(/nova conta ou cartão/i).isVisible().catch(() => false),
    ]);
    return checks.some(Boolean);
  }

  // ── Navigation ──────────────────────────────────────────────────────────────

  async goto(): Promise<void> {
    await this.page.goto('/personal-finance/finance-transactions');
    await this.page.getByRole('heading', { name: /movimentações financeiras/i }).waitFor({ state: 'visible' });
    await expect.poll(async () => this.anyMainStateVisible(), { timeout: 30000 }).toBe(true);
  }

  // ── Account selector ────────────────────────────────────────────────────────

  /**
   * Selects an account in the selector. Prefer passing `accountId` when the user has multiple
   * accounts — label-only clicks can hit the wrong node; `data-account-id` is unambiguous.
   */
  async selectAccount(accountName: string, accountId?: string): Promise<void> {
    await this.page.getByTestId('account-selector').click();
    const popover = this.page.getByTestId('account-selector-popover');
    await popover.waitFor({ state: 'visible', timeout: 10_000 });
    if (accountId) {
      await popover.locator(`[data-account-id="${accountId}"]`).click();
    } else {
      await popover.getByText(accountName, { exact: true }).click();
    }
    await expect(this.page.getByTestId('account-selector')).toContainText(accountName);
    await Promise.all([
      this.summary.waitForMetricsReady(),
      this.transactionTable.waitForLoad(),
    ]);
  }

  /**
   * Opens the edit drawer for a specific account through the selector popover.
   */
  async openEditAccountDrawer(accountName: string): Promise<CreateAccountDrawer> {
    await this.page.getByTestId('account-selector').click();
    const popover = this.page.getByTestId('account-selector-popover');
    await popover.waitFor({ state: 'visible', timeout: 10_000 });
    const accountNameCell = popover.getByText(accountName, { exact: true });
    await accountNameCell.waitFor({ state: 'visible' });
    const accountRow = accountNameCell.locator('xpath=ancestor::*[@role="button"][1]');
    await accountRow.getByLabel(/editar|edit/i).click();
    await this.createAccountDrawer.waitForOpen();
    return this.createAccountDrawer;
  }

  /**
   * Returns the currently selected account name from the selector.
   */
  async getSelectedAccountName(): Promise<string> {
    return (
      (await this.page.getByTestId('account-selector').textContent()) ?? ''
    ).trim();
  }

  /** Returns true when the empty-accounts state is visible (no accounts created yet). */
  async isEmptyAccountsState(): Promise<boolean> {
    return this.page.getByTestId('empty-accounts-state').isVisible();
  }

  // ── Create account ──────────────────────────────────────────────────────────

  /** Opens the Create Account drawer. */
  async openCreateAccountDrawer(initialType?: 'BANK' | 'CARD'): Promise<CreateAccountDrawer> {
    const emptyStateTypeButton =
      initialType != null ? this.emptyStateTypeButton(initialType) : null;

    if (emptyStateTypeButton && (await emptyStateTypeButton.isVisible().catch(() => false))) {
      await emptyStateTypeButton.scrollIntoViewIfNeeded();
      await emptyStateTypeButton.click();
    } else {
      await expect.poll(
        async () =>
          (await this.page.getByTestId('btn-new-account').isVisible().catch(() => false)) ||
          (await this.page.getByText(/nova conta ou cartão/i).isVisible().catch(() => false)),
        { timeout: 30000 },
      ).toBe(true);

      const newAccountButton = this.page.getByTestId('btn-new-account');
      if (await newAccountButton.isVisible().catch(() => false)) {
        await newAccountButton.scrollIntoViewIfNeeded();
        await newAccountButton.click();
      } else {
        await this.page.getByText(/nova conta ou cartão/i).click();
      }
    }

    await this.createAccountDrawer.waitForOpen();
    return this.createAccountDrawer;
  }

  // ── Month navigation ────────────────────────────────────────────────────────

  /** Returns the current month/year label shown in the carousel (e.g. "março 2026"). */
  async getCurrentMonthLabel(): Promise<string> {
    return (
      (await this.page.getByTestId('month-year-carousel-current').textContent()) ?? ''
    ).trim();
  }

  async goToPreviousMonth(): Promise<void> {
    await this.page.getByTestId('btn-prev-month').click();
    await this.summary.waitForLoad();
    await Promise.all([
      this.summary.waitForMetricsReady(),
      this.transactionTable.waitForLoad(),
    ]);
  }

  async goToNextMonth(): Promise<void> {
    await this.page.getByTestId('btn-next-month').click();
    await this.summary.waitForLoad();
    await Promise.all([
      this.summary.waitForMetricsReady(),
      this.transactionTable.waitForLoad(),
    ]);
  }

  /**
   * Aligns the carousel with a calendar month (page is expected to start on today’s month after `goto`).
   * @see navigateFinanceTransactionsToMonth in `helpers/finance-transactions-e2e.ts`
   */
  async navigateToCalendarMonth(target: { year: number; month: number }): Promise<void> {
    await navigateFinanceTransactionsToMonth(this, target);
  }

  // ── Transactions ────────────────────────────────────────────────────────────

  /** Opens the inline form for creating a new transaction. */
  async openCreateTransactionForm(): Promise<void> {
    await this.page.getByTestId('btn-new-transaction').click();
  }

  // ── Assertions ──────────────────────────────────────────────────────────────

  /** Asserts that the account with the given name is visible in the selector. */
  async expectAccountInSelector(accountName: string): Promise<void> {
    await this.page.getByTestId('account-selector').click();
    const popover = this.page.getByTestId('account-selector-popover');
    await popover.waitFor({ state: 'visible', timeout: 10_000 });
    await expect(popover.getByText(accountName, { exact: true })).toBeVisible({ timeout: 5000 });
    await this.page.keyboard.press('Escape');
  }

  /** Asserts that the account is NOT present in the selector (e.g. after soft-delete). */
  async expectAccountNotInSelector(accountName: string): Promise<void> {
    await this.page.getByTestId('account-selector').click();
    const popover = this.page.getByTestId('account-selector-popover');
    await popover.waitFor({ state: 'visible', timeout: 10_000 });
    await expect(popover.getByText(accountName, { exact: true })).toHaveCount(0);
    await this.page.keyboard.press('Escape');
  }
}
