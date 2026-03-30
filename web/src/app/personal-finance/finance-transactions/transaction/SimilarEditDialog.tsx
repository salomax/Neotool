import React, { useCallback, useState } from 'react';
import {
  Dialog,
  FormControlLabel,
  Checkbox,
  Typography,
  Box,
} from '@mui/material';
import { Button } from '@/shared/components/ui/primitives/Button';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../i18n';

interface SimilarEditDialogProps {
  open: boolean;
  onClose: () => void;
  onConfirm: (applyToExisting: boolean, applyToFuture: boolean) => void;
  similarCount: number;
  loading?: boolean;
}

export function SimilarEditDialog({
  open,
  onClose,
  onConfirm,
  similarCount,
  loading = false,
}: SimilarEditDialogProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const hasExisting = similarCount > 0;
  const [applyToExisting, setApplyToExisting] = useState(hasExisting);
  const [applyToFuture, setApplyToFuture] = useState(true);

  const handleEnter = useCallback(() => {
    setApplyToExisting(hasExisting);
    setApplyToFuture(true);
  }, [hasExisting]);

  const canConfirm = applyToExisting || applyToFuture;

  return (
    <Dialog
      open={open}
      onClose={onClose}
      maxWidth="xs"
      fullWidth
      TransitionProps={{ onEnter: handleEnter }}
      PaperProps={{ sx: { p: 2 } }}
    >
      <Box sx={{ px: 2, pt: 2 }}>
        <Typography variant="h6" sx={{ mb: 0.5 }}>
          {hasExisting
            ? t('financeTransactions.similarEditPrompt.title')
            : t('financeTransactions.similarEditPrompt.titleFutureOnly')}
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
          {t('financeTransactions.similarEditPrompt.description')}
        </Typography>

        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
          {hasExisting && (
            <FormControlLabel
              control={
                <Checkbox
                  checked={applyToExisting}
                  onChange={(e) => setApplyToExisting(e.target.checked)}
                  disabled={loading}
                  size="small"
                />
              }
              sx={{ alignItems: 'flex-start' }}
              label={
                <Box>
                  <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
                    {t('financeTransactions.similarEditPrompt.applyToExistingTitle', {
                      count: similarCount,
                    })}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    {t('financeTransactions.similarEditPrompt.applyToExistingDescription')}
                  </Typography>
                </Box>
              }
              componentsProps={{ typography: { component: 'div' } }}
            />
          )}

          <FormControlLabel
            control={
              <Checkbox
                checked={applyToFuture}
                onChange={(e) => setApplyToFuture(e.target.checked)}
                disabled={loading}
                size="small"
              />
            }
            sx={{ alignItems: 'flex-start' }}
            label={
              <Box>
                <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
                  {t('financeTransactions.similarEditPrompt.applyToFutureTitle')}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  {t('financeTransactions.similarEditPrompt.applyToFutureDescription')}
                </Typography>
              </Box>
            }
            componentsProps={{ typography: { component: 'div' } }}
          />
        </Box>

        <Box sx={{ mt: 5 }}>
          <Button
            onClick={() => onConfirm(applyToExisting, applyToFuture)}
            color="primary"
            variant="contained"
            loading={loading}
            disabled={!canConfirm}
            fullWidth
            sx={{ mb: 1 }}
          >
            {t('financeTransactions.similarEditPrompt.confirmFull')}
          </Button>
          <Button onClick={onClose} variant="text" color="primary" disabled={loading} fullWidth>
            {t('financeTransactions.similarEditPrompt.cancel')}
          </Button>
        </Box>
      </Box>
    </Dialog>
  );
}
