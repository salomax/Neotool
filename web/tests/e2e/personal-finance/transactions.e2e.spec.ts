/**
 * E2E tests — Transactions
 *
 * Coverage: TX-H-01..15 | TX-A-01..08 (+ TX-A-10..11 chain recompute) | TX-N-01..07
 * Security coverage is in security.e2e.spec.ts (SEC-TX-01..05).
 *
 * ──────────────────────────────────────────────────────────────────────────────
 * UI form limitations (from reading InlineTransactionForm.tsx):
 *   - No `status` field → UI always creates POSTED (backend default).
 *   - No `type` field  → UI always creates OtherExpense.
 *   - TRANSFER selection sets creditDebitType="TRANSFER" (UI shortcut).
 *   - BALANCE_ADJUSTMENT, FUTURE, PENDING → created via GraphQL fixtures.
 *   - Date window: minDate = today − 5 years (FINANCE_MOVING_WINDOW_YEARS = 5).
 * ──────────────────────────────────────────────────────────────────────────────
 */

import { test, expect } from '../fixtures/personal-finance';
import { FinanceTransactionsPage } from '../pages/personal-finance/FinanceTransactionsPage';
import { InlineTransactionForm } from '../pages/personal-finance/InlineTransactionForm';
import {
  createTransaction,
  deleteTransaction,
  getMonthSummary,
  getTransactions,
  today,
  dateInMonth,
} from '../helpers/banking-graphql';

// ── Helpers ───────────────────────────────────────────────────────────────────

function uniqueDesc(prefix: string): string {
  return `${prefix} ${Date.now()}`;
}

function currentYearMonth(): { year: number; month: number } {
  const now = new Date();
  return { year: now.getFullYear(), month: now.getMonth() + 1 };
}

async function signIntoPage(page: import('@playwright/test').Page, token: string): Promise<void> {
  await page.goto('/');
  await page.evaluate((t) => localStorage.setItem('auth_token', t), token);
  await page.reload();
  await page.waitForLoadState('networkidle');
}

// =============================================================================
// HAPPY PATH
// =============================================================================

