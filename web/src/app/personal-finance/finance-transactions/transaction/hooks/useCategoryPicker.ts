import { useCallback, useEffect, useRef, useState } from 'react';
import type { Transaction } from '@/lib/graphql/types/__generated__/graphql';
import { useLazyQuery, useMutation } from '@apollo/client/react';
import { UPDATE_TRANSACTION, APPLY_TRANSACTION_EDIT_TO_SIMILAR } from '@/lib/graphql/operations/banking/mutations';
import { SIMILAR_TRANSACTION_EDIT_PREVIEW } from '@/lib/graphql/operations/banking/queries';
import { usePreferences, useToast } from '@/shared/providers';
import { extractErrorMessage } from '@/shared/utils/error';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../../i18n';
import { resolveSimilarEditBehavior } from '../similarEditPreference';
import type { MouseEvent } from 'react';

type SimilarEditPreview = {
  similarCount: number;
  matchDescription: string;
  supportsFuturePropagation: boolean;
};

type PendingSimilarEditPrompt = {
  transactionId: string;
  similarCount: number;
  matchDescription: string;
  input: { categoryId?: string };
};

type ApplySimilarEditResult = {
  applyTransactionEditToSimilar: {
    affectedCount: number;
    futureRuleCreated: boolean;
  };
};

function shouldPromptForImportedCategoryEdit(transaction: Transaction | null, categoryId: string | null): boolean {
  if (!transaction) return false;
  return (transaction.category?.id ?? null) !== categoryId && categoryId !== null;
}

interface UseCategoryPickerOptions {
  transactions: Transaction[];
  onSuccess?: () => void;
  beginBusy: () => void;
  endBusy: () => void;
}

