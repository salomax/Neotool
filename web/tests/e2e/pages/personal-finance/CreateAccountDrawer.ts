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

  async waitForOpen(): Promise<void> {
    await this.drawer.waitFor({ state: 'visible' });
  }

  async waitForClose(): Promise<void> {
    await this.drawer.waitFor({ state: 'hidden' });
  }

  // ── Type selection step ─────────────────────────────────────────────────────

  /**
   * Selects an account type from the type selection step.
   * e.g. 'BANK', 'CREDIT', 'CASH', 'PAYMENT', 'INVESTMENT'
   */
  async selectType(type: string): Promise<void> {
    await this.drawer.getByTestId(`account-type-${type}`).click();
  }

  /**
   * Selects an account subtype.
   * e.g. 'CONTA_CORRENTE', 'CREDIT_CARD', 'CHARGE_CARD'
   * Opens the subtype SelectField and picks the option by its data-value attribute.
   */
  async selectSubtype(subtype: string): Promise<void> {
    await this.drawer.getByTestId('account-subtype-select').click();
    await this.page.locator(`[data-value="${subtype}"]`).first().click();
  }

  // ── Form fields ─────────────────────────────────────────────────────────────

  async fillName(name: string): Promise<void> {
    const input = this.drawer.getByTestId('account-name-input');
    await input.clear();
    await input.fill(name);
  }

  async fillInitialBalance(amount: number): Promise<void> {
    const input = this.drawer.getByTestId('initial-balance-input');
    await input.clear();
    await input.fill(String(amount));
  }

  async fillClosingDay(day: number): Promise<void> {
    const input = this.drawer.getByTestId('closing-day-input');
    await input.clear();
    await input.fill(String(day));
  }

  async fillDueDay(day: number): Promise<void> {
    const input = this.drawer.getByTestId('due-day-input');
    await input.clear();
    await input.fill(String(day));
  }

  // ── Actions ─────────────────────────────────────────────────────────────────

  async submit(): Promise<void> {
    await this.drawer.getByTestId('account-form-submit').click();
  }

  async cancel(): Promise<void> {
    await this.drawer.getByTestId('account-form-cancel').click();
  }

  // ── Assertions ──────────────────────────────────────────────────────────────

  /** Asserts that a validation error message is visible for the given field. */
  async expectFieldError(fieldTestId: string, messagePattern?: string | RegExp): Promise<void> {
    const field = this.drawer.getByTestId(fieldTestId);
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
    await expect(this.drawer.getByTestId('account-type-BANK')).toBeHidden();
    await expect(this.drawer.getByTestId('account-type-CARD')).toBeHidden();
  }

  /** Fills and submits the full form for a BANK account in one call. */
  async createBankAccount(params: {
    name: string;
    subtype?: string;
    initialBalance?: number;
  }): Promise<void> {
    await this.selectType('BANK');
    await this.selectSubtype(params.subtype ?? 'CONTA_CORRENTE');
    await this.fillName(params.name);
    if (params.initialBalance !== undefined) {
      await this.fillInitialBalance(params.initialBalance);
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
    await this.selectType('CREDIT');
    await this.selectSubtype(params.subtype ?? 'CREDIT_CARD');
    await this.fillName(params.name);
    if (params.closingDay !== undefined) await this.fillClosingDay(params.closingDay);
    if (params.dueDay !== undefined) await this.fillDueDay(params.dueDay);
    if (params.initialBalance !== undefined) await this.fillInitialBalance(params.initialBalance);
    await this.submit();
  }
}
