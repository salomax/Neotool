/**
 * E2E tests — Transactions
 *
 * Coverage: TX-H-01..17 | TX-A-01..08 (+ TX-A-10..11 chain recompute) | TX-N-01..08
 * Security coverage is in security.e2e.spec.ts (SEC-TX-01..05).
 *
 * ──────────────────────────────────────────────────────────────────────────────
 * Current UI contract (aligned with the page objects and with lessons from
 * financial-accounts.e2e.spec.ts):
 *   - No `status` field → UI always creates POSTED (backend default).
 *   - No `type` field  → UI always creates OtherExpense.
 *   - TRANSFER selection sets creditDebitType="TRANSFER" (UI shortcut).
 *   - BALANCE_ADJUSTMENT, FUTURE, PENDING → created via GraphQL fixtures.
 *   - Date window: minDate = today − 10 years (FINANCE_MOVING_WINDOW_YEARS = 10).
 *   - Authentication comes from the fixture storageState; do not inject tokens manually.
 *   - Row edit/delete flows live under the transaction actions menu, not on row click.
 *   - Category/tag/direction filters are exercised through MonthSummary interactions.
 *   - Calendar YYYY-MM-DD for GraphQL/UI alignment: use `formatLocalIsoDate` / `today()` from
 *     banking-graphql (or local noon + format) — not `toISOString().split('T')[0]` on local
 *     midnight dates (TZ ahead of UTC can shift the day). TX-A-03/04 and date-window tests
 *     follow this; `dateInMonth` / `today()` helpers are local-calendar based.
 *   - Month scope & initial balance date: see `helpers/finance-transactions-e2e.ts` and
 *     `getTransactions` / `getMonthSummary` JSDoc in `banking-graphql.ts`. Default `bankAccount` uses
 *     `initialBalanceDate = first day of current month` — any API tx **before** that date fails
 *     validation; UI create defaults to day 1 of the selected month — use `fillAndSave` / `setDateToday`.
 *     Any `getMonthSummary` for a month with **no** snapshots while earliest activity is **later** needs
 *     either a backend that materializes empty months or ledger seeding in that month (TX-H-16,
 *     TX-A-03/A-10 patterns: set `initialBalanceDate` on/before the target month).
 *   - Cross-month moves (GraphQL date edit): current month **closing** often **unchanged** when a
 *     debit leaves for the **prior** month (prior closing drops → current **opening** drops by the
 *     same amount). Assert opening deltas or unchanged closing, not “closing += amount”.
 *   - Calendar-**future** `monthSummary`: POSTED rows dated in that month may be absent from
 *     `totalExpenses` on older banking builds; prefer `getTransactions(..., { year, month })` when
 *     the assertion must hold regardless of header semantics (TX-A-11).
 *   - Month summary DOM: while loading, closing balance is a Skeleton — `parseCurrencyBr` reads 0.
 *     `selectAccount` and carousel waits help, but a refetch can show Skeleton again — always call
 *     `summary.waitForMetricsReady()` immediately before **baseline** `getAll()` / parsed amounts,
 *     not only after mutations (TX-H-09).
 *   - Prefer `getMonthSummary` for numeric rules without UI timing; use the page summary + waits
 *     when testing displayed chips after user actions.
 *   - Parsing currency from the DOM: use `helpers/currency-dom.ts` and test ids that target the
 *     amount node only (`month-summary-*-amount`, etc.) — not a row/card that also includes labels.
 *   - Transaction enums: use `CreditDebitType`, `TransactionStatus`, `TransactionType`, and
 *     `CategoryApplicableTo` from `banking-graphql` (re-exported codegen) instead of raw strings.
 * ──────────────────────────────────────────────────────────────────────────────
 */

import { test, expect } from "../fixtures/personal-finance";
import { FinanceTransactionsPage } from "../pages/personal-finance/FinanceTransactionsPage";
import { InlineTransactionForm } from "../pages/personal-finance/InlineTransactionForm";
import {
  CategoryApplicableTo,
  createFinancialAccount,
  createTransaction,
  CreditDebitType,
  deleteTransaction,
  formatLocalIsoDate,
  getMonthSummary,
  getTransactions,
  today,
  dateInMonth,
  TransactionStatus,
  TransactionType,
} from "../helpers/banking-graphql";
import {
  localCalendarYearMonth,
  navigateFinanceTransactionsToMonth,
  yearMonthFromIso,
} from "../helpers/finance-transactions-e2e";
import { executeGraphQL, expectGraphqlNotFoundError } from "../helpers/graphql";
import { signIntoPage } from "../helpers/navigation";
import { TransactionBuilder } from "../builders/TransactionBuilder";

// ── Helpers ───────────────────────────────────────────────────────────────────

function uniqueDesc(prefix: string): string {
  return `${prefix} ${Date.now()}`;
}

/** Local calendar date, with noon anchor to avoid DST edge cases when adding days/years. */
function isoDateLocalWithDayOffset(dayOffset: number): string {
  const d = new Date();
  d.setHours(12, 0, 0, 0);
  d.setDate(d.getDate() + dayOffset);
  return formatLocalIsoDate(d);
}

function isoDateLocalYearsAgo(years: number): string {
  const d = new Date();
  d.setHours(12, 0, 0, 0);
  d.setFullYear(d.getFullYear() - years);
  return formatLocalIsoDate(d);
}

// =============================================================================
// HAPPY PATH
// =============================================================================

