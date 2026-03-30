/**
 * Page Object for the main Finance Transactions page
 * (/personal-finance/finance-transactions).
 *
 * Acts as the root entry point for all PF E2E tests. Exposes child section
 * objects (MonthSummarySection, TransactionTablePage, CreateAccountDrawer).
 */

import { Page, Locator, expect } from '@playwright/test';
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

  // ── Navigation ──────────────────────────────────────────────────────────────

  async goto(): Promise<void> {
    await this.page.goto('/personal-finance/finance-transactions');
    await this.page.waitForLoadState('networkidle');
  }

  // ── Account selector ────────────────────────────────────────────────────────

  /**
   * Selects an account by its display name in the account selector.
   * Waits for the page to reflect the selection (URL param or data load).
   */
  async selectAccount(accountName: string): Promise<void> {
    await this.page.getByTestId('account-selector').click();
    await this.page.getByRole('option', { name: accountName }).click();
    await this.page.waitForLoadState('networkidle');
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
  async openCreateAccountDrawer(): Promise<CreateAccountDrawer> {
    await this.page.getByTestId('btn-new-account').click();
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
    await this.page.waitForLoadState('networkidle');
  }

  async goToNextMonth(): Promise<void> {
    await this.page.getByTestId('btn-next-month').click();
    await this.page.waitForLoadState('networkidle');
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
    await expect(this.page.getByRole('option', { name: accountName })).toBeVisible();
    // Close selector without selecting
    await this.page.keyboard.press('Escape');
  }

  /** Asserts that the account is NOT present in the selector (e.g. after soft-delete). */
  async expectAccountNotInSelector(accountName: string): Promise<void> {
    await this.page.getByTestId('account-selector').click();
    await expect(
      this.page.getByRole('option', { name: accountName })
    ).toHaveCount(0);
    await this.page.keyboard.press('Escape');
  }
}
