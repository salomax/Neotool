import { useCallback, useState } from 'react';
import type { Transaction } from '@/lib/graphql/types/__generated__/graphql';

export function useTransactionEdit() {
  const [editedTransaction, setEditedTransaction] = useState<Transaction | null>(null);
  const [isEditFormOpen, setIsEditFormOpen] = useState(false);

  const openEdit = useCallback((tx: Transaction) => {
    setEditedTransaction(tx);
    setIsEditFormOpen(true);
  }, []);

  const closeEdit = useCallback(() => {
    setIsEditFormOpen(false);
  }, []);

  const clearEdit = useCallback(() => {
    setEditedTransaction(null);
  }, []);

  return { editedTransaction, isEditFormOpen, openEdit, closeEdit, clearEdit };
}