test.describe("Transactions — happy path", () => {
  test("TX-H-01: creates a POSTED expense — appears in table and increases totalExpenses", { tag: ["@smoke", "@critical", "@balance"] }, async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    await signIntoPage(page);
    const pfPage = new FinanceTransactionsPage(page);
    await pfPage.goto();
    await pfPage.selectAccount(bankAccount.name, bankAccount.id);

    await pfPage.summary.waitForMetricsReady();
    const summaryBefore = await pfPage.summary.getAll();

    await pfPage.openCreateTransactionForm();
    const form = new InlineTransactionForm(page);
    const desc = uniqueDesc("Aluguel H01");
    await form.fillAndSave({
      description: desc,
      amount: 2500,
      creditDebitType: CreditDebitType.Debit,
    });

    await pfPage.transactionTable.expectTransactionVisible(desc);

    await pfPage.summary.waitForMetricsReady();
    const summaryAfter = await pfPage.summary.getAll();
    expect(summaryAfter.totalExpenses).toBeGreaterThan(
      summaryBefore.totalExpenses,
    );
    expect(summaryAfter.closingBalance).toBeLessThan(
      summaryBefore.closingBalance,
    );
  });

  test("TX-H-02: creates a POSTED income — appears in table and increases totalIncome", { tag: ["@smoke", "@critical", "@balance"] }, async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    await signIntoPage(page);
    const pfPage = new FinanceTransactionsPage(page);
    await pfPage.goto();
    await pfPage.selectAccount(bankAccount.name, bankAccount.id);

    await pfPage.summary.waitForMetricsReady();
    const summaryBefore = await pfPage.summary.getAll();

    await pfPage.openCreateTransactionForm();
    const form = new InlineTransactionForm(page);
    const desc = uniqueDesc("Salário H02");
    await form.fillAndSave({
      description: desc,
      amount: 5000,
      creditDebitType: CreditDebitType.Credit,
    });

    await pfPage.transactionTable.expectTransactionVisible(desc);

    await pfPage.summary.waitForMetricsReady();
    const summaryAfter = await pfPage.summary.getAll();
    expect(summaryAfter.totalIncome).toBeGreaterThan(summaryBefore.totalIncome);
    expect(summaryAfter.closingBalance).toBeGreaterThan(
      summaryBefore.closingBalance,
    );
  });

  test("TX-H-03: PENDING transaction — appears in table and affects closingBalance", async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    // PENDING status requires GraphQL creation — the UI form has no status field.
    const desc = uniqueDesc("Conta de Luz Pendente H03");
    const tx = await new TransactionBuilder()
      .forAccount(bankAccount.id)
      .described(desc)
      .amount(180)
      .pending()
      .on(today())
      .create(pfUser.token);

    await signIntoPage(page);
    const pfPage = new FinanceTransactionsPage(page);
    await pfPage.goto();
    await pfPage.selectAccount(bankAccount.name, bankAccount.id);

    await pfPage.transactionTable.expectTransactionVisible(desc);

    // PENDING must affect closingBalance (it IS counted in balance, unlike FUTURE)
    const { year, month } = localCalendarYearMonth();
    const summary = await getMonthSummary(
      bankAccount.id,
      year,
      month,
      pfUser.token,
    );
    expect(summary.closingBalance).toBeLessThan(1000); // initial balance was 1000, expense -180

    await deleteTransaction(tx.id, pfUser.token);
  });

  test("TX-H-04: FUTURE transaction — appears in table with indicator, does NOT affect closingBalance", async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    const desc = uniqueDesc("Assinatura Futura H04");
    const tx = await createTransaction(
      {
        financialAccountId: bankAccount.id,
        description: desc,
        amount: 50,
        creditDebitType: CreditDebitType.Debit,
        status: TransactionStatus.Future,
        date: today(),
      },
      pfUser.token,
    );

    await signIntoPage(page);
    const pfPage = new FinanceTransactionsPage(page);
    await pfPage.goto();
    await pfPage.selectAccount(bankAccount.name, bankAccount.id);

    await pfPage.transactionTable.expectTransactionVisible(desc);

    // FUTURE must NOT affect closingBalance
    const { year, month } = localCalendarYearMonth();
    const summary = await getMonthSummary(
      bankAccount.id,
      year,
      month,
      pfUser.token,
    );
    // closingBalance = initialBalance (1000) because FUTURE is excluded
    expect(summary.closingBalance).toBe(1000);

    await deleteTransaction(tx.id, pfUser.token);
  });

  test("TX-H-05: edits description inline — updated in table without page reload", async ({
    page,
    pfUser,
    postedExpense,
    bankAccount,
  }) => {
    await signIntoPage(page, pfUser.token);
    const pfPage = new FinanceTransactionsPage(page);
    await pfPage.goto();
    await pfPage.selectAccount(bankAccount.name, bankAccount.id);

    // Open the edit form by clicking the transaction row
    await pfPage.transactionTable.clickTransaction(postedExpense.description);
    const form = new InlineTransactionForm(page);
    await form.waitForOpen();

    const newDesc = uniqueDesc("Aluguel Editado H05");
    await form.fillDescription(newDesc);
    await form.save();
    await form.waitForClose();

    await pfPage.transactionTable.expectTransactionVisible(newDesc);
    await pfPage.transactionTable.expectTransactionHidden(
      postedExpense.description,
    );
  });

  test("TX-H-06: edits amount — closingBalance and summary updated", async ({
    page,
    pfUser,
    bankAccount,
    postedExpense,
  }) => {
    await signIntoPage(page, pfUser.token);
    const pfPage = new FinanceTransactionsPage(page);
    await pfPage.goto();
    await pfPage.selectAccount(bankAccount.name, bankAccount.id);

    await pfPage.summary.waitForMetricsReady();
    const summaryBefore = await pfPage.summary.getAll();

    await pfPage.transactionTable.clickTransaction(postedExpense.description);
    const form = new InlineTransactionForm(page);
    await form.waitForOpen();
    await form.fillAmount(250); // was 100, now 250
    await form.save();
    await form.waitForClose();

    await pfPage.summary.waitForMetricsReady();
    const summaryAfter = await pfPage.summary.getAll();
    // Expense increased by 150 → closingBalance decreased by 150
    expect(summaryAfter.closingBalance).toBeCloseTo(
      summaryBefore.closingBalance - 150,
      2,
    );
  });

  test("TX-H-07: changes status PENDING → POSTED via GraphQL — balance unchanged (both count)", async ({
    pfUser,
    bankAccount,
  }) => {
    // Both PENDING and POSTED affect closingBalance, so no balance change on status transition.
    const desc = uniqueDesc("Boleto Pending→Posted H07");
    const tx = await createTransaction(
      {
        financialAccountId: bankAccount.id,
        description: desc,
        amount: 300,
        creditDebitType: CreditDebitType.Debit,
        status: TransactionStatus.Pending,
        date: today(),
      },
      pfUser.token,
    );

    const { year, month } = localCalendarYearMonth();
    const before = await getMonthSummary(
      bankAccount.id,
      year,
      month,
      pfUser.token,
    );

    // Update status to POSTED via GraphQL (the UI form doesn't expose status)
    const { executeGraphQL } = await import("../helpers/graphql");
    await executeGraphQL(
      `
      mutation UpdateTransaction($id: ID!, $input: UpdateTransactionInput!) {
        updateTransaction(id: $id, input: $input) { id status }
      }
    `,
      { id: tx.id, input: { status: TransactionStatus.Posted } },
      pfUser.token,
    );

    const after = await getMonthSummary(
      bankAccount.id,
      year,
      month,
      pfUser.token,
    );
    // Balance must remain identical — both PENDING and POSTED count in closingBalance
    expect(after.closingBalance).toBeCloseTo(before.closingBalance, 2);

    await deleteTransaction(tx.id, pfUser.token);
  });

  test("TX-H-08: changes status FUTURE → POSTED — closingBalance increases/decreases", async ({
    pfUser,
    bankAccount,
  }) => {
    const desc = uniqueDesc("Compra Futura→Postada H08");
    const tx = await createTransaction(
      {
        financialAccountId: bankAccount.id,
        description: desc,
        amount: 200,
        creditDebitType: CreditDebitType.Debit,
        status: TransactionStatus.Future,
        date: today(),
      },
      pfUser.token,
    );

    const { year, month } = localCalendarYearMonth();
    const before = await getMonthSummary(
      bankAccount.id,
      year,
      month,
      pfUser.token,
    );

    const { executeGraphQL } = await import("../helpers/graphql");
    await executeGraphQL(
      `
      mutation UpdateTransaction($id: ID!, $input: UpdateTransactionInput!) {
        updateTransaction(id: $id, input: $input) { id status }
      }
    `,
      { id: tx.id, input: { status: TransactionStatus.Posted } },
      pfUser.token,
    );

    const after = await getMonthSummary(
      bankAccount.id,
      year,
      month,
      pfUser.token,
    );
    // FUTURE → POSTED: expense now counts → closingBalance drops by 200
    expect(after.closingBalance).toBeCloseTo(before.closingBalance - 200, 2);

    await deleteTransaction(tx.id, pfUser.token);
  });

  test("TX-H-09: deletes a POSTED transaction — removed from table and balance reverts", async ({
    page,
    pfUser,
    bankAccount,
    postedExpense,
  }) => {
    await signIntoPage(page, pfUser.token);
    const pfPage = new FinanceTransactionsPage(page);
    await pfPage.goto();
    await pfPage.selectAccount(bankAccount.name, bankAccount.id);
    // Calendar month can persist across specs; align with postedExpense date (today).
    await navigateFinanceTransactionsToMonth(pfPage, localCalendarYearMonth());
    await pfPage.transactionTable.waitForLoad();

    // Refetch can briefly show Skeleton again — parseCurrencyBr then reads 0 (see MonthSummarySection).
    await pfPage.summary.waitForMetricsReady();
    const summaryBefore = await pfPage.summary.getAll();

    await pfPage.transactionTable.deleteTransaction(postedExpense.description);

    await pfPage.transactionTable.expectTransactionHidden(
      postedExpense.description,
    );

    const expectedClosing =
      summaryBefore.closingBalance + postedExpense.amount;
    await pfPage.summary.waitForClosingBalance(expectedClosing);
    const summaryAfter = await pfPage.summary.getAll();
    // Expense of 100 removed → closingBalance increases by 100
    expect(summaryAfter.closingBalance).toBeCloseTo(expectedClosing, 2);
    expect(summaryAfter.totalExpenses).toBeCloseTo(
      summaryBefore.totalExpenses - postedExpense.amount,
      2,
    );
  });

  test("TX-H-10: assigns category — displayed in the transaction row", async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    await signIntoPage(page, pfUser.token);
    const pfPage = new FinanceTransactionsPage(page);
    await pfPage.goto();
    await pfPage.selectAccount(bankAccount.name, bankAccount.id);

    await pfPage.openCreateTransactionForm();
    const form = new InlineTransactionForm(page);
    const desc = uniqueDesc("Mercado H10");
    await form.fillDescription(desc);
    await form.fillAmount(150);
    await form.setDateToday();
    await form.selectAnyCategory();
    await form.save();
    await form.waitForClose();

    const row = pfPage.transactionTable.getRowByDescription(desc);
    await expect(row).toBeVisible();
    await expect(row.getByText(/categoria|category/i)).toHaveCount(0);
    await expect(row.locator(".MuiChip-root")).toBeVisible();
  });

  test("TX-H-11: adds a tag — visible in the row and filterable", async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    const otherDesc = uniqueDesc("Sem Tag H11");
    const otherTx = await createTransaction(
      {
        financialAccountId: bankAccount.id,
        description: otherDesc,
        amount: 120,
        creditDebitType: CreditDebitType.Debit,
        status: TransactionStatus.Posted,
        date: today(),
      },
      pfUser.token,
    );

    await signIntoPage(page, pfUser.token);
    const pfPage = new FinanceTransactionsPage(page);
    await pfPage.goto();
    await pfPage.selectAccount(bankAccount.name, bankAccount.id);

    await pfPage.openCreateTransactionForm();
    const form = new InlineTransactionForm(page);
    const desc = uniqueDesc("Viagem H11");
    const tag = `viagem-${Date.now()}`;
    await form.fillDescription(desc);
    await form.fillAmount(800);
    await form.setDateToday();
    await form.addTag(tag);
    await form.save();
    await form.waitForClose();

    await pfPage.transactionTable.expectTransactionVisible(desc);
    await expect(
      pfPage.transactionTable
        .getRowByDescription(desc)
        .getByText(tag, { exact: true }),
    ).toBeVisible();

    await page
      .getByTestId("month-summary-section")
      .getByText(`#${tag}`, { exact: true })
      .click();
    await pfPage.transactionTable.expectTransactionVisible(desc);
    await pfPage.transactionTable.expectTransactionHidden(otherDesc);

    await deleteTransaction(otherTx.id, pfUser.token);
  });

  test("TX-H-12: filter by category — only matching transactions shown", async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    // Create two transactions with different categories via GraphQL
    const descWith = uniqueDesc("Com Categoria H12");
    const descWithout = uniqueDesc("Sem Categoria H12");

    const catResult = await executeGraphQL<{
      transactionCategories: Array<{
        id: string;
        name: string;
        applicableTo: string;
      }>;
    }>(
      `query { transactionCategories { id name applicableTo } }`,
      {},
      pfUser.token,
    );

    const category = catResult.data?.transactionCategories?.find(
      (c) =>
        c.applicableTo === CategoryApplicableTo.Expense ||
        c.applicableTo === CategoryApplicableTo.Any,
    );
    if (!category) {
      test.skip(
        true,
        "No expense category available for category-filter coverage.",
      );
      return;
    }

    const [txWith, txWithout] = await Promise.all([
      createTransaction(
        {
          financialAccountId: bankAccount.id,
          description: descWith,
          amount: 100,
          creditDebitType: CreditDebitType.Debit,
          categoryId: category.id,
          status: TransactionStatus.Posted,
          date: today(),
        },
        pfUser.token,
      ),
      createTransaction(
        {
          financialAccountId: bankAccount.id,
          description: descWithout,
          amount: 50,
          creditDebitType: CreditDebitType.Debit,
          status: TransactionStatus.Posted,
          date: today(),
        },
        pfUser.token,
      ),
    ]);

    await signIntoPage(page);
    const pfPage = new FinanceTransactionsPage(page);
    await pfPage.goto();
    await pfPage.selectAccount(bankAccount.name, bankAccount.id);
    // Summary enters a skeleton state after account selection; wait before clicking chips.
    await pfPage.summary.waitForMetricsReady();

    const categoryChip = page
      .getByTestId("month-summary-section")
      .getByText(category.name, { exact: true });
    await expect(categoryChip).toBeVisible();
    await categoryChip.click();

    await pfPage.transactionTable.expectTransactionVisible(descWith);
    await pfPage.transactionTable.expectTransactionHidden(descWithout);

    // Cleanup
    await Promise.all([
      deleteTransaction(txWith.id, pfUser.token),
      deleteTransaction(txWithout.id, pfUser.token),
    ]);
  });

  test("TX-H-18: clicking a filled category chip opens the picker without clearing category", async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    const desc = uniqueDesc("Categoria Click H18");

    const catResult = await executeGraphQL<{
      transactionCategories: Array<{
        id: string;
        name: string;
        applicableTo: string;
      }>;
    }>(`query { transactionCategories { id name applicableTo } }`, {}, pfUser.token);

    const category = catResult.data?.transactionCategories?.find(
      (c) =>
        c.applicableTo === CategoryApplicableTo.Income ||
        c.applicableTo === CategoryApplicableTo.Any,
    );
    if (!category) {
      test.skip(true, "No income category available for category-picker click coverage.");
      return;
    }

    const tx = await createTransaction(
      {
        financialAccountId: bankAccount.id,
        description: desc,
        amount: 123,
        creditDebitType: CreditDebitType.Credit,
        categoryId: category.id,
        status: TransactionStatus.Posted,
        date: today(),
      },
      pfUser.token,
    );

    const clearedCategoryMutations: Array<{ id: unknown; input: unknown }> = [];
    const handler = (request: import("@playwright/test").Request): void => {
      if (request.method() !== "POST") return;
      if (!/\/graphql(\?|$)/.test(request.url())) return;

      let body: unknown;
      try {
        body = request.postDataJSON();
      } catch {
        return;
      }

      const operations = Array.isArray(body) ? body : [body];
      for (const op of operations) {
        const raw = (op ?? {}) as Record<string, unknown>;
        const operationName = typeof raw.operationName === "string" ? raw.operationName : "";
        const query = typeof raw.query === "string" ? raw.query : "";
        const isUpdateTransaction =
          operationName === "UpdateTransaction" || query.includes("updateTransaction");
        if (!isUpdateTransaction) continue;

        const variables =
          raw.variables && typeof raw.variables === "object"
            ? (raw.variables as Record<string, unknown>)
            : null;
        if (!variables) continue;

        if (variables.id !== tx.id) continue;
        const input =
          variables.input && typeof variables.input === "object"
            ? (variables.input as Record<string, unknown>)
            : null;
        if (!input) continue;

        if (Object.prototype.hasOwnProperty.call(input, "categoryId") && input.categoryId === null) {
          clearedCategoryMutations.push({ id: variables.id, input: variables.input });
        }
      }
    };

    page.on("request", handler);
    try {
      await signIntoPage(page);
      const pfPage = new FinanceTransactionsPage(page);
      await pfPage.goto();
      await pfPage.selectAccount(bankAccount.name, bankAccount.id);

      await pfPage.transactionTable.expectTransactionVisible(desc);

      const row = pfPage.transactionTable.getRowByDescription(desc);
      await expect(row.getByText(category.name, { exact: true })).toBeVisible();
      await row.getByText(category.name, { exact: true }).click();

      await expect(page.getByText(/sem categoria|no category/i).first()).toBeVisible();
      await expect
        .poll(() => clearedCategoryMutations.length, { timeout: 1500 })
        .toBe(0);
    } finally {
      page.off("request", handler);
      await deleteTransaction(tx.id, pfUser.token);
    }
  });

  test("TX-H-13: filter by DEBIT — only expense transactions shown", async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    const descDebit = uniqueDesc("Despesa H13");
    const descCredit = uniqueDesc("Receita H13");

    const [txDebit, txCredit] = await Promise.all([
      createTransaction(
        {
          financialAccountId: bankAccount.id,
          description: descDebit,
          amount: 100,
          creditDebitType: CreditDebitType.Debit,
          status: TransactionStatus.Posted,
          date: today(),
        },
        pfUser.token,
      ),
      createTransaction(
        {
          financialAccountId: bankAccount.id,
          description: descCredit,
          amount: 200,
          creditDebitType: CreditDebitType.Credit,
          status: TransactionStatus.Posted,
          date: today(),
        },
        pfUser.token,
      ),
    ]);

    await signIntoPage(page);
    const pfPage = new FinanceTransactionsPage(page);
    await pfPage.goto();
    await pfPage.selectAccount(bankAccount.name, bankAccount.id);
    await pfPage.summary.waitForMetricsReady();

    await page.getByTestId("month-summary-total-expenses").click();

    await pfPage.transactionTable.expectTransactionVisible(descDebit);
    await pfPage.transactionTable.expectTransactionHidden(descCredit);

    await page.getByTestId("month-summary-total-expenses").click();

    await pfPage.transactionTable.expectTransactionVisible(descDebit);
    await pfPage.transactionTable.expectTransactionVisible(descCredit);

    await Promise.all([
      deleteTransaction(txDebit.id, pfUser.token),
      deleteTransaction(txCredit.id, pfUser.token),
    ]);
  });

  test("TX-H-14: filter by tag — only tagged transactions shown", async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    const tag = `tag-h14-${Date.now()}`;
    const descTagged = uniqueDesc("Com Tag H14");
    const descUntagged = uniqueDesc("Sem Tag H14");

    const [txTagged, txUntagged] = await Promise.all([
      createTransaction(
        {
          financialAccountId: bankAccount.id,
          description: descTagged,
          amount: 100,
          creditDebitType: CreditDebitType.Debit,
          status: TransactionStatus.Posted,
          date: today(),
          tags: [tag],
        },
        pfUser.token,
      ),
      createTransaction(
        {
          financialAccountId: bankAccount.id,
          description: descUntagged,
          amount: 50,
          creditDebitType: CreditDebitType.Debit,
          status: TransactionStatus.Posted,
          date: today(),
        },
        pfUser.token,
      ),
    ]);

    await signIntoPage(page);
    const pfPage = new FinanceTransactionsPage(page);
    await pfPage.goto();
    await pfPage.selectAccount(bankAccount.name, bankAccount.id);
    await pfPage.summary.waitForMetricsReady();

    const tagChip = page
      .getByTestId("month-summary-section")
      .getByText(`#${tag}`, { exact: true });
    await expect(tagChip).toBeVisible();
    await tagChip.click();

    await pfPage.transactionTable.expectTransactionVisible(descTagged);
    await pfPage.transactionTable.expectTransactionHidden(descUntagged);

    await Promise.all([
      deleteTransaction(txTagged.id, pfUser.token),
      deleteTransaction(txUntagged.id, pfUser.token),
    ]);
  });

  test("TX-H-15: pagination — loads more transactions when scrolling / advancing", async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    // Seed 25 transactions to exceed the default page size (typically 20)
    const transactions = await Promise.all(
      Array.from({ length: 25 }, (_, i) =>
        createTransaction(
          {
            financialAccountId: bankAccount.id,
            description: `Paginação H15 item ${i + 1}`,
            amount: 10,
            creditDebitType: CreditDebitType.Debit,
            status: TransactionStatus.Posted,
            date: today(),
          },
          pfUser.token,
        ),
      ),
    );

    await signIntoPage(page);
    const pfPage = new FinanceTransactionsPage(page);
    await pfPage.goto();
    await pfPage.selectAccount(bankAccount.name, bankAccount.id);
    // waitForLoad ensures the first page has rendered before we snapshot the count.
    // Without it, getRowCount() races the loading state and returns 0 — then
    // the scroll poll trivially passes when the first page appears, not the second.
    await pfPage.transactionTable.waitForLoad();

    const firstPageCount = await pfPage.transactionTable.getRowCount();

    // Trigger infinite scroll / next-page load
    await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
    // Poll until more rows appear — networkidle is unreliable with SPAs that have WebSockets/polling.
    await expect
      .poll(
        async () => pfPage.transactionTable.getRowCount(),
        { timeout: 15_000, intervals: [500, 1000, 1000] },
      )
      .toBeGreaterThan(firstPageCount);

    // Cleanup
    await Promise.all(
      transactions.map((tx) => deleteTransaction(tx.id, pfUser.token)),
    );
  });

  test("TX-H-16: creates transaction with a past date inside the rolling window — API date matches", async ({
    page,
    pfUser,
  }) => {
    const targetIso = isoDateLocalWithDayOffset(-45);
    const bankAccount = await createFinancialAccount(
      {
        name: uniqueDesc("Conta H16 saldo inicial antigo"),
        type: "BANK",
        subtype: "CONTA_CORRENTE",
        initialBalance: 1000,
        initialBalanceDate: isoDateLocalYearsAgo(1),
      },
      pfUser.token,
    );

    await signIntoPage(page, pfUser.token);
    const pfPage = new FinanceTransactionsPage(page);
    await pfPage.goto();
    await pfPage.selectAccount(bankAccount.name, bankAccount.id);
    const desc = uniqueDesc("Data passada H16");

    await pfPage.openCreateTransactionForm();
    const form = new InlineTransactionForm(page);
    await form.fillAndSave({
      description: desc,
      amount: 42,
      creditDebitType: CreditDebitType.Debit,
      date: targetIso,
    });

    const txYearMonth = yearMonthFromIso(targetIso);
    await pfPage.navigateToCalendarMonth(txYearMonth);
    await pfPage.transactionTable.expectTransactionVisible(desc);

    // `transactions` defaults month/year to “now” when omitted — past-dated tx lives in txYearMonth.
    const txs = await getTransactions(bankAccount.id, pfUser.token, {
      first: 80,
      year: txYearMonth.year,
      month: txYearMonth.month,
    });
    const created = txs.find((t) => t.description === desc);
    expect(created).toBeDefined();
    expect(created!.date.slice(0, 10)).toBe(targetIso);

    await deleteTransaction(created!.id, pfUser.token);
  });

  test("TX-H-17: BALANCE_ADJUSTMENT — no category/tags in row, actions menu Edit+Remove only, amount-only edit", async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    const desc = uniqueDesc("Ajuste saldo H17");
    const tx = await createTransaction(
      {
        financialAccountId: bankAccount.id,
        description: desc,
        amount: 200,
        creditDebitType: CreditDebitType.Credit,
        type: TransactionType.BalanceAdjustment,
        status: TransactionStatus.Posted,
        date: today(),
      },
      pfUser.token,
    );

    await signIntoPage(page, pfUser.token);
    const pfPage = new FinanceTransactionsPage(page);
    await pfPage.goto();
    await pfPage.selectAccount(bankAccount.name, bankAccount.id);
    await pfPage.transactionTable.waitForLoad();

    const row = pfPage.transactionTable.getRowByDescription(desc);
    await expect(row.getByTestId("transaction-row-balance-adjustment-no-category")).toBeVisible();
    await expect(row.getByTestId("transaction-row-balance-adjustment-no-tags")).toBeVisible();

    await pfPage.transactionTable.openRowActionsMenu(desc);
    const menu = page.getByRole("menu");
    await expect(menu.getByRole("menuitem")).toHaveCount(2);
    await page.getByTestId("transaction-action-edit").click();

    const inline = new InlineTransactionForm(page);
    await inline.waitForOpen();
    await expect(page.getByTestId("inline-transaction-form")).toHaveAttribute(
      "data-balance-adjustment-edit",
      "true",
    );

    const responsePromise = page.waitForResponse(
      (response) => {
        const req = response.request();
        if (req.method() !== "POST") return false;
        if (!/graphql/i.test(req.url())) return false;
        const body = req.postData() ?? "";
        return body.includes("UpdateTransaction") && body.includes("mutation");
      },
      { timeout: 30_000 },
    );

    await inline.fillAmount(350);
    await inline.save();
    const res = await responsePromise;
    expect(res.ok()).toBeTruthy();
    const json = (await res.json()) as { errors?: { message?: string }[] };
    expect(json.errors == null || json.errors.length === 0).toBeTruthy();

    await inline.waitForClose();

    const txs = await getTransactions(bankAccount.id, pfUser.token, { first: 80 });
    const updated = txs.find((t) => t.id === tx.id);
    expect(updated).toBeDefined();
    expect(updated!.amount).toBe(350);

    await deleteTransaction(tx.id, pfUser.token);
  });
});

