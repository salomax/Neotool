'use client';

import { useState } from 'react';
import dayjs from 'dayjs';
import { FinancialAccount } from '@/lib/graphql/types/__generated__/graphql';
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

export function useAccountDisplayMode(selectedAccount: FinancialAccount | null): DisplayMode {
  void selectedAccount;
  const [year, setYear] = useState(dayjs().year());
  const [month, setMonth] = useState(dayjs().month() + 1);

  return {
    kind: 'calendar',
    year,
    month,
    setYear,
    setMonth,
  };
}
