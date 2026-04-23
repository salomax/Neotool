'use client';

import React from 'react';
import { Box, Divider, Skeleton, Typography, alpha, useTheme } from '@mui/material';
import { getWidgetDefinition } from '../registry';
import { WidgetWrapper } from '../components/WidgetWrapper';
import { useDashboardStore } from '../store';
import { WidgetProps } from '../types';
import { useDashboardPeriodSummary } from '../hooks/useDashboardAnalytics';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '@/app/personal-finance/i18n';
import { CurrencyAmountParts, PercentageGain } from '@/shared/components/ui/data-display';
import { Label } from '@/shared/components/ui/primitives/Label';

function formatCurrency(value: number) {
  return new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency: 'BRL',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value);
}

function formatPercent(value: number | null | undefined) {
  if (value == null || !Number.isFinite(value)) return '—';
  return `${new Intl.NumberFormat('pt-BR', {
    minimumFractionDigits: 1,
    maximumFractionDigits: 1,
  }).format(value)}%`;
}

function splitVolumePercentages(absIncome: number, absExpenses: number, totalVolume: number) {
  if (totalVolume <= 0) return { incomePct: 0, expensesPct: 0 };
  const incomePct = Math.round((absIncome / totalVolume) * 100);
  const roundedExpensesPct = Math.round((absExpenses / totalVolume) * 100);
  const expensesPct = incomePct + roundedExpensesPct === 100 ? roundedExpensesPct : 100 - incomePct;
  return { incomePct, expensesPct };
}

