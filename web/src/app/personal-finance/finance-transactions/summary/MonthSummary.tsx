import React from 'react';
import { Box, Stack, Typography, Skeleton, useTheme, alpha, Avatar, Divider, Button, LinearProgress } from '@mui/material';
import {
  ArrowUpward as IncomeIcon,
  ArrowDownward as ExpenseIcon,
  ErrorOutline as UnavailableIcon,
} from '@mui/icons-material';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../i18n';
import { Label } from '@/shared/components/ui/primitives/Label';
import { CurrencyAmountParts } from '@/shared/components/ui/data-display';
import { extractErrorMessage } from '@/shared/utils/error';
import { Drawer } from '@/shared/components/ui/layout/Drawer';
import { CategoryIcon } from '../category/CategoryIcon';
import { CreditDebitType } from '@/lib/graphql/types/__generated__/graphql';

/** Locale/currency for month summary amounts until user prefs are wired. */
const MONTH_SUMMARY_LOCALE = 'pt-BR';
const MONTH_SUMMARY_CURRENCY = 'BRL';

const CURRENCY_FORMAT_OPTIONS: Intl.NumberFormatOptions = {
  style: 'currency',
  currency: MONTH_SUMMARY_CURRENCY,
};

export interface CategoryTotalItem {
  category: { id: string; name: string; icon: string | null; color: string | null } | null;
  uncategorized: boolean;
  totalAmount: number;
  transactionCount: number;
  percentage: number;
}

export interface CategoryFilterSelection {
  categoryId: string | null;
  uncategorized: boolean;
}

export interface TagTotalItem {
  tag: string;
  totalAmount: number;
  transactionCount: number;
  percentage: number;
}

export interface MonthSummaryData {
  year: number;
  month: number;
  totalIncome: number;
  totalExpenses: number;
  closingBalance: number;
  openingBalance: number;
  transfersIn: number;
  transfersOut: number;
  projectedClosingBalance: number | null;
  isCurrentMonth: boolean;
  isFutureMonth?: boolean;
  expensesByCategory?: CategoryTotalItem[];
  incomeByCategory?: CategoryTotalItem[];
  tagsSummary?: TagTotalItem[];
}

interface MonthSummaryProps {
  summary: MonthSummaryData | undefined;
  loading: boolean;
  error?: unknown;
  onRetry?: () => void;
  isConsolidated?: boolean;
  expensesByCategory?: CategoryTotalItem[];
  incomeByCategory?: CategoryTotalItem[];
  tagsSummary?: TagTotalItem[];
  /** Called when user clicks a category row to filter transactions. */
  onCategoryClick?: (selection: CategoryFilterSelection) => void;
  /** When set, rows for other categories render disabled (gray) across income and expenses. */
  activeCategoryFilter?: CategoryFilterSelection | null;
  /** Called when user clicks a tag row to filter transactions by tag. */
  onTagClick?: (tag: string) => void;
  /** When set, rows for other tags render disabled (gray) across income and expenses. */
  activeTagFilter?: string | null;
  /** Filter table by credit (income) or debit (expenses); the other summary row is dimmed. */
  onCreditDebitTypeClick?: (type: CreditDebitType) => void;
  activeCreditDebitTypeFilter?: CreditDebitType | null;
}

function splitVolumePercentages(absIncome: number, absExpenses: number, totalVolume: number) {
  if (totalVolume <= 0) return { incomePct: 0, expensesPct: 0 };
  const incomePct = Math.round((absIncome / totalVolume) * 100);
  return { incomePct, expensesPct: 100 - incomePct };
}

interface SummaryMetricRowProps {
  icon: React.ReactNode;
  color: string;
  label: string;
  value: number | undefined;
  percentage?: number;
  isLoading?: boolean;
  formatCurrency: (value: number) => string;
  totalSuffix: string;
  dimmed?: boolean;
  onActivate?: () => void;
  testId?: string;
}

