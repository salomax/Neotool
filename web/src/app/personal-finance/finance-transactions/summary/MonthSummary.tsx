import { Avatar, Box, Button, Divider, Skeleton, Stack, Typography, alpha, useTheme } from '@mui/material';
import { ErrorOutline as UnavailableIcon } from '@mui/icons-material';
import { useCallback, useMemo, useState } from 'react';
import { CreditDebitType, TransactionType } from '@/lib/graphql/types/__generated__/graphql';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../i18n';
import { CurrencyAmountParts } from '@/shared/components/ui/data-display';
import { Label } from '@/shared/components/ui/primitives/Label';
import { extractErrorMessage } from '@/shared/utils/error';
import type { CategoryTotalItem, CategoryFilterSelection, TagTotalItem, MonthSummaryData } from './types';
import { SummaryMetricRow } from './SummaryMetricRow';
import { CategorySummarySection } from './CategorySummarySection';
import { TagSummarySection } from './TagSummarySection';
import { CategoryTagDrawer } from './CategoryTagDrawer';

// Re-export types so existing consumers keep working without changing imports
export type { CategoryTotalItem, CategoryFilterSelection, TagTotalItem, MonthSummaryData };

/** Locale/currency for month summary amounts until user prefs are wired. */
const MONTH_SUMMARY_LOCALE = 'pt-BR';
const MONTH_SUMMARY_CURRENCY = 'BRL';

const CURRENCY_FORMAT_OPTIONS: Intl.NumberFormatOptions = {
  style: 'currency',
  currency: MONTH_SUMMARY_CURRENCY,
};
const DIVIDER_VERTICAL_SPACING = '1rem';

interface MonthSummaryProps {
  summary: MonthSummaryData | undefined;
  loading: boolean;
  error?: unknown;
  onRetry?: () => void;
  isConsolidated?: boolean;
  expensesByCategory?: CategoryTotalItem[];
  incomeByCategory?: CategoryTotalItem[];
  tagsSummary?: TagTotalItem[];
  onCategoryClick?: (selection: CategoryFilterSelection) => void;
  activeCategoryFilter?: CategoryFilterSelection | null;
  onTagClick?: (tag: string) => void;
  activeTagFilter?: string | null;
  onCreditDebitTypeClick?: (type: CreditDebitType) => void;
  activeCreditDebitTypeFilter?: CreditDebitType | null;
  onTransferClick?: () => void;
  activeTransferFilter?: boolean;
}

function splitVolumePercentages(absIncome: number, absExpenses: number, totalVolume: number) {
  if (totalVolume <= 0) return { incomePct: 0, expensesPct: 0 };
  const incomePct = Math.round((absIncome / totalVolume) * 100);
  return { incomePct, expensesPct: 100 - incomePct };
}

