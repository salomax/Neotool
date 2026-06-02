import { Box, LinearProgress, Typography } from '@mui/material';
import { memo } from 'react';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../../i18n';

interface ProcessingStepProps {
  jobStatus?: string | null;
  statusLabel: (status: string) => string;
}

export const ProcessingStep = memo(function ProcessingStep({ jobStatus, statusLabel }: ProcessingStepProps) {
  const { t } = useTranslation(personalFinanceTranslations);

  return (
    <Box sx={{ p: 1 }}>
      <Typography variant="h6" sx={{ mb: 2 }}>
        {t('financeTransactions.billImport.processingStep.title')}
      </Typography>
      <LinearProgress sx={{ mb: 2 }} />
      <Typography variant="body2" color="text.secondary">
        {t('financeTransactions.billImport.processingStep.description')}
      </Typography>
      {jobStatus && (
        <Typography variant="caption" color="text.disabled" display="block" sx={{ mt: 1 }}>
          {t('financeTransactions.billImport.processingStep.status', { status: statusLabel(jobStatus) })}
        </Typography>
      )}
    </Box>
  );
});
