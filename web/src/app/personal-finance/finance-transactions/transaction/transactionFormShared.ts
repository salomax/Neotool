import { useCallback } from 'react';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../i18n';
import { useTransactionFormState, type TransactionFormState } from './hooks/useTransactionFormState';
import { useTransactionSave, type TransactionSaveState } from './hooks/useTransactionSave';
import type { CreditDebitType, Transaction } from '@/lib/graphql/types/__generated__/graphql';
import { TransactionType } from '@/lib/graphql/types/__generated__/graphql';
import type { CreditDebitTypeExtended } from './TypeSelector';

export interface TransactionSuccessPayload {
  action: 'create' | 'update';
  transactionId: string;
  date: string;
}

export interface TransactionCreateDraft {
  creditDebitType?: CreditDebitTypeExtended;
  amount?: number;
  description?: string;
  date?: string;
  accountId?: string | null;
  categoryId?: string | null;
  merchantId?: string | null;
  tags?: string[];
}

export interface TransactionCreateSubmitInput {
  financialAccountId: string;
  creditDebitType: CreditDebitType;
  type: TransactionType;
  amount: number;
  date: string;
  description: string;
  categoryId?: string;
  merchantId?: string;
  tags?: string[];
}

export interface TransactionFormProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: (payload?: TransactionSuccessPayload) => void | Promise<void>;
  onRefresh?: () => void;
  selectedAccountId: string | null;
  selectedMonth?: number;
  selectedYear?: number;
  initialValues?: Transaction;
  initialDraft?: TransactionCreateDraft;
  fixedCreditDebitType?: CreditDebitTypeExtended;
  onCreateSubmit?: (input: TransactionCreateSubmitInput) => Promise<TransactionSuccessPayload | void>;
  onBusyStart?: () => void;
  onBusyEnd?: () => void;
}

export interface TransactionFormController {
  form: TransactionFormState;
  save: TransactionSaveState;
  isBillPaymentEdit: boolean;
  t: (key: string, params?: Record<string, unknown>) => string;
}

export function useTransactionFormController({
  isOpen,
  onClose,
  onSuccess,
  onRefresh,
  selectedAccountId,
  selectedMonth,
  selectedYear,
  initialValues,
  initialDraft,
  fixedCreditDebitType,
  onCreateSubmit,
  onBusyStart,
  onBusyEnd,
}: TransactionFormProps): TransactionFormController {
  const { t } = useTranslation(personalFinanceTranslations);

  const form = useTransactionFormState({
    isOpen,
    initialValues,
    initialDraft,
    fixedCreditDebitType,
    selectedAccountId,
    selectedMonth,
    selectedYear,
  });

  const resetFormFields = useCallback(() => {
    form.setAmount(undefined);
    form.setDescription('');
    form.setDate(form.defaultCreateDate);
    form.setCategoryId(null);
    form.setMerchantId(null);
    form.setTags([]);
  }, [form]);

  const save = useTransactionSave({
    isOpen,
    amount: form.amount,
    date: form.date,
    accountId: form.accountId,
    requiresAccountSelection: !selectedAccountId,
    isBalanceAdjustmentEdit: form.isBalanceAdjustmentEdit,
    description: form.description,
    isDateInvalidForSave: form.isDateInvalidForSave,
    creditDebitType: form.creditDebitType,
    initialValues,
    categoryId: form.categoryId,
    merchantId: form.merchantId,
    tags: form.tags,
    onCreateSubmit,
    fixedCreditDebitType,
    setCreditDebitType: form.setCreditDebitType,
    setCategoryId: form.setCategoryId,
    resetFormFields,
    onBusyStart,
    onBusyEnd,
    onSuccess,
    onClose,
    onRefresh,
  });

  return {
    form,
    save,
    isBillPaymentEdit: initialValues?.type === TransactionType.BillPayment,
    t,
  };
}
