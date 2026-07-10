import { Box, LinearProgress, Typography, alpha, useTheme } from '@mui/material';
import { EventRepeat as FrequencyIcon } from '@mui/icons-material';
import { memo } from 'react';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../i18n';
import type { FrequencyTotalItem } from './types';
import { interactiveRowSx, makeKeyDownHandler } from './interactiveRowUtils';
import type { RecurringFrequency } from '@/lib/graphql/types/__generated__/graphql';

interface FrequencyProgressRowProps {
  item: FrequencyTotalItem;
  nameFontWeight: number;
  percentFontWeight: number;
  dimmed?: boolean;
  onFrequencyClick?: (frequency: RecurringFrequency) => void;
  ariaLabelPrefix?: string;
}

export const FrequencyProgressRow = memo(function FrequencyProgressRow({
  item,
  nameFontWeight,
  percentFontWeight,
  dimmed = false,
  onFrequencyClick,
  ariaLabelPrefix,
}: FrequencyProgressRowProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();
  const trackBg = alpha(theme.palette.text.primary, 0.08);
  const disabledFg = theme.palette.text.disabled;
  const disabledBar = theme.palette.action.disabledBackground;
  const disabledBarTrack = alpha(theme.palette.text.primary, 0.06);
  const barColor = theme.palette.primary.main;
  const label = t(`recurringPayments.frequency.${item.frequency}`);
  const interactive = Boolean(onFrequencyClick) && !dimmed;
  const handleActivate = interactive ? () => onFrequencyClick!(item.frequency) : undefined;

  return (
    <Box
      onClick={handleActivate}
      onKeyDown={interactive ? makeKeyDownHandler(handleActivate!) : undefined}
      role={interactive ? 'button' : dimmed ? 'presentation' : undefined}
      tabIndex={interactive ? 0 : undefined}
      aria-disabled={dimmed || undefined}
      aria-label={interactive && ariaLabelPrefix ? `${ariaLabelPrefix}: ${label}` : undefined}
      sx={interactiveRowSx(theme, interactive, dimmed)}
    >
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 2 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, minWidth: 0 }}>
          <FrequencyIcon sx={{ fontSize: 18, color: dimmed ? disabledFg : barColor, flexShrink: 0 }} />
          <Typography variant="body2" sx={{ fontWeight: nameFontWeight, color: dimmed ? disabledFg : 'text.primary' }} noWrap>
            {label}
          </Typography>
        </Box>
        <Typography
          variant="body2"
          sx={{ fontWeight: percentFontWeight, whiteSpace: 'nowrap', color: dimmed ? disabledFg : 'text.primary' }}
        >
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
          bgcolor: dimmed ? disabledBarTrack : trackBg,
          '& .MuiLinearProgress-bar': {
            bgcolor: dimmed ? disabledBar : barColor,
            borderRadius: 2,
          },
        }}
      />
    </Box>
  );
});
