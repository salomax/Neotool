'use client';

import React from 'react';
import { Box, Divider, Skeleton, Typography, alpha, useTheme } from '@mui/material';
import type { Theme } from '@mui/material/styles';
import { getWidgetDefinition } from '../registry';
import { WidgetWrapper } from '../components/WidgetWrapper';
import { useDashboardStore } from '../store';
import { WidgetProps } from '../types';
import { useDashboardPeriodSummary } from '../hooks/useDashboardAnalytics';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '@/app/personal-finance/i18n';
import { CurrencyAmountParts, PercentageGain } from '@/shared/components/ui/data-display';
import { Label } from '@/shared/components/ui/primitives/Label';
import {
  formatCurrency,
  formatPercent,
  splitVolumePercentages,
  toPercentageGainRatio,
} from './month-summary/utils';

const TWO_COLUMN_GRID_TEMPLATE = {
  xs: 'repeat(1, minmax(0, 1fr))',
  sm: 'repeat(2, minmax(0, 1fr))',
};

const WIDGET_CONTENT_SX = { px: 2 };
const SECTIONS_STACK_SX = { display: 'flex', flexDirection: 'column', gap: 1.5 };
const NET_WORTH_SECTION_SX = {
  px: 1,
  pt: 1,
  display: 'flex',
  flexDirection: 'column',
  alignItems: 'stretch',
  gap: 1.5,
};
const TWO_COLUMN_GRID_SX = {
  display: 'grid',
  gridTemplateColumns: TWO_COLUMN_GRID_TEMPLATE,
  gap: 1.5,
};
const TWO_COLUMN_GRID_BASELINE_SX = {
  ...TWO_COLUMN_GRID_SX,
  alignItems: 'baseline',
};
const TWO_COLUMN_GRID_SECTION_SX = {
  px: 1,
  ...TWO_COLUMN_GRID_SX,
};
const TWO_COLUMN_GRID_SECTION_FULL_WIDTH_SX = {
  ...TWO_COLUMN_GRID_SECTION_SX,
  width: '100%',
};
const SECTION_PX_SX = { px: 1 };
const VALUE_COLUMN_SX = { display: 'flex', flexDirection: 'column', gap: 0.75 };
const VALUE_COLUMN_WIDE_SX = { display: 'flex', flexDirection: 'column', gap: 1, flex: 1 };
const METRIC_COLUMN_SX = { display: 'flex', flexDirection: 'column', gap: 0.5 };
const METRIC_COLUMN_START_SX = { display: 'flex', flexDirection: 'column', alignItems: 'flex-start', gap: 0.5 };
const BASELINE_ROW_SX = { display: 'flex', alignItems: 'baseline', gap: 1 };
const RETURN_LOADING_ROW_SX = { display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 2 };
const MONTHLY_AVERAGE_ROW_SX = { display: 'flex', alignItems: 'baseline', justifyContent: 'flex-start', gap: 0.5 };
const LABEL_SX = { mb: 0 };
const BOLD_SX = { fontWeight: 700 };
const SUCCESS_VALUE_SX = (theme: Theme) => ({ ...BOLD_SX, color: theme.palette.success.main });
const ERROR_VALUE_SX = (theme: Theme) => ({ ...BOLD_SX, color: theme.palette.error.main });
const BOLD_MUTED_SX = { fontWeight: 700, color: 'text.secondary' };
const MEDIUM_MUTED_SX = { fontWeight: 600 };
const CAPTION_MUTED_SX = { mt: -0.25 };
const NET_WORTH_VALUE_SLOT_SX = { textAlign: 'left', justifySelf: 'flex-start' };
const VOLUME_BAR_SX = {
  mt: 0.5,
  height: 6,
  borderRadius: 3,
  display: 'flex',
  overflow: 'hidden',
  width: '100%',
};
const VOLUME_BAR_SEGMENT_SX = { transition: 'flex 0.5s ease-in-out' };
const INVESTMENT_SECTION_SX = {
  px: 1,
  display: 'flex',
  flexDirection: { xs: 'column', sm: 'row' },
};

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
  const returnPctRatio = toPercentageGainRatio(summary.returnPct);
  const totalReturnValueSx = { ...BOLD_SX, color: totalReturnColor };
  const volumeBarTrackSx = React.useMemo(() => ({ ...VOLUME_BAR_SX, bgcolor: volumeBarTrackBg }), [volumeBarTrackBg]);
  const incomeVolumeBarSegmentSx = React.useMemo(
    () => ({ ...VOLUME_BAR_SEGMENT_SX, flex: absIncome, bgcolor: theme.palette.success.main }),
    [absIncome, theme.palette.success.main],
  );
  const expenseVolumeBarSegmentSx = React.useMemo(
    () => ({ ...VOLUME_BAR_SEGMENT_SX, flex: absExpenses, bgcolor: theme.palette.error.main }),
    [absExpenses, theme.palette.error.main],
  );

  return (
    <WidgetWrapper
      title={widget.title || defaultTitle}
      isEditMode={isEditMode}
      onRemove={() => removeWidget(widget.id)}
      onRefresh={() => void refetch()}
      dragHandleProps={dragHandleProps}
    >
      <Box sx={WIDGET_CONTENT_SX}>
        {error ? (
          <Typography color="error" variant="body2">
            {error.message}
          </Typography>
        ) : (
          <Box sx={SECTIONS_STACK_SX}>
            <Box sx={NET_WORTH_SECTION_SX}>
              <Box sx={TWO_COLUMN_GRID_BASELINE_SX}>
                <Label sx={LABEL_SX}>{netWorthLabel}</Label>
                <Box sx={NET_WORTH_VALUE_SLOT_SX}>
                  {loading ? (
                    <Skeleton variant="text" width={220} height={40} />
                  ) : (
                    <CurrencyAmountParts
                      value={summary.totalNetWorth}
                      currency="BRL"
                      locale="pt-BR"
                      variant="h5"
                    />
                  )}
                </Box>
              </Box>
            </Box>

            <Divider />

            <Box sx={TWO_COLUMN_GRID_SECTION_FULL_WIDTH_SX}>
              <Box sx={VALUE_COLUMN_SX}>
                <Label sx={LABEL_SX}>{cashLabel}</Label>
                {loading ? (
                  <Box sx={BASELINE_ROW_SX}>
                    <Skeleton variant="text" width={160} height={32} />
                    <Skeleton variant="text" width={56} height={22} />
                  </Box>
                ) : (
                  <Box sx={BASELINE_ROW_SX}>
                    <CurrencyAmountParts
                      value={summary.closingCashBalance}
                      currency="BRL"
                      locale="pt-BR"
                      variant="h6"
                      component="div"
                      sx={BOLD_SX}
                    />
                    <Typography variant="caption" sx={BOLD_MUTED_SX}>
                      • {formatPercent(cashPct)}
                    </Typography>
                  </Box>
                )}
              </Box>

              <Box sx={VALUE_COLUMN_SX}>
                <Label sx={LABEL_SX}>{portfolioLabel}</Label>
                {loading ? (
                  <Box sx={BASELINE_ROW_SX}>
                    <Skeleton variant="text" width={160} height={32} />
                    <Skeleton variant="text" width={56} height={22} />
                  </Box>
                ) : (
                  <Box sx={BASELINE_ROW_SX}>
                    <CurrencyAmountParts
                      value={summary.closingPortfolioValue}
                      currency="BRL"
                      locale="pt-BR"
                      variant="h6"
                      component="div"
                      sx={BOLD_SX}
                    />
                    <Typography variant="caption" sx={BOLD_MUTED_SX}>
                      • {formatPercent(portfolioPct)}
                    </Typography>
                  </Box>
                )}
              </Box>
            </Box>

            <Divider />

            <Box sx={TWO_COLUMN_GRID_SECTION_SX}>
              <Box sx={METRIC_COLUMN_SX}>
                <Label sx={LABEL_SX}>{savingsCapacityLabel}</Label>
                {loading ? (
                  <Skeleton variant="text" width={120} height={40} />
                ) : (
                  <Typography variant="h5" sx={BOLD_SX}>
                    {savingsRateValue}
                  </Typography>
                )}
              </Box>

              <Box sx={METRIC_COLUMN_START_SX}>
                <Label sx={LABEL_SX}>{monthlyAverageShort}</Label>
                {loading ? (
                  <Skeleton variant="text" width={200} height={24} />
                ) : (
                  <Box sx={MONTHLY_AVERAGE_ROW_SX}>
                    <CurrencyAmountParts
                      value={summary.avgMonthlySavings}
                      currency="BRL"
                      locale="pt-BR"
                      variant="h6"
                      component="div"
                      sx={BOLD_SX}
                    />
                    <Typography variant="body2" color="text.secondary" sx={MEDIUM_MUTED_SX}>
                      {perMonthSuffix}
                    </Typography>
                  </Box>
                )}
              </Box>
            </Box>

            <Divider />

            <Box sx={SECTION_PX_SX}>
              <Box sx={TWO_COLUMN_GRID_SX}>
                <Box sx={VALUE_COLUMN_SX}>
                  <Label>{t('dashboard.widgets.periodSummary.totalIncome')}</Label>
                  {loading ? (
                    <>
                      <Box sx={BASELINE_ROW_SX}>
                        <Skeleton variant="text" width={180} height={36} />
                        <Skeleton variant="text" width={44} height={22} />
                      </Box>
                      <Skeleton variant="text" width={140} height={18} />
                    </>
                  ) : (
                    <>
                      <Box sx={BASELINE_ROW_SX}>
                        <CurrencyAmountParts
                          value={summary.totalIncome}
                          currency="BRL"
                          locale="pt-BR"
                          variant="h6"
                          component="div"
                          sx={SUCCESS_VALUE_SX}
                        />
                        <Typography variant="body2" sx={SUCCESS_VALUE_SX}>
                          • {totalVolume > 0 ? `${incomePercentage}%` : '—'}
                        </Typography>
                      </Box>
                      <Typography variant="caption" color="text.secondary" sx={CAPTION_MUTED_SX}>
                        {avgIncomeLabel}
                      </Typography>
                    </>
                  )}
                </Box>

                <Box sx={VALUE_COLUMN_SX}>
                  <Label>{t('dashboard.widgets.periodSummary.totalExpenses')}</Label>
                  {loading ? (
                    <>
                      <Box sx={BASELINE_ROW_SX}>
                        <Skeleton variant="text" width={180} height={36} />
                        <Skeleton variant="text" width={44} height={22} />
                      </Box>
                      <Skeleton variant="text" width={140} height={18} />
                    </>
                  ) : (
                    <>
                      <Box sx={BASELINE_ROW_SX}>
                        <CurrencyAmountParts
                          value={summary.totalExpenses}
                          currency="BRL"
                          locale="pt-BR"
                          variant="h6"
                          component="div"
                          sx={ERROR_VALUE_SX}
                        />
                        <Typography variant="body2" sx={ERROR_VALUE_SX}>
                          • {totalVolume > 0 ? `${expensesPercentage}%` : '—'}
                        </Typography>
                      </Box>
                      <Typography variant="caption" color="text.secondary" sx={CAPTION_MUTED_SX}>
                        {avgExpenseLabel}
                      </Typography>
                    </>
                  )}
                </Box>
              </Box>

              {(totalVolume > 0 || loading) && (
                <Box sx={volumeBarTrackSx}>
                  {loading ? (
                    <Skeleton variant="rectangular" width="100%" height="100%" />
                  ) : (
                    <>
                      <Box sx={incomeVolumeBarSegmentSx} />
                      <Box sx={expenseVolumeBarSegmentSx} />
                    </>
                  )}
                </Box>
              )}
            </Box>

            <Divider />

            <Box sx={INVESTMENT_SECTION_SX}>
              <Box sx={VALUE_COLUMN_WIDE_SX}>
                <Label sx={LABEL_SX}>{totalInvestedLabel}</Label>
                {loading ? (
                  <>
                    <Skeleton variant="text" width="80%" height={36} />
                    <Skeleton variant="text" width={140} height={18} />
                  </>
                ) : (
                  <>
                    <CurrencyAmountParts value={summary.totalInvested} currency="BRL" locale="pt-BR" variant="h6" sx={BOLD_SX} />
                    <Typography variant="caption" color="text.secondary" sx={CAPTION_MUTED_SX}>
                      {avgInvestedLabel}
                    </Typography>
                  </>
                )}
              </Box>

              <Box sx={VALUE_COLUMN_WIDE_SX}>
                <Label sx={LABEL_SX}>{totalReturnLabel}</Label>
                {loading ? (
                  <>
                    <Box sx={RETURN_LOADING_ROW_SX}>
                      <Skeleton variant="text" width="65%" height={36} />
                      <Skeleton variant="text" width={56} height={22} />
                    </Box>
                    <Skeleton variant="text" width={140} height={18} />
                  </>
                ) : (
                  <>
                    <Box sx={BASELINE_ROW_SX}>
                      <CurrencyAmountParts
                        value={summary.totalReturn}
                        currency="BRL"
                        locale="pt-BR"
                        variant="h6"
                        sx={totalReturnValueSx}
                      />
                      <Typography variant="body2" sx={totalReturnValueSx}>
                        •
                      </Typography>
                      <PercentageGain
                        value={returnPctRatio}
                        locale="pt-BR"
                        showIcon
                        variant="body2"
                        fontWeight={700}
                        maximumFractionDigits={1}
                      />
                    </Box>
                    <Typography variant="caption" color="text.secondary" sx={CAPTION_MUTED_SX}>
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
