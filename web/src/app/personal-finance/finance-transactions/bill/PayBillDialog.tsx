'use client';

import type { FinancialAccountsQuery } from '@/lib/graphql/operations/banking/queries.generated';
import React, { useCallback, useState } from 'react';
import {
  Box,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  TextField,
  Typography,
} from '@mui/material';
import { useMutation } from '@apollo/client/react';
import { PAY_BILL } from '@/lib/graphql/operations/banking/mutations';
import { PayBillMutation, PayBillMutationVariables } from '@/lib/graphql/operations/banking/mutations.generated';
import { CreditCardBill } from './billUtils';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../i18n';

interface PayBillDialogProps {
  open: boolean;
  bill: CreditCardBill | null;
  bankAccounts: NonNullable<FinancialAccountsQuery['financialAccounts']>;
  onClose: () => void;
  onSuccess: () => void;
}

export function PayBillDialog({ open, bill, bankAccounts, onClose, onSuccess }: PayBillDialogProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const [amount, setAmount] = useState('');
  const [sourceAccountId, setSourceAccountId] = useState('');
  const [error, setError] = useState<string | null>(null);

  const resetForm = useCallback(() => {
    setAmount(bill?.totalAmount != null ? String(bill.totalAmount) : '');
    setSourceAccountId('');
    setError(null);
  }, [bill]);

  const handleClose = useCallback(() => {
    resetForm();
    onClose();
  }, [onClose, resetForm]);

  const [payBill, { loading }] = useMutation<PayBillMutation, PayBillMutationVariables>(PAY_BILL, {
    onCompleted: () => {
      onSuccess();
      handleClose();
    },
    onError: (err) => setError(err.message),
  });

  const handleSubmit = () => {
    if (!bill) return;
    const parsed = parseFloat(amount.replace(',', '.'));
    if (isNaN(parsed) || parsed <= 0) {
      setError(t('financeTransactions.payBillDialog.invalidAmount'));
      return;
    }
    setError(null);
    payBill({
      variables: {
        billId: bill.id,
        amount: parsed,
        sourceAccountId: sourceAccountId || undefined,
      },
    });
  };

  return (
    <Dialog
      open={open}
      onClose={handleClose}
      maxWidth="xs"
      fullWidth
      TransitionProps={{ onEnter: resetForm }}
    >
      <DialogTitle>{t('financeTransactions.payBillDialog.title')}</DialogTitle>
      <DialogContent>
        <Box sx={{ pt: 1, display: 'flex', flexDirection: 'column', gap: 2 }}>
          <TextField
            label={t('financeTransactions.payBillDialog.amount')}
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            type="number"
            inputProps={{ min: 0.01, step: 0.01 }}
            fullWidth
            autoFocus
          />
          {bankAccounts.length > 0 && (
            <FormControl fullWidth>
              <InputLabel>{t('financeTransactions.payBillDialog.sourceAccount')}</InputLabel>
              <Select
                value={sourceAccountId}
                label={t('financeTransactions.payBillDialog.sourceAccount')}
                onChange={(e) => setSourceAccountId(e.target.value)}
              >
                <MenuItem value="">
                  {t('financeTransactions.payBillDialog.noSourceAccount')}
                </MenuItem>
                {bankAccounts.map((acc) => (
                  <MenuItem key={acc.id} value={acc.id}>
                    {acc.name}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          )}
          {error && (
            <Typography variant="caption" color="error">
              {error}
            </Typography>
          )}
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={loading}>
          {t('financeTransactions.actions.cancel')}
        </Button>
        <Button variant="contained" onClick={handleSubmit} disabled={loading || !bill}>
          {loading ? <CircularProgress size={20} /> : t('financeTransactions.billSummary.payBill')}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
