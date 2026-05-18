'use client';

import { useEffect, useMemo, useState } from 'react';
import {
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Typography,
} from '@mui/material';
import { Button } from '@/shared/components/ui/primitives/Button';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs';
import dayjs, { type Dayjs } from 'dayjs';
import 'dayjs/locale/pt-br';
import { useTranslation as useReactI18nTranslation } from 'react-i18next';
import type { RecurringPaymentRecord } from './RecurringPaymentForm';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../i18n';
import {
  APP_DATE_PICKER_VIEWS,
  appDatePickerDisplayFormatFromLanguageTag,
  getDatePickerOutlinedInputSx,
  mergeAppDatePickerSlotProps,
  TextField,
} from '@/shared/components/ui/forms/form';
import { toStartOfDayLocal } from '@/shared/utils/date/dateOnly';

const DATE_PICKER_TEXT_FIELD_SLOTS = { textField: TextField } as const;

interface ConfirmPaymentDialogProps {
  row: RecurringPaymentRecord | null;
  loading?: boolean;
  onClose: () => void;
  onConfirm: (paymentDate: Date) => void;
}

export function ConfirmPaymentDialog({
  row,
  loading = false,
  onClose,
  onConfirm,
}: ConfirmPaymentDialogProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const { t: tCommon, i18n: i18nInstance } = useReactI18nTranslation('common');
  const [dateValue, setDateValue] = useState<Dayjs | null>(dayjs());

  const { adapterLocale, dateFormat } = useMemo(() => {
    const lang = i18nInstance.language?.toLowerCase() ?? 'pt';
    const isPt = lang.startsWith('pt');
    return {
      adapterLocale: isPt ? 'pt-br' : 'en',
      dateFormat: appDatePickerDisplayFormatFromLanguageTag(lang),
    };
  }, [i18nInstance.language]);

  const localeText = useMemo(
    () => ({
      fieldDayPlaceholder: () => tCommon('datePicker.fieldDay'),
      fieldMonthPlaceholder: () => tCommon('datePicker.fieldMonth'),
      fieldYearPlaceholder: () => tCommon('datePicker.fieldYear'),
    }),
    [tCommon],
  );

  const maxDate = useMemo(() => dayjs().endOf('day'), []);

  useEffect(() => {
    if (row) setDateValue(dayjs());
  }, [row?.id]);

  return (
    <Dialog
      open={Boolean(row)}
      onClose={() => {
        if (!loading) onClose();
      }}
      maxWidth="xs"
      fullWidth
    >
      <DialogTitle>{t('recurringPayments.confirmPaymentDialog.title')}</DialogTitle>
      <DialogContent>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          {t('recurringPayments.confirmPaymentDialog.description', { name: row?.name ?? '' })}
        </Typography>
        <LocalizationProvider
          dateAdapter={AdapterDayjs}
          adapterLocale={adapterLocale}
          localeText={localeText}
        >
          <DatePicker
            label={t('recurringPayments.confirmPaymentDialog.dateLabel')}
            format={dateFormat}
            views={[...APP_DATE_PICKER_VIEWS]}
            value={dateValue}
            onChange={setDateValue}
            maxDate={maxDate}
            disableFuture
            disabled={loading}
            enableAccessibleFieldDOMStructure={false}
            slots={DATE_PICKER_TEXT_FIELD_SLOTS}
            slotProps={mergeAppDatePickerSlotProps({
              textField: {
                fullWidth: true,
                sx: getDatePickerOutlinedInputSx('medium'),
              },
            })}
          />
        </LocalizationProvider>
      </DialogContent>
      <DialogActions>
        <Button variant="outlined" onClick={onClose} disabled={loading}>
          {t('actions.cancel')}
        </Button>
        <Button
          variant="contained"
          loading={loading}
          onClick={() => dateValue && onConfirm(toStartOfDayLocal(dateValue.toDate()))}
          disabled={!dateValue}
        >
          {t('recurringPayments.confirmPaymentDialog.confirm')}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
