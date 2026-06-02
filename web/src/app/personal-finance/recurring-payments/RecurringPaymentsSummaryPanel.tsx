'use client';

import { Box, Divider, Skeleton, Stack, Typography, useTheme } from '@mui/material';
import { useMemo } from 'react';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../i18n';
import { CurrencyAmountParts } from '@/shared/components/ui/data-display';
import { QueryErrorState } from '@/shared/components/ui/feedback';
import { Label } from '@/shared/components/ui/primitives';
import { CategorySummarySection } from '../finance-transactions/summary/CategorySummarySection';
import { FrequencySummarySection } from '../finance-transactions/summary/FrequencySummarySection';
import type { RecurringPaymentRow } from './recurringPaymentRowModel';
import {
  buildRecurringCategoryTotals,
  buildRecurringFrequencyTotals,
  computeRecurringSummaryMetrics,
} from './recurringPaymentRowModel';

const LOCALE = 'pt-BR';
const CURRENCY = 'BRL';
const DIVIDER_VERTICAL_SPACING = '1rem';

function formatCurrency(value: number): string {
  return new Intl.NumberFormat(LOCALE, { style: 'currency', currency: CURRENCY }).format(value);
}

function SummaryMetricRow({
  label,
  count,
  amount,
  countColor,
}: {
  label: string;
  count: number;
  amount: number;
  countColor?: string;
}) {
  return (
    <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', gap: 2 }}>
      <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 1, minWidth: 0 }}>
        <Typography variant="body2" color="text.secondary">
          {label}
        </Typography>
        <Typography variant="body2" sx={{ fontWeight: 600, color: countColor ?? 'text.primary' }}>
          {count}
        </Typography>
      </Box>
      <Typography variant="body1" sx={{ fontWeight: 600, flexShrink: 0 }}>
        {formatCurrency(amount)}
      </Typography>
    </Box>
  );
}

export interface RecurringPaymentsSummaryPanelProps {
  rows: RecurringPaymentRow[];
  loading?: boolean;
  error?: unknown;
  onRetry?: () => void;
}

export function RecurringPaymentsSummaryPanel({ rows, loading, error, onRetry }: RecurringPaymentsSummaryPanelProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();

  const metrics = useMemo(() => computeRecurringSummaryMetrics(rows), [rows]);
  const categoryTotals = useMemo(() => buildRecurringCategoryTotals(rows), [rows]);
  const frequencyTotals = useMemo(() => buildRecurringFrequencyTotals(rows), [rows]);

  if (error && onRetry) {
    return (
      <Box sx={{ px: 0.5 }}>
        <QueryErrorState error={error} onRetry={onRetry} />
      </Box>
    );
  }

  return (
    <Box sx={{ maxWidth: 360, mx: 'auto' }}>
      {loading ? (
        <Stack spacing={2}>
          <Skeleton variant="rounded" height={56} />
          <Skeleton variant="rounded" height={56} />
          <Skeleton variant="rounded" height={56} />
          <Skeleton variant="rounded" height={56} />
          <Skeleton variant="rounded" height={120} />
          <Skeleton variant="rounded" height={120} />
        </Stack>
      ) : (
        <Stack spacing={2.5}>
          <Box>
            <Label>{t('recurringPayments.summary.monthlyCommitted')}</Label>
            <CurrencyAmountParts
              value={metrics.monthlyCommittedApprox}
              currency={CURRENCY}
              locale={LOCALE}
              variant="h3"
              component="div"
              sx={{ fontWeight: 700, mt: 0.5, fontSize: '2.3rem', color: theme.palette.text.primary }}
            />
          </Box>

          <Divider sx={{ my: DIVIDER_VERTICAL_SPACING }} />

          <Stack spacing={1.5}>
            <SummaryMetricRow
              label={t('recurringPayments.summary.overdue')}
              count={metrics.overdueCount}
              amount={metrics.overdueSum}
              countColor={metrics.overdueCount > 0 ? 'error.main' : undefined}
            />
            <SummaryMetricRow
              label={t('recurringPayments.summary.next7Days')}
              count={metrics.next7Count}
              amount={metrics.next7Sum}
            />
            <SummaryMetricRow
              label={t('recurringPayments.table.groups.nextMonth')}
              count={metrics.nextMonthCount}
              amount={metrics.nextMonthSum}
            />
          </Stack>

          <Divider sx={{ my: DIVIDER_VERTICAL_SPACING }} />

          <Stack>
            <CategorySummarySection
              title={t('recurringPayments.summary.byCategory')}
              emptyText={t('recurringPayments.summary.noDistributionData')}
              items={categoryTotals}
              kind="expenses"
              onSeeAll={() => {}}
              activeCategoryFilter={null}
              activeCreditDebitTypeFilter={null}
            />
            <Divider sx={{ my: DIVIDER_VERTICAL_SPACING }} />
            <FrequencySummarySection
              title={t('recurringPayments.summary.byFrequency')}
              emptyText={t('recurringPayments.summary.noDistributionData')}
              items={frequencyTotals}
            />
          </Stack>
        </Stack>
      )}
    </Box>
  );
}
