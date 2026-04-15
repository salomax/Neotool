import { Box, alpha, useTheme } from '@mui/material';
import { memo } from 'react';
import dayjs from 'dayjs';
import { DatePicker, Divider } from '@/shared/ui/mui-imports';
import {
  APP_DATE_PICKER_VIEWS,
  appDatePickerDisplayFormatFromLanguageTag,
  mergeAppDatePickerSlotProps,
} from '@/shared/components/ui/forms/form';
import { i18n } from '@/shared/i18n';
import { Button } from '@/shared/components/ui/primitives/Button';
import { MerchantSelect } from './MerchantSelect';
import { AttachmentSection } from '../../components/AttachmentSection';
import { FINANCE_MOVING_WINDOW_YEARS } from '../constants/dateWindow';
import type { Transaction } from '@/lib/graphql/types/__generated__/graphql';

interface FormFooterProps {
  date: dayjs.Dayjs | null;
  merchantId: string | null;
  initialValues: Transaction | undefined;
  isBalanceAdjustmentEdit: boolean;
  isDateInvalidForSave: boolean;
  isDateInFuture: boolean;
  isDateOutsideWindow: boolean;
  minAllowedDate: dayjs.Dayjs;
  maxAllowedDate: dayjs.Dayjs;
  creating: boolean;
  updating: boolean;
  applyingSimilarEdit: boolean;
  canSave: boolean;
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
  isDateInvalidForSave,
  isDateInFuture,
  isDateOutsideWindow,
  minAllowedDate,
  maxAllowedDate,
  creating,
  updating,
  applyingSimilarEdit,
  canSave,
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
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, minWidth: 0 }}>
        <DatePicker
          format={appDatePickerDisplayFormatFromLanguageTag(i18n.language)}
          views={[...APP_DATE_PICKER_VIEWS]}
          value={date}
          onChange={(newValue: dayjs.Dayjs | null) => onDateChange(newValue)}
          readOnly={isBalanceAdjustmentEdit}
          minDate={minAllowedDate}
          maxDate={maxAllowedDate}
          enableAccessibleFieldDOMStructure={false}
          slotProps={mergeAppDatePickerSlotProps({
            textField: {
              hiddenLabel: true,
              size: 'small',
              variant: 'standard',
              inputProps: {
                'data-testid': 'tx-date-input',
                readOnly: isBalanceAdjustmentEdit,
              },
              error: isDateInvalidForSave,
              helperText: isDateInFuture
                ? t('financeTransactions.validation.dateNotInFuture')
                : isDateOutsideWindow
                  ? t('financeTransactions.validation.dateTooOld', { years: FINANCE_MOVING_WINDOW_YEARS })
                  : undefined,
              InputProps: {
                disableUnderline: true,
                sx: {
                  fontSize: '0.875rem',
                  flexDirection: 'row-reverse',
                  ...inputBottomBorderSx,
                  '& .MuiInputAdornment-root': { ml: 0, mr: 1 },
                },
              },
              sx: {
                width: 140,
                m: 0,
                '& .MuiInputBase-input': { textAlign: 'left', fontSize: '0.875rem', p: '0px 0 5px' },
              },
            },
          })}
        />

        {!isBalanceAdjustmentEdit && (
          <>
            <Divider orientation="vertical" flexItem sx={{ mx: 1.5, alignSelf: 'stretch' }} />
            <Box
              sx={{
                width: 'fit-content',
                maxWidth: 360,
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
              />
            </Box>
          </>
        )}
      </Box>

      {initialValues && !isBalanceAdjustmentEdit && (
        <Divider orientation="vertical" flexItem sx={{ mx: 2, alignSelf: 'stretch' }} />
      )}

      {initialValues && !isBalanceAdjustmentEdit && (
        <Box sx={{ flex: 1, display: 'flex' }}>
          <Box
            sx={{
              position: 'relative',
              display: 'flex',
              alignItems: 'center',
              minHeight: 32,
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
                position: 'absolute',
                left: '100%',
                top: '50%',
                transform: 'translateY(-50%)',
                ml: 1,
                width: 'max-content',
                maxWidth: 'none',
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
      )}

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
          disabled={!canSave}
          sx={{ boxShadow: theme.shadows[4], borderRadius: 1 }}
        >
          {t('actions.save')}
        </Button>
      </Box>
    </Box>
  );
});
