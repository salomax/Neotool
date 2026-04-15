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
import dayjs from 'dayjs';
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
import type { Transaction } from '@/lib/graphql/types/__generated__/graphql';

interface InlineTransactionFormProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
  onRefresh?: () => void;
  selectedAccountId: string | null;
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
  initialValues,
  onExited,
  onBusyStart,
  onBusyEnd,
}: InlineTransactionFormProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();

  const form = useTransactionFormState({ isOpen, initialValues, selectedAccountId });

  const resetFormFields = useCallback(() => {
    form.setAmount(undefined);
    form.setDescription('');
    form.setDate(dayjs());
    form.setCategoryId(null);
    form.setMerchantId(null);
    form.setTags([]);
  }, [form]);

  const save = useTransactionSave({
    amount: form.amount,
    date: form.date,
    accountId: form.accountId,
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
    if (isOpen) setShouldRender(true);
  }, [isOpen]);
  useEffect(() => {
    if (!isOpen && onExited) onExited();
  }, [isOpen, onExited]);

  if (!shouldRender) return null;

  return (
    <>
      <TableRow sx={{ position: 'relative', zIndex: 3 }}>
        <TableCell colSpan={form.colSpan} sx={{ p: 0, borderBottom: 'none', bgcolor: 'transparent' }}>
          <Collapse
            in={isOpen}
            timeout={250}
            appear
            onExited={() => {
              setShouldRender(false);
              onExited?.();
            }}
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
                <Box sx={{ display: 'flex', alignItems: 'flex-end', width: '100%' }}>
                  <TextField
                    value={form.description}
                    onChange={(e) => form.setDescription(e.target.value)}
                    placeholder={t('financeTransactions.description')}
                    variant="standard"
                    fullWidth
                    required={!form.isBalanceAdjustmentEdit}
                    autoFocus={!form.isBalanceAdjustmentEdit}
                    multiline
                    inputProps={{ 'data-testid': 'tx-description-input', readOnly: form.isBalanceAdjustmentEdit }}
                    minRows={1}
                    maxRows={4}
                    InputProps={{ disableUnderline: true, sx: form.inputBottomBorderSx }}
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
                        placeholder={t('financeTransactions.category')}
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
                  <Box sx={{ display: 'flex', alignItems: 'flex-end', width: '100%' }}>
                    <AccountSelect
                      value={form.accountId}
                      accounts={form.accounts}
                      isReadonly={form.isBalanceAdjustmentEdit}
                      onChange={form.setAccountId}
                      inputBottomBorderSx={form.inputBottomBorderSx}
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
                >
                  <BaseMoneyInput
                    value={form.amount}
                    onChange={(val) => form.setAmount(val as number | undefined)}
                    variant="standard"
                    required
                    autoFocus={form.isBalanceAdjustmentEdit}
                    data-testid="tx-amount-input"
                    InputProps={{
                      disableUnderline: true,
                      sx: {
                        fontSize: '0.875rem',
                        fontWeight: 'medium',
                        maxWidth: 180,
                        ml: 'auto',
                        ...form.inputBottomBorderSx,
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
                isDateInvalidForSave={form.isDateInvalidForSave}
                isDateInFuture={form.isDateInFuture}
                isDateOutsideWindow={form.isDateOutsideWindow}
                minAllowedDate={form.minAllowedDate}
                maxAllowedDate={form.maxAllowedDate}
                creating={save.creating}
                updating={save.updating}
                applyingSimilarEdit={save.applyingSimilarEdit}
                canSave={save.canSave}
                inputBottomBorderSx={form.inputBottomBorderSx}
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
