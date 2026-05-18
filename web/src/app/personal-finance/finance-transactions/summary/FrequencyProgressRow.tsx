import { Box, LinearProgress, Typography, alpha, useTheme } from '@mui/material';
import { EventRepeat as FrequencyIcon } from '@mui/icons-material';
import { memo } from 'react';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../i18n';
import type { FrequencyTotalItem } from './types';

interface FrequencyProgressRowProps {
  item: FrequencyTotalItem;
  nameFontWeight: number;
  percentFontWeight: number;
}

export const FrequencyProgressRow = memo(function FrequencyProgressRow({
  item,
  nameFontWeight,
  percentFontWeight,
}: FrequencyProgressRowProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();
  const trackBg = alpha(theme.palette.text.primary, 0.08);
  const barColor = theme.palette.primary.main;
  const label = t(`recurringPayments.frequency.${item.frequency}`);

  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 2 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, minWidth: 0 }}>
          <FrequencyIcon sx={{ fontSize: 18, color: barColor, flexShrink: 0 }} />
          <Typography variant="body2" sx={{ fontWeight: nameFontWeight, color: 'text.primary' }} noWrap>
            {label}
          </Typography>
        </Box>
        <Typography variant="body2" sx={{ fontWeight: percentFontWeight, whiteSpace: 'nowrap', color: 'text.primary' }}>
          {Math.round(item.percentage)}%
        </Typography>
      </Box>
      <LinearProgress
        variant="determinate"
        value={Math.min(item.percentage, 100)}
        sx={{
          mt: 1,
          height: 6,
          borderRadius: 2,
          bgcolor: trackBg,
          '& .MuiLinearProgress-bar': {
            bgcolor: barColor,
            borderRadius: 2,
          },
        }}
      />
    </Box>
  );
});