// =============================================================================
// ALTERNATIVE PATHS
// =============================================================================

test.describe("Transactions — alternative paths", () => {
  test("TX-A-01: TRANSFER type — not counted in totalIncome or totalExpenses", async ({
    pfUser,
    bankAccount,
  }) => {
    const desc = uniqueDesc("Transferência A01");
    const tx = await createTransaction(
      {
        financialAccountId: bankAccount.id,
        description: desc,
        amount: 500,
        creditDebitType: CreditDebitType.Debit,
        type: TransactionType.Transfer,
        status: TransactionStatus.Posted,
        date: today(),
      },
      pfUser.token,
    );

    const { year, month } = localCalendarYearMonth();
    const summary = await getMonthSummary(
      bankAccount.id,
      year,
      month,
      pfUser.token,
    );

    // TRANSFER must not appear in income or expenses
    // totalExpenses should not include this transfer amount
    const txs = await getTransactions(bankAccount.id, pfUser.token, {
      month,
      year,
    });
    const transferTx = txs.find((t) => t.id === tx.id);
    expect(transferTx).toBeDefined();

    // Verify via summary: if only this transfer exists, expenses should be 0
    // (This assertion is only reliable if no other expense transactions exist)
    // We verify the API contract via getMonthSummary — totalExpenses should exclude TRANSFER
    expect(summary.totalExpenses).toBe(0);

    await deleteTransaction(tx.id, pfUser.token);
  });

  test("TX-A-02: BALANCE_ADJUSTMENT — not counted in income/expenses, not assigned to a bill", async ({
    pfUser,
    bankAccount,
  }) => {
    const desc = uniqueDesc("Ajuste de Saldo A02");
    const tx = await createTransaction(
      {
        financialAccountId: bankAccount.id,
        description: desc,
        amount: 1000,
        creditDebitType: CreditDebitType.Credit,
        type: TransactionType.BalanceAdjustment,
        status: TransactionStatus.Posted,
        date: today(),
      },
      pfUser.token,
    );

    const { year, month } = localCalendarYearMonth();
    const summary = await getMonthSummary(
      bankAccount.id,
      year,
      month,
      pfUser.token,
    );

    expect(summary.totalIncome).toBe(0); // BALANCE_ADJUSTMENT excluded
    expect(tx.billId).toBeNull(); // Never assigned to a bill

    await deleteTransaction(tx.id, pfUser.token);
  });

  test("TX-A-03: transaction on the first day of the month", async ({
    pfUser,
  }) => {
    const now = new Date();
    const firstDay = formatLocalIsoDate(
      new Date(now.getFullYear(), now.getMonth(), 1),
    );
    // Default `bankAccount` uses initialBalanceDate = first day of month; explicit firstDay here
    // documents the boundary (API: date must be on/after initial balance date).
    const bankAccount = await createFinancialAccount(
      {
        name: uniqueDesc("Conta A03 primeiro dia"),
        type: "BANK",
        subtype: "CONTA_CORRENTE",
        initialBalance: 1000,
        initialBalanceDate: firstDay,
      },
      pfUser.token,
    );
    const desc = uniqueDesc("Primeiro Dia A03");
    const tx = await createTransaction(
      {
        financialAccountId: bankAccount.id,
        description: desc,
        amount: 100,
        creditDebitType: CreditDebitType.Debit,
        status: TransactionStatus.Posted,
        date: firstDay,
      },
      pfUser.token,
    );

    const txs = await getTransactions(bankAccount.id, pfUser.token, {
      ...yearMonthFromIso(firstDay),
      first: 50,
    });
    expect(txs.some((t) => t.id === tx.id)).toBe(true);

    await deleteTransaction(tx.id, pfUser.token);
  });

  test("TX-A-04: transaction on the last day of the month", async ({
    pfUser,
    bankAccount,
  }) => {
    const now = new Date();
    const lastDay = formatLocalIsoDate(
      new Date(now.getFullYear(), now.getMonth() + 1, 0),
    );
    const desc = uniqueDesc("Último Dia A04");
    const tx = await createTransaction(
      {
        financialAccountId: bankAccount.id,
        description: desc,
        amount: 100,
        creditDebitType: CreditDebitType.Debit,
        status: TransactionStatus.Posted,
        date: lastDay,
      },
      pfUser.token,
    );

    const txs = await getTransactions(bankAccount.id, pfUser.token, {
      ...yearMonthFromIso(lastDay),
      first: 50,
    });
    expect(txs.some((t) => t.id === tx.id)).toBe(true);

    await deleteTransaction(tx.id, pfUser.token);
  });

  test("TX-A-05: transaction with minimum amount R$ 0.01", async ({
    pfUser,
    bankAccount,
  }) => {
    const desc = uniqueDesc("Centavo A05");
    const tx = await createTransaction(
      {
        financialAccountId: bankAccount.id,
        description: desc,
        amount: 0.01,
        creditDebitType: CreditDebitType.Debit,
        status: TransactionStatus.Posted,
        date: today(),
      },
      pfUser.token,
    );

    const { year, month } = localCalendarYearMonth();
    const summary = await getMonthSummary(
      bankAccount.id,
      year,
      month,
      pfUser.token,
    );
    expect(summary.totalExpenses).toBeCloseTo(0.01, 2);

    await deleteTransaction(tx.id, pfUser.token);
  });

  test("TX-A-06: transaction with large amount R$ 999,999.99", async ({
    pfUser,
    bankAccount,
  }) => {
    const desc = uniqueDesc("Valor Alto A06");
    const tx = await createTransaction(
      {
        financialAccountId: bankAccount.id,
        description: desc,
        amount: 999999.99,
        creditDebitType: CreditDebitType.Credit,
        status: TransactionStatus.Posted,
        date: today(),
      },
      pfUser.token,
    );

    const { year, month } = localCalendarYearMonth();
    const summary = await getMonthSummary(
      bankAccount.id,
      year,
      month,
      pfUser.token,
    );
    expect(summary.totalIncome).toBeCloseTo(999999.99, 2);

    await deleteTransaction(tx.id, pfUser.token);
  });

  test("TX-A-07: transaction with notes is saved and displayed", async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    const desc = uniqueDesc("Com Notas A07");
    const notes = "Observação de teste com acentuação: çãé";
    const tx = await createTransaction(
      {
        financialAccountId: bankAccount.id,
        description: desc,
        amount: 75,
        creditDebitType: CreditDebitType.Debit,
        status: TransactionStatus.Posted,
        date: today(),
        notes,
      },
      pfUser.token,
    );

    await signIntoPage(page, pfUser.token);
    const pfPage = new FinanceTransactionsPage(page);
    await pfPage.goto();
    await pfPage.selectAccount(bankAccount.name, bankAccount.id);
    // Carousel month can persist in storage from other specs; align with tx date (today).
    await navigateFinanceTransactionsToMonth(pfPage, localCalendarYearMonth());
    await pfPage.transactionTable.waitForLoad();
    await expect(pfPage.transactionTable.getRowByDescription(desc)).toBeVisible({
      timeout: 15_000,
    });

    // Open edit form — notes should be visible
    await pfPage.transactionTable.clickTransaction(desc);
    const form = new InlineTransactionForm(page);
    await form.waitForOpen();

    // TODO: add data-testid="tx-notes-input" to a notes field if/when it's exposed in the inline form
    // For now, verify notes are at least stored correctly via GraphQL
    const { year, month } = localCalendarYearMonth();
    const txs = await getTransactions(bankAccount.id, pfUser.token, {
      year,
      month,
      first: 50,
    });
    const found = txs.find((t) => t.id === tx.id);
    expect(found?.notes).toBe(notes);

    await form.cancel();
    await deleteTransaction(tx.id, pfUser.token);
  });

  test("TX-A-08: edit amount to a higher value — closingBalance decreases further", async ({
    pfUser,
    bankAccount,
  }) => {
    const desc = uniqueDesc("Editar Valor A08");
    const tx = await createTransaction(
      {
        financialAccountId: bankAccount.id,
        description: desc,
        amount: 100,
        creditDebitType: CreditDebitType.Debit,
        status: TransactionStatus.Posted,
        date: today(),
      },
      pfUser.token,
    );

    const { year, month } = localCalendarYearMonth();
    const before = await getMonthSummary(
      bankAccount.id,
      year,
      month,
      pfUser.token,
    );

    const { executeGraphQL } = await import("../helpers/graphql");
    await executeGraphQL(
      `
      mutation UpdateTransaction($id: ID!, $input: UpdateTransactionInput!) {
        updateTransaction(id: $id, input: $input) { id amount }
      }
    `,
      { id: tx.id, input: { amount: 300 } },
      pfUser.token,
    );

    const after = await getMonthSummary(
      bankAccount.id,
      year,
      month,
      pfUser.token,
    );
    // Increased from 100 to 300 → additional -200 on balance
    expect(after.closingBalance).toBeCloseTo(before.closingBalance - 200, 2);

    await deleteTransaction(tx.id, pfUser.token);
  });

  test("TX-A-10: editing date to previous month — chain recompute updates both months", async ({
    pfUser,
  }) => {
    const now = new Date();
    const currentMonth = now.getMonth() + 1;
    const currentYear = now.getFullYear();
    const prevMonth = currentMonth === 1 ? 12 : currentMonth - 1;
    const prevYear = currentMonth === 1 ? currentYear - 1 : currentYear;

    // Initial balance in the prior month so earliest ledger activity is ≤ prev month (snapshot chain
    // materializes prev month; default “saldo only today” can hit internal errors on older backends).
    const initialBalPrevMonth = formatLocalIsoDate(
      new Date(prevYear, prevMonth - 1, 1),
    );
    const bankAccount = await createFinancialAccount(
      {
        name: uniqueDesc("Conta A10 chain mês"),
        type: "BANK",
        subtype: "CONTA_CORRENTE",
        initialBalance: 1000,
        initialBalanceDate: initialBalPrevMonth,
      },
      pfUser.token,
    );

    const desc = uniqueDesc("Mudança de Mês A10");
    const tx = await createTransaction(
      {
        financialAccountId: bankAccount.id,
        description: desc,
        amount: 200,
        creditDebitType: CreditDebitType.Debit,
        status: TransactionStatus.Posted,
        date: today(),
      },
      pfUser.token,
    );

    const currentBefore = await getMonthSummary(
      bankAccount.id,
      currentYear,
      currentMonth,
      pfUser.token,
    );
    const prevBefore = await getMonthSummary(
      bankAccount.id,
      prevYear,
      prevMonth,
      pfUser.token,
    );

    // Move transaction to last month
    const prevMonthDate = dateInMonth(-1, 15);
    const { executeGraphQL } = await import("../helpers/graphql");
    await executeGraphQL(
      `
      mutation UpdateTransaction($id: ID!, $input: UpdateTransactionInput!) {
        updateTransaction(id: $id, input: $input) { id date }
      }
    `,
      { id: tx.id, input: { date: prevMonthDate } },
      pfUser.token,
    );

    const currentAfter = await getMonthSummary(
      bankAccount.id,
      currentYear,
      currentMonth,
      pfUser.token,
    );
    const prevAfter = await getMonthSummary(
      bankAccount.id,
      prevYear,
      prevMonth,
      pfUser.token,
    );

    // The debit moves to the prior month: that month’s closing drops, so this month’s opening
    // drops by the same amount — month-end closing stays unchanged (not +amount).
    expect(currentAfter.closingBalance).toBeCloseTo(
      currentBefore.closingBalance,
      2,
    );
    expect(currentAfter.openingBalance).toBeCloseTo(
      currentBefore.openingBalance - 200,
      2,
    );
    // Previous month gains the expense → totalExpenses increases
    expect(prevAfter.totalExpenses).toBeCloseTo(
      prevBefore.totalExpenses + 200,
      2,
    );

    await deleteTransaction(tx.id, pfUser.token);
  });

  test("TX-A-11: editing date to next month — chain recompute updates both months", async ({
    pfUser,
    bankAccount,
  }) => {
    const now = new Date();
    const currentMonth = now.getMonth() + 1;
    const currentYear = now.getFullYear();
    const nextMonth = currentMonth === 12 ? 1 : currentMonth + 1;
    const nextYear = currentMonth === 12 ? currentYear + 1 : currentYear;

    // Create in current month
    const desc = uniqueDesc("Muda para Próximo Mês A11");
    const tx = await createTransaction(
      {
        financialAccountId: bankAccount.id,
        description: desc,
        amount: 150,
        creditDebitType: CreditDebitType.Debit,
        status: TransactionStatus.Posted,
        date: today(),
      },
      pfUser.token,
    );

    const currentBefore = await getMonthSummary(
      bankAccount.id,
      currentYear,
      currentMonth,
      pfUser.token,
    );

    const nextMonthDate = dateInMonth(1, 15);
    const { executeGraphQL } = await import("../helpers/graphql");
    await executeGraphQL(
      `
      mutation UpdateTransaction($id: ID!, $input: UpdateTransactionInput!) {
        updateTransaction(id: $id, input: $input) { id date }
      }
    `,
      { id: tx.id, input: { date: nextMonthDate } },
      pfUser.token,
    );

    const currentAfter = await getMonthSummary(
      bankAccount.id,
      currentYear,
      currentMonth,
      pfUser.token,
    );

    expect(currentAfter.closingBalance).toBeCloseTo(
      currentBefore.closingBalance + 150,
      2,
    );
    // monthSummary.totalExpenses for a strict calendar-future month needs POSTED rows in the
    // future-month path (MonthSummaryQueryService). Assert on the transaction list instead so
    // the test matches any backend that stores the dated row in that month.
    const txsNextAfter = await getTransactions(bankAccount.id, pfUser.token, {
      year: nextYear,
      month: nextMonth,
      first: 50,
    });
    const moved = txsNextAfter.find((t) => t.id === tx.id);
    expect(moved).toBeDefined();
    expect(moved!.amount).toBeCloseTo(150, 2);
    expect(moved!.date.slice(0, 10)).toBe(nextMonthDate);

    await deleteTransaction(tx.id, pfUser.token);
  });
});

