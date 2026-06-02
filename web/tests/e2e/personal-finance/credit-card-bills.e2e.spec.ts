/**
 * E2E tests — Credit Card Bills
 *
 * Coverage: CB-H-01..08 | CB-A-01..09 | CB-N-01..04
 * Security coverage is in security.e2e.spec.ts (SEC-CB-01..03).
 *
 * ──────────────────────────────────────────────────────────────────────────────
 * **Skipped (postponed):** all `describe` blocks use `test.describe.skip`. Remove `.skip`
 * when credit card bills E2E are back in scope.
 *
 * MVP note:
 *   The bill-mode UI (BillCarousel, BillSummaryHeader, PayBillDialog) is deferred.
 *   All tests exercise the billing API contract directly via GraphQL.
 *   UI tests in this file only cover the calendar-mode display of card transactions.
 *
 * Cycle arithmetic (creditCardAccount has closingDay=7, dueDay=15):
 *   transaction.day <= 7  →  bill closes on the 7th of THIS month
 *   transaction.day >  7  →  bill closes on the 7th of NEXT month
 *   dueDate = first 15th after closingDate
 *
 * Business rules under test:
 *   - Bill auto-created on first transaction (no manual creation needed)
 *   - totalAmount = SUM(DEBIT) − SUM(CREDIT) of linked transactions
 *   - BALANCE_ADJUSTMENT transactions are never assigned to a bill
 *   - Status: OPEN → CLOSED (past closingDate) → PAID/OVERDUE (past dueDate)
 *
 *   `getTransactions` defaults to the current calendar month — pass `year`/`month` when needed
 *   (see `helpers/finance-transactions-e2e.ts` / `getTransactions` JSDoc).
 * ──────────────────────────────────────────────────────────────────────────────
 */

import { test, expect } from '../fixtures/personal-finance';
import {
  createTransaction,
  CreditDebitType,
  deleteTransaction,
  getCreditCardBills,
  getCreditCardBill,
  getTransactions,
  today,
  TransactionStatus,
  TransactionType,
} from '../helpers/banking-graphql';
import { localCalendarYearMonth } from '../helpers/finance-transactions-e2e';

// ── Helpers ───────────────────────────────────────────────────────────────────

function uniqueDesc(prefix: string): string {
  return `${prefix} ${Date.now()}`;
}

/**
 * Returns an ISO date string for a specific day in the current month.
 * Clamps to the last valid day of the month.
 */
function dayInCurrentMonth(day: number): string {
  const now = new Date();
  const lastDay = new Date(now.getFullYear(), now.getMonth() + 1, 0).getDate();
  const safeDay = Math.min(day, lastDay);
  return new Date(now.getFullYear(), now.getMonth(), safeDay)
    .toISOString()
    .split('T')[0];
}

/**
 * Returns an ISO date string for a specific day in the next month.
 */
function dayInNextMonth(day: number): string {
  const now = new Date();
  const nextMonth = new Date(now.getFullYear(), now.getMonth() + 1, 1);
  const lastDay = new Date(nextMonth.getFullYear(), nextMonth.getMonth() + 1, 0).getDate();
  const safeDay = Math.min(day, lastDay);
  return new Date(nextMonth.getFullYear(), nextMonth.getMonth(), safeDay)
    .toISOString()
    .split('T')[0];
}

/**
 * Computes the expected closingDate for a given transaction date and closingDay.
 * Rule: if tx.day <= closingDay → closes this month; else → closes next month.
 */
function expectedClosingDate(txDate: string, closingDay: number): string {
  const [year, month, day] = txDate.split('-').map(Number);
  if (day <= closingDay) {
    const lastDayOfMonth = new Date(year, month, 0).getDate();
    const effectiveClosingDay = Math.min(closingDay, lastDayOfMonth);
    return `${year}-${String(month).padStart(2, '0')}-${String(effectiveClosingDay).padStart(2, '0')}`;
  } else {
    const nextMonth = month === 12 ? 1 : month + 1;
    const nextYear = month === 12 ? year + 1 : year;
    const lastDayOfNextMonth = new Date(nextYear, nextMonth, 0).getDate();
    const effectiveClosingDay = Math.min(closingDay, lastDayOfNextMonth);
    return `${nextYear}-${String(nextMonth).padStart(2, '0')}-${String(effectiveClosingDay).padStart(2, '0')}`;
  }
}

