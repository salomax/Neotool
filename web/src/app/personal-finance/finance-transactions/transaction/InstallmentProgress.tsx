import { Box, Typography } from '@mui/material';
import { Stack, alpha, useTheme } from '@mui/material';
import { memo } from 'react';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../i18n';

interface InstallmentProgressProps {
  installmentNumber: number;
  totalInstallments: number;
  fontSize?: string;
  lineHeight?: number;
}

export const InstallmentProgress = memo(function InstallmentProgress({
  installmentNumber,
  totalInstallments,
  fontSize = '0.7rem',
  lineHeight = 1.35,
}: InstallmentProgressProps) {
  const theme = useTheme();
  const { t } = useTranslation(personalFinanceTranslations);
  const filledColor = theme.palette.primary.main;

  return (
    <Stack spacing={0.5} sx={{ maxWidth: 200 }}>
      <Typography variant="caption" color="text.secondary" sx={{ fontSize, lineHeight }}>
        {installmentNumber} of {totalInstallments}
      </Typography>
      <Box
        role="progressbar"
        aria-valuemin={1}
        aria-valuemax={totalInstallments}
        aria-valuenow={Math.min(Math.max(installmentNumber, 1), totalInstallments)}
        aria-label={t('financeTransactions.transactionTable.installments.progressAriaLabel', {
          current: installmentNumber,
          total: totalInstallments,
        })}
        sx={{ display: 'flex', gap: '2px', height: 6 }}
      >
        {Array.from({ length: totalInstallments }, (_, i) => {
          const idx = i + 1;
          const isCompleted = idx < installmentNumber;
          const isCurrent = idx === installmentNumber;
          const bgColor = isCompleted
            ? alpha(filledColor, 0.55)
            : isCurrent
              ? filledColor
              : alpha(theme.palette.text.primary, 0.08);

          return (
            <Box
              key={idx}
              sx={{
                flex: 1,
                minWidth: 4,
                bgcolor: bgColor,
                borderRadius:
                  idx === 1 ? '6px 0 0 6px' : idx === totalInstallments ? '0 6px 6px 0' : 2,
              }}
            />
          );
        })}
      </Box>
    </Stack>
  );
});
