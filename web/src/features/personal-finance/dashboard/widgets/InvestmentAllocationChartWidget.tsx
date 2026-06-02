'use client';

import React from 'react';
import DonutLargeIcon from '@mui/icons-material/DonutLarge';
import {
  Box,
  Skeleton,
  Stack,
  Typography,
  useTheme,
} from '@mui/material';
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip as RechartsTooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { WidgetWrapper } from '../components/WidgetWrapper';
import { getWidgetDefinition } from '../registry';
import { useDashboardStore } from '../store';
import { WidgetProps } from '../types';
import {
  type PortfolioAllocationGroupKey,
  type PortfolioAllocationSeriesRow,
  usePortfolioAllocationSeries,
} from '../hooks/useDashboardAnalytics';
import { useDashboardDateRangeParams } from '../hooks/DashboardDateRangeParamsContext';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '@/app/personal-finance/i18n';
import { getCurrentLocale } from '@/app/personal-finance/investments/components/table/investmentTableHelpers';
import { extractErrorMessage } from '@/shared/utils/error';
import { Label } from '@/shared/components/ui/primitives/Label';

type PortfolioAllocationChartRow = PortfolioAllocationSeriesRow & {
  label: string;
  year: number;
  month: number;
};

type AllocationLegendRow = {
  groupKey: PortfolioAllocationGroupKey;
  label: string;
  amount: number;
  percent: number;
  color: string;
};

const ALLOCATION_GROUP_ORDER: readonly PortfolioAllocationGroupKey[] = [
  'cash',
  'fixedIncome',
  'mutualFunds',
  'pension',
  'structured',
  'variableIncome',
  'others',
];
const AREA_FILL_ALPHA = 0.8;
const AREA_GRADIENT_TOP_ALPHA = 0.8;
const AREA_GRADIENT_BOTTOM_ALPHA = 0.3;

function clamp01(value: number): number {
  if (!Number.isFinite(value)) return 0;
  return Math.min(1, Math.max(0, value));
}

function interpolateGradientAlpha(rate: number): number {
  const safeRate = clamp01(rate);
  return AREA_GRADIENT_BOTTOM_ALPHA + safeRate * (AREA_GRADIENT_TOP_ALPHA - AREA_GRADIENT_BOTTOM_ALPHA);
}

function parseMonthYear(monthKey: string): { year: number; month: number } | null {
  const [yearRaw, monthRaw] = monthKey.split('-');
  const year = Number(yearRaw);
  const month = Number(monthRaw);
  if (!Number.isFinite(year) || !Number.isFinite(month) || month < 1 || month > 12) {
    return null;
  }
  return { year, month };
}

function formatMonthLabel(monthKey: string, locale: string): string {
  const parsed = parseMonthYear(monthKey);
  if (!parsed) return monthKey;
  const { year, month } = parsed;
  const date = new Date(year, month - 1, 1);
  const formatter = new Intl.DateTimeFormat(locale, { month: 'short' });
  const raw = formatter.format(date).replace(/\.$/, '');
  const monthPart = raw ? raw.charAt(0).toLocaleUpperCase(locale) + raw.slice(1) : raw;
  return `${monthPart}/${year}`;
}

function formatPercent(locale: string, value: number): string {
  return new Intl.NumberFormat(locale, {
    style: 'percent',
    minimumFractionDigits: 1,
    maximumFractionDigits: 1,
  }).format(value);
}

function formatCurrency(locale: string, currencyCode: string, value: number): string {
  return new Intl.NumberFormat(locale, {
    style: 'currency',
    currency: currencyCode,
    maximumFractionDigits: 2,
  }).format(value);
}

function isRowWithinRange(
  row: Pick<PortfolioAllocationChartRow, 'year' | 'month'>,
  range: { startYear: number; startMonth: number; endYear: number; endMonth: number },
): boolean {
  const rowKey = row.year * 100 + row.month;
  const startKey = range.startYear * 100 + range.startMonth;
  const endKey = range.endYear * 100 + range.endMonth;
  return rowKey >= startKey && rowKey <= endKey;
}

export function buildPortfolioAllocationLegendRows(params: {
  row: PortfolioAllocationSeriesRow | null | undefined;
  resolveLabel: (groupKey: PortfolioAllocationGroupKey) => string;
  colors: Record<PortfolioAllocationGroupKey, string>;
}): AllocationLegendRow[] {
  const { row, resolveLabel, colors } = params;
  if (!row || row.totalGrossValue <= 0) return [];

  return ALLOCATION_GROUP_ORDER.map((groupKey) => {
    const amount = row[groupKey];
    return {
      groupKey,
      label: resolveLabel(groupKey),
      amount,
      percent: row.totalGrossValue > 0 ? amount / row.totalGrossValue : 0,
      color: colors[groupKey],
    };
  })
    .filter((entry) => entry.amount > 0)
    .sort((a, b) => b.amount - a.amount);
}

