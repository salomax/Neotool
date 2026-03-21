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
import { extractErrorMessage } from '@/shared/utils/error';
import { Drawer } from '@/shared/components/ui/layout/Drawer';
import { CategoryIcon } from './category/CategoryIcon';
import { CreditDebitType } from '@/lib/graphql/types/__generated__/graphql';

/** Locale/currency for month summary amounts until user prefs are wired. */
const MONTH_SUMMARY_LOCALE = 'pt-BR';
const MONTH_SUMMARY_CURRENCY = 'BRL';

const CURRENCY_FORMAT_OPTIONS: Intl.NumberFormatOptions = {
  style: 'currency',
  currency: MONTH_SUMMARY_CURRENCY,
};

export interface CategoryTotalItem {
  category: { id: string; name: string; icon: string | null; color: string | null };
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
}

interface MonthSummaryProps {
  summary: MonthSummaryData | undefined;
  loading: boolean;
  error?: unknown;
  onRetry?: () => void;
  isConsolidated?: boolean;
  expensesByCategory?: CategoryTotalItem[];
  incomeByCategory?: CategoryTotalItem[];
  /** Called when user clicks a category row to filter transactions by category. */
  onCategoryClick?: (categoryId: string) => void;
  /** When set, rows for other categories render disabled (gray) across income and expenses. */
  activeCategoryFilterId?: string | null;
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
}) => {
  const theme = useTheme();
  const disabledFg = theme.palette.text.disabled;
  const interactive = Boolean(onActivate) && !dimmed && !isLoading;

  return (
    <Box
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

interface CategoryProgressRowProps {
  item: CategoryTotalItem;
  onCategoryClick?: (categoryId: string) => void;
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
  const theme = useTheme();
  const barColor = item.category.color ?? theme.palette.primary.main;
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
  const activateCategory = interactive ? () => onCategoryClick!(item.category.id) : undefined;

  return (
    <Box
      onClick={activateCategory}
      onKeyDown={
        interactive
          ? (e: React.KeyboardEvent) => {
              if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                onCategoryClick!(item.category.id);
              }
            }
          : undefined
      }
      role={interactive ? 'button' : rowDimmed ? 'presentation' : undefined}
      tabIndex={interactive ? 0 : undefined}
      aria-disabled={rowDimmed || undefined}
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
            iconName={item.category.icon}
            color={rowDimmed ? disabledFg : item.category.color ?? undefined}
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
            {item.category.name}
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

export const MonthSummary: React.FC<MonthSummaryProps> = ({
  summary,
  loading,
  error,
  onRetry,
  isConsolidated = false,
  expensesByCategory,
  incomeByCategory,
  onCategoryClick,
  activeCategoryFilterId = null,
  onCreditDebitTypeClick,
  activeCreditDebitTypeFilter = null,
}) => {
  const { t, tCommon } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();
  const [drawerKind, setDrawerKind] = React.useState<'expenses' | 'income' | null>(null);

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
    const topItems = items.slice(0, 5);
    const canSeeAll = items.length > 5;

    return (
      <Box>
        <Divider sx={{ mb: 3 }} />
        <Box sx={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 2, mb: 1.5 }}>
          <Typography variant="subtitle2" sx={{ fontWeight: 600, lineHeight: 1.2 }}>
            {title}
          </Typography>
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
          <Stack spacing={2}>
            {topItems.map((item) => (
              <CategoryProgressRow
                key={item.category.id}
                item={item}
                onCategoryClick={onCategoryClick}
                nameFontWeight={500}
                percentFontWeight={600}
                dimmed={Boolean(activeCategoryFilterId && item.category.id !== activeCategoryFilterId)}
                categorySection={kind}
                activeCreditDebitTypeFilter={activeCreditDebitTypeFilter}
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

  const drawerItems = drawerKind === 'expenses' ? expensesByCategory : drawerKind === 'income' ? incomeByCategory : undefined;
  const drawerTitle =
    drawerKind === 'expenses'
      ? t('financeTransactions.monthSummary.expensesByCategory')
      : drawerKind === 'income'
        ? t('financeTransactions.monthSummary.incomeByCategory')
        : '';

  return (
    <Box>
      <Box sx={{ mb: 2 }}>
        <Label>{t('financeTransactions.monthSummary.balance')}</Label>
        <Typography
          variant="h3"
          component="div"
          sx={{
            fontWeight: 700,
            mt: 0.5,
            fontSize: '2.3rem',
            color: theme.palette.text.primary,
          }}
        >
          {loading && !summary ? (
            <Skeleton width={200} />
          ) : (
            currencyFormatter.formatToParts(balance).map((part, index) => {
              const isMain = part.type === 'integer' || part.type === 'group';
              return (
                <Box
                  key={`${part.type}-${index}`}
                  component="span"
                  sx={{
                    fontSize: isMain ? 'inherit' : '0.6em',
                    opacity: isMain ? 1 : 0.7,
                    mr: part.type === 'currency' ? 0.5 : 0,
                  }}
                >
                  {part.value}
                </Box>
              );
            })
          )}
        </Typography>
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

      {summary && !loading && expensesByCategory !== undefined && incomeByCategory !== undefined && (
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
        </Stack>
      )}

      <Drawer open={drawerKind !== null} onClose={() => setDrawerKind(null)} anchor="right" size="sm">
        <Drawer.Header title={drawerTitle} showCloseButton />
        <Drawer.Body>
          {drawerKind !== null && drawerItems && (
            <Stack spacing={2}>
              {drawerItems.map((item) => (
                <CategoryProgressRow
                  key={item.category.id}
                  item={item}
                  onCategoryClick={onCategoryClick}
                  nameFontWeight={600}
                  percentFontWeight={700}
                  dimmed={Boolean(activeCategoryFilterId && item.category.id !== activeCategoryFilterId)}
                  categorySection={drawerKind}
                  activeCreditDebitTypeFilter={activeCreditDebitTypeFilter}
                />
              ))}
            </Stack>
          )}
        </Drawer.Body>
      </Drawer>
    </Box>
  );
};
