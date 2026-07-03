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
      // Mobile layout hides AppHeader (and its h1 page title); use functional chrome instead.
      this.page.getByTestId('btn-open-import-drawer').isVisible().catch(() => false),
    ]);
    return checks.some(Boolean);
  }

  private async dismissMobileAppBannerIfVisible(): Promise<void> {
    const dismissBanner = this.page.getByRole('button', {
      name: /fechar banner do aplicativo|dismiss app banner/i,
    });
    if (await dismissBanner.isVisible().catch(() => false)) {
      await dismissBanner.click().catch(() => {});
    }
  }

  // ── Navigation ──────────────────────────────────────────────────────────────

  async goto(): Promise<void> {
    await this.page.goto('/personal-finance/finance-transactions');
    await this.dismissMobileAppBannerIfVisible();
    await expect
      .poll(
        async () => {
          const retryButton = this.page.getByRole('button', { name: /tentar novamente|try again/i }).first();
          if (await retryButton.isVisible().catch(() => false)) {
            await retryButton.click().catch(() => {});
          }
          const desktopHeadingVisible = await this.page
            .getByRole('heading', { name: /movimentações financeiras|financial transactions/i })
            .isVisible()
            .catch(() => false);
          return desktopHeadingVisible || (await this.anyMainStateVisible());
        },
        { timeout: 60000 },
      )
      .toBe(true);
  }

  private mobileSidebarTrigger(): Locator {
    return this.page.getByTestId('sidebarlayout-mobile-trigger');
  }

  private async openMobileAccountSidebarIfNeeded(): Promise<void> {
    const selector = this.page.getByTestId('account-selector');
    if (await selector.isVisible().catch(() => false)) {
      return;
    }

    const mobileTrigger = this.mobileSidebarTrigger();
    if (!(await mobileTrigger.isVisible().catch(() => false))) {
      return;
    }

    await mobileTrigger.click();
    await selector.waitFor({ state: 'visible', timeout: 10_000 });
  }

  private async closeMobileAccountSidebarIfOpen(): Promise<void> {
    const selector = this.page.getByTestId('account-selector');
    if (!(await selector.isVisible().catch(() => false))) {
      return;
    }

    await this.page.keyboard.press('Escape').catch(() => {});
    await selector.waitFor({ state: 'hidden', timeout: 5_000 }).catch(() => {});
  }

  private async accountLabelVisible(accountName: string): Promise<boolean> {
    const surfaces = [
      this.page.getByTestId('account-selector'),
      this.mobileSidebarTrigger(),
    ];

    for (const surface of surfaces) {
      if (await surface.isVisible().catch(() => false)) {
        const text = (await surface.textContent()) ?? '';
        if (text.includes(accountName)) {
          return true;
        }
      }
    }

    return false;
  }

  // ── Account selector ────────────────────────────────────────────────────────

  /**
   * Selects an account in the selector. Prefer passing `accountId` when the user has multiple
   * accounts — label-only clicks can hit the wrong node; `data-account-id` is unambiguous.
   *
   * When the user has a single account, {@link FinancialAccountSelector} auto-selects it and does
   * not open the popover — this method verifies the trigger label instead of waiting on the popover.
   */
  async selectAccount(accountName: string, accountId?: string): Promise<void> {
    if (await this.accountLabelVisible(accountName)) {
      await this.closeMobileAccountSidebarIfOpen();
      await Promise.all([
        this.summary.waitForMetricsReady(),
        this.transactionTable.waitForLoad(),
      ]);
      return;
    }

    await this.openMobileAccountSidebarIfNeeded();

    const selector = this.page.getByTestId('account-selector');
    await selector.waitFor({ state: 'visible', timeout: 10_000 });

    const labelShowsAccount = async (): Promise<boolean> => this.accountLabelVisible(accountName);

    if (await labelShowsAccount()) {
      await this.closeMobileAccountSidebarIfOpen();
      await Promise.all([
        this.summary.waitForMetricsReady(),
        this.transactionTable.waitForLoad(),
      ]);
      return;
    }

    await selector.click();
    const popover = this.page.getByTestId('account-selector-popover');

    await expect
      .poll(
        async () => {
          if (await labelShowsAccount()) return true;
          return (await popover.isVisible().catch(() => false)) ? true : false;
        },
        { timeout: 10_000 },
      )
      .toBe(true);

    if (!(await labelShowsAccount())) {
      const row = accountId
        ? popover.locator(`[data-account-id="${accountId}"]`).filter({ hasText: accountName }).first()
        : popover
            .getByText(accountName, { exact: true })
            .locator('xpath=ancestor::*[@role="button"][1]');
      await row.waitFor({ state: 'visible', timeout: 10_000 });
      await row.scrollIntoViewIfNeeded();
      await row.click({ force: true });
      if (await popover.isVisible().catch(() => false)) {
        await this.page.keyboard.press('Escape').catch(() => {});
      }
    }

    await expect
      .poll(async () => labelShowsAccount(), { timeout: 15_000 })
      .toBe(true);
    await this.closeMobileAccountSidebarIfOpen();
    await Promise.all([
      this.summary.waitForMetricsReady(),
      this.transactionTable.waitForLoad(),
    ]);
  }

  /**
   * Opens the edit drawer for a specific account through the selector popover.
   */
  async openEditAccountDrawer(accountName: string, accountId?: string): Promise<CreateAccountDrawer> {
    for (let attempt = 0; attempt < 3; attempt++) {
      await this.page.getByTestId('account-selector').click();
      const popover = this.page.getByTestId('account-selector-popover');
      const popoverOpened = await popover
        .waitFor({ state: 'visible', timeout: 3_000 })
        .then(() => true)
        .catch(() => false);
      if (!popoverOpened) {
        continue;
      }
      const accountRow = accountId
        ? popover.locator(`[data-account-id="${accountId}"]`).first()
        : popover
            .getByText(accountName, { exact: true })
            .locator('xpath=ancestor::*[@role="button"][1]');
      await accountRow.waitFor({ state: 'visible' });
      await accountRow.getByLabel(/editar|edit/i).click();

      const editReady = await this.createAccountDrawer
        .waitForEditMode()
        .then(() => true)
        .catch(() => false);
      if (editReady) {
        return this.createAccountDrawer;
      }

      await this.page.keyboard.press('Escape').catch(() => {});
      await this.createAccountDrawer.waitForClose().catch(() => {});
    }

    throw new Error(
      `Could not open edit account drawer for "${accountName}". ` +
        'The selector popover might be unavailable when only one account is visible in scope.',
    );
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
    const selector = this.page.getByTestId('account-selector');
    await selector.waitFor({ state: 'visible', timeout: 10_000 });

    if ((await selector.textContent())?.includes(accountName)) {
      return;
    }

    await selector.click();
    const popover = this.page.getByTestId('account-selector-popover');
    const popoverOpened = await popover
      .waitFor({ state: 'visible', timeout: 2_000 })
      .then(() => true)
      .catch(() => false);

    if (popoverOpened) {
      await expect(popover.getByText(accountName, { exact: true })).toBeVisible({ timeout: 5000 });
      await this.page.keyboard.press('Escape');
      return;
    }

    await expect
      .poll(
        async () => ((await selector.textContent()) ?? '').includes(accountName),
        { timeout: 15_000 },
      )
      .toBe(true);
  }

  /** Asserts that the account is NOT present in the selector (e.g. after soft-delete). */
  async expectAccountNotInSelector(accountName: string): Promise<void> {
    const selector = this.page.getByTestId('account-selector');
    const emptyState = this.page.getByTestId('empty-accounts-state');
    await expect
      .poll(
        async () =>
          (await selector.isVisible().catch(() => false)) || (await emptyState.isVisible().catch(() => false)),
        { timeout: 15_000 },
      )
      .toBe(true);

    if (await emptyState.isVisible().catch(() => false)) {
      return;
    }

    await selector.click();
    const popover = this.page.getByTestId('account-selector-popover');
    const popoverOpened = await popover
      .waitFor({ state: 'visible', timeout: 3_000 })
      .then(() => true)
      .catch(() => false);
    if (!popoverOpened) {
      await expect
        .poll(
          async () => !(((await selector.textContent()) ?? '').includes(accountName)),
          { timeout: 15_000 },
        )
        .toBe(true);
      return;
    }
    await expect(popover.getByText(accountName, { exact: true })).toHaveCount(0);
    await this.page.keyboard.press('Escape');
  }
}
