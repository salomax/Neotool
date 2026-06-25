import { parseFinanceDateKey } from '../../utils/financeDateGroupLabel';
import type { TransactionSuccessPayload } from './transactionFormShared';

export function shouldAutoScrollToCreatedTransaction(
  payload: TransactionSuccessPayload | undefined,
  selectedMonth?: number,
  selectedYear?: number,
): boolean {
  if (!payload || payload.action !== 'create') return false;
  if (typeof selectedMonth !== 'number' || typeof selectedYear !== 'number') return false;

  const parts = parseFinanceDateKey(payload.date);
  if (!parts) return false;

  return parts.month === selectedMonth && parts.year === selectedYear;
}

export function getTransactionScrollSelector(transactionId: string, isMobile: boolean): string {
  return isMobile
    ? `[data-testid="transaction-mobile-row-${transactionId}"]`
    : `[data-testid="transaction-row-${transactionId}"]`;
}