export function useCategoryPicker({ transactions, onSuccess, beginBusy, endBusy }: UseCategoryPickerOptions) {
  const { t } = useTranslation(personalFinanceTranslations);
  const toast = useToast();
  const { bankingUserPreferences } = usePreferences();

  // Stable ref for transactions so async callbacks always see latest list
  const transactionsRef = useRef<Transaction[]>(transactions);
  useEffect(() => {
    transactionsRef.current = transactions;
  }, [transactions]);

  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
  const [pickerTransaction, setPickerTransaction] = useState<Transaction | null>(null);
  const [updatingCategoryTransactionId, setUpdatingCategoryTransactionId] = useState<string | null>(null);
  // Optimistic "no category" override: shown immediately after clearing, reverted on error
  const [forceNoCategoryByTransactionId, setForceNoCategoryByTransactionId] = useState<Record<string, true>>({});
  const [pendingSimilarEditPrompt, setPendingSimilarEditPrompt] = useState<PendingSimilarEditPrompt | null>(null);

  const [updateTransaction] = useMutation(UPDATE_TRANSACTION);
  const [loadSimilarEditPreview] = useLazyQuery<{ similarTransactionEditPreview: SimilarEditPreview }>(
    SIMILAR_TRANSACTION_EDIT_PREVIEW,
    { fetchPolicy: 'no-cache' },
  );
  const [applyTransactionEditToSimilar, { loading: applyingSimilarEdit }] =
    useMutation<ApplySimilarEditResult>(APPLY_TRANSACTION_EDIT_TO_SIMILAR);

  // Clean up forceNoCategory entries once Apollo cache confirms the category is gone
  useEffect(() => {
    setForceNoCategoryByTransactionId((prev) => {
      const ids = Object.keys(prev);
      if (ids.length === 0) return prev;
      let changed = false;
      const next = { ...prev };
      for (const tx of transactions) {
        if (!next[tx.id]) continue;
        if (!tx.category) {
          delete next[tx.id];
          changed = true;
        }
      }
      return changed ? next : prev;
    });
  }, [transactions]);

  const close = useCallback(() => {
    setAnchorEl(null);
    setPickerTransaction(null);
  }, []);

  const openPicker = useCallback((event: MouseEvent<HTMLElement>, transaction: Transaction) => {
    setAnchorEl(event.currentTarget);
    const isForcedNoCategory = Boolean(forceNoCategoryByTransactionId[transaction.id]);
    setPickerTransaction(
      isForcedNoCategory ? { ...transaction, category: null } : transaction,
    );
  }, [forceNoCategoryByTransactionId]);

  const handleSelectCategory = useCallback(async (categoryId: string | null) => {
    const tx = pickerTransaction;
    close();
    if (!tx) return;

    const hadForcedNoCategory = Boolean(forceNoCategoryByTransactionId[tx.id]);
    if (categoryId === null) {
      setForceNoCategoryByTransactionId((prev) => ({ ...prev, [tx.id]: true }));
    } else if (hadForcedNoCategory) {
      setForceNoCategoryByTransactionId((prev) => {
        const next = { ...prev };
        delete next[tx.id];
        return next;
      });
    }

    setUpdatingCategoryTransactionId(tx.id);
    try {
      beginBusy();
      const shouldPrompt = shouldPromptForImportedCategoryEdit(tx, categoryId);
      const previewResult = shouldPrompt
        ? await loadSimilarEditPreview({ variables: { id: tx.id } })
        : undefined;

      await updateTransaction({ variables: { id: tx.id, input: { categoryId } } });

      toast.success(t('financeTransactions.transactionTable.categoryUpdated'));
      onSuccess?.();

      const preview = previewResult?.data?.similarTransactionEditPreview;
      const similarEditBehavior =
        preview && categoryId
          ? resolveSimilarEditBehavior(
              bankingUserPreferences.ruleCreationMode,
              preview.supportsFuturePropagation,
            )
          : 'skip';

      if (preview && similarEditBehavior === 'auto' && categoryId) {
        try {
          const result = await applyTransactionEditToSimilar({
            variables: {
              id: tx.id,
              matchDescription: preview.matchDescription,
              input: { categoryId },
              applyToFuture: true,
              applyToExisting: true,
            },
          });
          const affectedCount = result.data?.applyTransactionEditToSimilar?.affectedCount ?? preview.similarCount;
          toast.success(t('financeTransactions.similarEditPrompt.success', { count: affectedCount }));
          onSuccess?.();
        } catch (propagationError) {
          toast.error(
            extractErrorMessage(
              propagationError,
              'A categoria foi atualizada, mas não foi possível aplicar a alteração às transações semelhantes',
            ),
          );
        }
      }

      if (preview && similarEditBehavior === 'prompt' && categoryId) {
        setPendingSimilarEditPrompt({
          transactionId: tx.id,
          similarCount: preview.similarCount,
          matchDescription: preview.matchDescription,
          input: { categoryId },
        });
      }

      if (categoryId === null) {
        window.setTimeout(() => {
          setForceNoCategoryByTransactionId((prev) => {
            if (!prev[tx.id]) return prev;
            const latest = transactionsRef.current.find((t) => t.id === tx.id);
            if (latest?.category) {
              const next = { ...prev };
              delete next[tx.id];
              return next;
            }
            return prev;
          });
        }, 2500);
      }
    } catch (err) {
      if (categoryId === null) {
        setForceNoCategoryByTransactionId((prev) => {
          if (!prev[tx.id]) return prev;
          const next = { ...prev };
          delete next[tx.id];
          return next;
        });
      }
      toast.error(extractErrorMessage(err, 'Não foi possível atualizar a categoria'));
    } finally {
      setUpdatingCategoryTransactionId(null);
      endBusy();
    }
  }, [
    pickerTransaction,
    close,
    forceNoCategoryByTransactionId,
    beginBusy,
    endBusy,
    loadSimilarEditPreview,
    updateTransaction,
    applyTransactionEditToSimilar,
    bankingUserPreferences.ruleCreationMode,
    onSuccess,
    t,
    toast,
  ]);

  return {
    anchorEl,
    pickerTransaction,
    updatingCategoryTransactionId,
    forceNoCategoryByTransactionId,
    pendingSimilarEditPrompt,
    setPendingSimilarEditPrompt,
    applyingSimilarEdit,
    applyTransactionEditToSimilar,
    openPicker,
    close,
    handleSelectCategory,
  };
}
