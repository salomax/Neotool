import { useCallback, useEffect, useState } from 'react';
import type { Transaction } from '@/lib/graphql/types/__generated__/graphql';
import { useMutation } from '@apollo/client/react';
import { DELETE_TRANSACTION } from '@/lib/graphql/operations/banking/mutations';
import { useToast } from '@/shared/providers';
import { extractErrorMessage } from '@/shared/utils/error';

interface UseTransactionDeleteOptions {
  onDeleted?: (tx: Transaction) => void;
  beginBusy: () => void;
  endBusy: () => void;
}

export function useTransactionDelete({ onDeleted, beginBusy, endBusy }: UseTransactionDeleteOptions) {
  const toast = useToast();
  const [transactionToRemove, setTransactionToRemove] = useState<Transaction | null>(null);
  const [deletedTransactionId, setDeletedTransactionId] = useState<string | null>(null);

  const [deleteTransaction, { loading: removing }] = useMutation(DELETE_TRANSACTION);

  const promptDelete = useCallback((tx: Transaction) => {
    setTransactionToRemove(tx);
  }, []);

  const cancelDelete = useCallback(() => {
    setTransactionToRemove(null);
  }, []);

  const confirmDelete = useCallback(async () => {
    const tx = transactionToRemove;
    if (!tx) return;

    try {
      beginBusy();
      await deleteTransaction({ variables: { id: tx.id } });
      setTransactionToRemove(null);
      setDeletedTransactionId(tx.id);
      onDeleted?.(tx);
    } catch (error) {
      setTransactionToRemove(null);
      toast.error(extractErrorMessage(error, 'Não foi possível remover a transação'));
    } finally {
      endBusy();
    }
  }, [transactionToRemove, deleteTransaction, beginBusy, endBusy, onDeleted, toast]);

  return {
    transactionToRemove,
    deletedTransactionId,
    setDeletedTransactionId,
    removing,
    promptDelete,
    cancelDelete,
    confirmDelete,
  };
}
