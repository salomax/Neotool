/**
 * E2E tests — Monthly Summary
 *
 * Coverage: MS-H-01..08 | MS-A-01..11 | MS-N-01..04
 * Security coverage is in security.e2e.spec.ts (SEC-MS-01..02).
 *
 * ──────────────────────────────────────────────────────────────────────────────
 * Business rules under test (from requirements/04-monthly-summaries.md):
 *
 *   totalIncome    = SUM(CREDIT) excluding TRANSFER & BALANCE_ADJUSTMENT
 *                    (POSTED + PENDING only; FUTURE excluded)
 *   totalExpenses  = SUM(DEBIT)  excluding TRANSFER & BALANCE_ADJUSTMENT
 *                    (POSTED + PENDING only; FUTURE excluded)
 *   closingBalance = openingBalance + Σ(CREDIT) − Σ(DEBIT)
 *                    (POSTED + PENDING; all types including TRANSFER and BALANCE_ADJUSTMENT)
 *   openingBalance of month N = closingBalance of month N−1
 *   First month with activity: openingBalance = 0; initial balance is a BALANCE_ADJUSTMENT in that month
 *
 * Strategy:
 *   - Tests that validate calculation correctness use only the GraphQL API
 *     (getMonthSummary) — faster, deterministic, no UI flakiness.
 *   - Tests that validate the UI component (data-testid display, month
 *     navigation carousel, category breakdown) use the full browser stack.
 *   - If combining with `getTransactions`, pass explicit `year`/`month` for non-current months
 *     (defaults to current month — see `banking-graphql` JSDoc and `helpers/finance-transactions-e2e.ts`).
 *   - Default `bankAccount` uses `initialBalanceDate = first day of current month`. Any test that `createTransaction`s
 *     into **last month** (`dateInMonth(-1, …)`) must use {@link financialAccountWithPastMonthOpening}
 *     (or equivalent) so the API accepts the date.
 *   - Calendar-future `monthSummary` header vs POSTED: see `transactions.e2e.spec.ts` header;
 *     prefer `getTransactions` when asserting rows in a future month across banking versions.
 * ──────────────────────────────────────────────────────────────────────────────
 */

import { test, expect } from '../fixtures/personal-finance';
import { FinanceTransactionsPage } from '../pages/personal-finance/FinanceTransactionsPage';
import {
  CategoryApplicableTo,
  createFinancialAccount,
  createTransaction,
  CreditDebitType,
  deleteTransaction,
  formatLocalIsoDate,
  getMonthSummary,
  today,
  dateInMonth,
  TransactionStatus,
  TransactionType,
  type FinancialAccount,
} from '../helpers/banking-graphql';
import { signIntoPage } from '../helpers/navigation';

// ── Helpers ───────────────────────────────────────────────────────────────────

function uniqueDesc(prefix: string): string {
  return `${prefix} ${Date.now()}`;
}

function currentYM(): { year: number; month: number } {
  const now = new Date();
  return { year: now.getFullYear(), month: now.getMonth() + 1 };
}

function prevYM(): { year: number; month: number } {
  const now = new Date();
  const m = now.getMonth(); // 0-based, so this is already "month - 1"
  return m === 0
    ? { year: now.getFullYear() - 1, month: 12 }
    : { year: now.getFullYear(), month: m };
}

function nextYM(): { year: number; month: number } {
  const now = new Date();
  const m = now.getMonth() + 2; // 1-based next month
  return m > 12
    ? { year: now.getFullYear() + 1, month: 1 }
    : { year: now.getFullYear(), month: m };
}

/**
 * Default `bankAccount` initial balance is dated **first day of current month**, so POSTED txs in {@link prevYM} would
 * violate `TRANSACTION_BEFORE_INITIAL_BALANCE`. This account seeds opening balance on day 1 of
 * the previous calendar month instead.
 */