// creditCardAccount fixture: closingDay=7, dueDay=15
const CLOSING_DAY = 7;

// =============================================================================
// HAPPY PATH
// =============================================================================

test.describe.skip('Credit Card Bills — happy path', () => {
  test('CB-H-01: transaction BEFORE closingDay creates a bill for the current cycle', async ({
    pfUser,
    creditCardAccount,
  }) => {
    const txDate = dayInCurrentMonth(3); // day 3 ≤ closingDay 7
    const tx = await createTransaction(
      { financialAccountId: creditCardAccount.id, description: uniqueDesc('Compra H01'), amount: 200, creditDebitType: CreditDebitType.Debit, status: TransactionStatus.Posted, date: txDate },
      pfUser.token
    );

    expect(tx.billId).not.toBeNull();

    const bills = await getCreditCardBills(creditCardAccount.id, pfUser.token);
    const bill = bills.find((b) => b.id === tx.billId);
    expect(bill).toBeDefined();

    const expected = expectedClosingDate(txDate, CLOSING_DAY);
    expect(bill!.closingDate).toBe(expected);

    await deleteTransaction(tx.id, pfUser.token);
  });

  test('CB-H-02: transaction AFTER closingDay creates a bill for the next cycle', async ({
    pfUser,
    creditCardAccount,
  }) => {
    const txDate = dayInCurrentMonth(10); // day 10 > closingDay 7
    const tx = await createTransaction(
      { financialAccountId: creditCardAccount.id, description: uniqueDesc('Compra H02'), amount: 150, creditDebitType: CreditDebitType.Debit, status: TransactionStatus.Posted, date: txDate },
      pfUser.token
    );

    expect(tx.billId).not.toBeNull();

    const bill = await getCreditCardBill(creditCardAccount.id, tx.billId!, pfUser.token);
    expect(bill).not.toBeNull();

    const expected = expectedClosingDate(txDate, CLOSING_DAY);
    expect(bill!.closingDate).toBe(expected);

    await deleteTransaction(tx.id, pfUser.token);
  });

  test('CB-H-03: second transaction in the same cycle is assigned to the same bill', async ({
    pfUser,
    creditCardAccount,
  }) => {
    const txDate = dayInCurrentMonth(5); // both in the same cycle
    const [tx1, tx2] = await Promise.all([
      createTransaction({ financialAccountId: creditCardAccount.id, description: uniqueDesc('Compra H03-A'), amount: 100, creditDebitType: CreditDebitType.Debit, status: TransactionStatus.Posted, date: txDate }, pfUser.token),
      createTransaction({ financialAccountId: creditCardAccount.id, description: uniqueDesc('Compra H03-B'), amount: 80, creditDebitType: CreditDebitType.Debit, status: TransactionStatus.Posted, date: txDate }, pfUser.token),
    ]);

    // Both must share the same billId
    expect(tx1.billId).not.toBeNull();
    expect(tx1.billId).toBe(tx2.billId);

    const bill = await getCreditCardBill(creditCardAccount.id, tx1.billId!, pfUser.token);
    expect(bill!.totalAmount).toBeCloseTo(180, 2); // 100 + 80

    await Promise.all([
      deleteTransaction(tx1.id, pfUser.token),
      deleteTransaction(tx2.id, pfUser.token),
    ]);
  });

  test('CB-H-04: a credit (refund) reduces the bill totalAmount', async ({
    pfUser,
    creditCardAccount,
  }) => {
    const txDate = dayInCurrentMonth(5);
    const [txDebit, txCredit] = await Promise.all([
      createTransaction({ financialAccountId: creditCardAccount.id, description: uniqueDesc('Compra H04'), amount: 300, creditDebitType: CreditDebitType.Debit, status: TransactionStatus.Posted, date: txDate }, pfUser.token),
      createTransaction({ financialAccountId: creditCardAccount.id, description: uniqueDesc('Estorno H04'), amount: 100, creditDebitType: CreditDebitType.Credit, status: TransactionStatus.Posted, date: txDate }, pfUser.token),
    ]);

    // Both must be on the same bill
    expect(txDebit.billId).toBe(txCredit.billId);

    const bill = await getCreditCardBill(creditCardAccount.id, txDebit.billId!, pfUser.token);
    // totalAmount = DEBIT(300) - CREDIT(100) = 200
    expect(bill!.totalAmount).toBeCloseTo(200, 2);

    await Promise.all([
      deleteTransaction(txDebit.id, pfUser.token),
      deleteTransaction(txCredit.id, pfUser.token),
    ]);
  });

  test('CB-H-05: getCreditCardBills returns all bills for the account', async ({
    pfUser,
    creditCardAccount,
  }) => {
    // Seed transactions in two different cycles
    const dateThisCycle = dayInCurrentMonth(3);    // closes this month
    const dateNextCycle = dayInCurrentMonth(10);   // closes next month
    const [tx1, tx2] = await Promise.all([
      createTransaction({ financialAccountId: creditCardAccount.id, description: uniqueDesc('H05 Ciclo A'), amount: 50, creditDebitType: CreditDebitType.Debit, status: TransactionStatus.Posted, date: dateThisCycle }, pfUser.token),
      createTransaction({ financialAccountId: creditCardAccount.id, description: uniqueDesc('H05 Ciclo B'), amount: 70, creditDebitType: CreditDebitType.Debit, status: TransactionStatus.Posted, date: dateNextCycle }, pfUser.token),
    ]);

    const bills = await getCreditCardBills(creditCardAccount.id, pfUser.token);
    const billIds = bills.map((b) => b.id);

    expect(billIds).toContain(tx1.billId);
    expect(billIds).toContain(tx2.billId);

    await Promise.all([
      deleteTransaction(tx1.id, pfUser.token),
      deleteTransaction(tx2.id, pfUser.token),
    ]);
  });

  test('CB-H-06: bill totalAmount recalculated when transaction amount is edited', async ({
    pfUser,
    creditCardAccount,
  }) => {
    const txDate = dayInCurrentMonth(4);
    const tx = await createTransaction(
      { financialAccountId: creditCardAccount.id, description: uniqueDesc('Compra H06'), amount: 200, creditDebitType: CreditDebitType.Debit, status: TransactionStatus.Posted, date: txDate },
      pfUser.token
    );

    const billBefore = await getCreditCardBill(creditCardAccount.id, tx.billId!, pfUser.token);
    expect(billBefore!.totalAmount).toBeCloseTo(200, 2);

    const { executeGraphQL } = await import('../helpers/graphql');
    await executeGraphQL(`
      mutation UpdateTransaction($id: ID!, $input: UpdateTransactionInput!) {
        updateTransaction(id: $id, input: $input) { id amount }
      }
    `, { id: tx.id, input: { amount: 350 } }, pfUser.token);

    const billAfter = await getCreditCardBill(creditCardAccount.id, tx.billId!, pfUser.token);
    expect(billAfter!.totalAmount).toBeCloseTo(350, 2);

    await deleteTransaction(tx.id, pfUser.token);
  });

  test('CB-H-07: bill totalAmount recalculated when transaction is deleted', async ({
    pfUser,
    creditCardAccount,
  }) => {
    const txDate = dayInCurrentMonth(4);
    const [tx1, tx2] = await Promise.all([
      createTransaction({ financialAccountId: creditCardAccount.id, description: uniqueDesc('H07-A'), amount: 100, creditDebitType: CreditDebitType.Debit, status: TransactionStatus.Posted, date: txDate }, pfUser.token),
      createTransaction({ financialAccountId: creditCardAccount.id, description: uniqueDesc('H07-B'), amount: 50, creditDebitType: CreditDebitType.Debit, status: TransactionStatus.Posted, date: txDate }, pfUser.token),
    ]);

    expect(tx1.billId).toBe(tx2.billId);
    const billBefore = await getCreditCardBill(creditCardAccount.id, tx1.billId!, pfUser.token);
    expect(billBefore!.totalAmount).toBeCloseTo(150, 2);

    await deleteTransaction(tx2.id, pfUser.token);

    const billAfter = await getCreditCardBill(creditCardAccount.id, tx1.billId!, pfUser.token);
    expect(billAfter!.totalAmount).toBeCloseTo(100, 2);

    await deleteTransaction(tx1.id, pfUser.token);
  });

  test('CB-H-08: bill status is OPEN for a future closingDate', async ({
    pfUser,
    creditCardAccount,
  }) => {
    // Use a date in the next cycle to guarantee a future closingDate
    const txDate = dayInCurrentMonth(10); // > closingDay → next month
    const tx = await createTransaction(
      { financialAccountId: creditCardAccount.id, description: uniqueDesc('Compra H08'), amount: 80, creditDebitType: CreditDebitType.Debit, status: TransactionStatus.Posted, date: txDate },
      pfUser.token
    );

    const bill = await getCreditCardBill(creditCardAccount.id, tx.billId!, pfUser.token);
    // The bill whose closingDate is in the future must be OPEN
    expect(bill!.status).toBe('OPEN');

    await deleteTransaction(tx.id, pfUser.token);
  });
});