function AllocationChartTooltip(props: {
  active?: boolean;
  payload?: Array<{ payload?: PortfolioAllocationChartRow }>;
  locale: string;
  currencyCode: string;
  resolveLabel: (groupKey: PortfolioAllocationGroupKey) => string;
  colors: Record<PortfolioAllocationGroupKey, string>;
}) {
  const { active, payload, locale, currencyCode, resolveLabel, colors } = props;
  const row = payload?.[0]?.payload;
  if (!active || !row || row.totalGrossValue <= 0) return null;

  const entries = buildPortfolioAllocationLegendRows({
    row,
    resolveLabel,
    colors,
  });

  if (entries.length === 0) return null;

  return (
    <Box
      sx={{
        border: (theme) => `1px solid ${theme.palette.divider}`,
        bgcolor: 'background.paper',
        borderRadius: 1.5,
        px: 1.25,
        py: 1,
        boxShadow: 2,
        minWidth: 220,
      }}
    >
      <Typography variant="caption" color="text.secondary">
        {row.label}
      </Typography>
      <Stack spacing={0.5} sx={{ mt: 0.75 }}>
        {entries.map((entry) => (
          <Box
            key={entry.groupKey}
            sx={{
              display: 'flex',
              alignItems: 'baseline',
              justifyContent: 'space-between',
              gap: 2,
            }}
          >
            <Label sx={{ fontSize: '.85rem', color: 'text.secondary' }} noWrap>
              <Box
                component="span"
                sx={{
                  display: 'inline-block',
                  width: 8,
                  height: 8,
                  borderRadius: '50%',
                  bgcolor: entry.color,
                  mr: 0.75,
                  verticalAlign: 'middle',
                }}
              />
              <Box component="span" sx={{ verticalAlign: 'middle' }}>
                {entry.label}
              </Box>
            </Label>
            <Typography variant="body2" sx={{ fontWeight: 600, color: entry.color, flexShrink: 0 }}>
              {formatCurrency(locale, currencyCode, entry.amount)} • {formatPercent(locale, entry.percent)}
            </Typography>
          </Box>
        ))}
      </Stack>
    </Box>
  );
}