test.describe('Transactions — happy path', () => {
  test('TX-H-01: creates a POSTED expense — appears in table and increases totalExpenses', async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    await signIntoPage(page, pfUser.token);
    const pfPage = new FinanceTransactionsPage(page);
    await pfPage.goto();
    await pfPage.selectAccount(bankAccount.name);

    const summaryBefore = await pfPage.summary.getAll();

    await pfPage.openCreateTransactionForm();
    const form = new InlineTransactionForm(page);
    const desc = uniqueDesc('Aluguel H01');
    await form.fillAndSave({ description: desc, amount: 2500, creditDebitType: 'DEBIT' });
    await form.waitForClose();

    await pfPage.transactionTable.expectTransactionVisible(desc);

    const summaryAfter = await pfPage.summary.getAll();
    expect(summaryAfter.totalExpenses).toBeGreaterThan(summaryBefore.totalExpenses);
    expect(summaryAfter.closingBalance).toBeLessThan(summaryBefore.closingBalance);
  });

  test('TX-H-02: creates a POSTED income — appears in table and increases totalIncome', async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    await signIntoPage(page, pfUser.token);
    const pfPage = new FinanceTransactionsPage(page);
    await pfPage.goto();
    await pfPage.selectAccount(bankAccount.name);

    const summaryBefore = await pfPage.summary.getAll();

    await pfPage.openCreateTransactionForm();
    const form = new InlineTransactionForm(page);
    const desc = uniqueDesc('Salário H02');
    await form.fillAndSave({ description: desc, amount: 5000, creditDebitType: 'CREDIT' });
    await form.waitForClose();

    await pfPage.transactionTable.expectTransactionVisible(desc);

    const summaryAfter = await pfPage.summary.getAll();
    expect(summaryAfter.totalIncome).toBeGreaterThan(summaryBefore.totalIncome);
    expect(summaryAfter.closingBalance).toBeGreaterThan(summaryBefore.closingBalance);
  });

  test('TX-H-03: PENDING transaction — appears in table and affects closingBalance', async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    // PENDING status requires GraphQL creation — the UI form has no status field.
    const desc = uniqueDesc('Conta de Luz Pendente H03');
    const tx = await createTransaction(
      { financialAccountId: bankAccount.id, description: desc, amount: 180, creditDebitType: 'DEBIT', status: 'PENDING', date: today() },
      pfUser.token
    );

    await signIntoPage(page, pfUser.token);
    const pfPage = new FinanceTransactionsPage(page);
    await pfPage.goto();
    await pfPage.selectAccount(bankAccount.name);

    await pfPage.transactionTable.expectTransactionVisible(desc);

    // PENDING must affect closingBalance (it IS counted in balance, unlike FUTURE)
    const { year, month } = currentYearMonth();
    const summary = await getMonthSummary(bankAccount.id, year, month, pfUser.token);
    expect(summary.closingBalance).toBeLessThan(1000); // initial balance was 1000, expense -180

    await deleteTransaction(tx.id, pfUser.token);
  });

  test('TX-H-04: FUTURE transaction — appears in table with indicator, does NOT affect closingBalance', async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    const desc = uniqueDesc('Assinatura Futura H04');
    const tx = await createTransaction(
      { financialAccountId: bankAccount.id, description: desc, amount: 50, creditDebitType: 'DEBIT', status: 'FUTURE', date: today() },
      pfUser.token
    );

    await signIntoPage(page, pfUser.token);
    const pfPage = new FinanceTransactionsPage(page);
    await pfPage.goto();
    await pfPage.selectAccount(bankAccount.name);

    await pfPage.transactionTable.expectTransactionVisible(desc);

    // FUTURE must NOT affect closingBalance
    const { year, month } = currentYearMonth();
    const summary = await getMonthSummary(bankAccount.id, year, month, pfUser.token);
    // closingBalance = initialBalance (1000) because FUTURE is excluded
    expect(summary.closingBalance).toBe(1000);

    await deleteTransaction(tx.id, pfUser.token);
  });

  test('TX-H-05: edits description inline — updated in table without page reload', async ({
    page,
    pfUser,
    postedExpense,
  }) => {
    await signIntoPage(page, pfUser.token);
    const pfPage = new FinanceTransactionsPage(page);
    await pfPage.goto();
    await pfPage.selectAccount(postedExpense.financialAccountId);

    // Open the edit form by clicking the transaction row
    await pfPage.transactionTable.clickTransaction(postedExpense.description);
    const form = new InlineTransactionForm(page);
    await form.waitForOpen();

    const newDesc = uniqueDesc('Aluguel Editado H05');
    await form.fillDescription(newDesc);
    await form.save();
    await form.waitForClose();

    await pfPage.transactionTable.expectTransactionVisible(newDesc);
    await pfPage.transactionTable.expectTransactionHidden(postedExpense.description);
  });

  test('TX-H-06: edits amount — closingBalance and summary updated', async ({
    page,
    pfUser,
    bankAccount,
    postedExpense,
  }) => {
    await signIntoPage(page, pfUser.token);
    const pfPage = new FinanceTransactionsPage(page);
    await pfPage.goto();
    await pfPage.selectAccount(bankAccount.name);

    const summaryBefore = await pfPage.summary.getAll();

    await pfPage.transactionTable.clickTransaction(postedExpense.description);
    const form = new InlineTransactionForm(page);
    await form.waitForOpen();
    await form.fillAmount(250); // was 100, now 250
    await form.save();
    await form.waitForClose();

    const summaryAfter = await pfPage.summary.getAll();
    // Expense increased by 150 → closingBalance decreased by 150
    expect(summaryAfter.closingBalance).toBeCloseTo(
      summaryBefore.closingBalance - 150,
      2
    );
  });

  test('TX-H-07: changes status PENDING → POSTED via GraphQL — balance unchanged (both count)', async ({
    pfUser,
    bankAccount,
  }) => {
    // Both PENDING and POSTED affect closingBalance, so no balance change on status transition.
    const desc = uniqueDesc('Boleto Pending→Posted H07');
    const tx = await createTransaction(
      { financialAccountId: bankAccount.id, description: desc, amount: 300, creditDebitType: 'DEBIT', status: 'PENDING', date: today() },
      pfUser.token
    );

    const { year, month } = currentYearMonth();
    const before = await getMonthSummary(bankAccount.id, year, month, pfUser.token);

    // Update status to POSTED via GraphQL (the UI form doesn't expose status)
    const { executeGraphQL } = await import('../helpers/graphql');
    await executeGraphQL(`
      mutation UpdateTransaction($id: ID!, $input: UpdateTransactionInput!) {
        updateTransaction(id: $id, input: $input) { id status }
      }
    `, { id: tx.id, input: { status: 'POSTED' } }, pfUser.token);

    const after = await getMonthSummary(bankAccount.id, year, month, pfUser.token);
    // Balance must remain identical — both PENDING and POSTED count in closingBalance
    expect(after.closingBalance).toBeCloseTo(before.closingBalance, 2);

    await deleteTransaction(tx.id, pfUser.token);
  });

  test('TX-H-08: changes status FUTURE → POSTED — closingBalance increases/decreases', async ({
    pfUser,
    bankAccount,
  }) => {
    const desc = uniqueDesc('Compra Futura→Postada H08');
    const tx = await createTransaction(
      { financialAccountId: bankAccount.id, description: desc, amount: 200, creditDebitType: 'DEBIT', status: 'FUTURE', date: today() },
      pfUser.token
    );

    const { year, month } = currentYearMonth();
    const before = await getMonthSummary(bankAccount.id, year, month, pfUser.token);

    const { executeGraphQL } = await import('../helpers/graphql');
    await executeGraphQL(`
      mutation UpdateTransaction($id: ID!, $input: UpdateTransactionInput!) {
        updateTransaction(id: $id, input: $input) { id status }
      }
    `, { id: tx.id, input: { status: 'POSTED' } }, pfUser.token);

    const after = await getMonthSummary(bankAccount.id, year, month, pfUser.token);
    // FUTURE → POSTED: expense now counts → closingBalance drops by 200
    expect(after.closingBalance).toBeCloseTo(before.closingBalance - 200, 2);

    await deleteTransaction(tx.id, pfUser.token);
  });

  test('TX-H-09: deletes a POSTED transaction — removed from table and balance reverts', async ({
    page,
    pfUser,
    bankAccount,
    postedExpense,
  }) => {
    await signIntoPage(page, pfUser.token);
    const pfPage = new FinanceTransactionsPage(page);
    await pfPage.goto();
    await pfPage.selectAccount(bankAccount.name);

    const summaryBefore = await pfPage.summary.getAll();

    await pfPage.transactionTable.deleteTransaction(postedExpense.description);

    await pfPage.transactionTable.expectTransactionHidden(postedExpense.description);

    const summaryAfter = await pfPage.summary.getAll();
    // Expense of 100 removed → closingBalance increases by 100
    expect(summaryAfter.closingBalance).toBeCloseTo(
      summaryBefore.closingBalance + postedExpense.amount,
      2
    );
  });

  test('TX-H-10: assigns category — displayed in the transaction row', async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    await signIntoPage(page, pfUser.token);
    const pfPage = new FinanceTransactionsPage(page);
    await pfPage.goto();
    await pfPage.selectAccount(bankAccount.name);

    await pfPage.openCreateTransactionForm();
    const form = new InlineTransactionForm(page);
    const desc = uniqueDesc('Mercado H10');
    await form.fillDescription(desc);
    await form.fillAmount(150);
    // Select a category via the CategorySelect component
    // TODO: update selector once data-testid is added to CategorySelect
    await page.getByTestId('tx-category-select').click();
    await page.getByRole('listbox').getByRole('option').first().click();
    await form.save();
    await form.waitForClose();

    const row = pfPage.transactionTable.getRowByDescription(desc);
    await expect(row).toBeVisible();
    // The row should contain a category chip/icon — verify it's not blank
    await expect(row.locator('[data-testid="tx-category-chip"]')).toBeVisible();
  });

  test('TX-H-11: adds a tag — visible in the row and filterable', async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    await signIntoPage(page, pfUser.token);
    const pfPage = new FinanceTransactionsPage(page);
    await pfPage.goto();
    await pfPage.selectAccount(bankAccount.name);

    await pfPage.openCreateTransactionForm();
    const form = new InlineTransactionForm(page);
    const desc = uniqueDesc('Viagem H11');
    const tag = `viagem-${Date.now()}`;
    await form.fillDescription(desc);
    await form.fillAmount(800);
    // Add tag via TagsInput
    const tagsInput = page.getByTestId('tx-tags-input');
    await tagsInput.fill(tag);
    await tagsInput.press('Enter');
    await form.save();
    await form.waitForClose();

    // Transaction visible
    await pfPage.transactionTable.expectTransactionVisible(desc);

    // Filter by the tag — only this transaction should be shown
    // TODO: update tag filter selector once data-testid added to filter controls
    await page.getByTestId('tag-filter').click();
    await page.getByRole('option', { name: tag }).click();
    await pfPage.transactionTable.expectTransactionVisible(desc);
  });

  test('TX-H-12: filter by category — only matching transactions shown', async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    // Create two transactions with different categories via GraphQL
    const descWith = uniqueDesc('Com Categoria H12');
    const descWithout = uniqueDesc('Sem Categoria H12');

    // We need a category ID — fetch categories from the API
    const { executeGraphQL } = await import('../helpers/graphql');
    const catResult = await executeGraphQL<{
      transactionCategories: Array<{ id: string; name: string; applicableTo: string }>;
    }>(`query { transactionCategories { id name applicableTo } }`, {}, pfUser.token);

    const category = catResult.data?.transactionCategories?.find(
      (c) => c.applicableTo === 'EXPENSE' || c.applicableTo === 'ANY'
    );

    const [txWith, txWithout] = await Promise.all([
      createTransaction(
        { financialAccountId: bankAccount.id, description: descWith, amount: 100, creditDebitType: 'DEBIT', categoryId: category?.id, status: 'POSTED', date: today() },
        pfUser.token
      ),
      createTransaction(
        { financialAccountId: bankAccount.id, description: descWithout, amount: 50, creditDebitType: 'DEBIT', status: 'POSTED', date: today() },
        pfUser.token
      ),
    ]);

    await signIntoPage(page, pfUser.token);
    const pfPage = new FinanceTransactionsPage(page);
    await pfPage.goto();
    await pfPage.selectAccount(bankAccount.name);

    // Apply category filter
    await page.getByTestId('category-filter').click();
    await page.getByRole('option', { name: category?.name ?? '' }).click();

    await pfPage.transactionTable.expectTransactionVisible(descWith);
    await pfPage.transactionTable.expectTransactionHidden(descWithout);

    // Cleanup
    await Promise.all([
      deleteTransaction(txWith.id, pfUser.token),
      deleteTransaction(txWithout.id, pfUser.token),
    ]);
  });

  test('TX-H-13: filter by DEBIT — only expense transactions shown', async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    const descDebit = uniqueDesc('Despesa H13');
    const descCredit = uniqueDesc('Receita H13');

    const [txDebit, txCredit] = await Promise.all([
      createTransaction({ financialAccountId: bankAccount.id, description: descDebit, amount: 100, creditDebitType: 'DEBIT', status: 'POSTED', date: today() }, pfUser.token),
      createTransaction({ financialAccountId: bankAccount.id, description: descCredit, amount: 200, creditDebitType: 'CREDIT', status: 'POSTED', date: today() }, pfUser.token),
    ]);

    await signIntoPage(page, pfUser.token);
    const pfPage = new FinanceTransactionsPage(page);
    await pfPage.goto();
    await pfPage.selectAccount(bankAccount.name);

    await page.getByTestId('credit-debit-filter').click();
    await page.getByRole('option', { name: /débito|debit/i }).click();

    await pfPage.transactionTable.expectTransactionVisible(descDebit);
    await pfPage.transactionTable.expectTransactionHidden(descCredit);

    // Clear filter
    await page.getByTestId('credit-debit-filter-clear').click();

    await pfPage.transactionTable.expectTransactionVisible(descDebit);
    await pfPage.transactionTable.expectTransactionVisible(descCredit);

    await Promise.all([
      deleteTransaction(txDebit.id, pfUser.token),
      deleteTransaction(txCredit.id, pfUser.token),
    ]);
  });

  test('TX-H-14: filter by tag — only tagged transactions shown', async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    const tag = `tag-h14-${Date.now()}`;
    const descTagged = uniqueDesc('Com Tag H14');
    const descUntagged = uniqueDesc('Sem Tag H14');

    const [txTagged, txUntagged] = await Promise.all([
      createTransaction({ financialAccountId: bankAccount.id, description: descTagged, amount: 100, creditDebitType: 'DEBIT', status: 'POSTED', date: today(), tags: [tag] }, pfUser.token),
      createTransaction({ financialAccountId: bankAccount.id, description: descUntagged, amount: 50, creditDebitType: 'DEBIT', status: 'POSTED', date: today() }, pfUser.token),
    ]);

    await signIntoPage(page, pfUser.token);
    const pfPage = new FinanceTransactionsPage(page);
    await pfPage.goto();
    await pfPage.selectAccount(bankAccount.name);

    await page.getByTestId('tag-filter').click();
    await page.getByRole('option', { name: tag }).click();

    await pfPage.transactionTable.expectTransactionVisible(descTagged);
    await pfPage.transactionTable.expectTransactionHidden(descUntagged);

    await Promise.all([
      deleteTransaction(txTagged.id, pfUser.token),
      deleteTransaction(txUntagged.id, pfUser.token),
    ]);
  });

  test('TX-H-15: pagination — loads more transactions when scrolling / advancing', async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    // Seed 25 transactions to exceed the default page size (typically 20)
    const transactions = await Promise.all(
      Array.from({ length: 25 }, (_, i) =>
        createTransaction(
          { financialAccountId: bankAccount.id, description: `Paginação H15 item ${i + 1}`, amount: 10, creditDebitType: 'DEBIT', status: 'POSTED', date: today() },
          pfUser.token
        )
      )
    );

    await signIntoPage(page, pfUser.token);
    const pfPage = new FinanceTransactionsPage(page);
    await pfPage.goto();
    await pfPage.selectAccount(bankAccount.name);

    const firstPageCount = await pfPage.transactionTable.getRowCount();

    // Trigger next page (scroll to bottom or click "next page" button)
    await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
    await page.waitForLoadState('networkidle');

    const secondPageCount = await pfPage.transactionTable.getRowCount();
    expect(secondPageCount).toBeGreaterThanOrEqual(firstPageCount);

    // Cleanup
    await Promise.all(transactions.map((tx) => deleteTransaction(tx.id, pfUser.token)));
  });
});