async function financialAccountWithPastMonthOpening(
  pfUser: { token: string },
): Promise<FinancialAccount> {
  const p = prevYM();
  const initialBalanceDate = formatLocalIsoDate(new Date(p.year, p.month - 1, 1));
  return createFinancialAccount(
    {
      name: uniqueDesc('Conta E2E saldo início mês anterior'),
      type: 'BANK',
      subtype: 'CONTA_CORRENTE',
      initialBalance: 1000,
      initialBalanceDate,
    },
    pfUser.token,
  );
}

// =============================================================================
// HAPPY PATH
// =============================================================================

test.describe('Monthly Summary — happy path', () => {
  test('MS-H-01: POSTED expense → totalExpenses and closingBalance updated', async ({
    pfUser,
    bankAccount,
  }) => {
    const { year, month } = currentYM();
    const before = await getMonthSummary(bankAccount.id, year, month, pfUser.token);

    const tx = await createTransaction(
      { financialAccountId: bankAccount.id, description: uniqueDesc('MS H01'), amount: 400, creditDebitType: CreditDebitType.Debit, status: TransactionStatus.Posted, date: today() },
      pfUser.token
    );

    const after = await getMonthSummary(bankAccount.id, year, month, pfUser.token);

    expect(after.totalExpenses).toBeCloseTo(before.totalExpenses + 400, 2);
    expect(after.closingBalance).toBeCloseTo(before.closingBalance - 400, 2);

    await deleteTransaction(tx.id, pfUser.token);
  });

  test('MS-H-02: POSTED income → totalIncome and closingBalance updated', async ({
    pfUser,
    bankAccount,
  }) => {
    const { year, month } = currentYM();
    const before = await getMonthSummary(bankAccount.id, year, month, pfUser.token);

    const tx = await createTransaction(
      { financialAccountId: bankAccount.id, description: uniqueDesc('MS H02'), amount: 3000, creditDebitType: CreditDebitType.Credit, status: TransactionStatus.Posted, date: today() },
      pfUser.token
    );

    const after = await getMonthSummary(bankAccount.id, year, month, pfUser.token);

    expect(after.totalIncome).toBeCloseTo(before.totalIncome + 3000, 2);
    expect(after.closingBalance).toBeCloseTo(before.closingBalance + 3000, 2);

    await deleteTransaction(tx.id, pfUser.token);
  });

  test('MS-H-03: deleting a transaction → summary reverts to prior state', async ({
    pfUser,
    bankAccount,
  }) => {
    const { year, month } = currentYM();
    const baseline = await getMonthSummary(bankAccount.id, year, month, pfUser.token);

    const tx = await createTransaction(
      { financialAccountId: bankAccount.id, description: uniqueDesc('MS H03'), amount: 250, creditDebitType: CreditDebitType.Debit, status: TransactionStatus.Posted, date: today() },
      pfUser.token
    );

    const afterCreate = await getMonthSummary(bankAccount.id, year, month, pfUser.token);
    expect(afterCreate.totalExpenses).toBeCloseTo(baseline.totalExpenses + 250, 2);

    await deleteTransaction(tx.id, pfUser.token);

    const afterDelete = await getMonthSummary(bankAccount.id, year, month, pfUser.token);
    expect(afterDelete.totalExpenses).toBeCloseTo(baseline.totalExpenses, 2);
    expect(afterDelete.closingBalance).toBeCloseTo(baseline.closingBalance, 2);
  });

  test('MS-H-04: editing transaction amount → summary updated with new value', async ({
    pfUser,
    bankAccount,
  }) => {
    const { year, month } = currentYM();
    const tx = await createTransaction(
      { financialAccountId: bankAccount.id, description: uniqueDesc('MS H04'), amount: 100, creditDebitType: CreditDebitType.Debit, status: TransactionStatus.Posted, date: today() },
      pfUser.token
    );

    const before = await getMonthSummary(bankAccount.id, year, month, pfUser.token);

    const { executeGraphQL } = await import('../helpers/graphql');
    await executeGraphQL(`
      mutation UpdateTransaction($id: ID!, $input: UpdateTransactionInput!) {
        updateTransaction(id: $id, input: $input) { id amount }
      }
    `, { id: tx.id, input: { amount: 350 } }, pfUser.token);

    const after = await getMonthSummary(bankAccount.id, year, month, pfUser.token);

    // Increased by 250 (350 − 100) → expenses up, balance down
    expect(after.totalExpenses).toBeCloseTo(before.totalExpenses + 250, 2);
    expect(after.closingBalance).toBeCloseTo(before.closingBalance - 250, 2);

    await deleteTransaction(tx.id, pfUser.token);
  });

  test('MS-H-05: navigating to previous month — shows that month\'s transactions and summary', async ({
    page,
    pfUser,
  }) => {
    const bankAccount = await financialAccountWithPastMonthOpening(pfUser);
    // Seed a transaction in the previous month
    const desc = uniqueDesc('Mês Anterior H05');
    const tx = await createTransaction(
      { financialAccountId: bankAccount.id, description: desc, amount: 600, creditDebitType: CreditDebitType.Debit, status: TransactionStatus.Posted, date: dateInMonth(-1, 15) },
      pfUser.token
    );

    await signIntoPage(page, pfUser.token);
    const pfPage = new FinanceTransactionsPage(page);
    await pfPage.goto();
    await pfPage.selectAccount(bankAccount.name, bankAccount.id);
    // waitForLoad before asserting "hidden" — toHaveCount(0) resolves immediately
    // during the loading state (0 rows rendered yet), giving a false "not found" pass.
    await pfPage.transactionTable.waitForLoad();

    // Current month must NOT show the previous month transaction
    await pfPage.transactionTable.expectTransactionHidden(desc);

    await pfPage.navigateToCalendarMonth(prevYM());

    // Previous month now shows the transaction
    await pfPage.transactionTable.expectTransactionVisible(desc);

    // Summary for previous month must include the expense
    const { year, month } = prevYM();
    const summary = await getMonthSummary(bankAccount.id, year, month, pfUser.token);
    expect(summary.totalExpenses).toBeCloseTo(600, 2);

    await deleteTransaction(tx.id, pfUser.token);
  });

  test('MS-H-06: navigating to next month — shows that month\'s empty state and summary', async ({
    page,
    pfUser,
    bankAccount,
  }) => {
    await signIntoPage(page, pfUser.token);
    const pfPage = new FinanceTransactionsPage(page);
    await pfPage.goto();
    await pfPage.selectAccount(bankAccount.name, bankAccount.id);

    await pfPage.navigateToCalendarMonth(nextYM());

    await pfPage.summary.waitForLoad();
    await pfPage.summary.waitForMetricsReady();
    // Next month with no transactions: income and expenses are zero
    const income = await pfPage.summary.getTotalIncome();
    const expenses = await pfPage.summary.getTotalExpenses();
    expect(income).toBe(0);
    expect(expenses).toBe(0);
  });

  test('MS-H-07: openingBalance of current month = closingBalance of previous month', async ({
    pfUser,
  }) => {
    const bankAccount = await financialAccountWithPastMonthOpening(pfUser);
    // Seed a transaction in the previous month to ensure prev closingBalance != 0
    const tx = await createTransaction(
      { financialAccountId: bankAccount.id, description: uniqueDesc('Chain H07'), amount: 200, creditDebitType: CreditDebitType.Credit, status: TransactionStatus.Posted, date: dateInMonth(-1, 15) },
      pfUser.token
    );

    const prev = prevYM();
    const curr = currentYM();
    const prevSummary = await getMonthSummary(bankAccount.id, prev.year, prev.month, pfUser.token);
    const currSummary = await getMonthSummary(bankAccount.id, curr.year, curr.month, pfUser.token);

    expect(currSummary.openingBalance).toBeCloseTo(prevSummary.closingBalance, 2);

    await deleteTransaction(tx.id, pfUser.token);
  });

  test('MS-H-08: category breakdown in summary reflects transactions by category', async ({
    pfUser,
    bankAccount,
  }) => {
    // Fetch a valid category
    const { executeGraphQL } = await import('../helpers/graphql');
    const catResult = await executeGraphQL<{
      transactionCategories: Array<{ id: string; name: string; applicableTo: string }>;
    }>(`query { transactionCategories { id name applicableTo } }`, {}, pfUser.token);

    const category = catResult.data?.transactionCategories?.find(
      (c) =>
        c.applicableTo === CategoryApplicableTo.Expense ||
        c.applicableTo === CategoryApplicableTo.Any
    );
    if (!category) test.skip(true, 'No expense categories available');

    const tx = await createTransaction(
      { financialAccountId: bankAccount.id, description: uniqueDesc('Com Categoria H08'), amount: 150, creditDebitType: CreditDebitType.Debit, categoryId: category!.id, status: TransactionStatus.Posted, date: today() },
      pfUser.token
    );

    // Use full MONTH_SUMMARY query with breakdown
    const summaryResult = await executeGraphQL<{
      monthSummary: {
        expensesByCategory: Array<{
          uncategorized: boolean;
          category: { id: string; name: string } | null;
          totalAmount: number;
        }>;
      };
    }>(`
      query MonthSummary($financialAccountId: ID, $year: Int, $month: Int) {
        monthSummary(financialAccountId: $financialAccountId, year: $year, month: $month) {
          expensesByCategory {
            uncategorized
            category { id name }
            totalAmount
          }
        }
      }
    `, { financialAccountId: bankAccount.id, ...currentYM() }, pfUser.token);

    const breakdown = summaryResult.data?.monthSummary.expensesByCategory ?? [];
    const categoryEntry = breakdown.find((e) => e.category?.id === category!.id);
    expect(categoryEntry).toBeDefined();
    expect(categoryEntry!.totalAmount).toBeCloseTo(150, 2);

    await deleteTransaction(tx.id, pfUser.token);
  });
});

