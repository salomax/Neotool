/**
 * Page Object for the InlineTransactionForm component.
 *
 * The form is rendered as a TableRow inline inside the TransactionTable.
 * It is opened by clicking the "Nova transação" button.
 *
 * Key observations from reading the source:
 * - No `status` field exposed → transactions created via UI default to POSTED on the backend.
 * - No `type` field exposed → always defaults to OtherExpense for creation.
 * - `creditDebitType` accepts DEBIT | CREDIT | TRANSFER (TRANSFER is a UI shortcut).
 * - Date window: minDate = today - 10 years. Dates before this show an error message.
 * - Save button is disabled when: amount is empty, description is empty, date is invalid, or
 *   date is outside the allowed window.
 * - The form auto-focuses the description field when opened.
 *
 */

import { Page, Locator, expect } from "@playwright/test";

export type CreditDebitTypeOption = "DEBIT" | "CREDIT" | "TRANSFER";

export class InlineTransactionForm {
  private readonly form: Locator;

  constructor(private readonly page: Page) {
    this.form = page.getByTestId("inline-transaction-form");
  }

  async waitForOpen(): Promise<void> {
    await this.form.waitFor({ state: "visible" });
  }

  async waitForClose(): Promise<void> {
    await this.form.waitFor({ state: "hidden" });
  }

  // ── Field interactions ──────────────────────────────────────────────────────

  /**
   * Selects DEBIT, CREDIT or TRANSFER from the creditDebitType dropdown.
   * Opens the MUI Select and clicks the matching menu item.
   */
  async selectCreditDebitType(type: CreditDebitTypeOption): Promise<void> {
    await this.form.getByTestId("tx-credit-debit-type-select").click();
    const listbox = this.page.getByRole("listbox");
    await listbox.waitFor({ state: "visible" });

    const labelMap: Record<CreditDebitTypeOption, RegExp> = {
      DEBIT: /débit|debit/i,
      CREDIT: /crédit|credit/i,
      TRANSFER: /transf/i,
    };
    await listbox.getByRole("option", { name: labelMap[type] }).click();
    await listbox.waitFor({ state: "hidden" });
  }

  async fillDescription(text: string): Promise<void> {
    const input = this.form.getByTestId("tx-description-input");
    await input.clear();
    await input.fill(text);
  }

  /**
   * Fills the amount field.
   * BaseMoneyInput renders a plain <input type="text"> with currency formatting.
   * Clears the field first to avoid appending to an existing value.
   */
  async fillAmount(amount: number): Promise<void> {
    const input = this.form.getByTestId("tx-amount-input");
    await input.clear();
    await input.fill(String(amount));
    // Trigger change event (some money inputs require a blur or Enter to commit)
    await input.press("Tab");
  }

  /**
   * Sets the date using the MUI DatePicker text input.
   * Expects dateStr in the locale format: DD/MM/YYYY for pt-BR.
   */
  async setDate(dateStr: string): Promise<void> {
    const input = this.form.getByTestId("tx-date-input");
    await input.click();
    await input.selectAll();
    await input.fill(dateStr);
    await input.press("Enter");
  }

  /**
   * Converts a YYYY-MM-DD string to DD/MM/YYYY (pt-BR format for the DatePicker).
   */
  static formatDateBr(isoDate: string): string {
    const [year, month, day] = isoDate.split("-");
    return `${day}/${month}/${year}`;
  }

  // ── Actions ─────────────────────────────────────────────────────────────────

  async save(): Promise<void> {
    await this.form.getByTestId("tx-save-btn").click();
  }

  async cancel(): Promise<void> {
    await this.form.getByTestId("tx-cancel-btn").click();
  }

  async saveWithEnter(): Promise<void> {
    await this.form.getByTestId("tx-amount-input").press("Enter");
  }

  // ── Assertions ──────────────────────────────────────────────────────────────

  async expectSaveDisabled(): Promise<void> {
    await expect(this.form.getByTestId("tx-save-btn")).toBeDisabled();
  }

  async expectSaveEnabled(): Promise<void> {
    await expect(this.form.getByTestId("tx-save-btn")).toBeEnabled();
  }

  async isSaveEnabled(): Promise<boolean> {
    return this.form.getByTestId("tx-save-btn").isEnabled();
  }

  async expectDateError(messagePattern?: string | RegExp): Promise<void> {
    const helperText = this.form.locator(".MuiFormHelperText-root");
    if (messagePattern) {
      await expect(helperText).toHaveText(messagePattern);
    } else {
      await expect(helperText).toBeVisible();
    }
  }

  // ── Shortcut: fill the complete form in one call ─────────────────────────────

  async fillAndSave(params: {
    description: string;
    amount: number;
    creditDebitType?: CreditDebitTypeOption;
    date?: string; // YYYY-MM-DD
  }): Promise<void> {
    await this.waitForOpen();
    if (params.creditDebitType && params.creditDebitType !== "DEBIT") {
      await this.selectCreditDebitType(params.creditDebitType);
    }
    await this.fillDescription(params.description);
    await this.fillAmount(params.amount);
    if (params.date) {
      await this.setDate(InlineTransactionForm.formatDateBr(params.date));
    }
    await this.save();
  }

  async selectAnyCategory(): Promise<void> {
    await this.form.getByText(/categoria|category/i).click();
    await this.page
      .locator(".MuiPopover-root .MuiListItemButton-root")
      .nth(1)
      .click();
  }

  async addTag(tag: string): Promise<void> {
    const input = this.form.getByPlaceholder(/tags/i);
    await input.fill(tag);
    await input.press("Enter");
  }
}
