import { Box, alpha, useTheme } from '@mui/material';
import { memo } from 'react';
import dayjs from 'dayjs';
import { Button } from '@/shared/components/ui/primitives/Button';
import type { Transaction } from '@/lib/graphql/types/__generated__/graphql';
import { TransactionFormMetaFields } from './TransactionFormMetaFields';

interface FormFooterProps {
  date: dayjs.Dayjs | null;
  merchantId: string | null;
  initialValues: Transaction | undefined;
  isBalanceAdjustmentEdit: boolean;
  isDateInFuture: boolean;
  isDateOutsideWindow: boolean;
  minAllowedDate: dayjs.Dayjs;
  maxAllowedDate: dayjs.Dayjs;
  creating: boolean;
  updating: boolean;
  applyingSimilarEdit: boolean;
  isSaving: boolean;
  showDateError: boolean;
  dateHelperText?: string;
  inputBottomBorderSx: object;
  onDateChange: (v: dayjs.Dayjs | null) => void;
  onMerchantChange: (id: string | null) => void;
  onRefresh?: () => void;
  onSuccess: () => void;
  onClose: () => void;
  onSave: () => void;
  t: (key: string, params?: Record<string, unknown>) => string;
}

export const FormFooter = memo(function FormFooter({
  date,
  merchantId,
  initialValues,
  isBalanceAdjustmentEdit,
  isDateInFuture,
  isDateOutsideWindow,
  minAllowedDate,
  maxAllowedDate,
  creating,
  updating,
  applyingSimilarEdit,
  isSaving,
  showDateError,
  dateHelperText,
  inputBottomBorderSx,
  onDateChange,
  onMerchantChange,
  onRefresh,
  onSuccess,
  onClose,
  onSave,
  t,
}: FormFooterProps) {
  const theme = useTheme();

  return (
    <Box sx={{ display: 'flex', py: 1.5, px: 1, alignItems: 'center', borderColor: 'divider' }}>
      <TransactionFormMetaFields
        layout="inline"
        date={date}
        merchantId={merchantId}
        initialValues={initialValues}
        isBalanceAdjustmentEdit={isBalanceAdjustmentEdit}
        isDateInFuture={isDateInFuture}
        isDateOutsideWindow={isDateOutsideWindow}
        minAllowedDate={minAllowedDate}
        maxAllowedDate={maxAllowedDate}
        showDateError={showDateError}
        dateHelperText={dateHelperText}
        inputBottomBorderSx={inputBottomBorderSx}
        onDateChange={onDateChange}
        onMerchantChange={onMerchantChange}
        onRefresh={onRefresh}
        onSuccess={onSuccess}
        t={t}
      />

      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, ml: 'auto' }}>
        <Button
          onClick={onClose}
          size="small"
          data-testid="tx-cancel-btn"
          sx={{
            color: theme.palette.text.disabled as string,
            borderRadius: 1,
            minWidth: 'auto',
            bgcolor: alpha(theme.palette.text.primary, 0.0),
            '&:hover': { bgcolor: alpha(theme.palette.text.primary, 0.1) },
          }}
        >
          {t('actions.cancel')}
        </Button>
        <Button
          variant="contained"
          onClick={onSave}
          data-testid="tx-save-btn"
          loading={creating || updating || applyingSimilarEdit}
          size="small"
          disabled={isSaving}
          sx={{ boxShadow: theme.shadows[4], borderRadius: 1 }}
        >
          {t('actions.save')}
        </Button>
      </Box>
    </Box>
  );
});
