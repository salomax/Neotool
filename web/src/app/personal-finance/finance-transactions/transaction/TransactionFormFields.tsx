import { memo, type ReactNode } from 'react';
import { Box, Stack, TextField, Typography } from '@mui/material';
import { BaseMoneyInput } from '@/shared/components/ui/forms/form/MoneyInput';
import { TagsInput } from '@/shared/components/ui/forms/form/TagsInput';
import { CategorySelect } from '../category/CategorySelect';
import { TypeSelector } from './TypeSelector';
import { AccountSelect } from './AccountSelect';
import { inputBorderSx } from './inputBorderSx';
import type { TransactionFormController } from './transactionFormShared';

interface TransactionFormFieldsProps {
  controller: TransactionFormController;
  selectedAccountId: string | null;
  layout: 'inline' | 'drawer';
  drawerDateField?: ReactNode;
}

export const TransactionFormFields = memo(function TransactionFormFields({
  controller,
  selectedAccountId,
  layout,
  drawerDateField,
}: TransactionFormFieldsProps) {
  const { form, save, isBillPaymentEdit, t } = controller;

  const descriptionField = (
    <Box
      sx={{ display: 'flex', alignItems: 'flex-end', width: '100%' }}
      data-field-error={save.fieldErrors.description ? 'description' : undefined}
    >
      <TextField
        value={form.description}
        onChange={(event) => form.setDescription(event.target.value)}
        placeholder={t('financeTransactions.description')}
        variant="standard"
        fullWidth
        required={!form.isBalanceAdjustmentEdit}
        error={save.fieldErrors.description}
        helperText={save.fieldErrors.description ? t('financeTransactions.validation.required') : undefined}
        autoFocus={!form.isBalanceAdjustmentEdit}
        multiline
        inputProps={{ 'data-testid': 'tx-description-input', readOnly: form.isBalanceAdjustmentEdit }}
        minRows={1}
        maxRows={layout === 'drawer' ? 6 : 4}
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
  );

  const categoryField = form.isBalanceAdjustmentEdit ? null : (
    <Box
      sx={{
        display: 'flex',
        justifyContent: layout === 'inline' ? 'center' : 'flex-start',
        alignItems: 'flex-end',
        width: '100%',
        minWidth: 0,
        overflow: layout === 'inline' ? 'hidden' : 'visible',
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
        helperText={isBillPaymentEdit ? t('financeTransactions.category.disabledBillPayment') : undefined}
        variant={layout === 'drawer' ? 'field' : 'chip'}
      />
    </Box>
  );

  const tagsField = form.isBalanceAdjustmentEdit ? null : (
    <Box sx={{ minWidth: layout === 'inline' ? 150 : 0, display: 'flex', alignItems: 'flex-end', width: '100%' }}>
      <TagsInput
        value={form.tags}
        onChange={form.setTags}
        options={form.availableTags}
        placeholder={t('financeTransactions.tags')}
        variant="standard"
        InputProps={{ disableUnderline: true, sx: form.inputBottomBorderSx }}
      />
    </Box>
  );

  const accountField = selectedAccountId ? null : (
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
        helperText={save.fieldErrors.account ? t('financeTransactions.validation.required') : undefined}
        inputBottomBorderSx={inputBorderSx(form.inputBottomBorderSx, save.fieldErrors.account)}
        variant={layout === 'drawer' ? 'field' : 'standard'}
      />
    </Box>
  );

  const amountField = (
    <Box
      sx={{
        gridColumn: form.amountGridColumn,
        minWidth: layout === 'inline' ? 100 : 0,
        textAlign: layout === 'inline' ? 'right' : 'left',
        display: 'flex',
        alignItems: 'flex-end',
        justifyContent: layout === 'inline' ? 'flex-end' : 'stretch',
        width: '100%',
      }}
      data-field-error={save.fieldErrors.amount ? 'amount' : undefined}
    >
      <BaseMoneyInput
        value={form.amount}
        onChange={(value) => form.setAmount(value as number | undefined)}
        variant="standard"
        required
        error={save.fieldErrors.amount}
        helperText={save.fieldErrors.amount ? t('financeTransactions.validation.required') : undefined}
        autoFocus={form.isBalanceAdjustmentEdit}
        data-testid="tx-amount-input"
        InputProps={{
          disableUnderline: true,
          sx: {
            fontSize: '0.875rem',
            fontWeight: 'medium',
            maxWidth: layout === 'inline' ? 180 : '100%',
            ml: layout === 'inline' ? 'auto' : 0,
            ...inputBorderSx(form.inputBottomBorderSx, save.fieldErrors.amount),
            '& input': { textAlign: layout === 'inline' ? 'right' : 'left' },
          },
          onKeyDown: save.handleKeyDown,
        }}
      />
    </Box>
  );

  if (layout === 'drawer') {
    return (
      <Stack spacing={2.5}>
        <Box sx={{ width: '100%' }}>
          <TypeSelector
            value={form.creditDebitType}
            isReadonly={form.isBalanceAdjustmentEdit}
            onChange={save.handleTypeChange}
            t={t}
            variant="carousel"
          />
        </Box>
        {descriptionField}
        {amountField}
        {accountField ? (
          <Box>
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.75 }}>
              {t('financeTransactions.accountOrCard')}
            </Typography>
            {accountField}
          </Box>
        ) : null}
        {drawerDateField || categoryField ? (
          <Box
            sx={{
              display: 'grid',
              gridTemplateColumns: 'repeat(2, minmax(0, 1fr))',
              gap: 1.5,
              alignItems: 'start',
            }}
          >
            {drawerDateField ? (
              <Box>
                <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.75 }}>
                  {t('financeTransactions.date')}
                </Typography>
                {drawerDateField}
              </Box>
            ) : null}
            {categoryField ? (
              <Box>
                <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.75 }}>
                  {t('financeTransactions.category.label')}
                </Typography>
                {categoryField}
              </Box>
            ) : null}
          </Box>
        ) : null}
        {tagsField}
      </Stack>
    );
  }

  return (
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
      <Box sx={{ maxWidth: 'fit-content' }}>
        <TypeSelector
          value={form.creditDebitType}
          isReadonly={form.isBalanceAdjustmentEdit}
          onChange={save.handleTypeChange}
          t={t}
        />
      </Box>
      {descriptionField}
      {categoryField}
      {tagsField}
      {accountField}
      {amountField}
    </Box>
  );
});
