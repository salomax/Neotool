'use client';

import { useState, useEffect } from 'react';
import dayjs from 'dayjs';
import type { FinancialAccountsQuery } from '@/lib/graphql/operations/banking/queries.generated';
import { BillStatus, CreditCardBill } from '../bill/billUtils';

export type { BillStatus, CreditCardBill };

export type DisplayMode =
  | {
      kind: 'calendar';
      year: number;
      month: number;
      setYear: (year: number) => void;
      setMonth: (month: number) => void;
    }
  | {
      kind: 'bill';
      bills: CreditCardBill[];
      selectedBill: CreditCardBill | null;
      setSelectedBill: (bill: CreditCardBill) => void;
      loading: boolean;
      refetchBills: () => void;
    };

function readSessionInt(key: string, fallback: number): number {
  try {
    const raw = sessionStorage.getItem(key);
    if (raw) {
      const parsed = parseInt(raw, 10);
      if (!isNaN(parsed)) return parsed;
    }
  } catch {
    // sessionStorage unavailable (SSR, private mode)
  }
  return fallback;
}

export function useAccountDisplayMode(selectedAccount: NonNullable<FinancialAccountsQuery['financialAccounts']>[number] | null): DisplayMode {
  void selectedAccount;

  // Lazy initializers read sessionStorage synchronously on first render,
  // so year/month are stable from the start — no second render, no aborted query.
  const [year, setYear] = useState(() => readSessionInt('financeTransactions.selectedYear', dayjs().year()));
  const [month, setMonth] = useState(() => readSessionInt('financeTransactions.selectedMonth', dayjs().month() + 1));

  // Persist changes back to sessionStorage
  useEffect(() => {
    try {
      sessionStorage.setItem('financeTransactions.selectedYear', year.toString());
      sessionStorage.setItem('financeTransactions.selectedMonth', month.toString());
    } catch {
      // Ignore sessionStorage errors
    }
  }, [year, month]);

  return {
    kind: 'calendar',
    year,
    month,
    setYear,
    setMonth,
  };
}