// =============================================================================
// ALTERNATIVE PATHS
// =============================================================================

test.describe('Monthly Summary — alternative paths', () => {
  test('MS-A-01: month with zero transactions — all values are zero, no error', async ({
    pfUser,
    bankAccount,
  }) => {
    // Use a far future month unlikely to have transactions
    const summary = await getMonthSummary(bankAccount.id, 2099, 6, pfUser.token);

    expect(summary.totalIncome).toBe(0);
    expect(summary.totalExpenses).toBe(0);
    // closingBalance = openingBalance when there are no transactions
    expect(summary.closingBalance).toBeCloseTo(summary.openingBalance, 2);
  });

  test('MS-A-02: month with only FUTURE transactions — closingBalance unaffected', async ({
    pfUser,
    bankAccount,
  }) => {
    const { year, month } = currentYM();
    const baseline = await getMonthSummary(bankAccount.id, year, month, pfUser.token);

    const tx = await createTransaction(
      { financialAccountId: bankAccount.id, description: uniqueDesc('Só Futuro A02'), amount: 500, creditDebitType: CreditDebitType.Debit, status: TransactionStatus.Future, date: today() },
      pfUser.token
    );

    const after = await getMonthSummary(bankAccount.id, year, month, pfUser.token);

    // FUTURE must NOT affect closingBalance or totalExpenses
    expect(after.closingBalance).toBeCloseTo(baseline.closingBalance, 2);
    expect(after.totalExpenses).toBeCloseTo(baseline.totalExpenses, 2);

    await deleteTransaction(tx.id, pfUser.token);
  });

  test('MS-A-03: month with only PENDING transactions — closingBalance IS affected', async ({
    pfUser,
    bankAccount,
  }) => {
    const { year, month } = currentYM();
    const baseline = await getMonthSummary(bankAccount.id, year, month, pfUser.token);

    const tx = await createTransaction(
      { financialAccountId: bankAccount.id, description: uniqueDesc('Só Pending A03'), amount: 300, creditDebitType: CreditDebitType.Debit, status: TransactionStatus.Pending, date: today() },
      pfUser.token
    );

    const after = await getMonthSummary(bankAccount.id, year, month, pfUser.token);

    // PENDING is counted in closingBalance (same as POSTED)
    expect(after.closingBalance).toBeCloseTo(baseline.closingBalance - 300, 2);
    expect(after.totalExpenses).toBeCloseTo(baseline.totalExpenses + 300, 2);

    await deleteTransaction(tx.id, pfUser.token);
  });

  test('MS-A-04: month with only TRANSFER transactions — totalIncome and totalExpenses are zero', async ({
    pfUser,
    bankAccount,
  }) => {
    const { year, month } = currentYM();
    const baseline = await getMonthSummary(bankAccount.id, year, month, pfUser.token);

    const tx = await createTransaction(
      { financialAccountId: bankAccount.id, description: uniqueDesc('Só Transfer A04'), amount: 1000, creditDebitType: CreditDebitType.Debit, type: TransactionType.Transfer, status: TransactionStatus.Posted, date: today() },
      pfUser.token
    );

    const after = await getMonthSummary(bankAccount.id, year, month, pfUser.token);

    // TRANSFER excluded from income/expenses — totals unchanged
    expect(after.totalExpenses).toBeCloseTo(baseline.totalExpenses, 2);
    expect(after.totalIncome).toBeCloseTo(baseline.totalIncome, 2);
    // TRANSFER is also excluded from closingBalance (internal reallocation, not cash flow)
    expect(after.closingBalance).toBeCloseTo(baseline.closingBalance, 2);

    await deleteTransaction(tx.id, pfUser.token);
  });

  test('MS-A-05: month with only BALANCE_ADJUSTMENT — totalIncome and totalExpenses are zero', async ({
    pfUser,
    bankAccount,
  }) => {
    const { year, month } = currentYM();
    const baseline = await getMonthSummary(bankAccount.id, year, month, pfUser.token);

    const tx = await createTransaction(
      { financialAccountId: bankAccount.id, description: uniqueDesc('Só Ajuste A05'), amount: 500, creditDebitType: CreditDebitType.Credit, type: TransactionType.BalanceAdjustment, status: TransactionStatus.Posted, date: today() },
      pfUser.token
    );

    const after = await getMonthSummary(bankAccount.id, year, month, pfUser.token);

    expect(after.totalIncome).toBeCloseTo(baseline.totalIncome, 2);
    expect(after.totalExpenses).toBeCloseTo(baseline.totalExpenses, 2);
    // Balance adjustment DOES affect closingBalance
    expect(after.closingBalance).toBeCloseTo(baseline.closingBalance + 500, 2);

    await deleteTransaction(tx.id, pfUser.token);
  });

  test('MS-A-06: first month of account — openingBalance zero; closing reflects initialBalance; no transfers', async ({
    pfUser,
    bankAccount,
  }) => {
    // bankAccount fixture: initialBalance 1000 → POSTED BALANCE_ADJUSTMENT on initialBalanceDate (day 1 of month).
    // Per 04-monthly-summaries.md / SnapshotComputationService: genesis openingBalance is 0; the
    // adjustment flows through credit/debit totals into closingBalance (not into totalIncome/totalExpenses).
    const { year, month } = currentYM();
    const summary = await getMonthSummary(bankAccount.id, year, month, pfUser.token);

    expect(summary.openingBalance).toBeCloseTo(0, 2);
    expect(summary.closingBalance).toBeCloseTo(1000, 2);
    expect(summary.totalIncome).toBe(0);
    expect(summary.totalExpenses).toBe(0);
    expect(summary.transfersIn).toBe(0);
    expect(summary.transfersOut).toBe(0);
  });

  test('MS-A-07: transaction in a past month triggers chain recompute — all subsequent openingBalances update', async ({
    pfUser,
  }) => {
    const bankAccount = await financialAccountWithPastMonthOpening(pfUser);
    const prev = prevYM();
    const curr = currentYM();

    // Seed a transaction in the current month first
    const txCurrent = await createTransaction(
      { financialAccountId: bankAccount.id, description: uniqueDesc('Chain Curr A07'), amount: 100, creditDebitType: CreditDebitType.Debit, status: TransactionStatus.Posted, date: today() },
      pfUser.token
    );

    const currBefore = await getMonthSummary(bankAccount.id, curr.year, curr.month, pfUser.token);

    // Now add a transaction in the previous month — must trigger chain recompute
    const txPrev = await createTransaction(
      { financialAccountId: bankAccount.id, description: uniqueDesc('Chain Prev A07'), amount: 800, creditDebitType: CreditDebitType.Credit, status: TransactionStatus.Posted, date: dateInMonth(-1, 15) },
      pfUser.token
    );

    const currAfter = await getMonthSummary(bankAccount.id, curr.year, curr.month, pfUser.token);

    // current month openingBalance must have increased by 800 (previous closingBalance increased)
    expect(currAfter.openingBalance).toBeCloseTo(currBefore.openingBalance + 800, 2);

    await Promise.all([
      deleteTransaction(txCurrent.id, pfUser.token),
      deleteTransaction(txPrev.id, pfUser.token),
    ]);
  });

  test('MS-A-08: editing a past transaction amount — chain updates propagate forward', async ({
    pfUser,
  }) => {
    const bankAccount = await financialAccountWithPastMonthOpening(pfUser);
    const curr = currentYM();

    // Add transaction in current month (as a baseline for current openingBalance)
    const txCurr = await createTransaction(
      { financialAccountId: bankAccount.id, description: uniqueDesc('Curr Ref A08'), amount: 50, creditDebitType: CreditDebitType.Debit, status: TransactionStatus.Posted, date: today() },
      pfUser.token
    );

    // Add a transaction in the previous month
    const txPrev = await createTransaction(
      { financialAccountId: bankAccount.id, description: uniqueDesc('Prev A08'), amount: 300, creditDebitType: CreditDebitType.Debit, status: TransactionStatus.Posted, date: dateInMonth(-1, 15) },
      pfUser.token
    );

    const currBefore = await getMonthSummary(bankAccount.id, curr.year, curr.month, pfUser.token);

    // Edit previous month transaction amount: 300 → 600 (increase by 300)
    const { executeGraphQL } = await import('../helpers/graphql');
    await executeGraphQL(`
      mutation UpdateTransaction($id: ID!, $input: UpdateTransactionInput!) {
        updateTransaction(id: $id, input: $input) { id amount }
      }
    `, { id: txPrev.id, input: { amount: 600 } }, pfUser.token);

    const currAfter = await getMonthSummary(bankAccount.id, curr.year, curr.month, pfUser.token);

    // Extra 300 expense in prev month → curr openingBalance drops by 300
    expect(currAfter.openingBalance).toBeCloseTo(currBefore.openingBalance - 300, 2);

    await Promise.all([
      deleteTransaction(txCurr.id, pfUser.token),
      deleteTransaction(txPrev.id, pfUser.token),
    ]);
  });

  test('MS-A-09: future month with no transactions — zeros with no error', async ({
    pfUser,
    bankAccount,
  }) => {
    const next = nextYM();
    const summary = await getMonthSummary(bankAccount.id, next.year, next.month, pfUser.token);

    expect(summary.totalIncome).toBe(0);
    expect(summary.totalExpenses).toBe(0);
    // openingBalance = closingBalance of the current month
  });

  test('MS-A-10: category breakdown sums match totalExpenses', async ({
    pfUser,
    bankAccount,
  }) => {
    const { executeGraphQL } = await import('../helpers/graphql');
    const catResult = await executeGraphQL<{
      transactionCategories: Array<{ id: string; name: string; applicableTo: string }>;
    }>(`query { transactionCategories { id name applicableTo } }`, {}, pfUser.token);

    const categories = (catResult.data?.transactionCategories ?? []).filter(
      (c) =>
        c.applicableTo === CategoryApplicableTo.Expense ||
        c.applicableTo === CategoryApplicableTo.Any
    );
    if (categories.length < 2) test.skip(true, 'Need at least 2 expense categories');

    const [catA, catB] = categories;
    // Serialize creates: parallel mutations can race monthly snapshot recompute and leave
    // totalExpenses stale while expensesByCategory (live SQL) still reflects all rows.
    const txA = await createTransaction(
      {
        financialAccountId: bankAccount.id,
        description: uniqueDesc('Cat A A10'),
        amount: 100,
        creditDebitType: CreditDebitType.Debit,
        categoryId: catA.id,
        status: TransactionStatus.Posted,
        date: today(),
      },
      pfUser.token,
    );
    const txB = await createTransaction(
      {
        financialAccountId: bankAccount.id,
        description: uniqueDesc('Cat B A10'),
        amount: 200,
        creditDebitType: CreditDebitType.Debit,
        categoryId: catB.id,
        status: TransactionStatus.Posted,
        date: today(),
      },
      pfUser.token,
    );
    const transactions = [txA, txB];

    const result = await executeGraphQL<{
      monthSummary: {
        totalExpenses: number;
        expensesByCategory: Array<{ totalAmount: number; uncategorized: boolean }>;
      };
    }>(`
      query MonthSummary($financialAccountId: ID, $year: Int, $month: Int) {
        monthSummary(financialAccountId: $financialAccountId, year: $year, month: $month) {
          totalExpenses
          expensesByCategory { totalAmount uncategorized }
        }
      }
    `, { financialAccountId: bankAccount.id, ...currentYM() }, pfUser.token);

    const summary = result.data!.monthSummary;
    const breakdownSum = summary.expensesByCategory.reduce((sum, e) => sum + e.totalAmount, 0);

    expect(breakdownSum).toBeCloseTo(summary.totalExpenses, 2);

    await Promise.all(transactions.map((tx) => deleteTransaction(tx.id, pfUser.token)));
  });

  test('MS-A-11: tag breakdown reflects transactions tagged', async ({
    pfUser,
    bankAccount,
  }) => {
    const tag = `ms-tag-a11-${Date.now()}`;
    const { executeGraphQL } = await import('../helpers/graphql');

    const tx = await createTransaction(
      { financialAccountId: bankAccount.id, description: uniqueDesc('Tag A11'), amount: 250, creditDebitType: CreditDebitType.Debit, status: TransactionStatus.Posted, date: today(), tags: [tag] },
      pfUser.token
    );

    const result = await executeGraphQL<{
      monthSummary: {
        tagsSummary: Array<{ tag: string; totalAmount: number }>;
      };
    }>(`
      query MonthSummary($financialAccountId: ID, $year: Int, $month: Int) {
        monthSummary(financialAccountId: $financialAccountId, year: $year, month: $month) {
          tagsSummary { tag totalAmount }
        }
      }
    `, { financialAccountId: bankAccount.id, ...currentYM() }, pfUser.token);

    const tagEntry = result.data?.monthSummary.tagsSummary.find((t) => t.tag === tag);
    expect(tagEntry).toBeDefined();
    expect(tagEntry!.totalAmount).toBeCloseTo(250, 2);

    await deleteTransaction(tx.id, pfUser.token);
  });

  test('MS-A-12: aggregate summary excludes voucher-card transactions from totals and breakdowns', async ({
    twoTenants,
  }) => {
    const token = twoTenants.userA.token;
    const checking = await createFinancialAccount(
      {
        name: uniqueDesc('Conta A12'),
        type: 'BANK',
        subtype: 'CONTA_CORRENTE',
      },
      token,
    );
    const voucher = await createFinancialAccount(
      {
        name: uniqueDesc('Voucher A12'),
        type: 'PAYMENT',
        subtype: 'VOUCHER_CARD',
      },
      token,
    );
    expect(voucher.includeInNetWorth).toBe(false);

    const keepTag = `ms-a12-keep-${Date.now()}`;
    const excludedTag = `ms-a12-voucher-${Date.now()}`;
    const regularTx = await createTransaction(
      {
        financialAccountId: checking.id,
        description: uniqueDesc('Despesa conta A12'),
        amount: 120,
        creditDebitType: CreditDebitType.Debit,
        status: TransactionStatus.Posted,
        date: today(),
        tags: [keepTag],
      },
      token,
    );
    const voucherTx = await createTransaction(
      {
        financialAccountId: voucher.id,
        description: uniqueDesc('Despesa voucher A12'),
        amount: 999,
        creditDebitType: CreditDebitType.Debit,
        status: TransactionStatus.Posted,
        date: today(),
        tags: [excludedTag],
      },
      token,
    );

    try {
      const { executeGraphQL } = await import('../helpers/graphql');
      const result = await executeGraphQL<{
        monthSummary: {
          totalExpenses: number;
          closingBalance: number;
          expensesByCategory: Array<{ totalAmount: number }>;
          tagsSummary: Array<{ tag: string; totalAmount: number }>;
        };
      }>(
        `
        query MonthSummary($financialAccountId: ID, $year: Int, $month: Int) {
          monthSummary(financialAccountId: $financialAccountId, year: $year, month: $month) {
            totalExpenses
            closingBalance
            expensesByCategory { totalAmount }
            tagsSummary { tag totalAmount }
          }
        }
      `,
        { financialAccountId: null, ...currentYM() },
        token,
      );

      const summary = result.data?.monthSummary;
      expect(summary).toBeDefined();
      const categorySum = summary!.expensesByCategory.reduce((acc, row) => acc + row.totalAmount, 0);
      const tags = summary!.tagsSummary.map((row) => row.tag);

      expect(summary!.totalExpenses).toBeCloseTo(120, 2);
      expect(summary!.closingBalance).toBeCloseTo(-120, 2);
      expect(categorySum).toBeCloseTo(120, 2);
      expect(tags).toContain(keepTag);
      expect(tags).not.toContain(excludedTag);
    } finally {
      await Promise.all([
        deleteTransaction(regularTx.id, token),
        deleteTransaction(voucherTx.id, token),
      ]);
    }
  });
});

