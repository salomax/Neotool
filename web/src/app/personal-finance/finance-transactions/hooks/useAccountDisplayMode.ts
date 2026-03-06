'use client';

import { useState, useEffect, useMemo } from 'react';
import { useQuery } from '@apollo/client/react';
import dayjs from 'dayjs';
import { FinancialAccount, FinancialAccountSubtype } from '@/lib/graphql/types/__generated__/graphql';
import { CREDIT_CARD_BILLS } from '@/lib/graphql/operations/banking/queries';
import { BillStatus, CreditCardBill } from './billUtils';

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
  // Calendar state (month is 1-indexed: 1-12)
  const [year, setYear] = useState(dayjs().year());
  const [month, setMonth] = useState(dayjs().month() + 1);

  // Bill state
  const [selectedBill, setSelectedBill] = useState<CreditCardBill | null>(null);

  const isCreditCard =
    selectedAccount?.subtype === FinancialAccountSubtype.CreditCard ||
    selectedAccount?.subtype === FinancialAccountSubtype.ChargeCard;

  const { data: billsData, loading: billsLoading, refetch: refetchBills } = useQuery<{ creditCardBills: CreditCardBill[] }>(
    CREDIT_CARD_BILLS,
    {
      variables: { financialAccountId: selectedAccount?.id },
      skip: !isCreditCard || !selectedAccount?.id,
      fetchPolicy: 'cache-and-network',
    }
  );

  const bills: CreditCardBill[] = useMemo(
    () => billsData?.creditCardBills ?? [],
    [billsData?.creditCardBills],
  );

  // Reset and auto-select bill when account or bills change (sync with external Apollo data)
  useEffect(() => {
    /* eslint-disable react-hooks/set-state-in-effect -- Sync selected bill with Apollo query result */
    if (!isCreditCard || bills.length === 0) {
      setSelectedBill(null);
      return;
    }
    const openBill = bills.find((b) => b.status === 'OPEN');
    setSelectedBill(openBill ?? bills[0] ?? null);
    /* eslint-enable react-hooks/set-state-in-effect */
  }, [selectedAccount?.id, isCreditCard, bills]);

  if (isCreditCard) {
    return {
      kind: 'bill',
      bills,
      selectedBill,
      setSelectedBill,
      loading: billsLoading,
      refetchBills,
    };
  }

  return {
    kind: 'calendar',
    year,
    month,
    setYear,
    setMonth,
  };
}
