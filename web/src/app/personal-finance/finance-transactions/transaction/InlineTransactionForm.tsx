import { memo, useCallback, useEffect, useState } from 'react';
import {
  Box,
  Collapse,
  Divider,
  TableCell,
  TableRow,
  TextField,
  alpha,
  useTheme,
} from '@mui/material';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../i18n';
import { BaseMoneyInput } from '@/shared/components/ui/forms/form/MoneyInput';
import { TagsInput } from '@/shared/components/ui/forms/form/TagsInput';
import { CategorySelect } from '../category/CategorySelect';
import { SimilarEditDialog } from './SimilarEditDialog';
import { TypeSelector } from './TypeSelector';
import { FormFooter } from './FormFooter';
import { AccountSelect } from './AccountSelect';
import { useTransactionFormState } from './hooks/useTransactionFormState';
import { useTransactionSave } from './hooks/useTransactionSave';
import { inputBorderSx } from './inputBorderSx';
import type { Transaction } from '@/lib/graphql/types/__generated__/graphql';
import { TransactionType } from '@/lib/graphql/types/__generated__/graphql';

interface InlineTransactionFormProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
  onRefresh?: () => void;
  selectedAccountId: string | null;
  selectedMonth?: number;
  selectedYear?: number;
  initialValues?: Transaction;
  onExited?: () => void;
  onBusyStart?: () => void;
  onBusyEnd?: () => void;
}