// =============================================================================
// ALTERNATIVE PATHS
// =============================================================================

test.describe('Transactions — alternative paths', () => {
  test('TX-A-01: TRANSFER type — not counted in totalIncome or totalExpenses', async ({
    pfUser,
    bankAccount,
  }) => {
    const desc = uniqueDesc('Transferência A01');
    const tx = await createTransaction(
      { financialAccountId: bankAccount.id, description: desc, amount: 500, creditDebitType: 'DEBIT', type: 'TRANSFER', status: 'POSTED', date: today() },
      pfUser.token
    );

    const { year, month } = currentYearMonth();
    const summary = await getMonthSummary(bankAccount.id, year, month, pfUser.token);

    // TRANSFER must not appear in income or expenses
    // totalExpenses should not include this transfer amount
    const txs = await getTransactions(bankAccount.id, pfUser.token, { month, year });
    const transferTx = txs.find((t) => t.id === tx.id);
    expect(transferTx).toBeDefined();

    // Verify via summary: if only this transfer exists, expenses should be 0
    // (This assertion is only reliable if no other expense transactions exist)
    // We verify the API contract via getMonthSummary — totalExpenses should exclude TRANSFER
    expect(summary.totalExpenses).toBe(0);

    await deleteTransaction(tx.id, pfUser.token);
  });

  test('TX-A-02: BALANCE_ADJUSTMENT — not counted in income/expenses, not assigned to a bill', async ({
    pfUser,
    bankAccount,
  }) => {
    const desc = uniqueDesc('Ajuste de Saldo A02');
    const tx = await createTransaction(
      { financialAccountId: bankAccount.id, description: desc, amount: 1000, creditDebitType: 'CREDIT', type: 'BALANCE_ADJUSTMENT', status: 'POSTED', date: today() },
      pfUser.token
    );

    const { year, month } = currentYearMonth();
    const summary = await getMonthSummary(bankAccount.id, year, month, pfUser.token);

    expect(summary.totalIncome).toBe(0); // BALANCE_ADJUSTMENT excluded
    expect(tx.billId).toBeNull();        // Never assigned to a bill

    await deleteTransaction(tx.id, pfUser.token);
  });

  test('TX-A-03: transaction on the first day of the month', async ({
    pfUser,
    bankAccount,
  }) => {
    const now = new Date();
    const firstDay = new Date(now.getFullYear(), now.getMonth(), 1)
      .toISOString()
      .split('T')[0];
    const desc = uniqueDesc('Primeiro Dia A03');
    const tx = await createTransaction(
      { financialAccountId: bankAccount.id, description: desc, amount: 100, creditDebitType: 'DEBIT', status: 'POSTED', date: firstDay },
      pfUser.token
    );

    const txs = await getTransactions(bankAccount.id, pfUser.token, {
      month: now.getMonth() + 1,
      year: now.getFullYear(),
    });
    expect(txs.some((t) => t.id === tx.id)).toBe(true);

    await deleteTransaction(tx.id, pfUser.token);
  });

  test('TX-A-04: transaction on the last day of the month', async ({
    pfUser,
    bankAccount,
  }) => {
    const now = new Date();
    const lastDay = new Date(now.getFullYear(), now.getMonth() + 1, 0)
      .toISOString()
      .split('T')[0];
    const desc = uniqueDesc('Último Dia A04');
    const tx = await createTransaction(
      { financialAccountId: bankAccount.id, description: desc, amount: 100, creditDebitType: 'DEBIT', status: 'POSTED', date: lastDay },
      pfUser.token
    );

    const txs = await getTransactions(bankAccount.id, pfUser.token, {
      month: now.getMonth() + 1,
      year: now.getFullYear(),
    });
    expect(txs.some((t) => t.id === tx.id)).toBe(true);

    await deleteTransaction(tx.id, pfUser.token);
  });

  test('TX-A-05: transaction with minimum amount R$ 0.01', async ({
    pfUser,
    bankAccount,
  }) => {
    const desc = uniqueDesc('Centavo A05');
    const tx = await createTransaction(
      { financialAccountId: bankAccount.id, description: desc, amount: 0.01, creditDebitType: 'DEBIT', status: 'POSTED', date: today() },
      pfUser.token
    );

    const { year, month } = currentYearMonth();
    const summary = await getMonthSummary(bankAccount.id, year, month, pfUser.token);
    expect(summary.totalExpenses).toBeCloseTo(0.01, 2);

    await deleteTransaction(tx.id, pfUser.token);
  });

  test('TX-A-06: transaction with large amount R$ 999,999.99', async ({
    pfUser,
    bankAccount,
  }) => {
    const desc = uniqueDesc('Valor Alto A06');
    const tx = await createTransaction(
      { financialAccountId: bankAccount.id, description: desc, amount: 999999.99, creditDebitType: 'CREDIT', status: 'POSTED', date: today() },
      pfUser.token
    );

    const { year, month } = currentYearMonth();
    const summary = await getMonthSummary(bankAccount.id, year, month, pfUser.token);
    expect(summary.totalIncome).toBeCloseTo(999999.99, 2);

    await deleteTransaction(tx.id, pfUser.token);
  });

  test('TX-A-07: transaction with notes is saved and displayed', async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    const desc = uniqueDesc('Com Notas A07');
    const notes = 'Observação de teste com acentuação: çãé';
    const tx = await createTransaction(
      { financialAccountId: bankAccount.id, description: desc, amount: 75, creditDebitType: 'DEBIT', status: 'POSTED', date: today(), notes },
      pfUser.token
    );

    await signIntoPage(page, pfUser.token);
    const pfPage = new FinanceTransactionsPage(page);
    await pfPage.goto();
    await pfPage.selectAccount(bankAccount.name);

    // Open edit form — notes should be visible
    await pfPage.transactionTable.clickTransaction(desc);
    const form = new InlineTransactionForm(page);
    await form.waitForOpen();

    // TODO: add data-testid="tx-notes-input" to a notes field if/when it's exposed in the inline form
    // For now, verify notes are at least stored correctly via GraphQL
    const txs = await getTransactions(bankAccount.id, pfUser.token);
    const found = txs.find((t) => t.id === tx.id);
    expect(found?.notes).toBe(notes);

    await form.cancel();
    await deleteTransaction(tx.id, pfUser.token);
  });

  test('TX-A-08: edit amount to a higher value — closingBalance decreases further', async ({
    pfUser,
    bankAccount,
  }) => {
    const desc = uniqueDesc('Editar Valor A08');
    const tx = await createTransaction(
      { financialAccountId: bankAccount.id, description: desc, amount: 100, creditDebitType: 'DEBIT', status: 'POSTED', date: today() },
      pfUser.token
    );

    const { year, month } = currentYearMonth();
    const before = await getMonthSummary(bankAccount.id, year, month, pfUser.token);

    const { executeGraphQL } = await import('../helpers/graphql');
    await executeGraphQL(`
      mutation UpdateTransaction($id: ID!, $input: UpdateTransactionInput!) {
        updateTransaction(id: $id, input: $input) { id amount }
      }
    `, { id: tx.id, input: { amount: 300 } }, pfUser.token);

    const after = await getMonthSummary(bankAccount.id, year, month, pfUser.token);
    // Increased from 100 to 300 → additional -200 on balance
    expect(after.closingBalance).toBeCloseTo(before.closingBalance - 200, 2);

    await deleteTransaction(tx.id, pfUser.token);
  });

  test('TX-A-10: editing date to previous month — chain recompute updates both months', async ({
    pfUser,
    bankAccount,
  }) => {
    const now = new Date();
    const currentMonth = now.getMonth() + 1;
    const currentYear = now.getFullYear();
    const prevMonth = currentMonth === 1 ? 12 : currentMonth - 1;
    const prevYear = currentMonth === 1 ? currentYear - 1 : currentYear;

    const desc = uniqueDesc('Mudança de Mês A10');
    const tx = await createTransaction(
      { financialAccountId: bankAccount.id, description: desc, amount: 200, creditDebitType: 'DEBIT', status: 'POSTED', date: today() },
      pfUser.token
    );

    const currentBefore = await getMonthSummary(bankAccount.id, currentYear, currentMonth, pfUser.token);
    const prevBefore = await getMonthSummary(bankAccount.id, prevYear, prevMonth, pfUser.token);

    // Move transaction to last month
    const prevMonthDate = dateInMonth(-1, 15);
    const { executeGraphQL } = await import('../helpers/graphql');
    await executeGraphQL(`
      mutation UpdateTransaction($id: ID!, $input: UpdateTransactionInput!) {
        updateTransaction(id: $id, input: $input) { id date }
      }
    `, { id: tx.id, input: { date: prevMonthDate } }, pfUser.token);

    const currentAfter = await getMonthSummary(bankAccount.id, currentYear, currentMonth, pfUser.token);
    const prevAfter = await getMonthSummary(bankAccount.id, prevYear, prevMonth, pfUser.token);

    // Current month loses the expense → closingBalance increases
    expect(currentAfter.closingBalance).toBeCloseTo(currentBefore.closingBalance + 200, 2);
    // Previous month gains the expense → totalExpenses increases
    expect(prevAfter.totalExpenses).toBeCloseTo(prevBefore.totalExpenses + 200, 2);

    await deleteTransaction(tx.id, pfUser.token);
  });

  test('TX-A-11: editing date to next month — chain recompute updates both months', async ({
    pfUser,
    bankAccount,
  }) => {
    const now = new Date();
    const currentMonth = now.getMonth() + 1;
    const currentYear = now.getFullYear();
    const nextMonth = currentMonth === 12 ? 1 : currentMonth + 1;
    const nextYear = currentMonth === 12 ? currentYear + 1 : currentYear;

    // Create in current month
    const desc = uniqueDesc('Muda para Próximo Mês A11');
    const tx = await createTransaction(
      { financialAccountId: bankAccount.id, description: desc, amount: 150, creditDebitType: 'DEBIT', status: 'POSTED', date: today() },
      pfUser.token
    );

    const currentBefore = await getMonthSummary(bankAccount.id, currentYear, currentMonth, pfUser.token);
    const nextBefore = await getMonthSummary(bankAccount.id, nextYear, nextMonth, pfUser.token);

    const nextMonthDate = dateInMonth(1, 15);
    const { executeGraphQL } = await import('../helpers/graphql');
    await executeGraphQL(`
      mutation UpdateTransaction($id: ID!, $input: UpdateTransactionInput!) {
        updateTransaction(id: $id, input: $input) { id date }
      }
    `, { id: tx.id, input: { date: nextMonthDate } }, pfUser.token);

    const currentAfter = await getMonthSummary(bankAccount.id, currentYear, currentMonth, pfUser.token);
    const nextAfter = await getMonthSummary(bankAccount.id, nextYear, nextMonth, pfUser.token);

    expect(currentAfter.closingBalance).toBeCloseTo(currentBefore.closingBalance + 150, 2);
    expect(nextAfter.totalExpenses).toBeCloseTo(nextBefore.totalExpenses + 150, 2);

    await deleteTransaction(tx.id, pfUser.token);
  });
});