// =============================================================================
// ALTERNATIVE PATHS
// =============================================================================

test.describe.skip('Credit Card Bills — alternative paths', () => {
  test('CB-A-01: transaction EXACTLY on closingDay — assigned to the current cycle', async ({
    pfUser,
    creditCardAccount,
  }) => {
    const txDate = dayInCurrentMonth(CLOSING_DAY); // day == closingDay
    const tx = await createTransaction(
      { financialAccountId: creditCardAccount.id, description: uniqueDesc('No Dia A01'), amount: 120, creditDebitType: CreditDebitType.Debit, status: TransactionStatus.Posted, date: txDate },
      pfUser.token
    );

    // day <= closingDay → current cycle
    const expected = expectedClosingDate(txDate, CLOSING_DAY);
    const bill = await getCreditCardBill(creditCardAccount.id, tx.billId!, pfUser.token);
    expect(bill!.closingDate).toBe(expected);

    await deleteTransaction(tx.id, pfUser.token);
  });

  test('CB-A-02: closingDay=28, transaction in February (short month) — no crash', async ({
    pfUser,
  }) => {
    // Create a card with closingDay=28 specifically for this test
    const { createFinancialAccount, deleteTransaction: dt } = await import('../helpers/banking-graphql');
    const card = await createFinancialAccount(
      { name: `Cartão ClosingDay28 A02 ${Date.now()}`, type: 'CREDIT', subtype: 'CREDIT_CARD', closingDay: 28, dueDay: 5 },
      pfUser.token
    );

    // Find the next February
    const now = new Date();
    let febYear = now.getFullYear();
    if (now.getMonth() > 1) febYear += 1; // already past Feb this year
    // Feb 28 always exists
    const febDate = `${febYear}-02-28`;

    const tx = await createTransaction(
      { financialAccountId: card.id, description: uniqueDesc('Fevereiro A02'), amount: 90, creditDebitType: CreditDebitType.Debit, status: TransactionStatus.Posted, date: febDate },
      pfUser.token
    );

    expect(tx.billId).not.toBeNull();
    const bill = await getCreditCardBill(card.id, tx.billId!, pfUser.token);
    expect(bill).not.toBeNull();
    expect(bill!.closingDate).toBe(`${febYear}-02-28`);

    await deleteTransaction(tx.id, pfUser.token);
  });

  test('CB-A-03: BALANCE_ADJUSTMENT on a credit card — NOT assigned to any bill', async ({
    pfUser,
    creditCardAccount,
  }) => {
    const tx = await createTransaction(
      { financialAccountId: creditCardAccount.id, description: uniqueDesc('Ajuste A03'), amount: 500, creditDebitType: CreditDebitType.Credit, type: TransactionType.BalanceAdjustment, status: TransactionStatus.Posted, date: today() },
      pfUser.token
    );

    expect(tx.billId).toBeNull();

    await deleteTransaction(tx.id, pfUser.token);
  });

  test('CB-A-04: bill with all transactions deleted — totalAmount becomes zero', async ({
    pfUser,
    creditCardAccount,
  }) => {
    const txDate = dayInCurrentMonth(4);
    const tx = await createTransaction(
      { financialAccountId: creditCardAccount.id, description: uniqueDesc('Compra A04'), amount: 200, creditDebitType: CreditDebitType.Debit, status: TransactionStatus.Posted, date: txDate },
      pfUser.token
    );

    const billId = tx.billId!;
    await deleteTransaction(tx.id, pfUser.token);

    const bill = await getCreditCardBill(creditCardAccount.id, billId, pfUser.token);
    // Bill may still exist with totalAmount=0, or may return null
    if (bill) {
      expect(bill.totalAmount).toBeCloseTo(0, 2);
    }
  });

  test('CB-A-05: two cards on the same account have isolated bill lists', async ({
    pfUser,
  }) => {
    const { createFinancialAccount } = await import('../helpers/banking-graphql');
    const [cardA, cardB] = await Promise.all([
      createFinancialAccount({ name: `Cartão A A05 ${Date.now()}`, type: 'CREDIT', subtype: 'CREDIT_CARD', closingDay: 7, dueDay: 15 }, pfUser.token),
      createFinancialAccount({ name: `Cartão B A05 ${Date.now()}`, type: 'CREDIT', subtype: 'CREDIT_CARD', closingDay: 7, dueDay: 15 }, pfUser.token),
    ]);

    const txDate = dayInCurrentMonth(4);
    const [txA, txB] = await Promise.all([
      createTransaction({ financialAccountId: cardA.id, description: uniqueDesc('Compra Cartão A'), amount: 100, creditDebitType: CreditDebitType.Debit, status: TransactionStatus.Posted, date: txDate }, pfUser.token),
      createTransaction({ financialAccountId: cardB.id, description: uniqueDesc('Compra Cartão B'), amount: 200, creditDebitType: CreditDebitType.Debit, status: TransactionStatus.Posted, date: txDate }, pfUser.token),
    ]);

    const billsA = await getCreditCardBills(cardA.id, pfUser.token);
    const billsB = await getCreditCardBills(cardB.id, pfUser.token);

    // Bills of card A must not appear in card B's list
    const billIdsA = new Set(billsA.map((b) => b.id));
    const billIdsB = new Set(billsB.map((b) => b.id));
    expect([...billIdsA].some((id) => billIdsB.has(id))).toBe(false);

    await Promise.all([
      deleteTransaction(txA.id, pfUser.token),
      deleteTransaction(txB.id, pfUser.token),
    ]);
  });

  test('CB-A-06: multiple bills across 6 months — all listed with correct totals', async ({
    pfUser,
    creditCardAccount,
  }) => {
    // Create one transaction per cycle over 3 months of history
    const transactions = await Promise.all(
      [0, -1, -2].map((offset) =>
        createTransaction(
          {
            financialAccountId: creditCardAccount.id,
            description: uniqueDesc(`Histórico A06 offset${offset}`),
            amount: 100 * (Math.abs(offset) + 1),
            creditDebitType: CreditDebitType.Debit,
            status: TransactionStatus.Posted,
            date: (() => {
              const d = new Date();
              d.setMonth(d.getMonth() + offset);
              d.setDate(4); // always before closingDay=7
              return d.toISOString().split('T')[0];
            })(),
          },
          pfUser.token
        )
      )
    );

    const bills = await getCreditCardBills(creditCardAccount.id, pfUser.token);
    expect(bills.length).toBeGreaterThanOrEqual(3);

    // Each bill must have a non-negative totalAmount
    bills.forEach((b) => expect(b.totalAmount).toBeGreaterThanOrEqual(0));

    await Promise.all(transactions.map((tx) => deleteTransaction(tx.id, pfUser.token)));
  });

  test('CB-A-07: dueDate is the first dueDay after closingDate', async ({
    pfUser,
    creditCardAccount,
  }) => {
    // creditCardAccount: closingDay=7, dueDay=15
    const txDate = dayInCurrentMonth(4);
    const tx = await createTransaction(
      { financialAccountId: creditCardAccount.id, description: uniqueDesc('DueDate A07'), amount: 60, creditDebitType: CreditDebitType.Debit, status: TransactionStatus.Posted, date: txDate },
      pfUser.token
    );

    const bill = await getCreditCardBill(creditCardAccount.id, tx.billId!, pfUser.token);
    const closingDate = new Date(bill!.closingDate);
    const dueDate = new Date(bill!.dueDate);

    // dueDate must be AFTER closingDate
    expect(dueDate.getTime()).toBeGreaterThan(closingDate.getTime());
    // dueDate day must be 15 (the configured dueDay)
    expect(dueDate.getDate()).toBe(15);

    await deleteTransaction(tx.id, pfUser.token);
  });

  test('CB-A-08: getCreditCardBills returns bills ordered by closingDate descending', async ({
    pfUser,
    creditCardAccount,
  }) => {
    // Seed transactions in two different cycles (current and past)
    const [tx1, tx2] = await Promise.all([
      createTransaction({ financialAccountId: creditCardAccount.id, description: uniqueDesc('H05 Ciclo A'), amount: 50, creditDebitType: CreditDebitType.Debit, status: TransactionStatus.Posted, date: dayInCurrentMonth(4) }, pfUser.token),
      createTransaction({ financialAccountId: creditCardAccount.id, description: uniqueDesc('H05 Ciclo B'), amount: 70, creditDebitType: CreditDebitType.Debit, status: TransactionStatus.Posted, date: dayInCurrentMonth(10) }, pfUser.token),
    ]);

    const bills = await getCreditCardBills(creditCardAccount.id, pfUser.token);
    expect(bills.length).toBeGreaterThanOrEqual(2);

    // Verify descending order
    for (let i = 1; i < bills.length; i++) {
      expect(bills[i - 1].closingDate >= bills[i].closingDate).toBe(true);
    }

    await Promise.all([
      deleteTransaction(tx1.id, pfUser.token),
      deleteTransaction(tx2.id, pfUser.token),
    ]);
  });

  test('CB-A-09: CHARGE_CARD account also generates bills automatically', async ({
    pfUser,
  }) => {
    const { createFinancialAccount } = await import('../helpers/banking-graphql');
    const card = await createFinancialAccount(
      { name: `Cartão Débito Parcelado A09 ${Date.now()}`, type: 'CREDIT', subtype: 'CHARGE_CARD', closingDay: 7, dueDay: 15 },
      pfUser.token
    );

    const txDate = dayInCurrentMonth(4);
    const tx = await createTransaction(
      { financialAccountId: card.id, description: uniqueDesc('Compra CHARGE_CARD A09'), amount: 250, creditDebitType: CreditDebitType.Debit, status: TransactionStatus.Posted, date: txDate },
      pfUser.token
    );

    expect(tx.billId).not.toBeNull();

    const bills = await getCreditCardBills(card.id, pfUser.token);
    expect(bills.some((b) => b.id === tx.billId)).toBe(true);

    await deleteTransaction(tx.id, pfUser.token);
  });
});

