import { useCallback } from 'react';
import { useMutation } from '@apollo/client/react';
import { APPLY_TRANSACTION_EDIT_TO_SIMILAR } from '@/lib/graphql/operations/banking/mutations';
import { useToast } from '@/shared/providers';
import { extractErrorMessage } from '@/shared/utils/error';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../../i18n';

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

interface UseSimilarEditOptions {
  pendingSimilarEditPrompt: PendingSimilarEditPrompt | null;
  setPendingSimilarEditPrompt: (v: PendingSimilarEditPrompt | null) => void;
  onSuccess?: () => void;
  beginBusy: () => void;
  endBusy: () => void;
}

export function useSimilarEdit({
  pendingSimilarEditPrompt,
  setPendingSimilarEditPrompt,
  onSuccess,
  beginBusy,
  endBusy,
}: UseSimilarEditOptions) {
  const { t } = useTranslation(personalFinanceTranslations);
  const toast = useToast();
  const [applyTransactionEditToSimilar, { loading: applyingSimilarEdit }] =
    useMutation<ApplySimilarEditResult>(APPLY_TRANSACTION_EDIT_TO_SIMILAR);

  const handleConfirmSimilarEdit = useCallback(
    async (applyToExisting: boolean, applyToFuture: boolean) => {
      if (!pendingSimilarEditPrompt) return;
      try {
        beginBusy();
        const result = await applyTransactionEditToSimilar({
          variables: {
            id: pendingSimilarEditPrompt.transactionId,
            matchDescription: pendingSimilarEditPrompt.matchDescription,
            input: pendingSimilarEditPrompt.input,
            applyToFuture,
            applyToExisting,
          },
        });
        const affectedCount =
          result.data?.applyTransactionEditToSimilar?.affectedCount ?? pendingSimilarEditPrompt.similarCount;
        toast.success(t('financeTransactions.similarEditPrompt.success', { count: affectedCount }));
        setPendingSimilarEditPrompt(null);
        onSuccess?.();
      } catch (err) {
        toast.error(extractErrorMessage(err, 'Não foi possível aplicar a alteração às transações semelhantes'));
      } finally {
        endBusy();
      }
    },
    [pendingSimilarEditPrompt, applyTransactionEditToSimilar, beginBusy, endBusy, setPendingSimilarEditPrompt, onSuccess, t, toast],
  );

  const handleDismissSimilarEditPrompt = useCallback(() => {
    setPendingSimilarEditPrompt(null);
  }, [setPendingSimilarEditPrompt]);

  return { applyingSimilarEdit, handleConfirmSimilarEdit, handleDismissSimilarEditPrompt };
}
