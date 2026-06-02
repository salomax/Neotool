/**
 * Page Object for the CreateFinancialAccountDrawer component.
 *
 * Covers both creation and editing flows (same drawer, different mode).
 */

import { Page, Locator, expect } from '@playwright/test';

/** Subtypes rendered in the card branch of CreateFinancialAccountDrawer step 2. */
const CARD_FORM_SUBTYPES = new Set([
  'CREDIT_CARD',
  'CHARGE_CARD',
  'PREPAID_CARD',
  'VOUCHER_CARD',
]);

export class CreateAccountDrawer {
  private readonly drawer: Locator;

  constructor(private readonly page: Page) {
    this.drawer = page.getByTestId('create-account-drawer');
  }

  /**
   * Scope to the drawer paper only. Using a generic `[role="dialog"]:visible` chain can
   * match the wrong surface when a nested/portaled confirm dialog is also a dialog, or
   * when `.last()` flips between transitions — the remove button then "disappears" for the locator.
   */
  private drawerPanel(): Locator {
    return this.drawer.locator('.MuiDrawer-paper').first();
  }

  /** Visible drawer paper — avoids matching a closed nested CreateFinancialAccountDrawer. */
  private visibleDrawerPanel(): Locator {
    return this.page
      .locator('[data-testid="create-account-drawer"]:visible')
      .locator('.MuiDrawer-paper')
      .first();
  }

  private submitButton(): Locator {
    return this.drawerPanel().getByTestId('account-form-submit');
  }

  private visibleDeleteConfirmButton(): Locator {
    return this.page
      .locator('[data-testid="account-delete-dialog"]:visible')
      .last()
      .getByRole('button', {
        name: /^remover$|^remove account$|remover conta|excluir conta|delete account|excluir|delete/i,
      })
      .first();
  }

  private visibleDeleteCancelButton(): Locator {
    return this.page
      .locator('[data-testid="account-delete-dialog"]:visible')
      .last()
      .getByRole('button', { name: /cancelar|cancel/i })
      .first();
  }

  async waitForOpen(): Promise<void> {
    await this.drawer.waitFor({ state: 'visible', timeout: 30000 });
    await expect
      .poll(
        async () =>
          (await this.drawerPanel().getByTestId('account-form-submit').first().isVisible().catch(() => false)) ||
          (await this.drawerPanel().getByTestId('account-subtype-select').first().isVisible().catch(() => false)) ||
          (await this.drawerPanel().getByTestId('account-type-BANK').first().isVisible().catch(() => false)) ||
          (await this.drawerPanel().getByTestId('account-type-CARD').first().isVisible().catch(() => false)),
        { timeout: 30000 },
      )
      .toBe(true);
  }

  /** Waits until the drawer is open in edit mode (remove button is edit-only). */
  async waitForEditMode(): Promise<void> {
    await this.visibleDrawerPanel().getByTestId('account-form-remove').waitFor({
      state: 'visible',
      timeout: 15_000,
    });
  }