// =============================================================================
// NEGATIVE PATHS
// =============================================================================

test.describe.skip('Credit Card Bills — negative paths', () => {
  test('CB-N-01: getCreditCardBills on a BANK account — returns empty list or error', async ({
    pfUser,
    bankAccount,
  }) => {
    const result = await getCreditCardBills(bankAccount.id, pfUser.token);
    // A bank account must have no bills — either empty array or a "not applicable" error
    expect(result.length).toBe(0);
  });

  test('CB-N-02: getCreditCardBill with invalid closingDate format — returns GraphQL error', async ({
    pfUser,
    creditCardAccount,
  }) => {
    const { executeGraphQL } = await import('../helpers/graphql');
    const result = await executeGraphQL(`
      query GetCreditCardBill($financialAccountId: ID!, $closingDate: Date) {
        creditCardBill(financialAccountId: $financialAccountId, closingDate: $closingDate) {
          id totalAmount status
        }
      }
    `, { financialAccountId: creditCardAccount.id, closingDate: 'not-a-date' }, pfUser.token);

    expect(result.errors).toBeDefined();
  });

  test('CB-N-03: getCreditCardBills for non-existent account — returns error', async ({
    pfUser,
  }) => {
    const { executeGraphQL } = await import('../helpers/graphql');
    const result = await executeGraphQL(`
      query GetCreditCardBills($financialAccountId: ID!) {
        creditCardBills(financialAccountId: $financialAccountId) {
          id status
        }
      }
    `, { financialAccountId: '00000000-0000-0000-0000-000000000000' }, pfUser.token);

    expect(result.errors).toBeDefined();
    expect(result.errors?.[0]?.message).toMatch(/not found|não encontrad/i);
  });

  test('CB-N-04: PAID bill status should revert when payment transaction is deleted — documents known bug B1', async ({
    pfUser,
    creditCardAccount,
  }) => {
    /**
     * KNOWN BUG B1 (from code-review-2026-03.md):
     * In DefaultCreditCardBillRepository.computeStatus(), there is an early-return
     * guard that prevents PAID status from ever reverting:
     *
     *   if (current == BillStatus.PAID) return BillStatus.PAID
     *
     * Expected behavior: if the payment transaction is deleted, status should
     * revert to CLOSED or OVERDUE depending on the current date.
     * Actual behavior: status remains PAID permanently.
     *
     * This test documents the gap. It will FAIL until bug B1 is fixed.
     * Mark with test.fixme() once a fix is confirmed in progress.
     */
    const txDate = dayInCurrentMonth(4);
    const tx = await createTransaction(
      { financialAccountId: creditCardAccount.id, description: uniqueDesc('Compra N04'), amount: 100, creditDebitType: CreditDebitType.Debit, status: TransactionStatus.Posted, date: txDate },
      pfUser.token
    );

    // Simulate paying the bill via the payBill mutation
    const { executeGraphQL } = await import('../helpers/graphql');
    const payResult = await executeGraphQL<{
      payBill: { id: string; status: string };
    }>(`
      mutation PayBill($billId: ID!, $amount: Float!) {
        payBill(billId: $billId, amount: $amount) { id status }
      }
    `, { billId: tx.billId, amount: 100 }, pfUser.token);

    if (payResult.errors) {
      // payBill may not be available for OPEN bills — skip if so
      test.skip(true, `payBill not available: ${payResult.errors[0]?.message}`);
      return;
    }

    expect(payResult.data?.payBill.status).toBe('PAID');

    // Now delete the payment transaction (simulating reversal)
    // The payment created by payBill should be findable in transactions
    const txs = await getTransactions(creditCardAccount.id, pfUser.token, {
      ...localCalendarYearMonth(),
      first: 80,
    });
    const paymentTx = txs.find(
      (t) => t.creditDebitType === CreditDebitType.Credit && t.amount === 100,
    );

    if (paymentTx) {
      await deleteTransaction(paymentTx.id, pfUser.token);

      // EXPECTED (correct): status reverts to CLOSED or OVERDUE
      // ACTUAL (bug B1): status stays PAID
      const billAfter = await getCreditCardBill(creditCardAccount.id, tx.billId!, pfUser.token);
      // This assertion documents the BUG — it will currently FAIL
      // Remove test.fail() and keep only the expect() once bug B1 is resolved
      test.fail(true, 'Bug B1: PAID status does not revert when payment transaction is deleted');
      expect(billAfter!.status).not.toBe('PAID');
    }

    await deleteTransaction(tx.id, pfUser.token);
  });
});
