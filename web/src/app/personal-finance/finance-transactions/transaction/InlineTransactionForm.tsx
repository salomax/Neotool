import { memo, useEffect, useState } from 'react';
import {
  Box,
  Collapse,
  Divider,
  TableCell,
  TableRow,
  alpha,
  useTheme,
} from '@mui/material';
import { SimilarEditDialog } from './SimilarEditDialog';
import { FormFooter } from './FormFooter';
import type { Transaction } from '@/lib/graphql/types/__generated__/graphql';
import { TransactionFormFields } from './TransactionFormFields';
import { inputBorderSx } from './inputBorderSx';
import { useTransactionFormController, type TransactionFormProps } from './transactionFormShared';

interface InlineTransactionFormProps extends TransactionFormProps {
  initialValues?: Transaction;
  onExited?: () => void;
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
  const theme = useTheme();
  const controller = useTransactionFormController({
    isOpen,
    onClose,
    onSuccess,
    onRefresh,
    selectedAccountId,
    selectedMonth,
    selectedYear,
    initialValues,
    onBusyStart,
    onBusyEnd,
  });
  const { form, save, t } = controller;
  const contentColSpan = Math.max(1, form.colSpan - 1);

  const [shouldRender, setShouldRender] = useState(isOpen);
  useEffect(() => {
    if (isOpen) Promise.resolve().then(() => setShouldRender(true));
  }, [isOpen]);
  useEffect(() => {
    if (!isOpen && onExited) onExited();
  }, [isOpen, onExited]);

  if (!shouldRender) return null;

  return (
    <>
      <TableRow sx={{ position: 'relative', zIndex: 3 }}>
        <TableCell colSpan={contentColSpan} sx={{ p: 0, borderBottom: 'none', bgcolor: 'transparent' }}>
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
              <TransactionFormFields
                controller={controller}
                selectedAccountId={selectedAccountId}
                layout="inline"
              />

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
        <TableCell sx={{ p: 0, borderBottom: 'none', bgcolor: 'transparent' }} />
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