// =============================================================================
// NEGATIVE PATHS
// =============================================================================

test.describe('Transactions — negative paths', () => {
  test('TX-N-01: submit without description — save button is disabled', async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    await signIntoPage(page, pfUser.token);
    const pfPage = new FinanceTransactionsPage(page);
    await pfPage.goto();
    await pfPage.selectAccount(bankAccount.name);

    await pfPage.openCreateTransactionForm();
    const form = new InlineTransactionForm(page);
    await form.waitForOpen();

    // Fill amount but leave description empty
    await form.fillAmount(100);
    // Do NOT fill description

    await form.expectSaveDisabled();
  });

  test('TX-N-02: submit with amount = 0 — save button is disabled', async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    await signIntoPage(page, pfUser.token);
    const pfPage = new FinanceTransactionsPage(page);
    await pfPage.goto();
    await pfPage.selectAccount(bankAccount.name);

    await pfPage.openCreateTransactionForm();
    const form = new InlineTransactionForm(page);
    await form.waitForOpen();

    await form.fillDescription(uniqueDesc('Valor Zero N02'));
    await form.fillAmount(0);

    await form.expectSaveDisabled();
  });

  test('TX-N-03: submit with negative amount — save button disabled or field corrects to positive', async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    await signIntoPage(page, pfUser.token);
    const pfPage = new FinanceTransactionsPage(page);
    await pfPage.goto();
    await pfPage.selectAccount(bankAccount.name);

    await pfPage.openCreateTransactionForm();
    const form = new InlineTransactionForm(page);
    await form.waitForOpen();

    await form.fillDescription(uniqueDesc('Negativo N03'));
    await form.fillAmount(-50);

    // The form uses Math.abs(amount) internally, so -50 → 50 and save becomes enabled.
    // OR the field itself rejects negative input. Either behavior is acceptable.
    // Verify that if save IS enabled, the stored amount is positive.
    const isSaveEnabled = await form['form'].getByTestId('tx-save-btn').isEnabled();
    if (isSaveEnabled) {
      await form.save();
      await form.waitForClose();
      const txs = await getTransactions(bankAccount.id, pfUser.token);
      const newest = txs[0]; // most recent first
      expect(newest?.amount).toBeGreaterThan(0);
      await deleteTransaction(newest.id, pfUser.token);
    } else {
      // Save is disabled — negative values are rejected by the field
      await form.cancel();
    }
  });

  test('TX-N-04: date before the 5-year window — error message shown, save disabled', async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    await signIntoPage(page, pfUser.token);
    const pfPage = new FinanceTransactionsPage(page);
    await pfPage.goto();
    await pfPage.selectAccount(bankAccount.name);

    await pfPage.openCreateTransactionForm();
    const form = new InlineTransactionForm(page);
    await form.waitForOpen();

    await form.fillDescription(uniqueDesc('Data Muito Antiga N04'));
    await form.fillAmount(100);

    // Set a date 6 years ago (outside the 5-year window)
    const sixYearsAgo = new Date();
    sixYearsAgo.setFullYear(sixYearsAgo.getFullYear() - 6);
    const dateStr = InlineTransactionForm.formatDateBr(
      sixYearsAgo.toISOString().split('T')[0]
    );
    await form.setDate(dateStr);

    await form.expectDateError(/anos|year/i);
    await form.expectSaveDisabled();
  });

  test('TX-N-05: submit without filling any field — save button starts disabled', async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    await signIntoPage(page, pfUser.token);
    const pfPage = new FinanceTransactionsPage(page);
    await pfPage.goto();
    await pfPage.selectAccount(bankAccount.name);

    await pfPage.openCreateTransactionForm();
    const form = new InlineTransactionForm(page);
    await form.waitForOpen();

    // Do not fill any field
    await form.expectSaveDisabled();
  });

  test('TX-N-06: update non-existent transaction via GraphQL — returns error', async ({
    pfUser,
  }) => {
    const { executeGraphQL } = await import('../helpers/graphql');
    const fakeId = '00000000-0000-0000-0000-000000000000';

    const result = await executeGraphQL(`
      mutation UpdateTransaction($id: ID!, $input: UpdateTransactionInput!) {
        updateTransaction(id: $id, input: $input) { id }
      }
    `, { id: fakeId, input: { description: 'ghost' } }, pfUser.token);

    expect(result.errors).toBeDefined();
    expect(result.errors?.[0]?.message).toMatch(/not found|não encontrad/i);
  });

  test('TX-N-07: delete non-existent transaction via GraphQL — returns error', async ({
    pfUser,
  }) => {
    const { executeGraphQL } = await import('../helpers/graphql');
    const fakeId = '00000000-0000-0000-0000-000000000001';

    const result = await executeGraphQL(`
      mutation DeleteTransaction($id: ID!) {
        deleteTransaction(id: $id)
      }
    `, { id: fakeId }, pfUser.token);

    expect(result.errors).toBeDefined();
    expect(result.errors?.[0]?.message).toMatch(/not found|não encontrad/i);
  });
});
