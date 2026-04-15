import { useCallback, useEffect, useState } from 'react';
import type { Transaction } from '@/lib/graphql/types/__generated__/graphql';
import {
  CategoryApplicableTo,
  CreditDebitType,
  TransactionType,
} from '@/lib/graphql/types/__generated__/graphql';
import { useMutation } from '@apollo/client/react';
import { UPDATE_TRANSACTION } from '@/lib/graphql/operations/banking/mutations';
import { useApolloClient } from '@apollo/client/react';
import { useToast } from '@/shared/providers';
import { extractErrorMessage } from '@/shared/utils/error';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../../i18n';
import type { ApolloClient } from '@apollo/client';
import { TRANSACTION_CATEGORIES } from '@/lib/graphql/operations/banking/queries';
import type { TransactionCategory } from '@/lib/graphql/types/__generated__/graphql';

const OWN_ACCOUNTS_TRANSFER_SUBCATEGORY_NAME = 'Entre contas próprias';

function findOwnAccountsTransferCategoryId(categories: TransactionCategory[]): string | null {
  const matches = categories.filter(
    (c) =>
      c.name === OWN_ACCOUNTS_TRANSFER_SUBCATEGORY_NAME &&
      c.applicableTo === CategoryApplicableTo.Transfer &&
      c.isSystem === true,
  );
  if (matches.length === 0) return null;
  matches.sort((a, b) => a.sortOrder - b.sortOrder);
  return matches[0]?.id ?? null;
}

async function resolveOwnAccountsTransferCategoryId(
  client: ApolloClient,
): Promise<string | null> {
  try {
    const cached = client.readQuery<{ transactionCategories: TransactionCategory[] }>({
      query: TRANSACTION_CATEGORIES,
    });
    const fromCache = findOwnAccountsTransferCategoryId(cached?.transactionCategories ?? []);
    if (fromCache) return fromCache;
  } catch {
    // not in Apollo cache yet
  }
  const fetched = await client.query<{ transactionCategories: TransactionCategory[] }>({
    query: TRANSACTION_CATEGORIES,
    fetchPolicy: 'network-only',
  });
  return findOwnAccountsTransferCategoryId(fetched.data?.transactionCategories ?? []);
}

export function resolveRuleApplicableTo(tx: Transaction): CategoryApplicableTo {
  if (tx.type === TransactionType.Transfer) return CategoryApplicableTo.Transfer;
  if (tx.creditDebitType === CreditDebitType.Credit) return CategoryApplicableTo.Income;
  if (tx.creditDebitType === CreditDebitType.Debit) return CategoryApplicableTo.Expense;
  return CategoryApplicableTo.Any;
}

interface UseActionMenuOptions {
  transactions: Transaction[];
  onSuccess?: () => void;
  beginBusy: () => void;
  endBusy: () => void;
}

export function useActionMenu({ transactions, onSuccess, beginBusy, endBusy }: UseActionMenuOptions) {
  const { t } = useTranslation(personalFinanceTranslations);
  const toast = useToast();
  const apolloClient = useApolloClient();
  const [updateTransaction] = useMutation(UPDATE_TRANSACTION);

  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
  const [menuTransaction, setMenuTransaction] = useState<Transaction | null>(null);
  const [markingTransferTransactionId, setMarkingTransferTransactionId] = useState<string | null>(null);

  const open = useCallback((el: HTMLElement, tx: Transaction) => {
    setAnchorEl(el);
    setMenuTransaction(tx);
  }, []);

  const close = useCallback(() => {
    setAnchorEl(null);
    setMenuTransaction(null);
  }, []);

  // Close if the menu's transaction has been removed from the list
  useEffect(() => {
    if (!menuTransaction) return;
    if (transactions.some((t) => t.id === menuTransaction.id)) return;
    close();
  }, [menuTransaction, close, transactions]);

  const handleMarkAsInternalTransfer = useCallback(async () => {
    const tx = menuTransaction;
    if (!tx) return;
    if (tx.type === TransactionType.Transfer) return;

    setMarkingTransferTransactionId(tx.id);
    try {
      beginBusy();
      const ownAccountsCategoryId = await resolveOwnAccountsTransferCategoryId(apolloClient);
      if (!ownAccountsCategoryId) {
        toast.error(t('financeTransactions.transactionTable.rowActions.markAsTransferMissingCategory'));
        return;
      }

      await updateTransaction({
        variables: {
          id: tx.id,
          input: {
            type: TransactionType.Transfer,
            categoryId: ownAccountsCategoryId,
          },
        },
      });

      toast.success(t('financeTransactions.transactionTable.rowActions.markAsTransferSuccess'));
      onSuccess?.();
      close();
    } catch (err) {
      toast.error(
        extractErrorMessage(err, t('financeTransactions.transactionTable.rowActions.markAsTransferError')),
      );
    } finally {
      setMarkingTransferTransactionId(null);
      endBusy();
    }
  }, [menuTransaction, apolloClient, updateTransaction, beginBusy, endBusy, close, onSuccess, t, toast]);

  return {
    anchorEl,
    menuTransaction,
    markingTransferTransactionId,
    open,
    close,
    handleMarkAsInternalTransfer,
  };
}
