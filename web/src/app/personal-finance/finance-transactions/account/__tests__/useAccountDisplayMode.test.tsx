import { renderHook } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { useAccountDisplayMode } from '../useAccountDisplayMode';
import { FinancialAccount, FinancialAccountSubtype, FinancialAccountType } from '@/lib/graphql/types/__generated__/graphql';

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

describe('useAccountDisplayMode', () => {
  it('returns calendar mode for null account', () => {
    const { result } = renderHook(() => useAccountDisplayMode(null));
    expect(result.current.kind).toBe('calendar');
  });

  it('returns calendar mode for BANK account', () => {
    const { result } = renderHook(() =>
      useAccountDisplayMode(makeAccount(FinancialAccountSubtype.ContaCorrente, FinancialAccountType.Bank)),
    );
    expect(result.current.kind).toBe('calendar');
  });

  it('returns calendar mode for CREDIT_CARD account while bill view is deferred', () => {
    const { result } = renderHook(() =>
      useAccountDisplayMode(makeAccount(FinancialAccountSubtype.CreditCard, FinancialAccountType.Credit)),
    );
    expect(result.current.kind).toBe('calendar');
  });

  it('returns calendar mode for CHARGE_CARD account while bill view is deferred', () => {
    const { result } = renderHook(() =>
      useAccountDisplayMode(makeAccount(FinancialAccountSubtype.ChargeCard, FinancialAccountType.Credit)),
    );
    expect(result.current.kind).toBe('calendar');
  });
});
