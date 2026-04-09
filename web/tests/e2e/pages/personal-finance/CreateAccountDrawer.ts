/**
 * Page Object for the CreateFinancialAccountDrawer component.
 *
 * Covers both creation and editing flows (same drawer, different mode).
 */

import { Page, Locator, expect } from '@playwright/test';

export class CreateAccountDrawer {
  private readonly drawer: Locator;

  constructor(private readonly page: Page) {
    this.drawer = page.getByTestId('create-account-drawer');
  }

  /**
   * `data-testid` is on the MUI Modal root; type tiles and the form render inside the
   * Drawer Paper (`role="dialog"`). Waiting/querying only the modal root can race the
   * slide-in transition — inner nodes stay non-visible while the root is already visible.
   */
  private drawerPanel(): Locator {
    return this.drawer.getByRole('dialog').first();
  }

  async waitForOpen(): Promise<void> {
    await this.drawer.waitFor({ state: 'visible', timeout: 30000 });
    await this.drawerPanel().waitFor({ state: 'visible', timeout: 30000 });
  }

  async waitForClose(): Promise<void> {
    await this.drawer.waitFor({ state: 'hidden', timeout: 60000 });
  }

  /**
   * Step 1 shows type tiles inside a scrollable drawer body; Step 2 shows subtype select.
   * Waits for either state, clicks the type tile when needed (force + scroll avoids MUI /
   * overflow actionability timeouts), then waits for the subtype field.
   */
  private async ensureOnAccountSubtypeStep(type: 'BANK' | 'CARD'): Promise<void> {
    const surface = this.drawerPanel();
    const subtypeSelect = surface.getByTestId('account-subtype-select');
    const typeButton = surface.getByTestId(`account-type-${type}`);

    await expect
      .poll(
        async () =>
          (await subtypeSelect.first().isVisible().catch(() => false)) ||
          (await typeButton.first().isVisible().catch(() => false)),
        { timeout: 25000 },
      )
      .toBe(true);

    if (await typeButton.first().isVisible().catch(() => false)) {
      const btn = typeButton.first();
      await btn.scrollIntoViewIfNeeded();
      await btn.click({ force: true });
    }

    await subtypeSelect.first().waitFor({ state: 'visible', timeout: 20000 });
  }

  // ── Type selection step ─────────────────────────────────────────────────────

  /**
   * Selects an account type from the type selection step.
   * e.g. 'BANK', 'CREDIT', 'CASH', 'PAYMENT', 'INVESTMENT'
   */
  async selectType(type: string): Promise<void> {
    const normalizedType =
      type === 'BANK'
        ? 'BANK'
        : type === 'CARD' || type === 'CREDIT'
          ? 'CARD'
          : null;
    if (!normalizedType) {
      throw new Error(`Unsupported finance-transactions account type: ${type}`);
    }
    const surface = this.drawerPanel();
    const typeButton = surface.getByTestId(`account-type-${normalizedType}`);
    if (!(await typeButton.first().isVisible().catch(() => false))) {
      const backButton = surface.getByRole('button', { name: /voltar|back/i });
      if (await backButton.first().isVisible().catch(() => false)) {
        await backButton.first().click();
        await typeButton.first().waitFor({ state: 'visible', timeout: 15000 });
      }
    }
    if (await typeButton.first().isVisible().catch(() => false)) {
      const btn = typeButton.first();
      await btn.scrollIntoViewIfNeeded();
      // Force: type tiles are large ButtonBase targets but often sit in overflow:auto;
      // default actionability checks can time out while the drawer/slide settles.
      await btn.click({ force: true });
    }
  }

  /**
   * Selects an account subtype.
   * e.g. 'CONTA_CORRENTE', 'CREDIT_CARD', 'CHARGE_CARD'
   * Opens the subtype SelectField and picks the option by its data-value attribute.
   */
  async selectSubtype(subtype: string): Promise<void> {
    await this.drawerPanel().getByTestId('account-subtype-select').first().click();
    await this.page.locator(`[data-value="${subtype}"]`).first().click();
  }

  // ── Form fields ─────────────────────────────────────────────────────────────

  async fillName(name: string): Promise<void> {
    const input = this.drawerPanel().getByTestId('account-name-input');
    await input.clear();
    await input.fill(name);
  }

  async selectFirstIssuer(): Promise<void> {
    const issuerInput = this.drawerPanel().getByLabel(/instituição|institution/i).first();
    await issuerInput.click();
    const options = this.page.getByRole('option');
    await options.first().waitFor({ state: 'visible', timeout: 15000 });
    for (let attempt = 0; attempt < 10; attempt++) {
      if ((await options.count()) > 1) break;
      await this.page.waitForTimeout(300);
    }

    const preferredIssuer = (await options.count()) > 1 ? options.first() : options.first();
    const optionText = ((await preferredIssuer.textContent()) ?? '').trim();
    await preferredIssuer.click();

    if (/outro|personalizado|custom/i.test(optionText)) {
      const customIssuerInput = this.drawerPanel().getByLabel(/nome da instituição|institution name/i).first();
      await customIssuerInput.fill('E2E Test Issuer');
    }
  }

