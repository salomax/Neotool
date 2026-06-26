import { memo } from 'react';
import dayjs from 'dayjs';
import { DatePicker } from '@/shared/ui/mui-imports';
import {
  APP_DATE_PICKER_VIEWS,
  appDatePickerDisplayFormatFromLanguageTag,
  mergeAppDatePickerSlotProps,
} from '@/shared/components/ui/forms/form';
import { i18n } from '@/shared/i18n';
import { FINANCE_MOVING_WINDOW_YEARS } from '../constants/dateWindow';

interface TransactionDateFieldProps {
  layout: 'inline' | 'drawer';
  date: dayjs.Dayjs | null;
  isBalanceAdjustmentEdit: boolean;
  isDateInFuture: boolean;
  isDateOutsideWindow: boolean;
  minAllowedDate: dayjs.Dayjs;
  maxAllowedDate: dayjs.Dayjs;
  showDateError: boolean;
  dateHelperText?: string;
  inputBottomBorderSx: object;
  onDateChange: (value: dayjs.Dayjs | null) => void;
  t: (key: string, params?: Record<string, unknown>) => string;
}

export const TransactionDateField = memo(function TransactionDateField({
  layout,
  date,
  isBalanceAdjustmentEdit,
  isDateInFuture,
  isDateOutsideWindow,
  minAllowedDate,
  maxAllowedDate,
  showDateError,
  dateHelperText,
  inputBottomBorderSx,
  onDateChange,
  t,
}: TransactionDateFieldProps) {
  return (
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
          fullWidth: layout === 'drawer',
          inputProps: {
            'data-testid': 'tx-date-input',
            readOnly: isBalanceAdjustmentEdit,
          },
          error: showDateError,
          helperText: isDateInFuture
            ? t('financeTransactions.validation.dateNotInFuture')
            : isDateOutsideWindow
              ? t('financeTransactions.validation.dateTooOld', { years: FINANCE_MOVING_WINDOW_YEARS })
              : dateHelperText,
          InputProps: {
            disableUnderline: true,
            sx: {
              fontSize: '0.875rem',
              flexDirection: 'row-reverse',
              ...(layout === 'drawer'
                ? {
                    px: 1.25,
                    py: 0.375,
                    borderRadius: 1,
                    border: '1px solid',
                    borderColor: showDateError ? 'error.main' : 'divider',
                    '&:hover': {
                      borderColor: showDateError ? 'error.main' : 'divider',
                    },
                    '&.Mui-focused': {
                      borderColor: showDateError ? 'error.main' : 'divider',
                    },
                  }
                : inputBottomBorderSx),
              '& .MuiInputAdornment-root': { ml: 0, mr: 1 },
            },
          },
          sx: {
            width: layout === 'inline' ? 140 : '100%',
            m: 0,
            '& .MuiInputBase-input': {
              textAlign: 'left',
              fontSize: '0.875rem',
              p: layout === 'drawer' ? '6px 0' : '0px 0 5px',
            },
          },
        },
      })}
    />
  );
});
