/**
 * Page Object for the TransactionTable component.
 */

import { Page, Locator, expect } from "@playwright/test";

export class TransactionTablePage {
  private readonly table: Locator;
  private readonly rowActionsPattern = /ações|actions/i;

  constructor(private readonly page: Page) {
    this.table = page.getByTestId("transaction-table");
  }

  async waitForLoad(): Promise<void> {
    await this.table.waitFor({ state: "visible" });
    // Wait for any loading skeleton to disappear.
    await this.page
      .getByTestId("transaction-table-loading")
      .waitFor({ state: "hidden" })
      .catch(() => {
        /* loading indicator may not be present */
      });
  }

  /** Returns the number of visible transaction rows. */
  async getRowCount(): Promise<number> {
    return this.table.getByRole("row").count();
  }

  /** Returns the Locator for a transaction row by its description text. */
  getRowByDescription(description: string): Locator {
    return this.table.getByRole("row").filter({ hasText: description });
  }

  /** Asserts that a transaction with the given description is visible. */
  async expectTransactionVisible(description: string): Promise<void> {
    await expect(this.getRowByDescription(description)).toBeVisible();
  }

  /** Asserts that a transaction with the given description is NOT visible. */
  async expectTransactionHidden(description: string): Promise<void> {
    await expect(this.getRowByDescription(description)).toHaveCount(0);
  }

  /**
   * Clicks the delete button on the row with the given description.
   * Expects a confirmation dialog and confirms it.
   */
  async deleteTransaction(description: string): Promise<void> {
    await this.openRowActions(description);
    await this.page.getByTestId("transaction-action-delete").click();

    // Confirm the deletion in the dialog that appears.
    const dialog = this.page.getByRole("dialog");
    await dialog.waitFor({ state: "visible" });
    await dialog
      .getByRole("button", { name: /confirmar|confirm|excluir|delete/i })
      .click();
    await dialog.waitFor({ state: "hidden" });
  }

  /** Clicks a transaction row to open the edit drawer/dialog. */
  async clickTransaction(description: string): Promise<void> {
    await this.openRowActions(description);
    await this.page.getByTestId("transaction-action-edit").click();
  }

  /** Returns the displayed amount text for a transaction row. */
  async getTransactionAmount(description: string): Promise<string> {
    const row = this.getRowByDescription(description);
    // The amount is typically in a dedicated cell. Adjust selector as needed.
    return (await row.locator('[data-testid="tx-amount"]').textContent()) ?? "";
  }

  async isEmpty(): Promise<boolean> {
    const emptyState = this.page.getByTestId("transaction-table-empty");
    return emptyState.isVisible();
  }

  private async openRowActions(description: string): Promise<void> {
    const row = this.getRowByDescription(description);
    await expect(row).toBeVisible();
    await row.getByRole("button", { name: this.rowActionsPattern }).click();
  }
}
