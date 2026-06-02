import { Alert, Box, Button, Stack, Typography } from '@mui/material';
import { memo } from 'react';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../../i18n';

interface ErrorStepProps {
  errorMessage: string | null;
  onRetry: () => void;
  onClose: () => void;
}

export const ErrorStep = memo(function ErrorStep({ errorMessage, onRetry, onClose }: ErrorStepProps) {
  const { t } = useTranslation(personalFinanceTranslations);

  return (
    <Box sx={{ p: 1 }}>
      <Typography variant="h6" sx={{ mb: 2 }}>
        {t('financeTransactions.billImport.errorStep.title')}
      </Typography>
      <Alert severity="error" sx={{ mb: 2 }}>
        {errorMessage ?? t('financeTransactions.billImport.errorStep.generic')}
      </Alert>
      <Stack direction="row" spacing={2}>
        <Button variant="outlined" onClick={onRetry}>
          {t('financeTransactions.billImport.errorStep.retry')}
        </Button>
        <Button variant="contained" onClick={onClose}>
          {t('financeTransactions.billImport.common.close')}
        </Button>
      </Stack>
    </Box>
  );
});
