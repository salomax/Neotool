'use client';

import { InvestmentTransactionDirection } from '@/lib/graphql/types/__generated__/graphql';
import { parseDateOnly } from '@/shared/utils/date/dateOnly';
import type { UpdateHoldingEditTransaction, UpdateHoldingFormValues } from './types';

function parseTransactionDate(value: unknown): Date | undefined {
  if (value == null) return undefined;
  const parsed = parseDateOnly(String(value));
  return parsed ?? undefined;
}

function deriveTotalFees(
  grossValue: number,
  netValue: number | null | undefined,
  direction: InvestmentTransactionDirection,
): number {
  if (netValue == null || !Number.isFinite(netValue)) return 0;
  return direction === InvestmentTransactionDirection.Inflow
    ? Math.max(netValue - grossValue, 0)
    : Math.max(grossValue - netValue, 0);
}

export function mapTransactionToFormValues(
  transaction: Pick<
    UpdateHoldingEditTransaction,
    | 'direction'
    | 'tradeDate'
    | 'settlementDate'
    | 'grossValue'
    | 'netValue'
    | 'quantity'
    | 'unitPrice'
    | 'incomeTax'
    | 'iof'
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
    totalFees: deriveTotalFees(transaction.grossValue, transaction.netValue, transaction.direction),
    valuedAt: undefined,
  };
}
