import { Alert, Box, Button } from '@mui/material';
import { memo } from 'react';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../../i18n';

interface ResultStepProps {
  included: number;
  total: number;
  onClose: () => void;
}

export const ResultStep = memo(function ResultStep({ included, total, onClose }: ResultStepProps) {
  const { t } = useTranslation(personalFinanceTranslations);

  return (
    <Box sx={{ p: 1 }}>
      <Alert severity="success" sx={{ mb: 2 }}>
        {t('financeTransactions.billImport.resultStep.success', { included, total })}
      </Alert>
      <Button variant="contained" fullWidth onClick={onClose}>
        {t('financeTransactions.billImport.common.close')}
      </Button>
    </Box>
  );
});
