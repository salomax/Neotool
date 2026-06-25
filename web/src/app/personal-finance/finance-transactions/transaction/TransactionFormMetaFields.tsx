import { memo } from 'react';
import { Box, Divider, Stack, Typography, alpha, useTheme } from '@mui/material';
import dayjs from 'dayjs';
import { MerchantSelect } from './MerchantSelect';
import { AttachmentSection } from '../../components/AttachmentSection';
import type { Transaction } from '@/lib/graphql/types/__generated__/graphql';
import { TransactionDateField } from './TransactionDateField';

interface TransactionFormMetaFieldsProps {
  layout: 'inline' | 'drawer';
  showDateField?: boolean;
  date: dayjs.Dayjs | null;
  merchantId: string | null;
  initialValues: Transaction | undefined;
  isBalanceAdjustmentEdit: boolean;
  isDateInFuture: boolean;
  isDateOutsideWindow: boolean;
  minAllowedDate: dayjs.Dayjs;
  maxAllowedDate: dayjs.Dayjs;
  showDateError: boolean;
  dateHelperText?: string;
  inputBottomBorderSx: object;
  onDateChange: (value: dayjs.Dayjs | null) => void;
  onMerchantChange: (id: string | null) => void;
  onRefresh?: () => void;
  onSuccess: () => void;
  t: (key: string, params?: Record<string, unknown>) => string;
}

export const TransactionFormMetaFields = memo(function TransactionFormMetaFields({
  layout,
  showDateField = true,
  date,
  merchantId,
  initialValues,
  isBalanceAdjustmentEdit,
  isDateInFuture,
  isDateOutsideWindow,
  minAllowedDate,
  maxAllowedDate,
  showDateError,
  dateHelperText,
  inputBottomBorderSx,
  onDateChange,
  onMerchantChange,
  onRefresh,
  onSuccess,
  t,
}: TransactionFormMetaFieldsProps) {
  const theme = useTheme();

  const datePicker = (
    <TransactionDateField
      layout={layout}
      date={date}
      isBalanceAdjustmentEdit={isBalanceAdjustmentEdit}
      isDateInFuture={isDateInFuture}
      isDateOutsideWindow={isDateOutsideWindow}
      minAllowedDate={minAllowedDate}
      maxAllowedDate={maxAllowedDate}
      showDateError={showDateError}
      dateHelperText={dateHelperText}
      inputBottomBorderSx={inputBottomBorderSx}
      onDateChange={onDateChange}
      t={t}
    />
  );

  const merchantField = !isBalanceAdjustmentEdit ? (
    <Box
      sx={{
        width: layout === 'inline' ? 'fit-content' : '100%',
        maxWidth: layout === 'inline' ? 360 : '100%',
        display: 'flex',
        alignItems: 'center',
        overflow: 'hidden',
        borderRadius: 1,
        '& .MuiChip-root': { maxWidth: '100%' },
      }}
    >
      <MerchantSelect
        value={merchantId}
        initialMerchant={initialValues?.merchant}
        onChange={onMerchantChange}
        placeholder={t('financeTransactions.merchant')}
        variant={layout === 'drawer' ? 'field' : 'chip'}
      />
    </Box>
  ) : null;

  const attachmentField =
    initialValues && !isBalanceAdjustmentEdit ? (
      <Box sx={{ flex: 1, display: 'flex', width: '100%' }}>
        <Box
          sx={{
            position: 'relative',
            display: 'flex',
            alignItems: 'center',
            minHeight: 32,
            width: layout === 'inline' ? 'auto' : '100%',
            px: 0.75,
            borderRadius: 1,
            border: 1,
            borderColor: alpha(theme.palette.primary.main, 0.5),
            bgcolor: 'transparent',
            overflow: 'visible',
            '&:hover': {
              borderColor: 'primary.main',
              bgcolor: alpha(theme.palette.primary.main, 0.04),
            },
            '&:focus-within': {
              borderColor: 'primary.main',
              boxShadow: `0 0 0 2px ${alpha(theme.palette.primary.main, 0.16)}`,
            },
            '& .MuiChip-root': {
              position: layout === 'inline' ? 'absolute' : 'static',
              left: layout === 'inline' ? '100%' : 'auto',
              top: layout === 'inline' ? '50%' : 'auto',
              transform: layout === 'inline' ? 'translateY(-50%)' : 'none',
              ml: layout === 'inline' ? 1 : 0,
              mt: layout === 'inline' ? 0 : 0.5,
              width: 'max-content',
              maxWidth: layout === 'inline' ? 'none' : '100%',
              borderRadius: `${(theme as any).custom?.radius?.sm ?? theme.shape.borderRadius}px`,
            },
            '& .MuiChip-label': { maxWidth: 'none' },
            '& .MuiChip-icon, & .MuiChip-icon svg': { color: 'primary.main' },
            '& .MuiStack-root > :not(style) + :not(style)': { marginLeft: '0 !important' },
            '& .MuiIconButton-root': { color: 'primary.main', marginLeft: '0 !important' },
          }}
        >
          <AttachmentSection
            transactionId={initialValues.id}
            attachments={initialValues.attachments ?? []}
            onChanged={onRefresh ?? onSuccess}
            compact
          />
        </Box>
      </Box>
    ) : null;

  if (layout === 'drawer') {
    return (
      <Stack spacing={2}>
        {showDateField ? (
          <Box>
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.75 }}>
              {t('financeTransactions.date')}
            </Typography>
            {datePicker}
          </Box>
        ) : null}
        {merchantField ? (
          <Box>
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.75 }}>
              {t('financeTransactions.merchant')}
            </Typography>
            {merchantField}
          </Box>
        ) : null}
        {attachmentField ? (
          <Box>
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.75 }}>
              {t('financeTransactions.transactionTable.attachments.ariaLabel')}
            </Typography>
            {attachmentField}
          </Box>
        ) : null}
      </Stack>
    );
  }

  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, minWidth: 0 }}>
      {showDateField ? datePicker : null}
      {!isBalanceAdjustmentEdit && (
        <>
          {showDateField ? <Divider orientation="vertical" flexItem sx={{ mx: 1.5, alignSelf: 'stretch' }} /> : null}
          {merchantField}
        </>
      )}
      {initialValues && !isBalanceAdjustmentEdit && (
        <>
          <Divider orientation="vertical" flexItem sx={{ mx: 2, alignSelf: 'stretch' }} />
          {attachmentField}
        </>
      )}
    </Box>
  );
});
