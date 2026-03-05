import { renderHook, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, Mock } from 'vitest';
import { useAccountDisplayMode } from '../useAccountDisplayMode';
import { FinancialAccount, FinancialAccountSubtype, FinancialAccountType } from '@/lib/graphql/types/__generated__/graphql';
import { useQuery } from '@apollo/client/react';

// Mock dependencies
const mockRefetch = vi.fn();

vi.mock('@apollo/client/react', () => ({
  useQuery: vi.fn(),
}));

vi.mock('@/lib/graphql/operations/banking/queries', () => ({
  CREDIT_CARD_BILLS: 'CREDIT_CARD_BILLS',
}));

describe('useAccountDisplayMode', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    (useQuery as unknown as Mock).mockReturnValue({
      data: { creditCardBills: [] },
      loading: false,
      refetch: mockRefetch,
    });
  });

  it('returns calendar mode for null account', () => {
    const { result } = renderHook(() => useAccountDisplayMode(null));
    expect(result.current.kind).toBe('calendar');
  });

  it('returns calendar mode for BANK account', () => {
    const bankAccount = {
      id: 'acc-1',
      subtype: FinancialAccountSubtype.ContaCorrente,
      type: FinancialAccountType.Bank,
      name: 'Bank Account',
      currencyCode: 'BRL',
      includeInDashboard: true,
      includeInNetWorth: true,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    } as FinancialAccount;

    const { result } = renderHook(() => useAccountDisplayMode(bankAccount));
    expect(result.current.kind).toBe('calendar');
  });

  it('returns bill mode for CREDIT_CARD account', () => {
    const cardAccount = {
      id: 'card-1',
      subtype: FinancialAccountSubtype.CreditCard,
      type: FinancialAccountType.Credit,
      name: 'Credit Card',
      currencyCode: 'BRL',
      includeInDashboard: true,
      includeInNetWorth: true,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    } as FinancialAccount;

    const { result } = renderHook(() => useAccountDisplayMode(cardAccount));
    expect(result.current.kind).toBe('bill');
  });

  it('returns bill mode for CHARGE_CARD account', () => {
    const chargeCardAccount = {
      id: 'card-2',
      subtype: FinancialAccountSubtype.ChargeCard,
      type: FinancialAccountType.Credit,
      name: 'Charge Card',
      currencyCode: 'BRL',
      includeInDashboard: true,
      includeInNetWorth: true,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    } as FinancialAccount;

    const { result } = renderHook(() => useAccountDisplayMode(chargeCardAccount));
    expect(result.current.kind).toBe('bill');
  });

  it('selects open bill by default', async () => {
    const cardAccount = {
      id: 'card-1',
      subtype: FinancialAccountSubtype.CreditCard,
      type: FinancialAccountType.Credit,
      name: 'Credit Card',
      currencyCode: 'BRL',
      includeInDashboard: true,
      includeInNetWorth: true,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    } as FinancialAccount;

    const openBill = { id: 'bill-1', status: 'OPEN', totalAmount: 100 };
    const closedBill = { id: 'bill-2', status: 'CLOSED', totalAmount: 200 };

    (useQuery as unknown as Mock).mockReturnValue({
      data: { creditCardBills: [closedBill, openBill] },
      loading: false,
      refetch: mockRefetch,
    });

    const { result } = renderHook(() => useAccountDisplayMode(cardAccount));
    
    expect(result.current.kind).toBe('bill');
    if (result.current.kind === 'bill') {
      expect(result.current.selectedBill).toEqual(openBill);
    }
  });
});