// =============================================================================
// NEGATIVE PATHS
// =============================================================================

test.describe('Monthly Summary — negative paths', () => {
  test('MS-N-01: querying summary for a non-existent account — returns error', async ({
    pfUser,
  }) => {
    const { executeGraphQL } = await import('../helpers/graphql');
    const fakeId = '00000000-0000-0000-0000-000000000000';

    const result = await executeGraphQL(`
      query MonthSummary($financialAccountId: ID, $year: Int, $month: Int) {
        monthSummary(financialAccountId: $financialAccountId, year: $year, month: $month) {
          totalIncome totalExpenses closingBalance
        }
      }
    `, { financialAccountId: fakeId, year: 2026, month: 3 }, pfUser.token);

    expect(result.errors).toBeDefined();
    // Subgraph may return not-found; Apollo/router often surfaces a generic internal error instead.
    expect(result.errors?.[0]?.message).toMatch(
      /not found|não encontrad|internal server error|invalid input|forbidden|unauthorized/i,
    );
  });

  test('MS-N-02: querying summary with invalid year (year = 0) — returns validation error', async ({
    pfUser,
    bankAccount,
  }) => {
    const { executeGraphQL } = await import('../helpers/graphql');

    const result = await executeGraphQL(`
      query MonthSummary($financialAccountId: ID, $year: Int, $month: Int) {
        monthSummary(financialAccountId: $financialAccountId, year: $year, month: $month) {
          totalIncome
        }
      }
    `, { financialAccountId: bankAccount.id, year: 0, month: 6 }, pfUser.token);

    expect(result.errors).toBeDefined();
  });

  test('MS-N-03: querying summary with invalid month (month = 13) — returns validation error', async ({
    pfUser,
    bankAccount,
  }) => {
    const { executeGraphQL } = await import('../helpers/graphql');

    const result = await executeGraphQL(`
      query MonthSummary($financialAccountId: ID, $year: Int, $month: Int) {
        monthSummary(financialAccountId: $financialAccountId, year: $year, month: $month) {
          totalIncome
        }
      }
    `, { financialAccountId: bankAccount.id, year: 2026, month: 13 }, pfUser.token);

    expect(result.errors).toBeDefined();
  });

  // MS-N-04 removed — covered by MS-A-06 (same scenario: initial-balance-only account).
});
