import { memo, useState } from 'react';
import { CategoryApplicableTo, CreditDebitType, TransactionType } from '@/lib/graphql/types/__generated__/graphql';
import type { Transaction } from '@/lib/graphql/types/__generated__/graphql';
import { ConfirmationDialog } from '@/shared/components/ui/feedback';
import { FilePreviewDialog, type PreviewAttachment } from '../../components/FilePreviewDialog';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../i18n';
import { CategoryPicker } from '../category/CategoryPicker';
import { AppliedTransactionRulesDrawer } from './AppliedTransactionRulesDrawer';
import { TransactionRuleDrawer } from '../transaction-rules/TransactionRuleDrawer';
import { SimilarEditDialog } from './SimilarEditDialog';
import { ApplyRuleToHistoricalDialog } from './ApplyRuleToHistoricalDialog';
import { useLazyQuery, useMutation } from '@apollo/client/react';
import { APPLY_TRANSACTION_RULE_TO_HISTORICAL } from '@/lib/graphql/operations/banking/mutations';
import { TRANSACTION_RULE_HISTORICAL_MATCH_COUNT } from '@/lib/graphql/operations/banking/queries';
import { useToast } from '@/shared/providers';
import { extractErrorMessage } from '@/shared/utils/error';

interface TransactionDialogsProps {
  // Category picker
  categoryPickerAnchorEl: HTMLElement | null;
  categoryPickerTransaction: Transaction | null;
  onCloseCategoryPicker: () => void;
  onSelectCategory: (categoryId: string | null) => void;

  // Applied rules drawer
  appliedRulesTransaction: Transaction | null;
  onCloseAppliedRules: () => void;

  // Create rule drawer
  createRuleDraft: { pattern: string; applicableTo: CategoryApplicableTo } | null;
  onCloseCreateRule: () => void;
  onSuccess?: () => void;

  // Delete dialog
  transactionToRemove: Transaction | null;
  removing: boolean;
  onCancelDelete: () => void;
  onConfirmDelete: () => void;

  // Similar edit dialog
  pendingSimilarEditPrompt: {
    transactionId: string;
    similarCount: number;
    matchDescription: string;
    input: { categoryId?: string };
  } | null;
  applyingSimilarEdit: boolean;
  onDismissSimilarEdit: () => void;
  onConfirmSimilarEdit: (applyToExisting: boolean, applyToFuture: boolean) => void;

  // Attachment preview
  previewAttachments: PreviewAttachment[] | null;
  previewIndex: number;
  removingAttachmentId: string | null;
  onRemoveAttachment: (att: PreviewAttachment) => void;
  onClosePreview: () => void;
}

export const TransactionDialogs = memo(function TransactionDialogs({
  categoryPickerAnchorEl,
  categoryPickerTransaction,
  onCloseCategoryPicker,
  onSelectCategory,
  appliedRulesTransaction,
  onCloseAppliedRules,
  createRuleDraft,
  onCloseCreateRule,
  onSuccess,
  transactionToRemove,
  removing,
  onCancelDelete,
  onConfirmDelete,
  pendingSimilarEditPrompt,
  applyingSimilarEdit,
  onDismissSimilarEdit,
  onConfirmSimilarEdit,
  previewAttachments,
  previewIndex,
  removingAttachmentId,
  onRemoveAttachment,
  onClosePreview,
}: TransactionDialogsProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const toast = useToast();

  const [loadRuleHistoricalMatchCount] = useLazyQuery<{ transactionRuleHistoricalMatchCount: number }>(
    TRANSACTION_RULE_HISTORICAL_MATCH_COUNT,
    { fetchPolicy: 'no-cache' },
  );
  const [applyTransactionRuleToHistorical, { loading: applyingRuleToHistory }] = useMutation(
    APPLY_TRANSACTION_RULE_TO_HISTORICAL,
  );
  const [applyRuleToHistoryPrompt, setApplyRuleToHistoryPrompt] = useState<{
    ruleId: string;
    matchCount: number;
  } | null>(null);

  return (
    <>
      <CategoryPicker
        value={categoryPickerTransaction?.category?.id ?? null}
        onChange={onSelectCategory}
        creditDebitType={
          categoryPickerTransaction?.type === TransactionType.Transfer
            ? 'TRANSFER'
            : categoryPickerTransaction?.creditDebitType === CreditDebitType.Credit
              ? 'CREDIT'
              : 'DEBIT'
        }
        autoClearSelection={false}
        anchorEl={categoryPickerAnchorEl}
        onClose={onCloseCategoryPicker}
      />

      <AppliedTransactionRulesDrawer
        open={Boolean(appliedRulesTransaction)}
        transaction={appliedRulesTransaction}
        onClose={onCloseAppliedRules}
      />

      <TransactionRuleDrawer
        open={Boolean(createRuleDraft)}
        rule={null}
        initialPattern={createRuleDraft?.pattern ?? ''}
        initialApplicableTo={createRuleDraft?.applicableTo}
        onClose={onCloseCreateRule}
        onSaved={(savedRule) => {
          onCloseCreateRule();
          void onSuccess?.();
          void (async () => {
            try {
              const result = await loadRuleHistoricalMatchCount({ variables: { id: savedRule.id } });
              const matchCount = result.data?.transactionRuleHistoricalMatchCount ?? 0;
              if (matchCount > 0) {
                setApplyRuleToHistoryPrompt({ ruleId: savedRule.id, matchCount });
              }
            } catch (err) {
              toast.error(extractErrorMessage(err, t('financeTransactions.applyRuleToHistoryPrompt.toastError')));
            }
          })();
        }}
      />

      <ConfirmationDialog
        open={Boolean(transactionToRemove)}
        onClose={onCancelDelete}
        onConfirm={onConfirmDelete}
        title={t('financeTransactions.deleteConfirmation.title')}
        message={t('financeTransactions.deleteConfirmation.message')}
        cancelText={t('actions.cancel')}
        confirmText={t('actions.delete')}
        confirmColor="error"
        loading={removing}
      />

      <SimilarEditDialog
        open={Boolean(pendingSimilarEditPrompt)}
        onClose={onDismissSimilarEdit}
        onConfirm={onConfirmSimilarEdit}
        similarCount={pendingSimilarEditPrompt?.similarCount ?? 0}
        loading={applyingSimilarEdit}
      />

      <ApplyRuleToHistoricalDialog
        open={Boolean(applyRuleToHistoryPrompt)}
        matchCount={applyRuleToHistoryPrompt?.matchCount ?? 0}
        loading={applyingRuleToHistory}
        onClose={() => setApplyRuleToHistoryPrompt(null)}
        onConfirm={() => {
          const payload = applyRuleToHistoryPrompt;
          if (!payload) return;
          void (async () => {
            try {
              const res = await applyTransactionRuleToHistorical({ variables: { id: payload.ruleId } });
              const affected = (res.data as any)?.applyTransactionRuleToHistorical?.affectedCount as
                | number
                | undefined;
              toast.success(
                t('financeTransactions.applyRuleToHistoryPrompt.toastSuccess', {
                  count: typeof affected === 'number' ? affected : payload.matchCount,
                }),
              );
              setApplyRuleToHistoryPrompt(null);
              void onSuccess?.();
            } catch (err) {
              toast.error(extractErrorMessage(err, t('financeTransactions.applyRuleToHistoryPrompt.toastError')));
            }
          })();
        }}
      />

      <FilePreviewDialog
        attachments={previewAttachments}
        initialIndex={previewIndex}
        removingAttachmentId={removingAttachmentId}
        onRemove={onRemoveAttachment}
        onClose={onClosePreview}
      />
    </>
  );
});
