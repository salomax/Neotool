import { describe, expect, it } from 'vitest';
import {
  getTransactionScrollSelector,
  shouldAutoScrollToCreatedTransaction,
} from '../transactionScrollUtils';

describe('transactionScrollUtils', () => {
  it('returns true only for created transactions in the selected month/year', () => {
    expect(
      shouldAutoScrollToCreatedTransaction(
        { action: 'create', transactionId: 'tx-1', date: '2026-03-15' },
        3,
        2026,
      ),
    ).toBe(true);

    expect(
      shouldAutoScrollToCreatedTransaction(
        { action: 'create', transactionId: 'tx-1', date: '2026-04-15' },
        3,
        2026,
      ),
    ).toBe(false);

    expect(
      shouldAutoScrollToCreatedTransaction(
        { action: 'update', transactionId: 'tx-1', date: '2026-03-15' },
        3,
        2026,
      ),
    ).toBe(false);
  });

  it('builds the correct row selector for desktop and mobile', () => {
    expect(getTransactionScrollSelector('tx-1', false)).toBe('[data-testid="transaction-row-tx-1"]');
    expect(getTransactionScrollSelector('tx-1', true)).toBe('[data-testid="transaction-mobile-row-tx-1"]');
  });
});
