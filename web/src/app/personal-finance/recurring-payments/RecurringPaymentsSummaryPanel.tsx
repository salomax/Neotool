'use client';

import {
  Box,
  Divider,
  LinearProgress,
  Skeleton,
  Stack,
  ToggleButton,
  ToggleButtonGroup,
  Typography,
  useTheme,
} from '@mui/material';
import { useMemo, useState } from 'react';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../i18n';
import { CurrencyAmountParts } from '@/shared/components/ui/data-display';
import { QueryErrorState } from '@/shared/components/ui/feedback';
import type { RecurringPaymentRow } from './recurringPaymentRowModel';
import {
  buildCategoryDistribution,
  buildFrequencyDistribution,
  computeRecurringSummaryMetrics,
  type DistributionMode,
} from './recurringPaymentRowModel';

const LOCALE = 'pt-BR';
const CURRENCY = 'BRL';

function formatCurrency(value: number): string {
  return new Intl.NumberFormat(LOCALE, { style: 'currency', currency: CURRENCY }).format(value);
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
  const [distributionMode, setDistributionMode] = useState<DistributionMode>('category');

  const metrics = useMemo(() => computeRecurringSummaryMetrics(rows), [rows]);

  const distribution = useMemo(() => {
    return distributionMode === 'category' ? buildCategoryDistribution(rows) : buildFrequencyDistribution(rows);
  }, [rows, distributionMode]);

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
          <Skeleton variant="rounded" height={120} />
        </Stack>
      ) : (
        <Stack spacing={2.5}>
          <Box>
            <Typography variant="caption" color="text.secondary" sx={{ textTransform: 'uppercase', letterSpacing: 0.6 }}>
              {t('recurringPayments.summary.monthlyCommitted')}
            </Typography>
            <CurrencyAmountParts
              value={metrics.monthlyCommittedApprox}
              currency={CURRENCY}
              locale={LOCALE}
              variant="h3"
              component="div"
              sx={{ fontWeight: 700, mt: 0.5, fontSize: '2.3rem', color: theme.palette.text.primary }}
            />
          </Box>

          <Divider />

          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', gap: 2 }}>
            <Typography variant="body2" color="text.secondary">
              {t('recurringPayments.summary.next7Days')}
            </Typography>
            <Box sx={{ textAlign: 'right' }}>
              <Typography variant="body1" sx={{ fontWeight: 600 }}>
                {metrics.next7Count} · {formatCurrency(metrics.next7Sum)}
              </Typography>
            </Box>
          </Box>

          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', gap: 2 }}>
            <Typography variant="body2" color="text.secondary">
              {t('recurringPayments.summary.overdue')}
            </Typography>
            <Typography
              variant="body1"
              sx={{ fontWeight: 600, color: metrics.overdueCount > 0 ? 'error.main' : 'text.primary' }}
            >
              {metrics.overdueCount}
            </Typography>
          </Box>

          <Divider />

          <Box>
            <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1.5 }}>
              <Typography variant="caption" color="text.secondary" sx={{ textTransform: 'uppercase', letterSpacing: 0.6 }}>
                {t('recurringPayments.summary.distribution')}
              </Typography>
              <ToggleButtonGroup
                size="small"
                exclusive
                value={distributionMode}
                onChange={(_, v: DistributionMode | null) => v && setDistributionMode(v)}
              >
                <ToggleButton value="category" sx={{ px: 1.5, textTransform: 'none' }}>
                  {t('recurringPayments.summary.byCategory')}
                </ToggleButton>
                <ToggleButton value="frequency" sx={{ px: 1.5, textTransform: 'none' }}>
                  {t('recurringPayments.summary.byFrequency')}
                </ToggleButton>
              </ToggleButtonGroup>
            </Stack>

            {distribution.length === 0 ? (
              <Typography variant="body2" color="text.secondary">
                {t('recurringPayments.summary.noDistributionData')}
              </Typography>
            ) : (
              <Stack spacing={1.25}>
                {distribution.map((item) => (
                  <Box key={item.key}>
                    <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 0.25 }}>
                      <Typography variant="body2" noWrap sx={{ maxWidth: '58%' }} title={item.label}>
                        {distributionMode === 'frequency'
                          ? t(`recurringPayments.frequency.${item.label}`)
                          : item.key === '__none__'
                            ? t('recurringPayments.uncategorized')
                            : item.label}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {formatCurrency(item.amount)} ({item.pct}%)
                      </Typography>
                    </Stack>
                    <LinearProgress
                      variant="determinate"
                      value={item.pct}
                      sx={{
                        height: 6,
                        borderRadius: 1,
                        bgcolor: theme.palette.action.hover,
                        '& .MuiLinearProgress-bar': { borderRadius: 1 },
                      }}
                    />
                  </Box>
                ))}
              </Stack>
            )}
          </Box>
        </Stack>
      )}
    </Box>
  );
}