export const InvestmentAllocationChartWidget: React.FC<WidgetProps> = ({
  widget,
  dragHandleProps,
}) => {
  const theme = useTheme();
  const { t } = useTranslation(personalFinanceTranslations);
  const { isEditMode, removeWidget } = useDashboardStore();
  const definition = getWidgetDefinition(widget.type);
  const locale = React.useMemo(() => getCurrentLocale(), []);
  const range = useDashboardDateRangeParams();

  const { series, currencyCode, loading, error, refetch } = usePortfolioAllocationSeries();

  const colors = theme.custom.portfolioAllocationColors;
  const gradientIdPrefix = React.useMemo(
    () => `allocation-gradient-${String(widget.id).replace(/[^a-zA-Z0-9_-]/g, '-')}`,
    [widget.id],
  );
  const resolveGroupLabel = React.useCallback(
    (groupKey: PortfolioAllocationGroupKey) =>
      t(`investments.table.groups.${groupKey}`, { defaultValue: groupKey }),
    [t],
  );

  const chartData = React.useMemo<PortfolioAllocationChartRow[]>(
    () =>
      series.map((row) => {
        const parsed = parseMonthYear(row.monthKey);
        return {
          ...row,
          label: formatMonthLabel(row.monthKey, locale),
          year: parsed?.year ?? 0,
          month: parsed?.month ?? 1,
        };
      }),
    [series, locale],
  );
  const filteredChartData = React.useMemo<PortfolioAllocationChartRow[]>(
    () => (range ? chartData.filter((row) => isRowWithinRange(row, range)) : chartData),
    [chartData, range],
  );
  const latestRow = filteredChartData.length > 0 ? filteredChartData[filteredChartData.length - 1] : null;
  const groupAllocationRate = React.useMemo<Record<PortfolioAllocationGroupKey, number>>(() => {
    if (!latestRow || latestRow.totalGrossValue <= 0) {
      return {
        cash: 0,
        fixedIncome: 0,
        mutualFunds: 0,
        pension: 0,
        structured: 0,
        variableIncome: 0,
        others: 0,
      };
    }

    const total = latestRow.totalGrossValue;
    return {
      cash: clamp01(latestRow.cash / total),
      fixedIncome: clamp01(latestRow.fixedIncome / total),
      mutualFunds: clamp01(latestRow.mutualFunds / total),
      pension: clamp01(latestRow.pension / total),
      structured: clamp01(latestRow.structured / total),
      variableIncome: clamp01(latestRow.variableIncome / total),
      others: clamp01(latestRow.others / total),
    };
  }, [latestRow]);

  const hasData = filteredChartData.length > 0;
  const xAxisTick = React.useCallback(
    (props: { x?: number | string; y?: number | string; payload?: { value?: string } }) => {
      const x = Number(props.x ?? 0);
      const y = Number(props.y ?? 0);
      return (
        <text x={x} y={y} dy={16} textAnchor="middle" fill={theme.palette.text.secondary} fontSize={11}>
          {props.payload?.value ?? ''}
        </text>
      );
    },
    [theme.palette.text.secondary],
  );

  const yAxisTick = React.useCallback(
    (props: { x?: number | string; y?: number | string; payload?: { value?: number | string } }) => {
      const x = Number(props.x ?? 0);
      const y = Number(props.y ?? 0);
      const rawValue = props.payload?.value ?? 0;
      const value = typeof rawValue === 'number' ? rawValue : Number(rawValue);
      const safeValue = Number.isFinite(value) ? value : 0;
      return (
        <text x={x} y={y} dy={4} textAnchor="start" fill={theme.palette.text.secondary} fontSize={11}>
          {formatPercent(locale, safeValue)}
        </text>
      );
    },
    [locale, theme.palette.text.secondary],
  );

  return (
    <WidgetWrapper
      title={widget.title || (definition?.defaultTitleKey ? t(definition.defaultTitleKey) : '')}
      icon={<DonutLargeIcon />}
      isEditMode={isEditMode}
      onRemove={() => removeWidget(widget.id)}
      onRefresh={() => void refetch()}
      dragHandleProps={dragHandleProps}
    >
      <Box
        sx={{
          px: 2,
          pt: 1.25,
          pb: 2,
          height: '100%',
          display: 'flex',
          flexDirection: 'column',
          boxSizing: 'border-box',
        }}
      >
        {error && !hasData ? (
          <Typography color="error" variant="body2">
            {extractErrorMessage(error) || t('dashboard.widgets.investmentAllocation.error')}
          </Typography>
        ) : loading && !hasData ? (
          <Stack spacing={1.5}>
            <Skeleton variant="rounded" height={240} />
            <Skeleton variant="text" width="65%" />
            <Skeleton variant="text" width="78%" />
            <Skeleton variant="text" width="59%" />
          </Stack>
        ) : !hasData ? (
          <Box
            sx={{
              height: '100%',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <Typography variant="body2" color="text.secondary" textAlign="center">
              {t('dashboard.widgets.investmentAllocation.noData')}
            </Typography>
          </Box>
        ) : (
          <Box sx={{ width: '100%', flex: 1, minHeight: 240, display: 'flex', flexDirection: 'column' }}>
            <Box sx={{ width: '100%', flex: 1, minHeight: 0 }}>
            <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={filteredChartData} stackOffset="expand" margin={{ top: 0, right: 8, bottom: 0, left: 0 }}>
                  <defs>
                    {ALLOCATION_GROUP_ORDER.map((groupKey) => (
                      <linearGradient
                        key={groupKey}
                        id={`${gradientIdPrefix}-${groupKey}`}
                        x1="0"
                        y1="0"
                        x2="0"
                        y2="1"
                      >
                        <stop
                          offset="0%"
                          stopColor={colors[groupKey]}
                          stopOpacity={interpolateGradientAlpha(groupAllocationRate[groupKey])}
                        />
                        <stop
                          offset="100%"
                          stopColor={colors[groupKey]}
                          stopOpacity={AREA_GRADIENT_BOTTOM_ALPHA}
                        />
                      </linearGradient>
                    ))}
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" stroke={theme.palette.divider} vertical={false} />
                  <XAxis
                    dataKey="label"
                    tick={xAxisTick}
                    axisLine={{ stroke: theme.palette.divider }}
                    tickLine={{ stroke: theme.palette.divider }}
                    minTickGap={24}
                    interval="preserveStartEnd"
                    tickMargin={8}
                  />
                  <YAxis
                    mirror
                    domain={[0, 1]}
                    tick={yAxisTick}
                    axisLine={false}
                    tickLine={false}
                    padding={{ top: 0, bottom: 0 }}
                  />
                  <RechartsTooltip
                    content={
                      <AllocationChartTooltip
                        locale={locale}
                        currencyCode={currencyCode}
                        resolveLabel={resolveGroupLabel}
                        colors={colors}
                      />
                    }
                  />

                  {ALLOCATION_GROUP_ORDER.map((groupKey) => (
                    <Area
                      key={groupKey}
                      type="monotone"
                      dataKey={groupKey}
                      stackId="allocation"
                      stroke="none"
                      fill={`url(#${gradientIdPrefix}-${groupKey})`}
                      fillOpacity={AREA_FILL_ALPHA}
                      dot={false}
                      activeDot={false}
                      isAnimationActive={false}
                    />
                  ))}
                </AreaChart>
            </ResponsiveContainer>
            </Box>
          </Box>
        )}
      </Box>
    </WidgetWrapper>
  );
};
