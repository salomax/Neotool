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
    await this.page.getByRole('heading', { name: /movimentações financeiras/i }).waitFor({ state: 'visible' });
    await Promise.race([
      this.page.getByTestId('empty-accounts-state').waitFor({ state: 'visible' }),
      this.page.getByTestId('account-selector').waitFor({ state: 'visible' }),
      this.page.getByTestId('account-type-BANK').waitFor({ state: 'visible' }),
      this.page.getByTestId('btn-new-account').waitFor({ state: 'visible' }),
    ]);
  }

  // ── Account selector ────────────────────────────────────────────────────────

  /**
   * Selects an account by its display name in the account selector.
   * Waits for the page to reflect the selection (URL param or data load).
   */
  async selectAccount(accountName: string): Promise<void> {
    await this.page.getByTestId('account-selector').click();
    // FinancialAccountSelector uses ListItemButton in a Popover, not role="option".
    // The account name appears both in the selector trigger (h6) and the popover list (p).
    // .last() targets the popover item — .first() would be the trigger, covered by the backdrop.
    await this.page.getByText(accountName, { exact: true }).last().click();
    await expect(this.page.getByTestId('account-selector')).toContainText(accountName);
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
      initialType != null ? this.page.getByTestId(`account-type-${initialType}`) : null;

    if (emptyStateTypeButton && (await emptyStateTypeButton.count()) > 0 && (await emptyStateTypeButton.first().isVisible().catch(() => false))) {
      await emptyStateTypeButton.first().click();
    } else {
      await this.page.getByTestId('btn-new-account').click();
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
  }

  async goToNextMonth(): Promise<void> {
    await this.page.getByTestId('btn-next-month').click();
    await this.summary.waitForLoad();
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
    // FinancialAccountSelector uses ListItemButton (role="button") in a Popover, not role="option".
    // The name is visible either in the selector trigger (1 account) or in the popover list.
    await expect(this.page.getByText(accountName, { exact: true }).first()).toBeVisible({ timeout: 5000 });
    await this.page.keyboard.press('Escape');
  }

  /** Asserts that the account is NOT present in the selector (e.g. after soft-delete). */
  async expectAccountNotInSelector(accountName: string): Promise<void> {
    await this.page.getByTestId('account-selector').click();
    // Wait for popover to render, then assert name is absent from the list
    await this.page.waitForTimeout(300);
    await expect(this.page.getByText(accountName, { exact: true })).toHaveCount(0);
    await this.page.keyboard.press('Escape');
  }
}