export const MonthSummaryKpiWidget: React.FC<WidgetProps> = ({ widget, dragHandleProps }) => {
  const theme = useTheme();
  const { isEditMode, removeWidget } = useDashboardStore();
  const { summary, loading, error, refetch } = useDashboardPeriodSummary();
  const { t } = useTranslation(personalFinanceTranslations);
  const definition = getWidgetDefinition(widget.type);
  const netWorthLabel = t('dashboard.widgets.periodSummary.totalNetWorth');
  const cashLabel = t('dashboard.widgets.periodSummary.closingCashBalance');
  const portfolioLabel = t('dashboard.widgets.netWorth.portfolioValue');
  const savingsCapacityLabel = t('dashboard.widgets.periodSummary.savingsCapacityLabel');
  const monthlyAverageShort = t('dashboard.widgets.periodSummary.monthlyAverageShort');
  const perMonthSuffix = t('dashboard.widgets.periodSummary.perMonthSuffix');
  const totalInvestedLabel = t('dashboard.widgets.periodSummary.totalInvested');
  const totalReturnLabel = t('dashboard.widgets.periodSummary.totalReturnShort');

  const totalNetWorth = summary.totalNetWorth;
  const cashPct = totalNetWorth > 0 ? (summary.closingCashBalance / totalNetWorth) * 100 : null;
  const portfolioPct = totalNetWorth > 0 ? (summary.closingPortfolioValue / totalNetWorth) * 100 : null;
  const avgIncomeLabel = t('dashboard.widgets.periodSummary.monthlyAveragePerMonth', {
    value: formatCurrency(summary.avgMonthlyIncome),
  });
  const avgExpenseLabel = t('dashboard.widgets.periodSummary.monthlyAveragePerMonth', {
    value: formatCurrency(summary.avgMonthlyExpenses),
  });
  const avgInvestedLabel = t('dashboard.widgets.periodSummary.monthlyAveragePerMonth', {
    value: formatCurrency(summary.avgMonthlyInvested),
  });
  const avgReturnLabel = t('dashboard.widgets.periodSummary.monthlyAveragePerMonth', {
    value: formatCurrency(summary.avgMonthlyReturn),
  });
  const savingsRateValue = formatPercent(summary.savingsRate);
  const defaultTitle = definition?.defaultTitleKey ? t(definition.defaultTitleKey) : '';

  const absIncome = Math.abs(summary.totalIncome);
  const absExpenses = Math.abs(summary.totalExpenses);
  const totalVolume = absIncome + absExpenses;
  const { incomePct: incomePercentage, expensesPct: expensesPercentage } = splitVolumePercentages(absIncome, absExpenses, totalVolume);
  const volumeBarTrackBg = alpha(theme.palette.divider, 0.28);
  const totalReturnColor =
    summary.totalReturn > 0 ? theme.palette.success.main : summary.totalReturn < 0 ? theme.palette.error.main : theme.palette.text.primary;
  const returnPctRatio = summary.returnPct == null ? null : summary.returnPct / 100;
  const avgMonthlySavings = summary.monthCount > 0 ? summary.net / summary.monthCount : null;

  return (
    <WidgetWrapper
      title={widget.title || defaultTitle}
      isEditMode={isEditMode}
      onRemove={() => removeWidget(widget.id)}
      onRefresh={() => void refetch()}
      dragHandleProps={dragHandleProps}
    >
      <Box sx={{ p: 2 }}>
        {error ? (
          <Typography color="error" variant="body2">
            {error.message}
          </Typography>
        ) : (
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0 }}>
            <Box
              sx={{
                p: 2,
                borderRadius: 1.5,
                display: 'flex',
                flexDirection: { xs: 'column', sm: 'row' },
                alignItems: { xs: 'flex-start', sm: 'center' },
                justifyContent: 'space-between',
                gap: 2,
              }}
            >
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.5 }}>
                <Typography variant="caption" color="text.secondary" sx={{ textTransform: 'uppercase' }}>
                  {netWorthLabel}
                </Typography>
                {loading ? (
                  <Skeleton variant="text" width={220} height={40} />
                ) : (
                  <CurrencyAmountParts value={summary.totalNetWorth} currency="BRL" variant="h5" sx={{ fontWeight: 700 }} />
                )}
              </Box>

              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.5, width: { xs: '100%', sm: 'auto' } }}>
                <Box sx={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 2 }}>
                  <Typography variant="caption" color="text.secondary">
                    {cashLabel} {formatPercent(cashPct)}
                  </Typography>
                  <Typography variant="body2" sx={{ fontWeight: 600 }}>
                    {loading ? <Skeleton variant="text" width={120} /> : formatCurrency(summary.closingCashBalance)}
                  </Typography>
                </Box>
                <Divider />
                <Box sx={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 2 }}>
                  <Typography variant="caption" color="text.secondary">
                    {portfolioLabel} {formatPercent(portfolioPct)}
                  </Typography>
                  <Typography variant="body2" sx={{ fontWeight: 600 }}>
                    {loading ? <Skeleton variant="text" width={120} /> : formatCurrency(summary.closingPortfolioValue)}
                  </Typography>
                </Box>
              </Box>
            </Box>

            <Divider />

            <Box
              sx={{
                p: 2,
                borderRadius: 1.5,
                display: 'flex',
                flexDirection: { xs: 'column', sm: 'row' },
                alignItems: { xs: 'flex-start', sm: 'flex-end' },
                justifyContent: 'space-between',
                gap: 1.5,
              }}
            >
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.5 }}>
                <Typography variant="caption" color="text.secondary" sx={{ textTransform: 'uppercase' }}>
                  {savingsCapacityLabel}
                </Typography>
                {loading ? (
                  <Skeleton variant="text" width={120} height={40} />
                ) : (
                  <Typography variant="h5" sx={{ fontWeight: 700 }}>
                    {savingsRateValue}
                  </Typography>
                )}
              </Box>

              <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: { xs: 'flex-start', sm: 'flex-end' }, gap: 0.5 }}>
                {loading ? (
                  <Skeleton variant="text" width={200} height={24} />
                ) : (
                  <Typography
                    variant="body2"
                    color="text.secondary"
                    sx={{ display: 'flex', alignItems: 'baseline', justifyContent: 'flex-end', gap: 0.5, fontWeight: 600 }}
                  >
                    <Box component="span">{monthlyAverageShort}</Box>
                    <CurrencyAmountParts
                      value={avgMonthlySavings}
                      currency="BRL"
                      locale="pt-BR"
                      variant="h6"
                      component="span"
                      sx={{ fontWeight: 700 }}
                    />
                    <Box component="span">{perMonthSuffix}</Box>
                  </Typography>
                )}
              </Box>
            </Box>

            <Divider />

            <Box
              sx={{
                p: 2,
                borderRadius: 1.5,
              }}
            >
              <Box
                sx={{
                  display: 'grid',
                  gridTemplateColumns: { xs: 'repeat(1, minmax(0, 1fr))', sm: 'repeat(2, minmax(0, 1fr))' },
                  gap: 1.5,
                }}
              >
                <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.75 }}>
                  <Label>{t('dashboard.widgets.periodSummary.totalIncome')}</Label>
                  {loading ? (
                    <>
                      <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 1 }}>
                        <Skeleton variant="text" width={180} height={36} />
                        <Skeleton variant="text" width={44} height={22} />
                      </Box>
                      <Skeleton variant="text" width={140} height={18} />
                    </>
                  ) : (
                    <>
                      <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 1 }}>
                        <CurrencyAmountParts
                          value={summary.totalIncome}
                          currency="BRL"
                          locale="pt-BR"
                          variant="h6"
                          component="div"
                          sx={{ fontWeight: 700, color: theme.palette.success.main }}
                        />
                        <Typography variant="body2" sx={{ fontWeight: 700, color: theme.palette.success.main }}>
                          • {totalVolume > 0 ? `${incomePercentage}%` : '—'}
                        </Typography>
                      </Box>
                      <Typography variant="caption" color="text.secondary" sx={{ mt: -0.25 }}>
                        {avgIncomeLabel}
                      </Typography>
                    </>
                  )}
                </Box>

                <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.75 }}>
                  <Label>{t('dashboard.widgets.periodSummary.totalExpenses')}</Label>
                  {loading ? (
                    <>
                      <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 1 }}>
                        <Skeleton variant="text" width={180} height={36} />
                        <Skeleton variant="text" width={44} height={22} />
                      </Box>
                      <Skeleton variant="text" width={140} height={18} />
                    </>
                  ) : (
                    <>
                      <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 1 }}>
                        <CurrencyAmountParts
                          value={summary.totalExpenses}
                          currency="BRL"
                          locale="pt-BR"
                          variant="h6"
                          component="div"
                          sx={{ fontWeight: 700, color: theme.palette.error.main }}
                        />
                        <Typography variant="body2" sx={{ fontWeight: 700, color: theme.palette.error.main }}>
                          • {totalVolume > 0 ? `${expensesPercentage}%` : '—'}
                        </Typography>
                      </Box>
                      <Typography variant="caption" color="text.secondary" sx={{ mt: -0.25 }}>
                        {avgExpenseLabel}
                      </Typography>
                    </>
                  )}
                </Box>
              </Box>

              {(totalVolume > 0 || loading) && (
                <>
                  <Box
                    sx={{
                      mt: 0.5,
                      height: 6,
                      borderRadius: 3,
                      display: 'flex',
                      overflow: 'hidden',
                      width: '100%',
                      bgcolor: volumeBarTrackBg,
                    }}
                  >
                    {loading ? (
                      <Skeleton variant="rectangular" width="100%" height="100%" />
                    ) : (
                      <>
                        <Box sx={{ flex: absIncome, bgcolor: theme.palette.success.main, transition: 'flex 0.5s ease-in-out' }} />
                        <Box sx={{ flex: absExpenses, bgcolor: theme.palette.error.main, transition: 'flex 0.5s ease-in-out' }} />
                      </>
                    )}
                  </Box>
                </>
              )}
            </Box>

            <Divider />

            <Box
              sx={{
                display: 'flex',
                flexDirection: { xs: 'column', sm: 'row' },
              }}
            >
              <Box
                sx={{
                  p: 2,
                  borderRadius: 1.5,
                  display: 'flex',
                  flexDirection: 'column',
                  gap: 1,
                  flex: 1,
                }}
              >
                <Typography variant="caption" color="text.secondary" sx={{ textTransform: 'uppercase' }}>
                  {totalInvestedLabel}
                </Typography>
                {loading ? (
                  <>
                    <Skeleton variant="text" width="80%" height={36} />
                    <Skeleton variant="text" width={140} height={18} />
                  </>
                ) : (
                  <>
                    <CurrencyAmountParts value={summary.totalInvested} currency="BRL" locale="pt-BR" variant="h6" sx={{ fontWeight: 700 }} />
                    <Typography variant="caption" color="text.secondary" sx={{ mt: -0.25 }}>
                      {avgInvestedLabel}
                    </Typography>
                  </>
                )}
              </Box>

              <Box
                sx={{
                  p: 2,
                  borderRadius: 1.5,
                  display: 'flex',
                  flexDirection: 'column',
                  gap: 1,
                  flex: 1,
                }}
              >
                <Typography variant="caption" color="text.secondary" sx={{ textTransform: 'uppercase' }}>
                  {totalReturnLabel}
                </Typography>
                {loading ? (
                  <>
                    <Box sx={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 2 }}>
                      <Skeleton variant="text" width="65%" height={36} />
                      <Skeleton variant="text" width={56} height={22} />
                    </Box>
                    <Skeleton variant="text" width={140} height={18} />
                  </>
                ) : (
                  <>
                    <Box sx={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 2 }}>
                      <CurrencyAmountParts
                        value={summary.totalReturn}
                        currency="BRL"
                        locale="pt-BR"
                        variant="h6"
                        sx={{ fontWeight: 700, color: totalReturnColor }}
                      />
                      <PercentageGain
                        value={returnPctRatio}
                        locale="pt-BR"
                        showIcon
                        variant="body2"
                        fontWeight={700}
                        maximumFractionDigits={1}
                      />
                    </Box>
                    <Typography variant="caption" color="text.secondary" sx={{ mt: -0.25 }}>
                      {avgReturnLabel}
                    </Typography>
                  </>
                )}
              </Box>
            </Box>
          </Box>
        )}
      </Box>
    </WidgetWrapper>
  );
};
