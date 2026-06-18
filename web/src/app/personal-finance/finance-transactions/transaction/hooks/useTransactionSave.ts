import { useCallback, useEffect, useMemo, useState, type KeyboardEvent } from 'react';
import { useLazyQuery, useMutation } from '@apollo/client/react';
import dayjs from 'dayjs';
import {
  APPLY_TRANSACTION_EDIT_TO_SIMILAR,
  CREATE_TRANSACTION,
  UPDATE_TRANSACTION,
} from '@/lib/graphql/operations/banking/mutations';
import { SIMILAR_TRANSACTION_EDIT_PREVIEW } from '@/lib/graphql/operations/banking/queries';
import { CreditDebitType, TransactionType, type Transaction } from '@/lib/graphql/types/__generated__/graphql';
import { formatDateOnly } from '@/shared/utils/date/dateOnly';
import { extractErrorMessage } from '@/shared/utils/error';
import { usePreferences, useToast } from '@/shared/providers';
import { resolveSimilarEditBehavior } from '../similarEditPreference';
import type { CreditDebitTypeExtended } from '../TypeSelector';

// ── Types ─────────────────────────────────────────────────────────────────────

type SimilarEditPreview = {
  similarCount: number;
  matchDescription: string;
  supportsFuturePropagation: boolean;
};

type PendingSimilarEditPrompt = {
  transactionId: string;
  similarCount: number;
  matchDescription: string;
  input: {
    description?: string;
    categoryId?: string | null;
    tags?: string[];
  };
};

type ApplySimilarEditResult = {
  applyTransactionEditToSimilar: {
    affectedCount: number;
    futureRuleCreated: boolean;
  };
};

// ── Pure helpers ──────────────────────────────────────────────────────────────

function normalizeTags(values: string[]): string[] {
  return [...new Set(values.map((v) => v.trim()).filter(Boolean))].sort();
}

function shouldPromptForSimilarEdit(
  transaction: Transaction,
  nextDescription: string,
  nextCategoryId: string | null,
  nextTags: string[],
): boolean {
  const descriptionChanged = transaction.description !== nextDescription;
  const categoryChanged =
    (transaction.category?.id ?? null) !== nextCategoryId && nextCategoryId !== null;
  const tagsChanged =
    JSON.stringify(normalizeTags(transaction.tags ?? [])) !== JSON.stringify(normalizeTags(nextTags));
  return descriptionChanged || categoryChanged || tagsChanged;
}

function buildSimilarEditInput(
  transaction: Transaction,
  nextDescription: string,
  nextCategoryId: string | null,
  nextTags: string[],
): PendingSimilarEditPrompt['input'] {
  const input: PendingSimilarEditPrompt['input'] = {};
  if (transaction.description !== nextDescription) input.description = nextDescription;
  if ((transaction.category?.id ?? null) !== nextCategoryId && nextCategoryId !== null) {
    input.categoryId = nextCategoryId;
  }
  if (JSON.stringify(normalizeTags(transaction.tags ?? [])) !== JSON.stringify(normalizeTags(nextTags))) {
    input.tags = nextTags;
  }
  return input;
}

// ── Hook ──────────────────────────────────────────────────────────────────────

export type TransactionFieldErrors = {
  amount: boolean;
  description: boolean;
  account: boolean;
  date: boolean;
};

interface UseTransactionSaveProps {
  isOpen: boolean;
  amount: number | undefined;
  date: dayjs.Dayjs | null;
  accountId: string;
  requiresAccountSelection: boolean;
  isBalanceAdjustmentEdit: boolean;
  description: string;
  isDateInvalidForSave: boolean;
  creditDebitType: CreditDebitTypeExtended;
  initialValues: Transaction | undefined;
  categoryId: string | null;
  merchantId: string | null;
  tags: string[];
  setCreditDebitType: (v: CreditDebitTypeExtended) => void;
  setCategoryId: (v: string | null) => void;
  resetFormFields: () => void;
  onBusyStart?: () => void;
  onBusyEnd?: () => void;
  onSuccess: () => void;
  onClose: () => void;
  onRefresh?: () => void;
}

function computeFieldErrors({
  amount,
  description,
  accountId,
  requiresAccountSelection,
  isBalanceAdjustmentEdit,
  date,
  isDateInvalidForSave,
}: Pick<
  UseTransactionSaveProps,
  | 'amount'
  | 'description'
  | 'accountId'
  | 'requiresAccountSelection'
  | 'isBalanceAdjustmentEdit'
  | 'date'
  | 'isDateInvalidForSave'
>): TransactionFieldErrors {
  return {
    amount: !amount,
    description: !isBalanceAdjustmentEdit && !description.trim(),
    account: requiresAccountSelection && !accountId,
    date: !date || !date.isValid() || isDateInvalidForSave,
  };
}

