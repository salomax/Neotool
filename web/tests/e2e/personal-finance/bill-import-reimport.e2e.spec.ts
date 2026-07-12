/**
 * E2E tests — Bill import re-import reconciliation (issue #367)
 *
 * Coverage: BI-R-01..04
 *
 * Exercises the real submit → confirm GraphQL flow (Nubank CSV, deterministic parser).
 * Re-import with a corrected due month must move import-owned transactions to the new
 * referenceMonth bill and soft-delete the orphaned prior bill.
 */

import { test, expect } from '../fixtures/personal-finance';
import {
  buildConfirmBillImportInput,
  confirmBillImport,
  createFinancialAccount,
  getBillImportJob,
  getCreditCardBill,
  getCreditCardBills,
  getTransactionsByBillId,
  referenceMonthFromDueDate,
  submitBillImport,
  toBase64Utf8,
} from '../helpers/banking-graphql';

const TX_DATE = '2025-12-10';
const TX_DESCRIPTION = 'BI-R reconcile E2E';
const TX_AMOUNT = 99.9;

function nubankCsv(description: string = TX_DESCRIPTION): string {
  return [
    'date,transaction_type,title,amount,category,charges,Identificador',
    `${TX_DATE},purchase,${description},${TX_AMOUNT.toFixed(2)},Food,,bir-e2e-${Date.now()}`,
  ].join('\n');
}

async function createImportCard(token: string) {
  return createFinancialAccount(
    {
      name: `Cartão BI-R ${Date.now()}`,
      type: 'CREDIT',
      subtype: 'CREDIT_CARD',
      closingDay: 7,
      dueDay: 15,
    },
    token
  );
}

async function submitAndAwaitReview(csv: string, token: string) {
  const job = await submitBillImport(
    {
      fileName: 'fatura-bir-e2e.csv',
      fileBase64: toBase64Utf8(csv),
    },
    token
  );
  expect(job.status).toBe('AWAITING_REVIEW');
  expect(job.reviewPayload?.cards.length).toBeGreaterThan(0);
  return job;
}

async function confirmWithDueMonth(
  jobId: string,
  financialAccountId: string,
  token: string,
  dueDate: string,
  closingDate: string
) {
  const job = await getBillImportJob(jobId, token);
  const input = buildConfirmBillImportInput(job, financialAccountId, { dueDate, closingDate });
  const result = await confirmBillImport(input, token);
  expect(result.status).toBe('DONE');
  return referenceMonthFromDueDate(dueDate);
}

// =============================================================================
// RE-IMPORT RECONCILIATION
// =============================================================================

test.describe('Bill import re-import — referenceMonth reconciliation', () => {
  test('BI-R-01: first confirm with December due month places transactions on Dec bill', async ({
    pfUser,
  }) => {
    const card = await createImportCard(pfUser.token);
    const csv = nubankCsv();
    const job = await submitAndAwaitReview(csv, pfUser.token);

    const decReferenceMonth = await confirmWithDueMonth(
      job.id,
      card.id,
      pfUser.token,
      '2025-12-25',
      '2025-12-07'
    );

    const decBill = await getCreditCardBill(card.id, decReferenceMonth, pfUser.token);
    expect(decBill).not.toBeNull();
    expect(decBill!.referenceMonth).toBe('2025-12-01');

    const txs = await getTransactionsByBillId(decBill!.id, card.id, pfUser.token);
    expect(txs).toHaveLength(1);
    expect(txs[0].description).toBe(TX_DESCRIPTION);
    expect(txs[0].amount).toBeCloseTo(TX_AMOUNT, 2);
  });

  test('BI-R-02: re-import with corrected November due month moves transactions off December bill', async ({
    pfUser,
  }) => {
    const card = await createImportCard(pfUser.token);
    const csv = nubankCsv();
    const job = await submitAndAwaitReview(csv, pfUser.token);

    await confirmWithDueMonth(job.id, card.id, pfUser.token, '2025-12-25', '2025-12-07');

    const resubmit = await submitAndAwaitReview(csv, pfUser.token);
    expect(resubmit.id).toBe(job.id);

    const novReferenceMonth = await confirmWithDueMonth(
      job.id,
      card.id,
      pfUser.token,
      '2025-11-25',
      '2025-11-07'
    );

    const novBill = await getCreditCardBill(card.id, novReferenceMonth, pfUser.token);
    expect(novBill).not.toBeNull();
    expect(novBill!.referenceMonth).toBe('2025-11-01');

    const novTxs = await getTransactionsByBillId(novBill!.id, card.id, pfUser.token);
    expect(novTxs).toHaveLength(1);
    expect(novTxs[0].description).toBe(TX_DESCRIPTION);

    const allBills = await getCreditCardBills(card.id, pfUser.token);
    expect(allBills).toHaveLength(1);
    expect(allBills[0].referenceMonth).toBe('2025-11-01');
    expect(allBills.some((b) => b.referenceMonth === '2025-12-01')).toBe(false);
  });

  test('BI-R-03: idempotent re-import with same due month does not duplicate transactions', async ({
    pfUser,
  }) => {
    const card = await createImportCard(pfUser.token);
    const csv = nubankCsv();
    const job = await submitAndAwaitReview(csv, pfUser.token);

    const referenceMonth = await confirmWithDueMonth(
      job.id,
      card.id,
      pfUser.token,
      '2025-12-25',
      '2025-12-07'
    );

    await submitAndAwaitReview(csv, pfUser.token);
    await confirmWithDueMonth(job.id, card.id, pfUser.token, '2025-12-25', '2025-12-07');

    const bill = await getCreditCardBill(card.id, referenceMonth, pfUser.token);
    expect(bill).not.toBeNull();

    const txs = await getTransactionsByBillId(bill!.id, card.id, pfUser.token);
    expect(txs).toHaveLength(1);
    expect(txs[0].description).toBe(TX_DESCRIPTION);

    const bills = await getCreditCardBills(card.id, pfUser.token);
    expect(bills.filter((b) => b.referenceMonth === referenceMonth)).toHaveLength(1);
  });

  test('BI-R-04: same-month due day correction updates bill metadata without duplicating rows', async ({
    pfUser,
  }) => {
    const card = await createImportCard(pfUser.token);
    const csv = nubankCsv();
    const job = await submitAndAwaitReview(csv, pfUser.token);

    await confirmWithDueMonth(job.id, card.id, pfUser.token, '2025-12-25', '2025-12-07');

    await submitAndAwaitReview(csv, pfUser.token);
    const referenceMonth = await confirmWithDueMonth(
      job.id,
      card.id,
      pfUser.token,
      '2025-12-20',
      '2025-12-07'
    );

    expect(referenceMonth).toBe('2025-12-01');

    const bill = await getCreditCardBill(card.id, referenceMonth, pfUser.token);
    expect(bill).not.toBeNull();
    expect(bill!.dueDate).toBe('2025-12-20');

    const txs = await getTransactionsByBillId(bill!.id, card.id, pfUser.token);
    expect(txs).toHaveLength(1);

    const bills = await getCreditCardBills(card.id, pfUser.token);
    expect(bills.filter((b) => b.referenceMonth === '2025-12-01')).toHaveLength(1);
  });
});