const SummaryMetricRow: React.FC<SummaryMetricRowProps> = ({
  icon,
  color,
  label,
  value,
  percentage,
  isLoading = false,
  formatCurrency,
  totalSuffix,
  dimmed = false,
  onActivate,
  testId,
}) => {
  const { t } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();
  const disabledFg = theme.palette.text.disabled;
  const interactive = Boolean(onActivate) && !dimmed && !isLoading;

  return (
    <Box
      data-testid={testId}
      onClick={interactive ? onActivate : undefined}
      onKeyDown={
        interactive
          ? (e: React.KeyboardEvent) => {
              if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                onActivate!();
              }
            }
          : undefined
      }
      role={interactive ? 'button' : dimmed ? 'presentation' : undefined}
      tabIndex={interactive ? 0 : undefined}
      aria-disabled={dimmed || undefined}
      aria-label={interactive ? `${t('financeTransactions.monthSummary.a11y.filterByMetric')} ${label}` : undefined}
      sx={{
        borderRadius: 1,
        px: 1,
        py: 0.75,
        opacity: dimmed ? 0.55 : 1,
        filter: dimmed ? 'grayscale(1)' : 'none',
        transition: theme.transitions.create(['opacity', 'filter', 'background-color'], {
          duration: theme.transitions.duration.shorter,
        }),
        cursor: interactive ? 'pointer' : 'default',
        pointerEvents: dimmed ? 'none' : 'auto',
        ...(interactive
          ? {
              '&:hover': { bgcolor: theme.palette.action.hover },
              '&:focus-visible': { outline: `2px solid ${theme.palette.primary.main}`, outlineOffset: 2 },
            }
          : {}),
      }}
    >
      <Box sx={{ display: 'flex', alignItems: 'center' }}>
        <Avatar
          variant="rounded"
          sx={{
            bgcolor: dimmed ? alpha(disabledFg, 0.12) : alpha(color, 0.1),
            color: dimmed ? disabledFg : color,
            mr: 1.5,
            width: 40,
            height: 40,
          }}
        >
          {icon}
        </Avatar>
        <Box sx={{ flexGrow: 1 }}>
          <Stack spacing={0.3}>
            <Typography
              variant="subtitle2"
              sx={{
                fontWeight: 600,
                lineHeight: 1.2,
                color: dimmed ? disabledFg : 'text.primary',
              }}
            >
              {label}
            </Typography>
            {percentage !== undefined && (
              <Typography variant="caption" color="text.secondary" sx={{ textTransform: 'uppercase', letterSpacing: 0.5 }}>
                {isLoading ? <Skeleton width={60} /> : `${percentage}% ${totalSuffix}`}
              </Typography>
            )}
          </Stack>
        </Box>
        <Typography variant="subtitle2" sx={{ fontWeight: 700, color: dimmed ? disabledFg : color }}>
          {isLoading ? <Skeleton width={88} height={22} /> : formatCurrency(value || 0)}
        </Typography>
      </Box>
    </Box>
  );
};

function isUncategorizedCategory(item: CategoryTotalItem) {
  return item.uncategorized;
}

function matchesCategoryFilter(item: CategoryTotalItem, activeCategoryFilter: CategoryFilterSelection | null) {
  if (!activeCategoryFilter) return true;
  if (item.uncategorized) return activeCategoryFilter.uncategorized;
  return !activeCategoryFilter.uncategorized && item.category?.id === activeCategoryFilter.categoryId;
}

interface CategoryProgressRowProps {
  item: CategoryTotalItem;
  onCategoryClick?: (selection: CategoryFilterSelection) => void;
  nameFontWeight: number;
  percentFontWeight: number;
  /** Muted styling and no interaction (another category is the active table filter). */
  dimmed?: boolean;
  /** Whether this row is under expenses or income (for credit/debit type filter dimming). */
  categorySection?: 'expenses' | 'income';
  activeCreditDebitTypeFilter?: CreditDebitType | null;
}

