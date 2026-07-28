/**
 * E2E — Transaction table visual glitches (GH-313)
 *
 * Asserts date-group corner radius, post-save highlight framing, and inline form width.
 */

import { test, expect } from "../fixtures/personal-finance";
import { FinanceTransactionsPage } from "../pages/personal-finance/FinanceTransactionsPage";
import { InlineTransactionForm } from "../pages/personal-finance/InlineTransactionForm";
import { CreditDebitType, today } from "../helpers/banking-graphql";
import { signIntoPage } from "../helpers/navigation";
import { TransactionBuilder } from "../builders/TransactionBuilder";

function uniqueDesc(prefix: string): string {
  return `${prefix} ${Date.now()}`;
}

async function cellRadii(cell: import("@playwright/test").Locator) {
  return cell.evaluate((el) => {
    const s = getComputedStyle(el);
    return {
      tl: Number.parseFloat(s.borderTopLeftRadius) || 0,
      tr: Number.parseFloat(s.borderTopRightRadius) || 0,
      bl: Number.parseFloat(s.borderBottomLeftRadius) || 0,
      br: Number.parseFloat(s.borderBottomRightRadius) || 0,
      tag: el.tagName,
    };
  });
}

test.describe("Transactions — table styles (GH-313)", () => {
  test("date-group corners, highlight frame, and inline form full width", async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    const date = today();
    const prefix = uniqueDesc("GH313");
    const descs = [`${prefix} A`, `${prefix} B`, `${prefix} C`];

    for (const description of descs) {
      await new TransactionBuilder()
        .forAccount(bankAccount.id)
        .described(description)
        .amount(10)
        .on(date)
        .create(pfUser.token);
    }

    await signIntoPage(page);
    const pfPage = new FinanceTransactionsPage(page);
    await pfPage.goto();
    await pfPage.selectAccount(bankAccount.name, bankAccount.id);

    for (const description of descs) {
      await pfPage.transactionTable.expectTransactionVisible(description);
    }

    const rows = [
      page.locator('[data-testid^="transaction-row-"]').filter({ hasText: descs[0]! }).first(),
      page.locator('[data-testid^="transaction-row-"]').filter({ hasText: descs[1]! }).first(),
      page.locator('[data-testid^="transaction-row-"]').filter({ hasText: descs[2]! }).first(),
    ];

    // Description cell must be a td with zero corner radius (old <th> bug).
    for (let i = 0; i < rows.length; i++) {
      const row = rows[i]!;
      const desc = descs[i]!;
      const descCell = row.locator(".MuiTableCell-root").filter({ hasText: desc }).first();
      const radii = await cellRadii(descCell);
      expect(radii.tag).toBe("TD");
      expect(radii.tl + radii.tr + radii.bl + radii.br).toBe(0);
    }

    // Exactly one row should own the top corners and one the bottom corners of the group.
    const edgeStats = [];
    for (const row of rows) {
      const cells = row.locator(":scope > .MuiTableCell-root");
      const count = await cells.count();
      const first = await cellRadii(cells.nth(0));
      const last = await cellRadii(cells.nth(count - 1));
      edgeStats.push({
        top: first.tl > 0 && last.tr > 0,
        bottom: first.bl > 0 && last.br > 0,
      });
    }
    expect(edgeStats.filter((s) => s.top).length).toBe(1);
    expect(edgeStats.filter((s) => s.bottom).length).toBe(1);

    // ── Inline form spans full row width (matches a data row)
    const dataRowBox = await rows[0]!.boundingBox();
    expect(dataRowBox).toBeTruthy();

    await pfPage.openCreateTransactionForm();
    const form = new InlineTransactionForm(page);
    await form.waitForOpen();

    const formCell = page.getByTestId("inline-transaction-form-cell");
    await expect(formCell).toBeVisible();
    expect(await formCell.getAttribute("colspan")).toBe("7");

    const formBounds = await page.getByTestId("inline-transaction-form").boundingBox();
    expect(formBounds).toBeTruthy();
    expect(Math.abs((formBounds?.width ?? 0) - (dataRowBox?.width ?? 0))).toBeLessThanOrEqual(4);

    const highlightDesc = uniqueDesc("GH313 highlight");
    await form.fillAndSave({
      description: highlightDesc,
      amount: 15,
      creditDebitType: CreditDebitType.Debit,
    });

    const highlightedRow = page
      .locator('[data-testid^="transaction-row-"]')
      .filter({ hasText: highlightDesc })
      .first();
    await expect(highlightedRow).toBeVisible();

    const highlightAudit = await highlightedRow.evaluate((row) => {
      const rowStyle = getComputedStyle(row);
      const cells = Array.from(row.querySelectorAll(":scope > .MuiTableCell-root"));
      const cellsWithInsetBorder = cells.filter((c) => {
        const shadow = getComputedStyle(c).boxShadow;
        return /inset/i.test(shadow) && !/^none$/i.test(shadow);
      }).length;
      const outlineColor = rowStyle.outlineColor;
      const outlineStyle = rowStyle.outlineStyle;
      const outlineWidth = Number.parseFloat(rowStyle.outlineWidth) || 0;
      const hasVisibleOutline =
        outlineStyle !== "none" &&
        outlineWidth > 0 &&
        outlineColor !== "rgba(0, 0, 0, 0)" &&
        outlineColor !== "transparent";
      return {
        hasVisibleOutline,
        outlineColor,
        outlineStyle,
        outlineWidth,
        cellsWithInsetBorder,
        cellCount: cells.length,
      };
    });

    expect(highlightAudit.cellCount).toBeGreaterThan(2);
    expect(highlightAudit.cellsWithInsetBorder).toBe(0);
    expect(highlightAudit.hasVisibleOutline).toBe(true);
  });
});
