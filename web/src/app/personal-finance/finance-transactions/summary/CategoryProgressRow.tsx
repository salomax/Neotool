import { Box, LinearProgress, Typography, alpha, useTheme } from '@mui/material';
import { memo } from 'react';
import { CreditDebitType } from '@/lib/graphql/types/__generated__/graphql';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../i18n';
import { CategoryIcon } from '../category/CategoryIcon';
import type { CategoryTotalItem, CategoryFilterSelection } from './types';
import { isDimmedByTypeFilter } from './dimmedByTypeFilter';
import { interactiveRowSx, makeKeyDownHandler } from './interactiveRowUtils';

export function isUncategorizedCategory(item: CategoryTotalItem): boolean {
  return item.uncategorized;
}

export function matchesCategoryFilter(
  item: CategoryTotalItem,
  activeCategoryFilter: CategoryFilterSelection | null,
): boolean {
  if (!activeCategoryFilter) return true;
  if (item.uncategorized) return activeCategoryFilter.uncategorized;
  return !activeCategoryFilter.uncategorized && item.category?.id === activeCategoryFilter.categoryId;
}

interface CategoryProgressRowProps {
  item: CategoryTotalItem;
  nameFontWeight: number;
  percentFontWeight: number;
  dimmed?: boolean;
  categorySection?: 'expenses' | 'income';
  activeCreditDebitTypeFilter?: CreditDebitType | null;
  onCategoryClick?: (selection: CategoryFilterSelection) => void;
}

export const CategoryProgressRow = memo(function CategoryProgressRow({
  item,
  nameFontWeight,
  percentFontWeight,
  dimmed = false,
  categorySection,
  activeCreditDebitTypeFilter,
  onCategoryClick,
}: CategoryProgressRowProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();
  const isUncategorized = isUncategorizedCategory(item);
  const barColor = isUncategorized
    ? theme.palette.grey[500]
    : (item.category?.color ?? theme.palette.primary.main);
  const trackBg = alpha(theme.palette.text.primary, 0.08);
  const disabledFg = theme.palette.text.disabled;
  const disabledBar = theme.palette.action.disabledBackground;
  const disabledBarTrack = alpha(theme.palette.text.primary, 0.06);

  const dimmedByType = isDimmedByTypeFilter(categorySection, activeCreditDebitTypeFilter);
  const rowDimmed = dimmed || dimmedByType;
  const interactive = Boolean(onCategoryClick) && !rowDimmed;

  const categorySelection: CategoryFilterSelection = {
    categoryId: item.category?.id ?? null,
    uncategorized: isUncategorized,
  };
  const categoryName = isUncategorized
    ? t('financeTransactions.monthSummary.uncategorized')
    : item.category?.name;

  const handleActivate = interactive ? () => onCategoryClick!(categorySelection) : undefined;

  return (
    <Box
      onClick={handleActivate}
      onKeyDown={interactive ? makeKeyDownHandler(handleActivate!) : undefined}
      role={interactive ? 'button' : rowDimmed ? 'presentation' : undefined}
      tabIndex={interactive ? 0 : undefined}
      aria-disabled={rowDimmed || undefined}
      aria-label={
        interactive && categoryName
          ? `${t('financeTransactions.monthSummary.a11y.filterByCategory')}: ${categoryName}`
          : undefined
      }
      sx={interactiveRowSx(theme, interactive, rowDimmed)}
    >
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 2 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, minWidth: 0 }}>
          <CategoryIcon
            iconName={item.category?.icon}
            color={
              rowDimmed
                ? disabledFg
                : isUncategorized
                  ? theme.palette.grey[600]
                  : (item.category?.color ?? undefined)
            }
            size={18}
            sx={{ flexShrink: 0 }}
          />
          <Typography
            variant="body2"
            sx={{ fontWeight: nameFontWeight, color: rowDimmed ? disabledFg : 'text.primary' }}
            noWrap
          >
            {categoryName}
          </Typography>
        </Box>
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
            bgcolor: rowDimmed ? disabledBar : barColor,
            borderRadius: 2,
          },
        }}
      />
    </Box>
  );
});