  async waitForClose(): Promise<void> {
    await expect
      .poll(
        async () =>
          (await this.drawerPanel().getByTestId('account-form-submit').first().isVisible().catch(() => false)) ||
          (await this.drawerPanel().getByTestId('account-subtype-select').first().isVisible().catch(() => false)) ||
          (await this.drawerPanel().getByTestId('account-type-BANK').first().isVisible().catch(() => false)) ||
          (await this.drawerPanel().getByTestId('account-type-CARD').first().isVisible().catch(() => false)),
        { timeout: 60000 },
      )
      .toBe(false);
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
      await btn.click({ force: true }).catch(async () => {
        await btn.dispatchEvent('click');
      });
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
    const subtypeSelect = surface.getByTestId('account-subtype-select').first();
    if (!(await typeButton.first().isVisible().catch(() => false))) {
      const backButton = surface.getByRole('button', { name: /voltar|back/i });
      if (await backButton.first().isVisible().catch(() => false)) {
        await backButton.first().click();
        // Some flows already land directly on step 2 for the chosen type.
        // If we cannot navigate back to type tiles, treat it as a no-op.
        const becameVisible = await typeButton.first().waitFor({ state: 'visible', timeout: 2000 }).then(() => true).catch(() => false);
        if (!becameVisible && (await subtypeSelect.isVisible().catch(() => false))) {
          return;
        }
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
   *
   * Ensures the drawer is on step 2 for the right product (bank vs card): `openCreateAccountDrawer('CARD')`
   * may land directly on the form, while "Nova conta" opens on step 1 — without this, `selectSubtype` races.
   */
  async selectSubtype(subtype: string): Promise<void> {
    const kind: 'BANK' | 'CARD' = CARD_FORM_SUBTYPES.has(subtype) ? 'CARD' : 'BANK';
    await this.ensureOnAccountSubtypeStep(kind);
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
    await expect.poll(async () => (await options.count()) > 0, { timeout: 15000 }).toBe(true);

    /** Matches only the synthetic "custom issuer" row, not bank names that contain "outro" as a substring. */
    const isCustomIssuerOption = (text: string) =>
      /^(outro\s*\/\s*personalizado|other\s*\/\s*custom)$/i.test(text.trim());

    const optionCount = await options.count();
    let preferredIssuer = options.first();
    let optionText = '';

    for (let index = 0; index < optionCount; index++) {
      const candidate = options.nth(index);
      const candidateText = ((await candidate.textContent()) ?? '').trim();
      if (!candidateText) continue;

      if (!optionText) {
        optionText = candidateText;
        preferredIssuer = candidate;
      }

      if (!isCustomIssuerOption(candidateText)) {
        preferredIssuer = candidate;
        optionText = candidateText;
        break;
      }
    }

    await preferredIssuer.click();

    if (isCustomIssuerOption(optionText)) {
      const customIssuerInput = this.drawerPanel().locator('input[name="issuerName"]').first();
      await expect(customIssuerInput).toBeVisible({ timeout: 10_000 });
      await customIssuerInput.fill('E2E Test Issuer');
    }
  }

  async fillInitialBalance(amount: number): Promise<void> {
    const input = this.drawerPanel().getByTestId('initial-balance-input');
    await input.clear();
    await input.fill(String(amount));
  }

  async fillBankData(params: { branchCode?: string; accountNumber?: string }): Promise<void> {
    if (params.branchCode !== undefined) {
      const branchInput = this.drawerPanel().getByTestId('bank-branch-input');
      await expect(branchInput).toBeVisible();
      await branchInput.clear();
      await branchInput.fill(params.branchCode);
    }

    if (params.accountNumber !== undefined) {
      const accountNumberInput = this.drawerPanel().getByTestId('bank-number-input');
      await expect(accountNumberInput).toBeVisible();
      await accountNumberInput.clear();
      await accountNumberInput.fill(params.accountNumber);
    }
  }

  /**
   * Fills the initial balance date field (MUI X DatePicker v7 segmented input).
   * Expects dateStr in pt-BR format: DD/MM/YYYY.
   * Clicks the wrapper to focus the first segment, then types digits to advance through day/month/year.
   */
  async fillInitialBalanceDate(dateStr: string): Promise<void> {
    const [dd, mm, yyyy] = dateStr.split('/');
    if (!dd || !mm || !yyyy) {
      throw new Error(`Invalid date format for fillInitialBalanceDate: ${dateStr}`);
    }
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

  async fillLastFourDigits(lastFourDigits: string): Promise<void> {
    const input = this.drawerPanel().getByLabel(/últimos 4 dígitos|last 4 digits/i).first();
    await input.clear();
    await input.fill(lastFourDigits);
  }

  async setMultiCard(enabled: boolean): Promise<void> {
    const toggle = this.drawerPanel().getByTestId('multi-card-toggle').locator('input[type="checkbox"]').first();
    await expect(toggle).toBeVisible();
    const checked = await toggle.isChecked();
    if (checked !== enabled) {
      await toggle.click();
    }
  }

  // ── Actions ─────────────────────────────────────────────────────────────────

  async submit(): Promise<void> {
    const submitButton = this.submitButton();
    await expect(submitButton).toBeVisible();
    await expect(submitButton).toBeEnabled();
    await submitButton.click();
  }

  async attemptSubmit(): Promise<void> {
    const submitButton = this.submitButton();
    await submitButton.waitFor({ state: 'visible' });
    if (await submitButton.isDisabled()) {
      return;
    }
    await submitButton.click();
  }

  async cancel(): Promise<void> {
    await this.drawerPanel().getByTestId('account-form-cancel').click();
  }

  async clickRemoveAccount(): Promise<void> {
    await this.waitForEditMode();
    const panel = this.visibleDrawerPanel();
    const confirmButton = this.visibleDeleteConfirmButton();

    for (let attempt = 0; attempt < 4; attempt++) {
      if (await confirmButton.isVisible().catch(() => false)) return;

      const removeButton = panel.getByTestId('account-form-remove');
      await expect(removeButton).toBeVisible();
      await expect(removeButton).toBeEnabled();
      await removeButton.scrollIntoViewIfNeeded();

      if (attempt === 0) {
        await removeButton.click({ timeout: 5000 }).catch(() => {});
      } else if (attempt === 1) {
        await removeButton.click({ force: true, timeout: 5000 }).catch(() => {});
      } else if (attempt === 2) {
        await removeButton.dispatchEvent('click');
      } else {
        await removeButton.evaluate((el: HTMLElement) => {
          el.click();
        });
      }

      const opened = await confirmButton.waitFor({ state: 'visible', timeout: 4000 }).then(() => true).catch(() => false);
      if (opened) return;
    }

    throw new Error('Delete confirmation dialog did not open after clicking remove account');
  }

  async confirmRemoveAccount(): Promise<void> {
    await this.visibleDeleteConfirmButton().click();
  }

  async cancelRemoveAccount(): Promise<void> {
    await this.visibleDeleteCancelButton().click();
  }

  async removeAccount(): Promise<void> {
    await this.clickRemoveAccount();
    if (await this.visibleDeleteConfirmButton().isVisible().catch(() => false)) {
      await this.confirmRemoveAccount();
    }
  }

  // ── Assertions ──────────────────────────────────────────────────────────────

  /** Asserts that a validation error message is visible for the given field. */
  async expectFieldError(fieldTestId: string, messagePattern?: string | RegExp): Promise<void> {
    const fieldCandidates = this.drawerPanel().getByTestId(fieldTestId);
    const inputLikeField = fieldCandidates.locator('input, textarea, select').first();
    const field =
      (await inputLikeField.count()) > 0
        ? inputLikeField
        : fieldCandidates.first();
    const describedBy = await field.getAttribute('aria-describedby');
    const describedByIds = (describedBy ?? '')
      .split(' ')
      .map((s) => s.trim())
      .filter(Boolean);

    const errorByDescribedBy = describedByIds.length
      ? this.page.locator(describedByIds.map((id) => `[id="${id}"]`).join(', '))
      : this.page.locator('never-match-selector');

    const errorNearby = field
      .locator('xpath=ancestor::*[contains(@class,"MuiFormControl-root")][1]')
      .locator('.MuiFormHelperText-root.Mui-error');

    const errorEl =
      (await errorByDescribedBy.count()) > 0
        ? errorByDescribedBy.first()
        : errorNearby.first();

    if (messagePattern) {
      await expect(errorEl).toHaveText(messagePattern);
      return;
    }

    // No specific copy asserted: accept any visible field-level error signal.
    if ((await errorByDescribedBy.count()) > 0 || (await errorNearby.count()) > 0) {
      await expect(errorEl).toBeVisible();
      return;
    }
    await expect(field).toHaveAttribute('aria-invalid', 'true');
  }

  /** Asserts that the drawer is still open (form was not closed despite submit). */
  async expectStillOpen(): Promise<void> {
    await expect(this.drawer).toBeVisible();
  }

  async expectDeleteError(messagePattern?: string | RegExp): Promise<void> {
    const alert = this.drawerPanel().locator('.MuiAlert-root').first();
    await expect(alert).toBeVisible();
    if (messagePattern) {
      await expect(alert).toHaveText(messagePattern);
    }
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
    branchCode?: string;
    accountNumber?: string;
  }): Promise<void> {
    await this.selectSubtype(params.subtype ?? 'CONTA_CORRENTE');
    await this.selectFirstIssuer();
    await this.fillName(params.name);
    if (params.branchCode !== undefined || params.accountNumber !== undefined) {
      await this.fillBankData({
        branchCode: params.branchCode,
        accountNumber: params.accountNumber,
      });
    }
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
    lastFourDigits?: string;
    multiCard?: boolean;
    initialBalance?: number;
  }): Promise<void> {
    await this.selectSubtype(params.subtype ?? 'CREDIT_CARD');
    await this.selectFirstIssuer();
    await this.fillName(params.name);
    if (params.multiCard !== undefined) await this.setMultiCard(params.multiCard);
    if (params.lastFourDigits !== undefined) await this.fillLastFourDigits(params.lastFourDigits);
    if (params.closingDay !== undefined) await this.fillClosingDay(params.closingDay);
    if (params.dueDay !== undefined) await this.fillDueDay(params.dueDay);
    if (params.initialBalance !== undefined) await this.fillInitialBalance(params.initialBalance);
    await this.submit();
  }
}
