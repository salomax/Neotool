'use client';

import { useEffect, useState } from 'react';
import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  TextField,
  Typography,
} from '@mui/material';
import type { RecurringPaymentRecord } from './RecurringPaymentForm';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../i18n';

interface ConfirmPaymentDialogProps {
  row: RecurringPaymentRecord | null;
  onClose: () => void;
  onConfirm: (paymentDate: Date) => void;
}

export function ConfirmPaymentDialog({ row, onClose, onConfirm }: ConfirmPaymentDialogProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const todayIso = new Date().toISOString().slice(0, 10);
  const [dateValue, setDateValue] = useState(todayIso);

  useEffect(() => {
    if (row) setDateValue(new Date().toISOString().slice(0, 10));
  }, [row?.id]);

  return (
    <Dialog open={Boolean(row)} onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle>{t('recurringPayments.confirmPaymentDialog.title')}</DialogTitle>
      <DialogContent>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          {t('recurringPayments.confirmPaymentDialog.description', { name: row?.name ?? '' })}
        </Typography>
        <TextField
          label={t('recurringPayments.confirmPaymentDialog.dateLabel')}
          type="date"
          fullWidth
          value={dateValue}
          onChange={(e) => setDateValue(e.target.value)}
          InputLabelProps={{ shrink: true }}
          inputProps={{ max: todayIso }}
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t('actions.cancel')}</Button>
        <Button
          variant="contained"
          onClick={() => onConfirm(new Date(dateValue + 'T12:00:00'))}
          disabled={!dateValue}
        >
          {t('recurringPayments.confirmPaymentDialog.confirm')}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
