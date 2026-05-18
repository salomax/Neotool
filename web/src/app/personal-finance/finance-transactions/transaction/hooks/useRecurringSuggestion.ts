import { useEffect, useState, useCallback } from 'react';
import { useLazyQuery, useMutation } from '@apollo/client/react';
import type { Transaction } from '@/lib/graphql/types/__generated__/graphql';
import { TransactionType } from '@/lib/graphql/types/__generated__/graphql';
import {
  LINK_TRANSACTION_TO_RECURRING,
  SUGGEST_RECURRING_FOR_TRANSACTION,
} from '@/lib/graphql/operations/banking/recurringPaymentOperations';
import { extractErrorMessage } from '@/shared/utils/error';
import { useToast } from '@/shared/providers';

type RecurringPaymentSuggestion = {
  id: string;
  name: string;
  nextDueDate: string;
};

type UseRecurringSuggestionOptions = {
  menuTransaction: Transaction | null;
  onSuccess?: () => void;
  beginBusy: () => void;
  endBusy: () => void;
  closeMenu: () => void;
  unknownErrorMessage: string;
};

export function useRecurringSuggestion({
  menuTransaction,
  onSuccess,
  beginBusy,
  endBusy,
  closeMenu,
  unknownErrorMessage,
}: UseRecurringSuggestionOptions) {
  const toast = useToast();
  const [recurringPaymentSuggestion, setRecurringPaymentSuggestion] =
    useState<RecurringPaymentSuggestion | null>(null);

  const [fetchRecurringSuggestion] = useLazyQuery<{
    suggestRecurringForTransaction: RecurringPaymentSuggestion | null;
  }>(SUGGEST_RECURRING_FOR_TRANSACTION, {
    fetchPolicy: 'network-only',
  });

  const [linkTransactionToRecurring] = useMutation(LINK_TRANSACTION_TO_RECURRING);

  useEffect(() => {
    const tx = menuTransaction;
    const txId = tx?.id;
    const shouldFetchSuggestion =
      Boolean(txId) &&
      tx?.type !== TransactionType.BalanceAdjustment &&
      tx?.type !== TransactionType.Transfer;

    if (!shouldFetchSuggestion || !txId) {
      setRecurringPaymentSuggestion(null);
      return;
    }

    let cancelled = false;
    setRecurringPaymentSuggestion(null);
    void fetchRecurringSuggestion({ variables: { transactionId: txId } })
      .then((result) => {
        if (cancelled) return;
        setRecurringPaymentSuggestion(result.data?.suggestRecurringForTransaction ?? null);
      })
      .catch(() => {
        if (cancelled) return;
        setRecurringPaymentSuggestion(null);
      });

    return () => {
      cancelled = true;
    };
  }, [menuTransaction?.id, fetchRecurringSuggestion]);

  const handleLinkRecurringPayment = useCallback(async (tx: Transaction, recurringPaymentId: string) => {
    if (!tx.id) return;
    try {
      beginBusy();
      await linkTransactionToRecurring({
        variables: {
          transactionId: tx.id,
          recurringPaymentId,
        },
      });
      onSuccess?.();
      closeMenu();
    } catch (err) {
      toast.error(extractErrorMessage(err, unknownErrorMessage));
    } finally {
      endBusy();
    }
  }, [beginBusy, closeMenu, endBusy, linkTransactionToRecurring, onSuccess, toast, unknownErrorMessage]);

  return {
    recurringPaymentSuggestion,
    handleLinkRecurringPayment,
    clearRecurringPaymentSuggestion: () => setRecurringPaymentSuggestion(null),
  };
}
