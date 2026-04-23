'use client';

import React from 'react';
import ShowChartIcon from '@mui/icons-material/ShowChart';
import { Box, Skeleton, Typography, useTheme } from '@mui/material';
import { alpha } from '@mui/material/styles';
import {
  Area,
  AreaChart,
  CartesianGrid,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import type { GridLineTypeFunctionProps } from 'recharts/types/cartesian/CartesianGrid';
import { getWidgetDefinition } from '../registry';
import { WidgetWrapper } from '../components/WidgetWrapper';
import { useDashboardStore } from '../store';
import { WidgetProps } from '../types';
import { useCashFlowSeries, usePortfolioValueSeries } from '../hooks/useDashboardAnalytics';
import { i18n, useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '@/app/personal-finance/i18n';
import { Label } from '@/shared/components/ui/primitives/Label';
import { CurrencyAmountParts } from '@/shared/components/ui/data-display/CurrencyAmountParts';

type AxisTickProps = {
  x?: number | string;
  y?: number | string;
  payload?: { value?: number | string };
};

type XAxisTickProps = {
  x?: number | string;
  y?: number | string;
  payload?: { value?: string };
};

type NetWorthChartRow = {
  label: string;
  year: number;
  month: number;
  bankingBalance: number | null;
  portfolioValue: number | null;
  totalNetWorth: number | null;
  isEstimated: boolean;
};

function formatBrl(value: number) {
  return new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency: 'BRL',
    maximumFractionDigits: 0,
  }).format(value);
}

function formatBrlFull(value: number) {
  return new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency: 'BRL',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value);
}

