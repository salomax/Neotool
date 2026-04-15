import { Divider, Stack } from '@mui/material';
import { memo } from 'react';
import { CreditDebitType } from '@/lib/graphql/types/__generated__/graphql';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../i18n';
import { Drawer } from '@/shared/components/ui/layout/Drawer';
import type { CategoryTotalItem, CategoryFilterSelection, TagTotalItem } from './types';
import {
  CategoryProgressRow,
  isUncategorizedCategory,
  matchesCategoryFilter,
} from './CategoryProgressRow';
import { TagProgressRow } from './TagProgressRow';

type DrawerKind = 'expenses' | 'income' | 'tags';

interface CategoryTagDrawerProps {
  kind: DrawerKind | null;
  categoryItems?: CategoryTotalItem[];
  tagItems?: TagTotalItem[];
  onClose: () => void;
  onCategoryClick?: (selection: CategoryFilterSelection) => void;
  activeCategoryFilter: CategoryFilterSelection | null;
  activeCreditDebitTypeFilter: CreditDebitType | null;
  onTagClick?: (tag: string) => void;
  activeTagFilter: string | null;
}

export const CategoryTagDrawer = memo(function CategoryTagDrawer({
  kind,
  categoryItems,
  tagItems,
  onClose,
  onCategoryClick,
  activeCategoryFilter,
  activeCreditDebitTypeFilter,
  onTagClick,
  activeTagFilter,
}: CategoryTagDrawerProps) {
  const { t } = useTranslation(personalFinanceTranslations);

  const title =
    kind === 'expenses'
      ? t('financeTransactions.monthSummary.expensesByCategory')
      : kind === 'income'
        ? t('financeTransactions.monthSummary.incomeByCategory')
        : kind === 'tags'
          ? t('financeTransactions.monthSummary.tagsSummary')
          : '';

  return (
    <Drawer open={kind !== null} onClose={onClose} anchor="right" size="sm">
      <Drawer.Header
        title={title}
        showCloseButton
        closeButtonAriaLabel={t('financeTransactions.actions.close')}
      />
      <Drawer.Body>
        {kind !== null && categoryItems && (
          <Stack spacing={1.25}>
            {categoryItems.filter((item) => !isUncategorizedCategory(item)).map((item) => (
              <CategoryProgressRow
                key={item.category?.id ?? 'uncategorized'}
                item={item}
                nameFontWeight={600}
                percentFontWeight={700}
                dimmed={!matchesCategoryFilter(item, activeCategoryFilter)}
                categorySection={kind === 'expenses' ? 'expenses' : 'income'}
                activeCreditDebitTypeFilter={activeCreditDebitTypeFilter}
                onCategoryClick={onCategoryClick}
              />
            ))}
            {categoryItems.some(isUncategorizedCategory) && (
              <>
                {categoryItems.some((item) => !isUncategorizedCategory(item)) && (
                  <Divider sx={{ my: 0.5 }} />
                )}
                {categoryItems.filter(isUncategorizedCategory).map((item) => (
                  <CategoryProgressRow
                    key={item.category?.id ?? 'uncategorized'}
                    item={item}
                    nameFontWeight={600}
                    percentFontWeight={700}
                    dimmed={!matchesCategoryFilter(item, activeCategoryFilter)}
                    categorySection={kind === 'expenses' ? 'expenses' : 'income'}
                    activeCreditDebitTypeFilter={activeCreditDebitTypeFilter}
                    onCategoryClick={onCategoryClick}
                  />
                ))}
              </>
            )}
          </Stack>
        )}

        {kind !== null && tagItems && (
          <Stack spacing={1.25}>
            {tagItems.map((item) => (
              <TagProgressRow
                key={item.tag}
                item={item}
                nameFontWeight={600}
                percentFontWeight={700}
                dimmed={Boolean(activeTagFilter && item.tag !== activeTagFilter)}
                onTagClick={onTagClick}
              />
            ))}
          </Stack>
        )}
      </Drawer.Body>
    </Drawer>
  );
});
