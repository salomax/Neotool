import { Box, LinearProgress, Typography, alpha, useTheme } from '@mui/material';
import { memo } from 'react';
import { CreditDebitType } from '@/lib/graphql/types/__generated__/graphql';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../i18n';
import type { TagTotalItem } from './types';
import { isDimmedByTypeFilter } from './dimmedByTypeFilter';
import { interactiveRowSx, makeKeyDownHandler } from './interactiveRowUtils';

interface TagProgressRowProps {
  item: TagTotalItem;
  nameFontWeight: number;
  percentFontWeight: number;
  dimmed?: boolean;
  categorySection?: 'expenses' | 'income';
  activeCreditDebitTypeFilter?: CreditDebitType | null;
  onTagClick?: (tag: string) => void;
}

export const TagProgressRow = memo(function TagProgressRow({
  item,
  nameFontWeight,
  percentFontWeight,
  dimmed = false,
  categorySection,
  activeCreditDebitTypeFilter,
  onTagClick,
}: TagProgressRowProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();
  const trackBg = alpha(theme.palette.text.primary, 0.08);
  const disabledFg = theme.palette.text.disabled;
  const disabledBar = theme.palette.action.disabledBackground;
  const disabledBarTrack = alpha(theme.palette.text.primary, 0.06);

  const dimmedByType = isDimmedByTypeFilter(categorySection, activeCreditDebitTypeFilter);
  const rowDimmed = dimmed || dimmedByType;
  const interactive = Boolean(onTagClick) && !rowDimmed;

  const handleActivate = interactive ? () => onTagClick!(item.tag) : undefined;

  return (
    <Box
      onClick={handleActivate}
      onKeyDown={interactive ? makeKeyDownHandler(handleActivate!) : undefined}
      role={interactive ? 'button' : rowDimmed ? 'presentation' : undefined}
      tabIndex={interactive ? 0 : undefined}
      aria-disabled={rowDimmed || undefined}
      aria-label={
        interactive
          ? `${t('financeTransactions.monthSummary.a11y.filterByTag')}: #${item.tag}`
          : undefined
      }
      sx={interactiveRowSx(theme, interactive, rowDimmed)}
    >
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 2 }}>
        <Typography
          variant="body2"
          sx={{ fontWeight: nameFontWeight, color: rowDimmed ? disabledFg : 'text.primary', minWidth: 0 }}
          noWrap
        >
          #{item.tag}
        </Typography>
        <Typography
          variant="body2"
          sx={{ fontWeight: percentFontWeight, whiteSpace: 'nowrap', color: rowDimmed ? disabledFg : 'text.primary' }}
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
          bgcolor: rowDimmed ? disabledBarTrack : trackBg,
          '& .MuiLinearProgress-bar': {
            bgcolor: rowDimmed ? disabledBar : theme.palette.info.main,
            borderRadius: 2,
          },
        }}
      />
    </Box>
  );
});
