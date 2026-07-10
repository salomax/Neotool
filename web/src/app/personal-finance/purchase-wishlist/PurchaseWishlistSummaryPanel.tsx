'use client';

import { Box, Divider, LinearProgress, Skeleton, Stack, Typography, alpha, useTheme } from '@mui/material';
import { useMemo } from 'react';
import { QueryErrorState } from '@/shared/components/ui/feedback';
import { Label } from '@/shared/components/ui/primitives';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../i18n';
import type { CategoryTotalItem } from '../finance-transactions/summary/types';
import {
  CategoryProgressRow,
  isUncategorizedCategory,
  matchesCategoryFilter,
} from '../finance-transactions/summary/CategoryProgressRow';
import { interactiveRowSx, makeKeyDownHandler } from '../finance-transactions/summary/interactiveRowUtils';
import { WishlistItemPriority } from '@/lib/graphql/types/__generated__/graphql';
import type { WishlistCategoryFilterSelection, WishlistItemRow } from './wishlistRowModel';
import {
  computeWishlistSummaryMetrics,
  formatWishlistCurrency,
} from './wishlistRowModel';

type PurchaseWishlistSummaryPanelProps = {
  rows: WishlistItemRow[];
  loading?: boolean;
  error?: unknown;
  onRetry?: () => void;
  activePriorityFilter?: WishlistItemPriority | null;
  onPriorityClick?: (priority: WishlistItemPriority) => void;
  activeCategoryFilter?: WishlistCategoryFilterSelection | null;
  onCategoryClick?: (selection: WishlistCategoryFilterSelection) => void;
};

function priorityTextColor(priority: WishlistItemPriority): 'error.main' | 'warning.main' | 'success.main' {
  if (priority === 'HIGH') return 'error.main';
  if (priority === 'MEDIUM') return 'warning.main';
  return 'success.main';
}

type WishlistPriorityMetrics = {
  priority: WishlistItemPriority;
  count: number;
  totalEstimated: number;
  percentage: number;
};

