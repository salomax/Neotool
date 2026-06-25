import { memo } from 'react';
import { Box } from '@mui/material';
import { Drawer } from '@/shared/components/ui/layout/Drawer';
import { SimilarEditDialog } from './SimilarEditDialog';
import { TransactionDateField } from './TransactionDateField';
import { TransactionFormFields } from './TransactionFormFields';
import { TransactionFormMetaFields } from './TransactionFormMetaFields';
import { inputBorderSx } from './inputBorderSx';
import { useTransactionFormController, type TransactionFormProps } from './transactionFormShared';
import type { Transaction } from '@/lib/graphql/types/__generated__/graphql';

interface MobileTransactionDrawerProps extends TransactionFormProps {
  initialValues?: Transaction;
  onExited?: () => void;
}

export const MobileTransactionDrawer = memo(function MobileTransactionDrawer({
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
}: MobileTransactionDrawerProps) {
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

  return (
    <>
      <Drawer
        open={isOpen}
        onClose={onClose}
        anchor="bottom"
        height="100%"
        slotProps={onExited ? { transition: { onExited } } : undefined}
        ariaLabel={
          initialValues
            ? t('financeTransactions.transactionTable.rowActions.edit')
            : t('financeTransactions.quickAdd')
        }
      >
        <Drawer.Header
          title={
            initialValues
              ? t('financeTransactions.transactionTable.rowActions.edit')
              : t('financeTransactions.quickAdd')
          }
          closeButtonAriaLabel={t('financeTransactions.actions.close')}
        />
        <Drawer.Body
          sx={{
            px: 2,
            py: 2.5,
          }}
        >
          <Box data-testid="mobile-transaction-drawer">
            <TransactionFormFields
              controller={controller}
              selectedAccountId={selectedAccountId}
              layout="drawer"
              drawerDateField={
                <TransactionDateField
                  layout="drawer"
                  date={form.date}
                  isBalanceAdjustmentEdit={form.isBalanceAdjustmentEdit}
                  isDateInFuture={form.isDateInFuture}
                  isDateOutsideWindow={form.isDateOutsideWindow}
                  minAllowedDate={form.minAllowedDate}
                  maxAllowedDate={form.maxAllowedDate}
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
                  t={t}
                />
              }
            />
            <Box sx={{ mt: 2.5 }}>
              <TransactionFormMetaFields
                layout="drawer"
                showDateField={false}
                date={form.date}
                merchantId={form.merchantId}
                initialValues={initialValues}
                isBalanceAdjustmentEdit={form.isBalanceAdjustmentEdit}
                isDateInFuture={form.isDateInFuture}
                isDateOutsideWindow={form.isDateOutsideWindow}
                minAllowedDate={form.minAllowedDate}
                maxAllowedDate={form.maxAllowedDate}
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
                t={t}
              />
            </Box>
          </Box>
        </Drawer.Body>
        <Drawer.Footer sx={{ p: 2, bgcolor: 'background.paper' }}>
          <Drawer.ActionButtons
            cancelText={t('actions.cancel')}
            submitText={t('actions.save')}
            onCancel={onClose}
            onSubmit={() => {
              void save.handleSave();
            }}
            submitDisabled={save.isSaving}
            submitting={save.creating || save.updating || save.applyingSimilarEdit}
            cancelTestId="tx-cancel-btn"
            submitTestId="tx-save-btn"
          />
        </Drawer.Footer>
      </Drawer>

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
