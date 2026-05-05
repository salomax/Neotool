'use client';

import { parseDateOnly } from '@/shared/utils/date/dateOnly';
import type { UpdateHoldingEditTransaction, UpdateHoldingFormValues } from './types';

function parseTransactionDate(value: unknown): Date | undefined {
  if (value == null) return undefined;
  const parsed = parseDateOnly(String(value));
  return parsed ?? undefined;
}

export function mapTransactionToFormValues(
  transaction: Pick<
    UpdateHoldingEditTransaction,
    'tradeDate' | 'settlementDate' | 'grossValue' | 'netValue' | 'quantity' | 'unitPrice' | 'incomeTax' | 'iof'
  >,
): Partial<UpdateHoldingFormValues> {
  return {
    tradeDate: parseTransactionDate(transaction.tradeDate),
    settlementDate: parseTransactionDate(transaction.settlementDate),
    grossValue: transaction.grossValue,
    netValue: transaction.netValue ?? undefined,
    quantity: transaction.quantity ?? undefined,
    unitPrice: transaction.unitPrice ?? undefined,
    incomeTax: transaction.incomeTax ?? 0,
    iof: transaction.iof ?? 0,
    brokerageFee: 0,
    settlementFee: 0,
    otherFees: 0,
    valuedAt: undefined,
  };
}
