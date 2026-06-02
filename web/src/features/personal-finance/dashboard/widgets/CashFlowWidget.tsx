'use client';

import React from 'react';
import BarChartIcon from '@mui/icons-material/BarChart';
import {
  Bar,
  ComposedChart as RechartsComposedChart,
  CartesianGrid,
  Cell,
  Line,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import type { TooltipContentProps } from 'recharts/types/component/Tooltip';
import type { GridLineTypeFunctionProps } from 'recharts/types/cartesian/CartesianGrid';
import { Box, Button, Skeleton, Typography, useTheme } from '@mui/material';
import { alpha } from '@mui/material/styles';
import { useRouter } from 'next/navigation';
import { getWidgetDefinition } from '../registry';
import { WidgetWrapper } from '../components/WidgetWrapper';
import { useDashboardStore } from '../store';
import { WidgetProps } from '../types';
import { useCashFlowSeries } from '../hooks/useDashboardAnalytics';
import { i18n, useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '@/app/personal-finance/i18n';
import { Label } from '@/shared/components/ui/primitives/Label';

type AxisTickProps = {
  x?: number | string;
  y?: number | string;
  payload?: { value?: number | string };
};

type XAxisTickProps = {
  x?: number | string;
  y?: number | string;
  payload?: { value?: string; index?: number };
};

type BarClickPayload = {
  payload?: { month?: number; year?: number };
};

export const CashFlowWidget: React.FC<WidgetProps> = ({ widget, dragHandleProps }) => {
  const theme = useTheme();
  const router = useRouter();
  const { isEditMode, removeWidget } = useDashboardStore();
  const { series, loading, error, refetch } = useCashFlowSeries();
  const { t } = useTranslation(personalFinanceTranslations);
  const definition = getWidgetDefinition(widget.type);
  const gradientSeed = React.useId().replace(/:/g, '');
  const locale = i18n.resolvedLanguage ?? i18n.language;
  const [hoveredIndex, setHoveredIndex] = React.useState<number | null>(null);

  const incomeLabel = t('financeTransactions.monthSummary.income');
  const expensesLabel = t('financeTransactions.monthSummary.expenses');
  const netLabel = t('dashboard.widgets.cashFlow.netSeries');
  const defaultTitle = definition?.defaultTitleKey ? t(definition.defaultTitleKey) : '';

  const monthLabelFormatter = React.useMemo(() => {
    const monthFormatter = new Intl.DateTimeFormat(locale, { month: 'short' });
    return (year: number, month: number) => {
      const date = new Date(year, month - 1, 1);
      const raw = monthFormatter.format(date).replace(/\.$/, '');
      const monthPart = raw ? raw.charAt(0).toLocaleUpperCase(locale) + raw.slice(1) : raw;
      return `${monthPart}/${year}`;
    };
  }, [locale]);

  const chartData = React.useMemo(
    () =>
      series.map((point) => ({
        incomeAbs: Math.abs(point.totalIncome),
        expensesAbs: Math.abs(point.totalExpenses),
        label: monthLabelFormatter(point.year, point.month),
        year: point.year,
        month: point.month,
        income: Math.abs(point.totalIncome),
        expenses: -Math.abs(point.totalExpenses),
        net:
          Math.abs(point.totalIncome) === 0 && Math.abs(point.totalExpenses) === 0
            ? null
            : Number.isFinite(point.net)
              ? point.net
              : null,
      })),
    [monthLabelFormatter, series]
  );

  const rowByLabel = React.useMemo(() => new Map(chartData.map((d) => [d.label, d])), [chartData]);

  const navigateToMonth = React.useCallback(
    (month: number, year: number) => {
      try {
        sessionStorage.setItem('financeTransactions.selectedYear', year.toString());
        sessionStorage.setItem('financeTransactions.selectedMonth', month.toString());
      } catch {
        // ignore
      }
      router.push('/personal-finance/finance-transactions');
    },
    [router]
  );

  const xAxisTick = React.useCallback(
    (props: XAxisTickProps) => {
      const xRaw = props.x ?? 0;
      const yRaw = props.y ?? 0;
      const x = typeof xRaw === 'number' ? xRaw : Number(xRaw);
      const y = typeof yRaw === 'number' ? yRaw : Number(yRaw);
      const label = props.payload?.value ?? '';
      const idx = typeof props.payload?.index === 'number' ? props.payload.index : null;
      const row = idx != null ? chartData[idx] : rowByLabel.get(label);
      const isClickable = Boolean(row);
      const handleClick = () => {
        if (!row) return;
        navigateToMonth(row.month, row.year);
      };

      return (
        <text
          x={x}
          y={y}
          dy={16}
          textAnchor="middle"
          fill={theme.palette.text.secondary}
          fontSize={11}
          style={{ cursor: isClickable ? 'pointer' : 'default' }}
          onClick={isClickable ? handleClick : undefined}
        >
          {label}
        </text>
      );
    },
    [chartData, navigateToMonth, rowByLabel, theme.palette.text.secondary]
  );

  const yDomain = React.useMemo((): readonly [number, number] => {
    let minY = 0;
    let maxY = 0;
    for (const row of chartData) {
      if (typeof row.income === 'number' && Number.isFinite(row.income)) {
        maxY = Math.max(maxY, row.income);
      }
      if (typeof row.expenses === 'number' && Number.isFinite(row.expenses)) {
        minY = Math.min(minY, row.expenses);
      }
    }

    const span = maxY - minY;
    const baseSpan = span > 0 ? span : Math.max(1, Math.abs(maxY) || Math.abs(minY) || 1);
    const pad = baseSpan * 0.1;

    return [minY - pad, maxY + pad];
  }, [chartData]);

  const currencyTick = React.useCallback(
    (value: number) =>
      new Intl.NumberFormat('pt-BR', {
        style: 'currency',
        currency: 'BRL',
        maximumFractionDigits: 0,
      }).format(value ?? 0),
    []
  );

  const yAxisTick = React.useCallback(
    (props: AxisTickProps) => {
      const xRaw = props.x ?? 0;
      const yRaw = props.y ?? 0;
      const x = typeof xRaw === 'number' ? xRaw : Number(xRaw);
      const y = typeof yRaw === 'number' ? yRaw : Number(yRaw);
      const vRaw = props.payload?.value ?? 0;
      const value = typeof vRaw === 'number' ? vRaw : Number(vRaw);
      const safeValue = Number.isFinite(value) ? value : 0;
      const fill = safeValue < 0 ? theme.palette.error.main : theme.palette.text.secondary;
      return (
        <text x={x} y={y} dy={4} textAnchor="start" fill={fill} fontSize={11}>
          {currencyTick(safeValue)}
        </text>
      );
    },
    [currencyTick, theme.palette.error.main, theme.palette.text.secondary]
  );

  const tooltipFormatter = React.useCallback((value: number) => {
    return new Intl.NumberFormat('pt-BR', {
      style: 'currency',
      currency: 'BRL',
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(value ?? 0);
  }, []);

  const tooltipContent = React.useCallback(
    ({ active, payload, label }: TooltipContentProps) => {
      if (!active || !payload || payload.length === 0) return null;

      const items = payload
        .filter((p) => p && typeof p.dataKey === 'string')
        .slice()
        .sort((a, b) => {
          const aKey = String(a.dataKey);
          const bKey = String(b.dataKey);
          const order = (k: string) => (k === 'income' ? 0 : k === 'expenses' ? 1 : k === 'net' ? 2 : 3);
          return order(aKey) - order(bKey);
        });

      return (
        <Box
          sx={{
            bgcolor: 'background.paper',
            border: '1px solid',
            borderColor: 'divider',
            borderRadius: 1.5,
            px: 1.25,
            py: 1,
            minWidth: 160,
          }}
        >
          <Typography variant="caption" sx={{ color: 'text.secondary' }}>
            {String(label ?? '')}
          </Typography>
          <Box sx={{ mt: 0.75, display: 'flex', flexDirection: 'column', gap: 0.5 }}>
            {items.map((item) => {
              const key = String(item.dataKey);
              const raw = typeof item.value === 'number' ? item.value : Number(item.value ?? 0);
              const value = Number.isFinite(raw) ? raw : 0;
              const lineColor =
                key === 'expenses'
                  ? theme.palette.error.main
                  : key === 'income'
                    ? theme.palette.success.main
                    : value < 0
                      ? theme.palette.error.main
                      : theme.palette.primary.main;
              return (
                <Box
                  key={key}
                  sx={{
                    display: 'flex',
                    alignItems: 'baseline',
                    justifyContent: 'space-between',
                    gap: 2,
                  }}
                >
                  <Label sx={{ fontSize: '.85rem', color: 'text.secondary' }}>{String(item.name ?? '')}</Label>
                  <Typography variant="body2" sx={{ fontWeight: 600, color: lineColor }}>
                    {tooltipFormatter(value)}
                  </Typography>
                </Box>
              );
            })}
          </Box>
        </Box>
      );
    },
    [theme.palette.error.main, theme.palette.primary.main, theme.palette.success.main, tooltipFormatter]
  );

  const horizontalGridLine = React.useCallback(
    (props: GridLineTypeFunctionProps) => {
      const { offset, y1, y2, strokeDasharray, index: _index, key: _key, ...lineProps } = props;
      const dash = Array.isArray(strokeDasharray) ? strokeDasharray.join(' ') : strokeDasharray;
      const isTop = Math.round(y1) === Math.round(offset.top);
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
    [theme.palette.divider]
  );

  return (
    <WidgetWrapper
      title={widget.title || defaultTitle}
      icon={<BarChartIcon />}
      isEditMode={isEditMode}
      onRemove={() => removeWidget(widget.id)}
      onRefresh={() => void refetch()}
      dragHandleProps={dragHandleProps}
    >
      <Box sx={{ p: 2, height: '100%', display: 'flex', flexDirection: 'column', boxSizing: 'border-box' }}>
        {loading ? (
          <Skeleton variant="rectangular" sx={{ borderRadius: 1, height: '100%' }} />
        ) : error ? (
          <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', gap: 1 }}>
            <Typography color="error" variant="body2">
              {error.message}
            </Typography>
            <Button size="small" variant="outlined" onClick={() => void refetch()}>
              {t('financeTransactions.monthSummary.retry')}
            </Button>
          </Box>
        ) : chartData.length === 0 ? (
          <Typography variant="body2" color="text.secondary">
            {t('dashboard.widgets.cashFlow.noData')}
          </Typography>
        ) : (
          <Box
            sx={{
              width: '100%',
              flex: 1,
              minHeight: 0,
              '& .recharts-bar-rectangle': { cursor: 'pointer' },
              '& .recharts-cartesian-axis-tick text': { cursor: 'pointer' },
              '& .recharts-line, & .recharts-line path, & .recharts-line-dot': { pointerEvents: 'none' },
            }}
          >
            <ResponsiveContainer width="100%" height="100%">
              <RechartsComposedChart
                data={chartData}
                margin={{ top: 8, right: 8, left: 8, bottom: 8 }}
                barCategoryGap="12%"
                stackOffset="sign"
              >
                <defs>
                  <linearGradient id={`cashflow-income-${gradientSeed}`} x1="0" y1="1" x2="0" y2="0">
                    <stop offset="0%" stopColor={alpha(theme.palette.success.main, 0.2)} />
                    <stop offset="45%" stopColor={alpha(theme.palette.success.main, 0.35)} />
                    <stop offset="100%" stopColor={alpha(theme.palette.success.main, 0.5)} />
                  </linearGradient>
                  <linearGradient id={`cashflow-income-hover-${gradientSeed}`} x1="0" y1="1" x2="0" y2="0">
                    <stop offset="0%" stopColor={alpha(theme.palette.success.main, 0.06)} />
                    <stop offset="45%" stopColor={alpha(theme.palette.success.main, 0.22)} />
                    <stop offset="100%" stopColor={alpha(theme.palette.success.main, 0.35)} />
                  </linearGradient>
                  <linearGradient id={`cashflow-expenses-${gradientSeed}`} x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor={alpha(theme.palette.error.main, 0.2)} />
                    <stop offset="45%" stopColor={alpha(theme.palette.error.main, 0.35)} />
                    <stop offset="100%" stopColor={alpha(theme.palette.error.main, 0.45)} />
                  </linearGradient>
                  <linearGradient id={`cashflow-expenses-hover-${gradientSeed}`} x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor={alpha(theme.palette.error.main, 0.06)} />
                    <stop offset="45%" stopColor={alpha(theme.palette.error.main, 0.22)} />
                    <stop offset="100%" stopColor={alpha(theme.palette.error.main, 0.35)} />
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
                  padding={{ top: 12, bottom: 12 }}
                  axisLine={false}
                  tickLine={false}
                />
                <ReferenceLine y={0} stroke={theme.palette.divider} />
                <Tooltip
                  content={tooltipContent}
                  cursor={{
                    fill: alpha(theme.palette.action.hover, 0.12),
                    style: { cursor: 'pointer' },
                  }}
                />
                <Bar
                  dataKey="income"
                  name={incomeLabel}
                  fill={`url(#cashflow-income-${gradientSeed})`}
                  stackId="cashFlow"
                  radius={[4, 4, 0, 0]}
                  onClick={(payload: BarClickPayload, index) => {
                    const month = payload?.payload?.month;
                    const year = payload?.payload?.year;
                    if (typeof month === 'number' && typeof year === 'number') {
                      navigateToMonth(month, year);
                      return;
                    }
                    const row = chartData[index];
                    if (!row) return;
                    navigateToMonth(row.month, row.year);
                  }}
                >
                  {chartData.map((entry, idx) => (
                    <Cell
                      key={`income-${entry.label}`}
                      fill={
                        hoveredIndex === idx
                          ? `url(#cashflow-income-hover-${gradientSeed})`
                          : `url(#cashflow-income-${gradientSeed})`
                      }
                      onMouseEnter={() => setHoveredIndex(idx)}
                      onMouseLeave={() => setHoveredIndex(null)}
                    />
                  ))}
                </Bar>
                <Bar
                  dataKey="expenses"
                  name={expensesLabel}
                  fill={`url(#cashflow-expenses-${gradientSeed})`}
                  stackId="cashFlow"
                  radius={[4, 4, 0, 0]}
                  onClick={(payload: BarClickPayload, index) => {
                    const month = payload?.payload?.month;
                    const year = payload?.payload?.year;
                    if (typeof month === 'number' && typeof year === 'number') {
                      navigateToMonth(month, year);
                      return;
                    }
                    const row = chartData[index];
                    if (!row) return;
                    navigateToMonth(row.month, row.year);
                  }}
                >
                  {chartData.map((entry, idx) => (
                    <Cell
                      key={`expenses-${entry.label}`}
                      fill={
                        hoveredIndex === idx
                          ? `url(#cashflow-expenses-hover-${gradientSeed})`
                          : `url(#cashflow-expenses-${gradientSeed})`
                      }
                      onMouseEnter={() => setHoveredIndex(idx)}
                      onMouseLeave={() => setHoveredIndex(null)}
                    />
                  ))}
                </Bar>
                <Line
                  dataKey="net"
                  name={netLabel}
                  type="linear"
                  stroke={alpha(theme.palette.primary.main, 0.85)}
                  strokeWidth={2}
                  dot={false}
                  activeDot={{ r: 3, fill: theme.palette.primary.main, strokeWidth: 0 }}
                  connectNulls={false}
                  isAnimationActive={false}
                  style={{ pointerEvents: 'none' }}
                />
              </RechartsComposedChart>
            </ResponsiveContainer>
          </Box>
        )}
      </Box>
    </WidgetWrapper>
  );
};