export function PurchaseWishlistSummaryPanel({
  rows,
  loading,
  error,
  onRetry,
  activePriorityFilter = null,
  onPriorityClick,
  activeCategoryFilter = null,
  onCategoryClick,
}: PurchaseWishlistSummaryPanelProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();

  const metrics = useMemo(() => computeWishlistSummaryMetrics(rows), [rows]);
  const priorityItems = useMemo<WishlistPriorityMetrics[]>(
    () =>
      [WishlistItemPriority.High, WishlistItemPriority.Medium, WishlistItemPriority.Low].map((priority) => {
        const item = metrics.byPriority[priority];
        return {
          priority,
          count: item.count,
          totalEstimated: item.totalEstimated,
          percentage: metrics.totalEstimated > 0 ? (item.totalEstimated / metrics.totalEstimated) * 100 : 0,
        };
      }),
    [metrics.byPriority, metrics.totalEstimated],
  );
  const categoryItems = useMemo<CategoryTotalItem[]>(
    () =>
      metrics.byCategory.map((entry) => ({
        category: entry.category
          ? {
              id: entry.category.id,
              name: entry.category.name,
              icon: entry.category.icon,
              color: entry.category.color,
            }
          : null,
        uncategorized: entry.category == null,
        totalAmount: entry.totalEstimated,
        transactionCount: entry.count,
        percentage: entry.percentage,
      })),
    [metrics.byCategory],
  );

  if (error && onRetry) {
    return <QueryErrorState error={error} onRetry={onRetry} />;
  }

  if (loading) {
    return (
      <Stack spacing={2}>
        <Skeleton variant="rounded" height={56} />
        <Skeleton variant="rounded" height={92} />
        <Skeleton variant="rounded" height={120} />
      </Stack>
    );
  }

  return (
    <Stack spacing={2.5}>
      <Box>
        <Label>{t('purchaseWishlist.summary.totalEstimated')}</Label>
        <Typography variant="h3" sx={{ mt: 0.5, fontWeight: 700, color: 'text.primary' }}>
          {formatWishlistCurrency(metrics.totalEstimated)}
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 0.75 }}>
          {t('purchaseWishlist.summary.totalItems', { count: metrics.totalItems })}
        </Typography>
      </Box>

      <Divider />

      <Box>
        <Box sx={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 2, mb: 1.5 }}>
          <Label sx={{ mb: 0 }}>{t('purchaseWishlist.summary.byPriority')}</Label>
        </Box>
        <Stack spacing={1.25}>
          {priorityItems.map((item) => {
            const dimmed = activePriorityFilter !== null && activePriorityFilter !== item.priority;
            const interactive = Boolean(onPriorityClick) && !dimmed;
            const accentColor = priorityTextColor(item.priority);

            return (
              <Box
                key={item.priority}
                onClick={interactive ? () => onPriorityClick?.(item.priority) : undefined}
                onKeyDown={interactive ? makeKeyDownHandler(() => onPriorityClick?.(item.priority)) : undefined}
                role={interactive ? 'button' : dimmed ? 'presentation' : undefined}
                tabIndex={interactive ? 0 : undefined}
                aria-disabled={dimmed || undefined}
                aria-label={
                  interactive ? `${t('purchaseWishlist.form.priority')}: ${t(`purchaseWishlist.priority.${item.priority}`)}` : undefined
                }
                sx={interactiveRowSx(theme, interactive, dimmed)}
              >
                <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 2 }}>
                  <Box sx={{ minWidth: 0, flex: 1 }}>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, minWidth: 0 }}>
                      <Box
                        sx={{
                          width: 10,
                          height: 10,
                          borderRadius: '50%',
                          bgcolor: dimmed ? 'text.disabled' : accentColor,
                          flexShrink: 0,
                        }}
                      />
                      <Typography
                        variant="subtitle2"
                        sx={{
                          fontWeight: 600,
                          lineHeight: 1.2,
                          color: dimmed ? 'text.disabled' : 'text.primary',
                        }}
                      >
                        {t(`purchaseWishlist.priority.${item.priority}`)}
                      </Typography>
                    </Box>
                    <Typography
                      variant="caption"
                      color="text.secondary"
                      sx={{ textTransform: 'uppercase', letterSpacing: 0.5 }}
                    >
                      {t('purchaseWishlist.summary.totalItems', { count: item.count })}
                    </Typography>
                  </Box>
                  <Typography
                    variant="subtitle2"
                    sx={{
                      fontWeight: 700,
                      whiteSpace: 'nowrap',
                      color: dimmed ? 'text.disabled' : 'text.primary',
                    }}
                  >
                    {formatWishlistCurrency(item.totalEstimated)}
                  </Typography>
                </Box>
                <LinearProgress
                  variant="determinate"
                  value={Math.min(item.percentage, 100)}
                  sx={{
                    mt: 1,
                    height: 6,
                    borderRadius: 2,
                    bgcolor: dimmed ? alpha(theme.palette.text.primary, 0.06) : alpha(theme.palette.text.primary, 0.08),
                    '& .MuiLinearProgress-bar': {
                      bgcolor: dimmed ? theme.palette.action.disabledBackground : accentColor,
                      borderRadius: 2,
                    },
                  }}
                />
              </Box>
            );
          })}
        </Stack>
      </Box>

      {categoryItems.length > 0 ? (
        <>
          <Divider />
          <Box>
            <Box sx={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 2, mb: 1.5 }}>
              <Label sx={{ mb: 0 }}>{t('purchaseWishlist.summary.byCategory')}</Label>
            </Box>
            <Stack spacing={1.5}>
              {categoryItems
                .filter((item) => !isUncategorizedCategory(item))
                .slice(0, 5)
                .map((item) => (
                  <CategoryProgressRow
                    key={item.category?.id ?? 'uncategorized'}
                    item={item}
                    nameFontWeight={500}
                    percentFontWeight={600}
                    dimmed={!matchesCategoryFilter(item, activeCategoryFilter)}
                    categorySection="expenses"
                    activeCreditDebitTypeFilter={null}
                    onCategoryClick={onCategoryClick}
                  />
                ))}
              {(() => {
                const uncategorizedItem = categoryItems.find(isUncategorizedCategory);
                if (!uncategorizedItem) {
                  return null;
                }

                return (
                  <Box sx={{ mt: categoryItems.some((item) => !isUncategorizedCategory(item)) ? 0.5 : 0 }}>
                    <CategoryProgressRow
                      item={uncategorizedItem}
                      nameFontWeight={500}
                      percentFontWeight={600}
                      dimmed={!matchesCategoryFilter(uncategorizedItem, activeCategoryFilter)}
                      categorySection="expenses"
                      activeCreditDebitTypeFilter={null}
                      onCategoryClick={onCategoryClick}
                    />
                  </Box>
                );
              })()}
            </Stack>
          </Box>
        </>
      ) : null}
    </Stack>
  );
}