const CategoryProgressRow = React.memo(function CategoryProgressRow({
  item,
  onCategoryClick,
  nameFontWeight,
  percentFontWeight,
  dimmed = false,
  categorySection,
  activeCreditDebitTypeFilter,
}: CategoryProgressRowProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();
  const isUncategorized = isUncategorizedCategory(item);
  const barColor =
    isUncategorized ? theme.palette.grey[500] : item.category?.color ?? theme.palette.primary.main;
  const trackBg = alpha(theme.palette.text.primary, 0.08);
  const disabledFg = theme.palette.text.disabled;
  const disabledBar = theme.palette.action.disabledBackground;
  const disabledBarTrack = alpha(theme.palette.text.primary, 0.06);

  const dimmedByType =
    categorySection != null &&
    activeCreditDebitTypeFilter != null &&
    ((activeCreditDebitTypeFilter === CreditDebitType.Credit && categorySection === 'expenses') ||
      (activeCreditDebitTypeFilter === CreditDebitType.Debit && categorySection === 'income'));

  const rowDimmed = dimmed || dimmedByType;

  const interactive = Boolean(onCategoryClick) && !rowDimmed;
  const categorySelection: CategoryFilterSelection = {
    categoryId: item.category?.id ?? null,
    uncategorized: isUncategorized,
  };
  const activateCategory = interactive ? () => onCategoryClick!(categorySelection) : undefined;
  const categoryName = isUncategorized ? t('financeTransactions.monthSummary.uncategorized') : item.category?.name;

  return (
    <Box
      onClick={activateCategory}
      onKeyDown={
        interactive
          ? (e: React.KeyboardEvent) => {
              if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                onCategoryClick!(categorySelection);
              }
            }
          : undefined
      }
      role={interactive ? 'button' : rowDimmed ? 'presentation' : undefined}
      tabIndex={interactive ? 0 : undefined}
      aria-disabled={rowDimmed || undefined}
      aria-label={
        interactive && categoryName
          ? `${t('financeTransactions.monthSummary.a11y.filterByCategory')}: ${categoryName}`
          : undefined
      }
      sx={{
        borderRadius: 1,
        px: 1,
        py: 0.75,
        opacity: rowDimmed ? 0.55 : 1,
        filter: rowDimmed ? 'grayscale(1)' : 'none',
        transition: theme.transitions.create(['opacity', 'filter', 'background-color'], {
          duration: theme.transitions.duration.shorter,
        }),
        cursor: interactive ? 'pointer' : 'default',
        pointerEvents: rowDimmed ? 'none' : 'auto',
        ...(interactive
          ? {
              '&:hover': {
                bgcolor: theme.palette.action.hover,
              },
              '&:focus-visible': { outline: `2px solid ${theme.palette.primary.main}`, outlineOffset: 2 },
            }
          : {}),
      }}
    >
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 2 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, minWidth: 0 }}>
          <CategoryIcon
            iconName={item.category?.icon}
            color={rowDimmed ? disabledFg : isUncategorized ? theme.palette.grey[600] : item.category?.color ?? undefined}
            size={18}
            sx={{ flexShrink: 0 }}
          />
          <Typography
            variant="body2"
            sx={{
              fontWeight: nameFontWeight,
              color: rowDimmed ? disabledFg : 'text.primary',
            }}
            noWrap
          >
            {categoryName}
          </Typography>
        </Box>
        <Typography
          variant="body2"
          sx={{
            fontWeight: percentFontWeight,
            whiteSpace: 'nowrap',
            color: rowDimmed ? disabledFg : 'text.primary',
          }}
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

interface TagProgressRowProps {
  item: TagTotalItem;
  onTagClick?: (tag: string) => void;
  nameFontWeight: number;
  percentFontWeight: number;
  dimmed?: boolean;
  categorySection?: 'expenses' | 'income';
  activeCreditDebitTypeFilter?: CreditDebitType | null;
}