function hasFieldErrors(errors: TransactionFieldErrors): boolean {
  return errors.amount || errors.description || errors.account || errors.date;
}

export interface TransactionSaveState {
  creating: boolean;
  updating: boolean;
  applyingSimilarEdit: boolean;
  pendingSimilarEditPrompt: PendingSimilarEditPrompt | null;
  isSaving: boolean;
  fieldErrors: TransactionFieldErrors;
  handleSave: () => Promise<void>;
  handleKeyDown: (e: KeyboardEvent) => void;
  handleTypeChange: (value: CreditDebitTypeExtended) => void;
  handleConfirmSimilarEdit: (applyToExisting: boolean, applyToFuture: boolean) => Promise<void>;
  handleDismissSimilarEditPrompt: () => Promise<void>;
}

export function useTransactionSave({
  isOpen,
  amount,
  date,
  accountId,
  requiresAccountSelection,
  isBalanceAdjustmentEdit,
  description,
  isDateInvalidForSave,
  creditDebitType,
  initialValues,
  categoryId,
  merchantId,
  tags,
  setCreditDebitType,
  setCategoryId,
  resetFormFields,
  onBusyStart,
  onBusyEnd,
  onSuccess,
  onClose,
  onRefresh,
}: UseTransactionSaveProps): TransactionSaveState {
  const { bankingUserPreferences } = usePreferences();
  const toast = useToast();

  const [createTransaction, { loading: creating }] = useMutation(CREATE_TRANSACTION);
  const [updateTransaction, { loading: updating }] = useMutation(UPDATE_TRANSACTION);
  const [loadSimilarEditPreview] = useLazyQuery<{ similarTransactionEditPreview: SimilarEditPreview }>(
    SIMILAR_TRANSACTION_EDIT_PREVIEW,
    { fetchPolicy: 'no-cache' },
  );
  const [applyTransactionEditToSimilar, { loading: applyingSimilarEdit }] =
    useMutation<ApplySimilarEditResult>(APPLY_TRANSACTION_EDIT_TO_SIMILAR);

  const [pendingSimilarEditPrompt, setPendingSimilarEditPrompt] = useState<PendingSimilarEditPrompt | null>(null);
  const [submitAttempted, setSubmitAttempted] = useState(false);

  useEffect(() => {
    if (isOpen) setSubmitAttempted(false);
  }, [isOpen, initialValues?.id]);

  const validationErrors = useMemo(
    () =>
      computeFieldErrors({
        amount,
        description,
        accountId,
        requiresAccountSelection,
        isBalanceAdjustmentEdit,
        date,
        isDateInvalidForSave,
      }),
    [
      amount,
      description,
      accountId,
      requiresAccountSelection,
      isBalanceAdjustmentEdit,
      date,
      isDateInvalidForSave,
    ],
  );

  const fieldErrors = useMemo(
    () =>
      submitAttempted
        ? validationErrors
        : { amount: false, description: false, account: false, date: false },
    [submitAttempted, validationErrors],
  );

  const handleTypeChange = useCallback((value: CreditDebitTypeExtended) => {
    setCreditDebitType(value);
    setCategoryId(null);
  }, [setCreditDebitType, setCategoryId]);

  const handleSave = useCallback(async () => {
    setSubmitAttempted(true);
    if (hasFieldErrors(validationErrors)) return;
    if (amount === undefined || date === null) return;

    const isTransferIn = creditDebitType === 'TRANSFER_IN';
    const isTransferOut = creditDebitType === 'TRANSFER_OUT';
    const isTransferShortcut = isTransferIn || isTransferOut;
    const finalCreditDebitType = isTransferIn
      ? CreditDebitType.Credit
      : isTransferOut
        ? CreditDebitType.Debit
        : (creditDebitType as CreditDebitType);

    const formattedAmount = Math.abs(amount);
    const formattedDate = formatDateOnly(date.toDate());

    let updateType: TransactionType | undefined = undefined;
    if (isTransferShortcut && initialValues?.type !== TransactionType.Transfer) {
      updateType = TransactionType.Transfer;
    } else if (!isTransferShortcut && initialValues?.type === TransactionType.Transfer) {
      updateType = finalCreditDebitType === CreditDebitType.Credit
        ? TransactionType.OtherIncome
        : TransactionType.OtherExpense;
    }

    const updateInput = isBalanceAdjustmentEdit
      ? { amount: formattedAmount }
      : {
          date: formattedDate,
          amount: formattedAmount,
          description,
          categoryId: categoryId || undefined,
          merchantId: merchantId ?? null,
          creditDebitType: finalCreditDebitType,
          ...(updateType ? { type: updateType } : {}),
          tags,
        };

    try {
      onBusyStart?.();
      if (initialValues) {
        const shouldPrompt =
          !isBalanceAdjustmentEdit &&
          shouldPromptForSimilarEdit(initialValues, description, categoryId, tags);
        const previewResult = shouldPrompt
          ? await loadSimilarEditPreview({ variables: { id: initialValues.id } })
          : undefined;

        await updateTransaction({ variables: { id: initialValues.id, input: updateInput } });

        const preview = previewResult?.data?.similarTransactionEditPreview;
        const similarEditBehavior = preview
          ? resolveSimilarEditBehavior(bankingUserPreferences.ruleCreationMode, preview.supportsFuturePropagation)
          : 'skip';

        if (preview && similarEditBehavior === 'auto') {
          try {
            await applyTransactionEditToSimilar({
              variables: {
                id: initialValues.id,
                matchDescription: preview.matchDescription,
                input: buildSimilarEditInput(initialValues, description, categoryId, tags),
                applyToFuture: true,
                applyToExisting: true,
              },
            });
          } catch (error) {
            console.error('Failed to auto-apply transaction edit to similar transactions:', error);
          }
        }

        if (preview && similarEditBehavior === 'prompt') {
          onRefresh?.();
          setPendingSimilarEditPrompt({
            transactionId: initialValues.id,
            similarCount: preview.similarCount,
            matchDescription: preview.matchDescription,
            input: buildSimilarEditInput(initialValues, description, categoryId, tags),
          });
          return;
        }
      } else {
        const createType = isTransferShortcut
          ? TransactionType.Transfer
          : finalCreditDebitType === CreditDebitType.Credit
            ? TransactionType.OtherIncome
            : TransactionType.OtherExpense;

        await createTransaction({
          variables: {
            input: {
              date: formattedDate,
              amount: formattedAmount,
              description,
              financialAccountId: accountId,
              categoryId: categoryId || undefined,
              merchantId: merchantId || undefined,
              creditDebitType: finalCreditDebitType,
              type: createType,
              tags: tags.length > 0 ? tags : undefined,
            },
          },
        });
      }

      if (!initialValues) resetFormFields();

      try {
        await Promise.resolve(onSuccess?.());
      } catch (refreshError) {
        console.error('Failed to refresh after save:', refreshError);
      }
      onClose();
    } catch (error) {
      console.error('Failed to save transaction:', error);
      toast.error(extractErrorMessage(error, 'Não foi possível salvar a transação'));
    } finally {
      onBusyEnd?.();
    }
  }, [
    validationErrors,
    amount, date, accountId, isBalanceAdjustmentEdit, description,
    creditDebitType, initialValues, categoryId, merchantId, tags,
    loadSimilarEditPreview, updateTransaction, applyTransactionEditToSimilar, createTransaction,
    bankingUserPreferences.ruleCreationMode,
    resetFormFields, onBusyStart, onBusyEnd, onSuccess, onClose, onRefresh, toast,
  ]);

  const handleKeyDown = useCallback(
    (e: KeyboardEvent) => {
      if (e.key !== 'Enter') return;
      e.preventDefault();
      if (!(creating || updating)) {
        void handleSave();
      }
    },
    [creating, updating, handleSave],
  );

  const handleConfirmSimilarEdit = useCallback(
    async (applyToExisting: boolean, applyToFuture: boolean) => {
      if (!pendingSimilarEditPrompt) return;
      try {
        onBusyStart?.();
        await applyTransactionEditToSimilar({
          variables: {
            id: pendingSimilarEditPrompt.transactionId,
            matchDescription: pendingSimilarEditPrompt.matchDescription,
            input: pendingSimilarEditPrompt.input,
            applyToFuture,
            applyToExisting,
          },
        });
        setPendingSimilarEditPrompt(null);
        try {
          await Promise.resolve(onSuccess?.());
        } catch (refreshError) {
          console.error('Failed to refresh after similar edit:', refreshError);
        }
        onClose();
      } catch (error) {
        console.error('Failed to apply transaction edit to similar transactions:', error);
      } finally {
        onBusyEnd?.();
      }
    },
    [pendingSimilarEditPrompt, applyTransactionEditToSimilar, onBusyStart, onBusyEnd, onSuccess, onClose],
  );

  const handleDismissSimilarEditPrompt = useCallback(async () => {
    setPendingSimilarEditPrompt(null);
    try {
      await Promise.resolve(onSuccess?.());
    } catch (refreshError) {
      console.error('Failed to refresh after dismiss similar edit:', refreshError);
    }
    onClose();
  }, [onSuccess, onClose]);

  const isSaving = creating || updating || applyingSimilarEdit;

  return {
    creating,
    updating,
    applyingSimilarEdit,
    pendingSimilarEditPrompt,
    isSaving,
    fieldErrors,
    handleSave,
    handleKeyDown,
    handleTypeChange,
    handleConfirmSimilarEdit,
    handleDismissSimilarEditPrompt,
  };
}
