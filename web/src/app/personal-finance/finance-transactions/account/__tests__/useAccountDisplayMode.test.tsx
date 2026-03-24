import { renderHook } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { useAccountDisplayMode } from '../useAccountDisplayMode';
import { FinancialAccount, FinancialAccountSubtype, FinancialAccountType } from '@/lib/graphql/types/__generated__/graphql';
import { useQuery } from '@apollo/client/react';

const makeAccount = (subtype: FinancialAccountSubtype, type: FinancialAccountType): FinancialAccount =>
  ({
    id: 'acc-1',
    subtype,
    type,
    name: 'Test Account',
    currencyCode: 'BRL',
    includeInDashboard: true,
    includeInNetWorth: true,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  }) as FinancialAccount;

vi.mock('@apollo/client/react', () => ({
  useQuery: vi.fn(),
}));

describe('useAccountDisplayMode', () => {
  it('returns calendar mode for null account', () => {
    (useQuery as any).mockReturnValue({ data: undefined, loading: false, refetch: vi.fn() });
    const { result } = renderHook(() => useAccountDisplayMode(null));
    expect(result.current.kind).toBe('calendar');
  });

  it('returns calendar mode for BANK account', () => {
    (useQuery as any).mockReturnValue({ data: undefined, loading: false, refetch: vi.fn() });
    const { result } = renderHook(() =>
      useAccountDisplayMode(makeAccount(FinancialAccountSubtype.ContaCorrente, FinancialAccountType.Bank)),
    );
    expect(result.current.kind).toBe('calendar');
  });

  it('returns bill mode for CREDIT_CARD account', () => {
    (useQuery as any).mockReturnValue({
      data: {
        creditCardBills: [
          {
            id: 'bill-1',
            financialAccountId: 'acc-1',
            closingDate: '2025-01-31',
            dueDate: '2025-02-10',
            totalAmount: 123.45,
            minimumPayment: null,
            status: 'OPEN',
            createdAt: '2025-01-01T00:00:00.000Z',
            updatedAt: '2025-01-02T00:00:00.000Z',
          },
        ],
      },
      loading: false,
      refetch: vi.fn(),
    });
    const { result } = renderHook(() =>
      useAccountDisplayMode(makeAccount(FinancialAccountSubtype.CreditCard, FinancialAccountType.Credit)),
    );
    expect(result.current.kind).toBe('bill');
    if (result.current.kind !== 'bill') {
      throw new Error('Expected bill mode');
    }
    expect(result.current.selectedBill?.id).toBe('bill-1');
  });

  it('returns bill mode for CHARGE_CARD account', () => {
    (useQuery as any).mockReturnValue({
      data: {
        creditCardBills: [
          {
            id: 'bill-1',
            financialAccountId: 'acc-1',
            closingDate: '2025-01-31',
            dueDate: '2025-02-10',
            totalAmount: 123.45,
            minimumPayment: null,
            status: 'OPEN',
            createdAt: '2025-01-01T00:00:00.000Z',
            updatedAt: '2025-01-02T00:00:00.000Z',
          },
        ],
      },
      loading: false,
      refetch: vi.fn(),
    });
    const { result } = renderHook(() =>
      useAccountDisplayMode(makeAccount(FinancialAccountSubtype.ChargeCard, FinancialAccountType.Credit)),
    );
    expect(result.current.kind).toBe('bill');
    if (result.current.kind !== 'bill') {
      throw new Error('Expected bill mode');
    }
    expect(result.current.selectedBill?.id).toBe('bill-1');
  });
});