export const NetWorthWidget: React.FC<WidgetProps> = ({ widget, dragHandleProps }) => {
  const theme = useTheme();
  const { isEditMode, removeWidget } = useDashboardStore();
  const { series, loading: seriesLoading, error: seriesError, refetch: refetchSeries } = useCashFlowSeries();
  const {
    portfolioSeries,
    loading: portfolioLoading,
    error: portfolioError,
    refetch: refetchPortfolioSeries,
  } = usePortfolioValueSeries();
  const { t } = useTranslation(personalFinanceTranslations);
  const definition = getWidgetDefinition(widget.type);
  const gradientSeed = React.useId().replace(/:/g, '');
  const locale = i18n.resolvedLanguage ?? i18n.language;

  const monthLabelFormatter = React.useMemo(() => {
    const monthFormatter = new Intl.DateTimeFormat(locale, { month: 'short' });
    return (year: number, month: number) => {
      const date = new Date(year, month - 1, 1);
      const raw = monthFormatter.format(date).replace(/\.$/, '');
      const monthPart = raw ? raw.charAt(0).toLocaleUpperCase(locale) + raw.slice(1) : raw;
      return `${monthPart}/${year}`;
    };
  }, [locale]);

  const chartData = React.useMemo((): NetWorthChartRow[] => {
    const portfolioMap = new Map<string, (typeof portfolioSeries)[number]>(
      portfolioSeries.map((p) => [`${p.year}-${p.month}`, p])
    );
    return series.map((point) => {
      const key = `${point.year}-${point.month}`;
      const portfolio = portfolioMap.get(key);
      const bankingBalance = Number.isFinite(point.closingBalance) ? point.closingBalance : null;
      const portfolioValue =
        portfolio && Number.isFinite(portfolio.grossValue) ? portfolio.grossValue : null;

      return {
        label: monthLabelFormatter(point.year, point.month),
        year: point.year,
        month: point.month,
        bankingBalance,
        portfolioValue,
        totalNetWorth:
          bankingBalance !== null && portfolioValue !== null ? bankingBalance + portfolioValue : null,
        isEstimated: portfolio?.isEstimated ?? false,
      };
    });
  }, [monthLabelFormatter, portfolioSeries, series]);

  const hasNegativeTotalNetWorth = React.useMemo(
    () =>
      chartData.some(
        (row) =>
          typeof row.totalNetWorth === 'number' && Number.isFinite(row.totalNetWorth) && row.totalNetWorth < 0,
      ),
    [chartData],
  );

  const lastMonthTotalNetWorth = React.useMemo(() => {
    const lastRow = chartData.length > 0 ? chartData[chartData.length - 1] : null;
    if (!lastRow) return null;

    if (typeof lastRow.totalNetWorth === 'number' && Number.isFinite(lastRow.totalNetWorth)) {
      return lastRow.totalNetWorth;
    }

    const banking = typeof lastRow.bankingBalance === 'number' && Number.isFinite(lastRow.bankingBalance) ? lastRow.bankingBalance : null;
    const portfolio = typeof lastRow.portfolioValue === 'number' && Number.isFinite(lastRow.portfolioValue) ? lastRow.portfolioValue : null;
    if (banking == null || portfolio == null) return null;
    const total = banking + portfolio;
    return Number.isFinite(total) ? total : null;
  }, [chartData]);

  const yDomain = React.useMemo((): readonly [number, number] => {
    let min = Infinity;
    let max = -Infinity;
    for (const row of chartData) {
      const values = [row.bankingBalance, row.portfolioValue, row.totalNetWorth];
      for (const value of values) {
        if (typeof value === 'number' && Number.isFinite(value)) {
          min = Math.min(min, value);
          max = Math.max(max, value);
        }
      }
    }

    if (!Number.isFinite(min) || !Number.isFinite(max)) {
      return [0, 1];
    }

    const minValue = hasNegativeTotalNetWorth ? min : 0;
    const span = Math.max(max - minValue, 1);
    const pad = span * 0.1;
    return hasNegativeTotalNetWorth ? [minValue - pad, max + pad] : [0, max + pad];
  }, [chartData, hasNegativeTotalNetWorth]);

  const horizontalGridLine = React.useCallback(
    (props: GridLineTypeFunctionProps) => {
      const { offset, y1, y2, strokeDasharray, index: _index, key: _key, ...lineProps } = props;
      const isTop = Math.round(y1) === Math.round(offset.top);
      const dash = Array.isArray(strokeDasharray) ? strokeDasharray.join(' ') : strokeDasharray;
      return (
        <line
          {...lineProps}
          y1={y1}
          y2={y2}
          strokeDasharray={dash}
          stroke={isTop ? 'none' : (lineProps.stroke ?? theme.palette.divider)}
        />
      );
    },
    [theme.palette.divider],
  );

  const xAxisTick = React.useCallback(
    (props: XAxisTickProps) => {
      const x = Number(props.x ?? 0);
      const y = Number(props.y ?? 0);
      return (
        <text x={x} y={y} dy={16} textAnchor="middle" fill={theme.palette.text.secondary} fontSize={11}>
          {props.payload?.value ?? ''}
        </text>
      );
    },
    [theme.palette.text.secondary]
  );

  const yAxisTick = React.useCallback(
    (props: AxisTickProps) => {
      const x = Number(props.x ?? 0);
      const y = Number(props.y ?? 0);
      const v = Number(props.payload?.value ?? 0);
      const safeValue = Number.isFinite(v) ? v : 0;
      return (
        <text
          x={x}
          y={y}
          dy={4}
          textAnchor="start"
          fontSize={11}
          fill={safeValue < 0 ? theme.palette.error.main : theme.palette.text.secondary}
        >
          {formatBrl(safeValue)}
        </text>
      );
    },
    [theme.palette.error.main, theme.palette.text.secondary]
  );

  const bankingBalanceLabel = t('dashboard.widgets.netWorth.bankingBalance');
  const portfolioValueLabel = t('dashboard.widgets.netWorth.portfolioValue');
  const totalNetWorthLabel = t('dashboard.widgets.netWorth.totalNetWorth');
  const estimatedNoteLabel = t('dashboard.widgets.netWorth.estimatedNote');
  const defaultTitle = definition?.defaultTitleKey ? t(definition.defaultTitleKey) : '';
  const loading = seriesLoading || portfolioLoading;
  const error = seriesError ?? portfolioError;

  const tooltipContent = React.useCallback(
    (props: any) => {
      const { active, payload } = props as { active?: boolean; payload?: Array<{ payload?: NetWorthChartRow }> };
      if (!active || !payload || payload.length === 0) return null;
      const row = payload[0]?.payload as NetWorthChartRow | undefined;
      if (!row) return null;

      return (
        <Box
          sx={{
            bgcolor: 'background.paper',
            border: '1px solid',
            borderColor: 'divider',
            borderRadius: 1.5,
            px: 1.25,
            py: 1,
            minWidth: 180,
          }}
        >
          <Typography variant="caption" sx={{ color: 'text.secondary' }}>
            {row.label}
          </Typography>
          <Box sx={{ mt: 0.75, display: 'flex', flexDirection: 'column', gap: 0.5 }}>
            <Box sx={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 2 }}>
              <Label sx={{ fontSize: '.85rem', color: 'text.secondary' }}>{bankingBalanceLabel}</Label>
              <Typography variant="body2" sx={{ fontWeight: 600, color: theme.palette.primary.main }}>
                {formatBrlFull(row.bankingBalance ?? 0)}
              </Typography>
            </Box>
            <Box sx={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 2 }}>
              <Label sx={{ fontSize: '.85rem', color: 'text.secondary' }}>{portfolioValueLabel}</Label>
              <Typography variant="body2" sx={{ fontWeight: 600, color: theme.palette.success.main }}>
                {formatBrlFull(row.portfolioValue ?? 0)}
              </Typography>
            </Box>
            <Box sx={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 2 }}>
              <Label sx={{ fontSize: '.85rem', color: 'text.secondary' }}>{totalNetWorthLabel}</Label>
              <Typography variant="body2" sx={{ fontWeight: 700, color: theme.palette.text.primary }}>
                {formatBrlFull(row.totalNetWorth ?? (row.bankingBalance ?? 0) + (row.portfolioValue ?? 0))}
              </Typography>
            </Box>
          </Box>
        </Box>
      );
    },
    [
      bankingBalanceLabel,
      portfolioValueLabel,
      theme.palette.primary.main,
      theme.palette.success.main,
      theme.palette.text.primary,
      totalNetWorthLabel,
    ]
  );

  const handleRefresh = React.useCallback(() => {
    void refetchSeries();
    void refetchPortfolioSeries();
  }, [refetchPortfolioSeries, refetchSeries]);

  return (
    <WidgetWrapper
      title={widget.title || defaultTitle}
      icon={<ShowChartIcon />}
      isEditMode={isEditMode}
      onRemove={() => removeWidget(widget.id)}
      onRefresh={handleRefresh}
      dragHandleProps={dragHandleProps}
    >
      <Box sx={{ p: 2, height: '100%', display: 'flex', flexDirection: 'column', boxSizing: 'border-box' }}>
        {loading ? (
          <Skeleton variant="rectangular" sx={{ borderRadius: 1, height: '100%', minHeight: 240 }} />
        ) : error ? (
          <Typography color="error" variant="body2">
            {error.message}
          </Typography>
        ) : chartData.length === 0 ? (
          <Typography variant="body2" color="text.secondary">
            {t('dashboard.widgets.netWorth.noData')}
          </Typography>
        ) : (
          <Box sx={{ width: '100%', flex: 1, minHeight: 240, position: 'relative' }}>
            <Box
              sx={{
                position: 'absolute',
                top: -20,
                right: 8,
                zIndex: 2,
                pointerEvents: 'none',
                bgcolor: "transparent",
                border: 'none',
                px: 1.25,
                py: 1,
                textAlign: 'right',
              }}
            >
              <Label sx={{ mb: 0, fontSize: '.75rem' }}>{totalNetWorthLabel}</Label>
              <CurrencyAmountParts
                value={lastMonthTotalNetWorth}
                currency="BRL"
                locale={i18n.language}
                variant="h3"
                sx={{ fontWeight: 800, whiteSpace: 'nowrap', lineHeight: 1 }}
              />
            </Box>
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={chartData} margin={{ top: 8, right: 8, left: 8, bottom: 8 }}>
                <defs>
                  <linearGradient id={`net-worth-banking-${gradientSeed}`} x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor={theme.palette.primary.main} stopOpacity={0.5} />
                    <stop offset="95%" stopColor={theme.palette.primary.main} stopOpacity={0.1} />
                  </linearGradient>
                  <linearGradient id={`net-worth-portfolio-${gradientSeed}`} x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor={theme.palette.success.main} stopOpacity={0.5} />
                    <stop offset="95%" stopColor={theme.palette.success.main} stopOpacity={0.3} />
                  </linearGradient>
                </defs>
                <CartesianGrid
                  strokeDasharray="3 3"
                  stroke={theme.palette.divider}
                  vertical={false}
                  horizontal={horizontalGridLine}
                />
                <XAxis
                  dataKey="label"
                  tick={xAxisTick}
                  axisLine={{ stroke: theme.palette.divider }}
                  tickLine={{ stroke: theme.palette.divider }}
                  interval="preserveStartEnd"
                  minTickGap={28}
                  tickMargin={8}
                />
                <YAxis
                  mirror
                  domain={yDomain}
                  tick={yAxisTick}
                  padding={{ top: 12, bottom: hasNegativeTotalNetWorth ? 12 : 0 }}
                  axisLine={false}
                  tickLine={false}
                />
                <ReferenceLine y={0} stroke={theme.palette.divider} />
                <Tooltip content={tooltipContent} />
                <Area
                  type="monotone"
                  dataKey="bankingBalance"
                  name={bankingBalanceLabel}
                  stackId="netWorth"
                  stroke="none"
                  fill={`url(#net-worth-banking-${gradientSeed})`}
                  dot={false}
                  activeDot={false}
                  connectNulls={false}
                  isAnimationActive={false}
                />
                <Area
                  type="monotone"
                  dataKey="portfolioValue"
                  name={portfolioValueLabel}
                  stackId="netWorth"
                  stroke="none"
                  fill={`url(#net-worth-portfolio-${gradientSeed})`}
                  dot={false}
                  activeDot={false}
                  connectNulls={false}
                  isAnimationActive={false}
                />
              </AreaChart>
            </ResponsiveContainer>
          </Box>
        )}
      </Box>
    </WidgetWrapper>
  );
};