export function MonthSummary({
  summary,
  loading,
  error,
  onRetry,
  expensesByCategory,
  incomeByCategory,
  tagsSummary,
  onCategoryClick,
  activeCategoryFilter = null,
  onTagClick,
  activeTagFilter = null,
  onCreditDebitTypeClick,
  activeCreditDebitTypeFilter = null,
  onTransferClick,
  activeTransferFilter = false,
}: MonthSummaryProps) {
  const { t, tCommon } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();
  const [drawerKind, setDrawerKind] = useState<'expenses' | 'income' | 'tags' | null>(null);

  // ── Currency formatting ───────────────────────────────────────────────────────
  const currencyFormatter = useMemo(
    () => new Intl.NumberFormat(MONTH_SUMMARY_LOCALE, CURRENCY_FORMAT_OPTIONS),
    [],
  );
  const formatCurrency = useCallback((value: number) => currencyFormatter.format(value), [currencyFormatter]);

  // ── Dimming logic ─────────────────────────────────────────────────────────────
  const activeMetricGroup: 'income' | 'expenses' | 'transfers' | null = activeTransferFilter
    ? 'transfers'
    : activeCreditDebitTypeFilter === CreditDebitType.Credit
      ? 'income'
      : activeCreditDebitTypeFilter === CreditDebitType.Debit
        ? 'expenses'
        : null;
  const isIncomeDimmed = activeMetricGroup !== null && activeMetricGroup !== 'income';
  const isExpensesDimmed = activeMetricGroup !== null && activeMetricGroup !== 'expenses';
  const isTransfersDimmed = activeMetricGroup !== null && activeMetricGroup !== 'transfers';

  // ── Stable callbacks for metric rows ─────────────────────────────────────────
  const canPickCreditDebitType = Boolean(onCreditDebitTypeClick) && !(loading && !summary);
  const handleIncomeClick = useCallback(
    () => onCreditDebitTypeClick?.(CreditDebitType.Credit),
    [onCreditDebitTypeClick],
  );
  const handleExpensesClick = useCallback(
    () => onCreditDebitTypeClick?.(CreditDebitType.Debit),
    [onCreditDebitTypeClick],
  );

  // ── Summary values ────────────────────────────────────────────────────────────
  const income = summary?.totalIncome ?? 0;
  const expenses = summary?.totalExpenses ?? 0;
  const balance = summary?.closingBalance ?? 0;
  const balanceColor = balance < 0 ? 'error.main' : 'text.primary';
  const absIncome = Math.abs(income);
  const absExpenses = Math.abs(expenses);
  const totalVolume = absIncome + absExpenses;
  const { incomePct: incomePercentage, expensesPct: expensesPercentage } = splitVolumePercentages(
    absIncome,
    absExpenses,
    totalVolume,
  );

  const transfersTitle = t('financeTransactions.monthSummary.transfersTitle');
  const transferInLabel = t('financeTransactions.monthSummary.transfersIn');
  const transferOutLabel = t('financeTransactions.monthSummary.transfersOut');

  // ── Error state ───────────────────────────────────────────────────────────────
  if (error && !loading && !summary) {
    const defaultErrorMessage = tCommon('errors.default');
    const extracted = extractErrorMessage(error, defaultErrorMessage);
    const description =
      extracted === defaultErrorMessage
        ? t('financeTransactions.monthSummary.unavailableDescription')
        : extracted;

    return (
      <Box sx={{ minHeight: 280, display: 'flex', alignItems: 'center', justifyContent: 'center', px: 2 }}>
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
  const isInitialLoad = loading && !summary;

  return (
    <Box data-testid="month-summary-section">
      {/* Hidden metadata elements for E2E tests */}
      <Box data-testid="month-summary-opening-balance" aria-hidden="true" sx={{ display: 'none' }}>
        {formatCurrency(summary?.openingBalance ?? 0)}
      </Box>

      {/* Closing balance */}
      <Box>
        <Label>{t('financeTransactions.monthSummary.balance')}</Label>
        {isInitialLoad ? (
          <Typography
            variant="h3"
            component="div"
            data-testid="month-summary-closing-balance"
            sx={{ fontWeight: 700, mt: 0.5, fontSize: '2.3rem', color: balanceColor }}
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
            sx={{ fontWeight: 700, mt: 0.5, fontSize: '2.3rem', color: balanceColor }}
          />
        )}
      </Box>

      <Divider sx={{ my: DIVIDER_VERTICAL_SPACING }} />

      {/* Income + expenses rows */}
      <Stack spacing={1.2}>
        <SummaryMetricRow
          creditDebitType={CreditDebitType.Credit}
          transactionType={TransactionType.OtherIncome}
          label={t('financeTransactions.monthSummary.income')}
          value={income}
          percentage={incomePercentage}
          isLoading={isInitialLoad}
          formatCurrency={formatCurrency}
          dimmed={isIncomeDimmed}
          onActivate={canPickCreditDebitType ? handleIncomeClick : undefined}
          testId="month-summary-total-income"
          amountTestId="month-summary-total-income-amount"
        />
        <SummaryMetricRow
          creditDebitType={CreditDebitType.Debit}
          transactionType={TransactionType.OtherExpense}
          label={t('financeTransactions.monthSummary.expenses')}
          value={expenses}
          percentage={expensesPercentage}
          isLoading={isInitialLoad}
          formatCurrency={formatCurrency}
          dimmed={isExpensesDimmed}
          onActivate={canPickCreditDebitType ? handleExpensesClick : undefined}
          testId="month-summary-total-expenses"
          amountTestId="month-summary-total-expenses-amount"
        />
      </Stack>

      {/* Income/expense volume bar */}
      {(totalVolume > 0 || isInitialLoad) && (
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
          {isInitialLoad ? (
            <Skeleton variant="rectangular" width="100%" height="100%" />
          ) : (
            <>
              <Box sx={{ flex: absIncome, bgcolor: theme.palette.success.main, transition: 'flex 0.5s ease-in-out' }} />
              <Box sx={{ flex: absExpenses, bgcolor: theme.palette.error.main, transition: 'flex 0.5s ease-in-out' }} />
            </>
          )}
        </Box>
      )}

      {/* Transfers */}
      {summary && !loading && (summary.transfersIn > 0 || summary.transfersOut > 0) && (
        <Box>
          <Divider sx={{ my: DIVIDER_VERTICAL_SPACING }} />
          <Box sx={{ mb: 1 }}>
            <Label>{transfersTitle}</Label>
          </Box>
          <Stack spacing={1.2}>
            <SummaryMetricRow
              creditDebitType={CreditDebitType.Credit}
              transactionType={TransactionType.Transfer}
              label={transferInLabel}
              value={summary.transfersIn}
              isLoading={false}
              formatCurrency={formatCurrency}
              dimmed={isTransfersDimmed}
              onActivate={onTransferClick}
            />
            <SummaryMetricRow
              creditDebitType={CreditDebitType.Debit}
              transactionType={TransactionType.Transfer}
              label={transferOutLabel}
              value={summary.transfersOut}
              isLoading={false}
              formatCurrency={formatCurrency}
              dimmed={isTransfersDimmed}
              onActivate={onTransferClick}
            />
          </Stack>
        </Box>
      )}

      {/* Category + tag breakdowns */}
      {summary && !loading && expensesByCategory !== undefined && incomeByCategory !== undefined && tagsSummary !== undefined && (
        <Stack spacing={3} sx={{ mt: 3 }}>
          <CategorySummarySection
            title={t('financeTransactions.monthSummary.expensesByCategory')}
            emptyText={t('financeTransactions.monthSummary.noCategorizedExpenses')}
            items={expensesByCategory}
            kind="expenses"
            onSeeAll={() => setDrawerKind('expenses')}
            onCategoryClick={onCategoryClick}
            activeCategoryFilter={activeCategoryFilter}
            activeCreditDebitTypeFilter={activeCreditDebitTypeFilter}
          />
          <CategorySummarySection
            title={t('financeTransactions.monthSummary.incomeByCategory')}
            emptyText={t('financeTransactions.monthSummary.noCategorizedIncome')}
            items={incomeByCategory}
            kind="income"
            onSeeAll={() => setDrawerKind('income')}
            onCategoryClick={onCategoryClick}
            activeCategoryFilter={activeCategoryFilter}
            activeCreditDebitTypeFilter={activeCreditDebitTypeFilter}
          />
          <TagSummarySection
            title={t('financeTransactions.monthSummary.tagsSummary')}
            emptyText={t('financeTransactions.monthSummary.noTaggedTransactions')}
            items={tagsSummary}
            onSeeAll={() => setDrawerKind('tags')}
            onTagClick={onTagClick}
            activeTagFilter={activeTagFilter}
          />
        </Stack>
      )}

      {/* "See all" drawer */}
      <CategoryTagDrawer
        kind={drawerKind}
        categoryItems={
          drawerKind === 'expenses'
            ? expensesByCategory
            : drawerKind === 'income'
              ? incomeByCategory
              : undefined
        }
        tagItems={drawerKind === 'tags' ? tagsSummary : undefined}
        onClose={() => setDrawerKind(null)}
        onCategoryClick={onCategoryClick}
        activeCategoryFilter={activeCategoryFilter}
        activeCreditDebitTypeFilter={activeCreditDebitTypeFilter}
        onTagClick={onTagClick}
        activeTagFilter={activeTagFilter}
      />
    </Box>
  );
}
