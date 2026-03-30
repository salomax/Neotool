'use client';

import { useCallback, useState } from 'react';
import { useQuery } from '@apollo/client/react';
import dayjs from 'dayjs';
import { FinancialAccount, FinancialAccountSubtype } from '@/lib/graphql/types/__generated__/graphql';
import { BillStatus, CreditCardBill } from '../bill/billUtils';
import { CREDIT_CARD_BILLS } from '@/lib/graphql/operations/banking/queries';
import { GetCreditCardBillsQuery } from '@/lib/graphql/operations/banking/queries.generated';

export type { BillStatus, CreditCardBill };

const CARD_SUBTYPES: FinancialAccountSubtype[] = [
  FinancialAccountSubtype.CreditCard,
  FinancialAccountSubtype.ChargeCard,
];

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
  const [year, setYear] = useState(dayjs().year());
  const [month, setMonth] = useState(dayjs().month() + 1);
  const accountId = selectedAccount?.id ?? null;
  const [selectedBillState, setSelectedBillState] = useState<{
    accountId: string | null;
    bill: CreditCardBill | null;
  }>(() => ({ accountId, bill: null }));
  const selectedBill = selectedBillState.accountId === accountId ? selectedBillState.bill : null;
  const setSelectedBill = useCallback(
    (bill: CreditCardBill) => {
      setSelectedBillState({ accountId, bill });
    },
    [accountId]
  );

  const isCardAccount =
    selectedAccount != null &&
    CARD_SUBTYPES.includes(selectedAccount.subtype as FinancialAccountSubtype);

  const { data, loading, refetch } = useQuery<GetCreditCardBillsQuery>(CREDIT_CARD_BILLS, {
    variables: { financialAccountId: selectedAccount?.id },
    skip: !isCardAccount || !selectedAccount?.id,
    fetchPolicy: 'cache-and-network',
  });

  if (isCardAccount) {
    const bills: CreditCardBill[] = (data?.creditCardBills ?? []).map((b) => ({
      ...b,
      closingDate: b.closingDate as string,
      dueDate: b.dueDate as string,
      createdAt: b.createdAt as string,
      updatedAt: b.updatedAt as string,
    }));

    // Default to the most recent bill (index 0; list is ordered DESC by closingDate).
    const effectiveSelectedBill = selectedBill ?? bills[0] ?? null;

    return {
      kind: 'bill',
      bills,
      selectedBill: effectiveSelectedBill,
      setSelectedBill,
      loading,
      refetchBills: refetch,
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