  async fillInitialBalance(amount: number): Promise<void> {
    const input = this.drawerPanel().getByTestId('initial-balance-input');
    await input.clear();
    await input.fill(String(amount));
  }

  /**
   * Fills the initial balance date field (MUI X DatePicker v7 segmented input).
   * Expects dateStr in pt-BR format: DD/MM/YYYY.
   * Clicks the wrapper to focus the first segment, then types digits to advance through day/month/year.
   */
  async fillInitialBalanceDate(dateStr: string): Promise<void> {
    const [dd, mm, yyyy] = dateStr.split('/');
    // .first() avoids strict-mode violation: data-testid is on both wrapper div and hidden native input
    const container = this.drawerPanel().getByTestId('initial-balance-date-input').first();
    await container.click();
    await this.page.keyboard.type(dd + mm + yyyy);
  }

  static formatDateBr(date: Date): string {
    const d = String(date.getDate()).padStart(2, '0');
    const m = String(date.getMonth() + 1).padStart(2, '0');
    const y = date.getFullYear();
    return `${d}/${m}/${y}`;
  }

  async fillClosingDay(day: number): Promise<void> {
    const input = this.drawerPanel().getByTestId('closing-day-input');
    await input.clear();
    await input.fill(String(day));
  }

  async fillDueDay(day: number): Promise<void> {
    const input = this.drawerPanel().getByTestId('due-day-input');
    await input.clear();
    await input.fill(String(day));
  }

  // ── Actions ─────────────────────────────────────────────────────────────────

  async submit(): Promise<void> {
    const submitButton = this.drawerPanel().getByTestId('account-form-submit');
    await submitButton.waitFor({ state: 'visible' });
    if (await submitButton.isDisabled()) {
      return;
    }
    await submitButton.click();
  }

  async cancel(): Promise<void> {
    await this.drawerPanel().getByTestId('account-form-cancel').click();
  }

  // ── Assertions ──────────────────────────────────────────────────────────────

  /** Asserts that a validation error message is visible for the given field. */
  async expectFieldError(fieldTestId: string, messagePattern?: string | RegExp): Promise<void> {
    const field = this.drawerPanel().getByTestId(fieldTestId);
    // MUI renders helper text adjacent to the field; look for an aria-describedby target.
    const errorEl = this.page.locator(`[id="${await field.getAttribute('aria-describedby')}"]`);
    if (messagePattern) {
      await expect(errorEl).toHaveText(messagePattern);
    } else {
      await expect(errorEl).toBeVisible();
    }
  }

  /** Asserts that the drawer is still open (form was not closed despite submit). */
  async expectStillOpen(): Promise<void> {
    await expect(this.drawer).toBeVisible();
  }

  /**
   * Asserts that the account type is immutable after creation:
   * when editing, the drawer opens directly on step 2 (no type selection buttons visible).
   */
  async expectTypeDisabled(): Promise<void> {
    // When editing, the type selection step (step 1) is skipped — type buttons are not rendered.
    await expect(this.drawerPanel().getByTestId('account-type-BANK')).toBeHidden();
    await expect(this.drawerPanel().getByTestId('account-type-CARD')).toBeHidden();
  }

  /** Fills and submits the full form for a BANK account in one call. */
  async createBankAccount(params: {
    name: string;
    subtype?: string;
    initialBalance?: number;
    initialBalanceDate?: string; // DD/MM/YYYY; defaults to today
  }): Promise<void> {
    await this.ensureOnAccountSubtypeStep('BANK');
    await this.selectSubtype(params.subtype ?? 'CONTA_CORRENTE');
    await this.selectFirstIssuer();
    await this.fillName(params.name);
    if (params.initialBalance !== undefined) {
      await this.fillInitialBalance(params.initialBalance);
      const dateStr = params.initialBalanceDate ?? CreateAccountDrawer.formatDateBr(new Date());
      await this.fillInitialBalanceDate(dateStr);
    }
    await this.submit();
  }

  /** Fills and submits the full form for a CREDIT card account in one call. */
  async createCreditCardAccount(params: {
    name: string;
    subtype?: string;
    closingDay?: number;
    dueDay?: number;
    initialBalance?: number;
  }): Promise<void> {
    await this.ensureOnAccountSubtypeStep('CARD');
    await this.selectSubtype(params.subtype ?? 'CREDIT_CARD');
    await this.selectFirstIssuer();
    await this.fillName(params.name);
    if (params.closingDay !== undefined) await this.fillClosingDay(params.closingDay);
    if (params.dueDay !== undefined) await this.fillDueDay(params.dueDay);
    if (params.initialBalance !== undefined) await this.fillInitialBalance(params.initialBalance);
    await this.submit();
  }
}