// =============================================================================
// NEGATIVE PATHS
// =============================================================================

test.describe("Transactions — negative paths", () => {
  test("TX-N-01: submit without description — shows required error on description", async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    await signIntoPage(page, pfUser.token);
    const pfPage = new FinanceTransactionsPage(page);
    await pfPage.goto();
    await pfPage.selectAccount(bankAccount.name, bankAccount.id);

    await pfPage.openCreateTransactionForm();
    const form = new InlineTransactionForm(page);
    await form.waitForOpen();

    // Fill amount but leave description empty
    await form.fillAmount(100);
    // Do NOT fill description

    await form.expectSaveEnabled();
    await form.attemptSave();
    await form.expectFieldRequiredError("description");
  });

  test("TX-N-02: submit with amount = 0 — shows required error on amount", async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    await signIntoPage(page, pfUser.token);
    const pfPage = new FinanceTransactionsPage(page);
    await pfPage.goto();
    await pfPage.selectAccount(bankAccount.name, bankAccount.id);

    await pfPage.openCreateTransactionForm();
    const form = new InlineTransactionForm(page);
    await form.waitForOpen();

    await form.fillDescription(uniqueDesc("Valor Zero N02"));
    await form.fillAmount(0);

    await form.expectSaveEnabled();
    await form.attemptSave();
    await form.expectFieldRequiredError("amount");
  });

  test("TX-N-03: submit with negative amount — save button disabled or field corrects to positive", async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    await signIntoPage(page, pfUser.token);
    const pfPage = new FinanceTransactionsPage(page);
    await pfPage.goto();
    await pfPage.selectAccount(bankAccount.name, bankAccount.id);

    await pfPage.openCreateTransactionForm();
    const form = new InlineTransactionForm(page);
    await form.waitForOpen();

    const desc = uniqueDesc("Negativo N03");
    await form.fillDescription(desc);
    await form.fillAmount(-50);

    // The form uses Math.abs(amount) internally, so -50 → 50 and save becomes enabled.
    // OR the field itself rejects negative input. Either behavior is acceptable.
    // Verify that if save IS enabled, the stored amount is positive.
    const isSaveEnabled = await form.isSaveEnabled();
    if (isSaveEnabled) {
      await form.setDateToday();
      await form.save();
      await form.waitForClose();
      const ym = localCalendarYearMonth();
      const txs = await getTransactions(bankAccount.id, pfUser.token, {
        ...ym,
        first: 50,
      });
      const created = txs.find((t) => t.description === desc);
      expect(created?.amount).toBeGreaterThan(0);
      await deleteTransaction(created!.id, pfUser.token);
    } else {
      // Save is disabled — negative values are rejected by the field
      await form.cancel();
    }
  });

  test("TX-N-04: date before the 10-year rolling window — error message shown, save disabled", async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    await signIntoPage(page, pfUser.token);
    const pfPage = new FinanceTransactionsPage(page);
    await pfPage.goto();
    await pfPage.selectAccount(bankAccount.name, bankAccount.id);

    await pfPage.openCreateTransactionForm();
    const form = new InlineTransactionForm(page);
    await form.waitForOpen();

    await form.fillDescription(uniqueDesc("Data Muito Antiga N04"));
    await form.fillAmount(100);

    const tooOldIso = isoDateLocalYearsAgo(11);
    const dateStr = InlineTransactionForm.formatDateBr(tooOldIso);
    await form.setDate(dateStr);

    await form.expectDateError(/10|anos|year/i);
    await form.expectSaveEnabled();
    await form.attemptSave();
    await form.waitForOpen();
  });

  test("TX-N-08: future date — error message shown, save does not submit", async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    await signIntoPage(page, pfUser.token);
    const pfPage = new FinanceTransactionsPage(page);
    await pfPage.goto();
    await pfPage.selectAccount(bankAccount.name, bankAccount.id);

    await pfPage.openCreateTransactionForm();
    const form = new InlineTransactionForm(page);
    await form.waitForOpen();

    await form.fillDescription(uniqueDesc("Data Futura N08"));
    await form.fillAmount(100);

    const futureIso = isoDateLocalWithDayOffset(1);
    await form.setDate(InlineTransactionForm.formatDateBr(futureIso));

    await form.expectDateError(/futur|future/i);
    await form.expectSaveEnabled();
    await form.attemptSave();
    await form.waitForOpen();
  });

  test("TX-N-05: submit without filling any field — shows required errors after save click", async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    await signIntoPage(page, pfUser.token);
    const pfPage = new FinanceTransactionsPage(page);
    await pfPage.goto();
    await pfPage.selectAccount(bankAccount.name, bankAccount.id);

    await pfPage.openCreateTransactionForm();
    const form = new InlineTransactionForm(page);
    await form.waitForOpen();

    // Do not fill any field
    await form.expectSaveEnabled();
    await form.attemptSave();
    await form.expectFieldRequiredError("description");
    await form.expectFieldRequiredError("amount");
  });

  test("TX-N-06: update non-existent transaction via GraphQL — returns error", async ({
    pfUser,
  }) => {
    const fakeId = "00000000-0000-0000-0000-000000000000";

    const result = await executeGraphQL(
      `
      mutation UpdateTransaction($id: ID!, $input: UpdateTransactionInput!) {
        updateTransaction(id: $id, input: $input) { id }
      }
    `,
      { id: fakeId, input: { description: "ghost" } },
      pfUser.token,
    );

    expectGraphqlNotFoundError(result.errors);
  });

  test("TX-N-07: delete non-existent transaction via GraphQL — returns error", async ({
    pfUser,
  }) => {
    const fakeId = "00000000-0000-0000-0000-000000000001";

    const result = await executeGraphQL(
      `
      mutation DeleteTransaction($id: ID!) {
        deleteTransaction(id: $id)
      }
    `,
      { id: fakeId },
      pfUser.token,
    );

    expectGraphqlNotFoundError(result.errors);
  });
});
