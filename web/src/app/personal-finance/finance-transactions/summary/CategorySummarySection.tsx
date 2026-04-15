import { Box, Button, Divider, Stack, Typography } from '@mui/material';
import { memo } from 'react';
import { CreditDebitType } from '@/lib/graphql/types/__generated__/graphql';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../i18n';
import { Label } from '@/shared/components/ui/primitives/Label';
import type { CategoryTotalItem, CategoryFilterSelection } from './types';
import {
  CategoryProgressRow,
  isUncategorizedCategory,
  matchesCategoryFilter,
} from './CategoryProgressRow';

interface CategorySummarySectionProps {
  title: string;
  emptyText: string;
  items: CategoryTotalItem[];
  kind: 'expenses' | 'income';
  onSeeAll: () => void;
  onCategoryClick?: (selection: CategoryFilterSelection) => void;
  activeCategoryFilter: CategoryFilterSelection | null;
  activeCreditDebitTypeFilter: CreditDebitType | null;
}

export const CategorySummarySection = memo(function CategorySummarySection({
  title,
  emptyText,
  items,
  kind,
  onSeeAll,
  onCategoryClick,
  activeCategoryFilter,
  activeCreditDebitTypeFilter,
}: CategorySummarySectionProps) {
  const { t } = useTranslation(personalFinanceTranslations);

  const uncategorizedItem = items.find(isUncategorizedCategory);
  const categorizedItems = items.filter((item) => !isUncategorizedCategory(item));
  const topItems = categorizedItems.slice(0, 5);
  const hasItems = categorizedItems.length > 0 || Boolean(uncategorizedItem);
  const canSeeAll = categorizedItems.length > 5;

  return (
    <Box>
      <Divider sx={{ mb: 3 }} />
      <Box sx={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 2, mb: 1.5 }}>
        <Label sx={{ mb: 0 }}>{title}</Label>
        {canSeeAll && (
          <Button
            variant="text"
            size="small"
            onClick={onSeeAll}
            sx={{ p: 0, minWidth: 'auto', textTransform: 'none', fontWeight: 500 }}
          >
            {t('financeTransactions.monthSummary.seeAll')}
          </Button>
        )}
      </Box>

      {!hasItems ? (
        <Typography variant="body2" color="text.secondary">
          {emptyText}
        </Typography>
      ) : (
        <Stack spacing={1.5}>
          {topItems.map((item) => (
            <CategoryProgressRow
              key={item.category?.id ?? 'uncategorized'}
              item={item}
              nameFontWeight={500}
              percentFontWeight={600}
              dimmed={!matchesCategoryFilter(item, activeCategoryFilter)}
              categorySection={kind}
              activeCreditDebitTypeFilter={activeCreditDebitTypeFilter}
              onCategoryClick={onCategoryClick}
            />
          ))}
          {uncategorizedItem && (
            <Box sx={{ mt: topItems.length ? 0.5 : 0 }}>
              <CategoryProgressRow
                item={uncategorizedItem}
                nameFontWeight={500}
                percentFontWeight={600}
                dimmed={!matchesCategoryFilter(uncategorizedItem, activeCategoryFilter)}
                categorySection={kind}
                activeCreditDebitTypeFilter={activeCreditDebitTypeFilter}
                onCategoryClick={onCategoryClick}
              />
            </Box>
          )}
        </Stack>
      )}
    </Box>
  );
});
