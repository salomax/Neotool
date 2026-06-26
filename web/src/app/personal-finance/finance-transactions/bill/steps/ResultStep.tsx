import { Alert, Box } from '@mui/material';
import { memo } from 'react';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../../i18n';

interface ResultStepProps {
  included: number;
  total: number;
}

export const ResultStep = memo(function ResultStep({ included, total }: ResultStepProps) {
  const { t } = useTranslation(personalFinanceTranslations);

  return (
    <Box sx={{ p: 1 }}>
      <Alert severity="success" sx={{ mb: 2 }}>
        {t('financeTransactions.billImport.resultStep.success', { included, total })}
      </Alert>
    </Box>
  );
});