const TagProgressRow = React.memo(function TagProgressRow({
  item,
  onTagClick,
  nameFontWeight,
  percentFontWeight,
  dimmed = false,
  categorySection,
  activeCreditDebitTypeFilter,
}: TagProgressRowProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();
  const barColor = theme.palette.info.main;
  const trackBg = alpha(theme.palette.text.primary, 0.08);
  const disabledFg = theme.palette.text.disabled;
  const disabledBar = theme.palette.action.disabledBackground;
  const disabledBarTrack = alpha(theme.palette.text.primary, 0.06);

  const dimmedByType =
    categorySection != null &&
    activeCreditDebitTypeFilter != null &&
    ((activeCreditDebitTypeFilter === CreditDebitType.Credit && categorySection === 'expenses') ||
      (activeCreditDebitTypeFilter === CreditDebitType.Debit && categorySection === 'income'));

  const rowDimmed = dimmed || dimmedByType;
  const interactive = Boolean(onTagClick) && !rowDimmed;
  const activateTag = interactive ? () => onTagClick!(item.tag) : undefined;

  return (
    <Box
      onClick={activateTag}
      onKeyDown={
        interactive
          ? (e: React.KeyboardEvent) => {
              if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                onTagClick!(item.tag);
              }
            }
          : undefined
      }
      role={interactive ? 'button' : rowDimmed ? 'presentation' : undefined}
      tabIndex={interactive ? 0 : undefined}
      aria-disabled={rowDimmed || undefined}
      aria-label={
        interactive ? `${t('financeTransactions.monthSummary.a11y.filterByTag')}: #${item.tag}` : undefined
      }
      sx={{
        borderRadius: 1,
        px: 1,
        py: 0.75,
        opacity: rowDimmed ? 0.55 : 1,
        filter: rowDimmed ? 'grayscale(1)' : 'none',
        transition: theme.transitions.create(['opacity', 'filter', 'background-color'], {
          duration: theme.transitions.duration.shorter,
        }),
        cursor: interactive ? 'pointer' : 'default',
        pointerEvents: rowDimmed ? 'none' : 'auto',
        ...(interactive
          ? {
              '&:hover': {
                bgcolor: theme.palette.action.hover,
              },
              '&:focus-visible': { outline: `2px solid ${theme.palette.primary.main}`, outlineOffset: 2 },
            }
          : {}),
      }}
    >
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 2 }}>
        <Typography
          variant="body2"
          sx={{
            fontWeight: nameFontWeight,
            color: rowDimmed ? disabledFg : 'text.primary',
            minWidth: 0,
          }}
          noWrap
        >
          #{item.tag}
        </Typography>
        <Typography
          variant="body2"
          sx={{
            fontWeight: percentFontWeight,
            whiteSpace: 'nowrap',
            color: rowDimmed ? disabledFg : 'text.primary',
          }}
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