export const InlineTransactionForm = memo(function InlineTransactionForm({
  isOpen,
  onClose,
  onSuccess,
  onRefresh,
  selectedAccountId,
  selectedMonth,
  selectedYear,
  initialValues,
  onExited,
  onBusyStart,
  onBusyEnd,
}: InlineTransactionFormProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();

  const form = useTransactionFormState({ isOpen, initialValues, selectedAccountId, selectedMonth, selectedYear });

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
    setCreditDebitType: form.setCreditDebitType,
    setCategoryId: form.setCategoryId,
    resetFormFields,
    onBusyStart,
    onBusyEnd,
    onSuccess,
    onClose,
    onRefresh,
  });

  const [shouldRender, setShouldRender] = useState(isOpen);
  useEffect(() => {
    if (isOpen) Promise.resolve().then(() => setShouldRender(true));
  }, [isOpen]);
  useEffect(() => {
    if (!isOpen && onExited) onExited();
  }, [isOpen, onExited]);

  if (!shouldRender) return null;

  const isBillPaymentEdit = initialValues?.type === TransactionType.BillPayment;

  return (
    <>
      <TableRow sx={{ position: 'relative', zIndex: 3 }}>
        <TableCell colSpan={form.colSpan} sx={{ p: 0, borderBottom: 'none', bgcolor: 'transparent' }}>
          <Collapse
            in={isOpen}
            timeout={250}
            appear
            onExited={() => {
              onExited?.();
            }}
            mountOnEnter
            unmountOnExit
          >
            <Box
              data-testid="inline-transaction-form"
              data-balance-adjustment-edit={form.isBalanceAdjustmentEdit ? 'true' : undefined}
              sx={{
                borderRadius: 1,
                border: 1,
                borderColor: 'divider',
                boxShadow: `0 10px 26px ${alpha(theme.palette.primary.main, 0.2)}`,
                overflow: 'hidden',
                bgcolor: 'background.paper',
              }}
            >
              <Box
                sx={{
                  display: 'grid',
                  gridTemplateColumns: form.gridTemplateColumns,
                  columnGap: 2.5,
                  alignItems: 'end',
                  px: 1,
                  pt: 2,
                  pb: 1,
                  '& > *': { minWidth: 0 },
                }}
              >
                {/* 1. Type */}
                <Box sx={{ maxWidth: 'fit-content' }}>
                  <TypeSelector
                    value={form.creditDebitType}
                    isReadonly={form.isBalanceAdjustmentEdit}
                    onChange={save.handleTypeChange}
                    t={t}
                  />
                </Box>

                {/* 2. Description */}
                <Box
                  sx={{ display: 'flex', alignItems: 'flex-end', width: '100%' }}
                  data-field-error={save.fieldErrors.description ? 'description' : undefined}
                >
                  <TextField
                    value={form.description}
                    onChange={(e) => form.setDescription(e.target.value)}
                    placeholder={t('financeTransactions.description')}
                    variant="standard"
                    fullWidth
                    required={!form.isBalanceAdjustmentEdit}
                    error={save.fieldErrors.description}
                    helperText={
                      save.fieldErrors.description
                        ? t('financeTransactions.validation.required')
                        : undefined
                    }
                    autoFocus={!form.isBalanceAdjustmentEdit}
                    multiline
                    inputProps={{ 'data-testid': 'tx-description-input', readOnly: form.isBalanceAdjustmentEdit }}
                    minRows={1}
                    maxRows={4}
                    InputProps={{
                      disableUnderline: true,
                      sx: inputBorderSx(form.inputBottomBorderSx, save.fieldErrors.description),
                    }}
                    sx={{
                      '& .MuiInputBase-input': { fontSize: '0.875rem' },
                      '& .MuiInputBase-inputMultiline': { padding: 0 },
                    }}
                  />
                </Box>

                {!form.isBalanceAdjustmentEdit && (
                  <>
                    {/* 3. Category */}
                    <Box
                      sx={{
                        display: 'flex',
                        justifyContent: 'center',
                        alignItems: 'flex-end',
                        width: '100%',
                        minWidth: 0,
                        overflow: 'hidden',
                        '& .MuiChip-root': { maxWidth: '100%' },
                      }}
                    >
                      <CategorySelect
                        value={form.categoryId}
                        onChange={form.setCategoryId}
                        categories={form.filteredCategories}
                        creditDebitType={
                          form.creditDebitType === 'TRANSFER_IN' || form.creditDebitType === 'TRANSFER_OUT'
                            ? 'TRANSFER'
                            : form.creditDebitType
                        }
                        placeholder={t('financeTransactions.category.label')}
                        disabled={isBillPaymentEdit}
                        helperText={
                          isBillPaymentEdit
                            ? t('financeTransactions.category.disabledBillPayment')
                            : undefined
                        }
                      />
                    </Box>

                    {/* 4. Tags */}
                    <Box sx={{ minWidth: 150, display: 'flex', alignItems: 'flex-end', width: '100%' }}>
                      <TagsInput
                        value={form.tags}
                        onChange={form.setTags}
                        options={form.availableTags}
                        placeholder={t('financeTransactions.tags')}
                        variant="standard"
                        InputProps={{ disableUnderline: true, sx: form.inputBottomBorderSx }}
                      />
                    </Box>
                  </>
                )}

                {/* 5. Account (conditional) */}
                {!selectedAccountId && (
                  <Box
                    sx={{ display: 'flex', alignItems: 'flex-end', width: '100%' }}
                    data-field-error={save.fieldErrors.account ? 'account' : undefined}
                  >
                    <AccountSelect
                      value={form.accountId}
                      accounts={form.accounts}
                      isReadonly={form.isBalanceAdjustmentEdit}
                      onChange={form.setAccountId}
                      error={save.fieldErrors.account}
                      helperText={
                        save.fieldErrors.account
                          ? t('financeTransactions.validation.required')
                          : undefined
                      }
                      inputBottomBorderSx={inputBorderSx(form.inputBottomBorderSx, save.fieldErrors.account)}
                    />
                  </Box>
                )}

                {/* 6. Amount */}
                <Box
                  sx={{
                    gridColumn: form.amountGridColumn,
                    minWidth: 100,
                    textAlign: 'right',
                    display: 'flex',
                    alignItems: 'flex-end',
                    justifyContent: 'flex-end',
                    width: '100%',
                  }}
                  data-field-error={save.fieldErrors.amount ? 'amount' : undefined}
                >
                  <BaseMoneyInput
                    value={form.amount}
                    onChange={(val) => form.setAmount(val as number | undefined)}
                    variant="standard"
                    required
                    error={save.fieldErrors.amount}
                    helperText={
                      save.fieldErrors.amount
                        ? t('financeTransactions.validation.required')
                        : undefined
                    }
                    autoFocus={form.isBalanceAdjustmentEdit}
                    data-testid="tx-amount-input"
                    InputProps={{
                      disableUnderline: true,
                      sx: {
                        fontSize: '0.875rem',
                        fontWeight: 'medium',
                        maxWidth: 180,
                        ml: 'auto',
                        ...inputBorderSx(form.inputBottomBorderSx, save.fieldErrors.amount),
                        '& input': { textAlign: 'right' },
                      },
                      onKeyDown: save.handleKeyDown,
                    }}
                  />
                </Box>
              </Box>

              <Divider sx={{ mt: 1, mb: 0.5 }} />

              <FormFooter
                date={form.date}
                merchantId={form.merchantId}
                initialValues={initialValues}
                isBalanceAdjustmentEdit={form.isBalanceAdjustmentEdit}
                isDateInFuture={form.isDateInFuture}
                isDateOutsideWindow={form.isDateOutsideWindow}
                minAllowedDate={form.minAllowedDate}
                maxAllowedDate={form.maxAllowedDate}
                creating={save.creating}
                updating={save.updating}
                applyingSimilarEdit={save.applyingSimilarEdit}
                isSaving={save.isSaving}
                showDateError={save.fieldErrors.date || form.isDateInvalidForSave}
                dateHelperText={
                  save.fieldErrors.date && !form.isDateInFuture && !form.isDateOutsideWindow
                    ? t('financeTransactions.validation.required')
                    : undefined
                }
                inputBottomBorderSx={inputBorderSx(
                  form.inputBottomBorderSx,
                  save.fieldErrors.date || form.isDateInvalidForSave,
                )}
                onDateChange={form.setDate}
                onMerchantChange={form.setMerchantId}
                onRefresh={onRefresh}
                onSuccess={onSuccess}
                onClose={onClose}
                onSave={save.handleSave}
                t={t}
              />
            </Box>
          </Collapse>
        </TableCell>
      </TableRow>

      <SimilarEditDialog
        open={Boolean(save.pendingSimilarEditPrompt)}
        onClose={save.handleDismissSimilarEditPrompt}
        onConfirm={save.handleConfirmSimilarEdit}
        similarCount={save.pendingSimilarEditPrompt?.similarCount ?? 0}
        loading={save.applyingSimilarEdit}
      />
    </>
  );
});