export const MonthSummary: React.FC<MonthSummaryProps> = ({
  summary,
  loading,
  error,
  onRetry,
  isConsolidated = false,
  expensesByCategory,
  incomeByCategory,
  tagsSummary,
  onCategoryClick,
  activeCategoryFilter = null,
  onTagClick,
  activeTagFilter = null,
  onCreditDebitTypeClick,
  activeCreditDebitTypeFilter = null,
}) => {
  const { t, tCommon } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();
  const [drawerKind, setDrawerKind] = React.useState<'expenses' | 'income' | 'tags' | null>(null);

  const canPickCreditDebitType = Boolean(onCreditDebitTypeClick) && !(loading && !summary);

  const currencyFormatter = React.useMemo(
    () => new Intl.NumberFormat(MONTH_SUMMARY_LOCALE, CURRENCY_FORMAT_OPTIONS),
    [],
  );

  const formatCurrency = React.useCallback((value: number) => currencyFormatter.format(value), [currencyFormatter]);

  const income = summary?.totalIncome || 0;
  const expenses = summary?.totalExpenses || 0;
  const balance = summary?.closingBalance || 0;

  const absIncome = Math.abs(income);
  const absExpenses = Math.abs(expenses);
  const totalVolume = absIncome + absExpenses;

  const { incomePct: incomePercentage, expensesPct: expensesPercentage } = splitVolumePercentages(
    absIncome,
    absExpenses,
    totalVolume,
  );

  const totalSuffix = t('financeTransactions.monthSummary.total');

  const renderCategorySummary = ({
    title,
    emptyText,
    items,
    kind,
  }: {
    title: string;
    emptyText: string;
    items: CategoryTotalItem[];
    kind: 'expenses' | 'income';
  }) => {
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
              onClick={() => setDrawerKind(kind)}
              sx={{
                p: 0,
                minWidth: 'auto',
                textTransform: 'uppercase',
                fontWeight: 500,
                letterSpacing: 0.6,
                fontSize: '0.75rem',
              }}
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
                onCategoryClick={onCategoryClick}
                nameFontWeight={500}
                percentFontWeight={600}
                dimmed={!matchesCategoryFilter(item, activeCategoryFilter)}
                categorySection={kind}
                activeCreditDebitTypeFilter={activeCreditDebitTypeFilter}
              />
            ))}
            {uncategorizedItem && (
              <Box sx={{ mt: topItems.length ? 0.5 : 0 }}>
                <CategoryProgressRow
                  item={uncategorizedItem}
                  onCategoryClick={onCategoryClick}
                  nameFontWeight={500}
                  percentFontWeight={600}
                  dimmed={!matchesCategoryFilter(uncategorizedItem, activeCategoryFilter)}
                  categorySection={kind}
                  activeCreditDebitTypeFilter={activeCreditDebitTypeFilter}
                />
              </Box>
            )}
          </Stack>
        )}
      </Box>
    );
  };

  const renderTagSummary = ({
    title,
    emptyText,
    items,
    kind = 'tags',
  }: {
    title: string;
    emptyText: string;
    items: TagTotalItem[];
    kind?: 'tags';
  }) => {
    const topItems = items.slice(0, 5);
    const canSeeAll = items.length > 5;

    return (
      <Box>
        <Divider sx={{ mb: 3 }} />
        <Box sx={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 2, mb: 1.5 }}>
          <Label sx={{ mb: 0 }}>{title}</Label>
          {canSeeAll && (
            <Button
              variant="text"
              size="small"
              onClick={() => setDrawerKind(kind)}
              sx={{
                p: 0,
                minWidth: 'auto',
                textTransform: 'uppercase',
                fontWeight: 500,
                letterSpacing: 0.6,
                fontSize: '0.75rem',
              }}
            >
              {t('financeTransactions.monthSummary.seeAll')}
            </Button>
          )}
        </Box>

        {items.length === 0 ? (
          <Typography variant="body2" color="text.secondary">
            {emptyText}
          </Typography>
        ) : (
          <Stack spacing={1.25}>
            {topItems.map((item) => (
              <TagProgressRow
                key={item.tag}
                item={item}
                onTagClick={onTagClick}
                nameFontWeight={500}
                percentFontWeight={600}
                dimmed={Boolean(activeTagFilter && item.tag !== activeTagFilter)}
              />
            ))}
          </Stack>
        )}
      </Box>
    );
  };

  if (error && !loading && !summary) {
    const defaultErrorMessage = tCommon('errors.default');
    const extracted = extractErrorMessage(error, defaultErrorMessage);
    const description = extracted === defaultErrorMessage ? t('financeTransactions.monthSummary.unavailableDescription') : extracted;

    return (
      <Box
        sx={{
          minHeight: 280,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          px: 2,
        }}
      >
        <Stack spacing={1.5} alignItems="center" textAlign="center" maxWidth={320}>
          <Avatar
            variant="rounded"
            sx={{
              bgcolor: alpha(theme.palette.warning.main, 0.12),
              color: theme.palette.warning.main,
              width: 52,
              height: 52,
            }}
          >
            <UnavailableIcon />
          </Avatar>
          <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
            {t('financeTransactions.monthSummary.unavailableTitle')}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {description}
          </Typography>
          {onRetry && (
            <Button variant="outlined" size="small" onClick={onRetry}>
              {t('financeTransactions.monthSummary.retry')}
            </Button>
          )}
        </Stack>
      </Box>
    );
  }

  const volumeBarTrackBg = alpha(theme.palette.divider, 0.28);

  const drawerCategoryItems =
    drawerKind === 'expenses' ? expensesByCategory : drawerKind === 'income' ? incomeByCategory : undefined;
  const drawerTagItems = drawerKind === 'tags' ? tagsSummary : undefined;
  const drawerTitle =
    drawerKind === 'expenses'
      ? t('financeTransactions.monthSummary.expensesByCategory')
      : drawerKind === 'income'
        ? t('financeTransactions.monthSummary.incomeByCategory')
        : drawerKind === 'tags'
          ? t('financeTransactions.monthSummary.tagsSummary')
        : '';

  return (
    <Box data-testid="month-summary-section">
      {/* Hidden metadata elements for E2E tests */}
      <Box
        data-testid="month-summary-opening-balance"
        aria-hidden="true"
        sx={{ display: 'none' }}
      >
        {formatCurrency(summary?.openingBalance ?? 0)}
      </Box>
      <Box sx={{ mb: 2 }}>
        <Label>{t('financeTransactions.monthSummary.balance')}</Label>
        {loading && !summary ? (
          <Typography
            variant="h3"
            component="div"
            data-testid="month-summary-closing-balance"
            sx={{
              fontWeight: 700,
              mt: 0.5,
              fontSize: '2.3rem',
              color: theme.palette.text.primary,
            }}
          >
            <Skeleton width={200} />
          </Typography>
        ) : (
          <CurrencyAmountParts
            value={balance}
            currency={MONTH_SUMMARY_CURRENCY}
            locale={MONTH_SUMMARY_LOCALE}
            variant="h3"
            component="div"
            data-testid="month-summary-closing-balance"
            sx={{
              fontWeight: 700,
              mt: 0.5,
              fontSize: '2.3rem',
              color: theme.palette.text.primary,
            }}
          />
        )}
      </Box>

      <Divider sx={{ mb: 3 }} />

      <Stack spacing={1.2}>
        <SummaryMetricRow
          icon={<IncomeIcon />}
          color={theme.palette.success.main}
          label={t('financeTransactions.monthSummary.income')}
          value={income}
          percentage={incomePercentage}
          isLoading={loading && !summary}
          formatCurrency={formatCurrency}
          totalSuffix={totalSuffix}
          dimmed={activeCreditDebitTypeFilter === CreditDebitType.Debit}
          onActivate={
            canPickCreditDebitType ? () => onCreditDebitTypeClick!(CreditDebitType.Credit) : undefined
          }
          testId="month-summary-total-income"
        />
        <SummaryMetricRow
          icon={<ExpenseIcon />}
          color={theme.palette.error.main}
          label={t('financeTransactions.monthSummary.expenses')}
          value={expenses}
          percentage={expensesPercentage}
          isLoading={loading && !summary}
          formatCurrency={formatCurrency}
          totalSuffix={totalSuffix}
          dimmed={activeCreditDebitTypeFilter === CreditDebitType.Credit}
          onActivate={
            canPickCreditDebitType ? () => onCreditDebitTypeClick!(CreditDebitType.Debit) : undefined
          }
          testId="month-summary-total-expenses"
        />
      </Stack>

      {(totalVolume > 0 || (loading && !summary)) && (
        <Box
          sx={{
            mt: 3,
            height: 6,
            borderRadius: 3,
            display: 'flex',
            overflow: 'hidden',
            width: '100%',
            bgcolor: volumeBarTrackBg,
          }}
        >
          {loading && !summary ? (
            <Skeleton variant="rectangular" width="100%" height="100%" />
          ) : (
            <>
              <Box
                sx={{
                  flex: absIncome,
                  bgcolor: theme.palette.success.main,
                  transition: 'flex 0.5s ease-in-out',
                }}
              />
              <Box
                sx={{
                  flex: absExpenses,
                  bgcolor: theme.palette.error.main,
                  transition: 'flex 0.5s ease-in-out',
                }}
              />
            </>
          )}
        </Box>
      )}

      {summary &&
        !loading &&
        expensesByCategory !== undefined &&
        incomeByCategory !== undefined &&
        tagsSummary !== undefined && (
        <Stack spacing={3} sx={{ mt: 3 }}>
          {renderCategorySummary({
            title: t('financeTransactions.monthSummary.expensesByCategory'),
            emptyText: t('financeTransactions.monthSummary.noCategorizedExpenses'),
            items: expensesByCategory,
            kind: 'expenses',
          })}
          {renderCategorySummary({
            title: t('financeTransactions.monthSummary.incomeByCategory'),
            emptyText: t('financeTransactions.monthSummary.noCategorizedIncome'),
            items: incomeByCategory,
            kind: 'income',
          })}
          {renderTagSummary({
            title: t('financeTransactions.monthSummary.tagsSummary'),
            emptyText: t('financeTransactions.monthSummary.noTaggedTransactions'),
            items: tagsSummary,
          })}
        </Stack>
      )}

      <Drawer open={drawerKind !== null} onClose={() => setDrawerKind(null)} anchor="right" size="sm">
        <Drawer.Header title={drawerTitle} showCloseButton closeButtonAriaLabel={t('financeTransactions.actions.close')} />
        <Drawer.Body>
          {drawerKind !== null && drawerCategoryItems && (
            <Stack spacing={1.25}>
              {drawerCategoryItems.filter((item) => !isUncategorizedCategory(item)).map((item) => (
                <CategoryProgressRow
                  key={item.category?.id ?? 'uncategorized'}
                  item={item}
                  onCategoryClick={onCategoryClick}
                  nameFontWeight={600}
                  percentFontWeight={700}
                  dimmed={!matchesCategoryFilter(item, activeCategoryFilter)}
                  categorySection={drawerKind === 'expenses' ? 'expenses' : 'income'}
                  activeCreditDebitTypeFilter={activeCreditDebitTypeFilter}
                />
              ))}
              {drawerCategoryItems.some(isUncategorizedCategory) && (
                <>
                  {drawerCategoryItems.some((item) => !isUncategorizedCategory(item)) && <Divider sx={{ my: 0.5 }} />}
                  {drawerCategoryItems.filter(isUncategorizedCategory).map((item) => (
                    <CategoryProgressRow
                      key={item.category?.id ?? 'uncategorized'}
                      item={item}
                      onCategoryClick={onCategoryClick}
                      nameFontWeight={600}
                      percentFontWeight={700}
                      dimmed={!matchesCategoryFilter(item, activeCategoryFilter)}
                      categorySection={drawerKind === 'expenses' ? 'expenses' : 'income'}
                      activeCreditDebitTypeFilter={activeCreditDebitTypeFilter}
                    />
                  ))}
                </>
              )}
            </Stack>
          )}
          {drawerKind !== null && drawerTagItems && (
            <Stack spacing={1.25}>
              {drawerTagItems.map((item) => (
                <TagProgressRow
                  key={item.tag}
                  item={item}
                  onTagClick={onTagClick}
                  nameFontWeight={600}
                  percentFontWeight={700}
                  dimmed={Boolean(activeTagFilter && item.tag !== activeTagFilter)}
                />
              ))}
            </Stack>
          )}
        </Drawer.Body>
      </Drawer>
    </Box>
  );
};
