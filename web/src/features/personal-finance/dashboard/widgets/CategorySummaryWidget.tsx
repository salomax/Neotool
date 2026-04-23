'use client';

import React from 'react';
import PieChartIcon from '@mui/icons-material/PieChart';
import { Box, MenuItem, Select, Skeleton, Typography, useTheme } from '@mui/material';
import { Bar, BarChart, CartesianGrid, Cell, LabelList, ResponsiveContainer, XAxis, YAxis } from 'recharts';
import { getWidgetDefinition } from '../registry';
import { WidgetWrapper } from '../components/WidgetWrapper';
import { useDashboardStore } from '../store';
import { WidgetProps } from '../types';
import { CategoryBreakdownRow, useCategoryBreakdownForRange } from '../hooks/useDashboardAnalytics';
import { CategoryApplicableTo, CreditDebitType, TransactionType } from '@/lib/graphql/types/__generated__/graphql';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '@/app/personal-finance/i18n';
import { TransactionDirectionBadge } from '@/shared/components/ui/data-display';
import { CategoryIcon } from '@/app/personal-finance/finance-transactions/category/CategoryIcon';

type ChartRow = { name: string; amount: number; percentage: number; color: string; iconName: string | null };

function toChartRows(breakdown: CategoryBreakdownRow[]): ChartRow[] {
  return breakdown
    .filter((r) => r.totalAmount > 0)
    .map((r) => ({
      name: r.uncategorized ? '' : (r.category?.name ?? ''),
      amount: r.totalAmount,
      percentage: Number.isFinite(r.percentage) ? Math.max(0, Math.min(100, r.percentage)) : 0,
      color: r.category?.color ?? '',
      iconName: r.category?.icon ?? null,
    }));
}

function formatBrl(value: number) {
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value);
}

function isSupportedApplicableTo(value: unknown): value is CategoryApplicableTo {
  return value === CategoryApplicableTo.Expense || value === CategoryApplicableTo.Income;
}

interface CategoryBarChartProps {
  data: ChartRow[];
  loading?: boolean;
  emptyMessage?: string;
  fillFallbackColor: string;
}

function CategoryBarChart({
  data,
  loading,
  emptyMessage,
  fillFallbackColor,
}: CategoryBarChartProps) {
  const theme = useTheme();
  const rows = React.useMemo(
    () => [...data].sort((a, b) => b.percentage - a.percentage).slice(0, 10),
    [data],
  );
  const domainMax = React.useMemo(() => {
    const maxPct = rows.reduce((max, row) => Math.max(max, row.percentage), 0);
    const padded = Math.ceil(maxPct * 1.2);
    return Math.max(1, padded);
  }, [rows]);
  const height = Math.max(240, Math.min(360, rows.length * 24 + 36));
  const barLabel = React.useCallback(
    (props: {
      x?: number;
      y?: number;
      width?: number;
      height?: number;
      value?: unknown;
      payload?: ChartRow;
      index?: number;
    }) => {
      const x = props.x ?? 0;
      const y = props.y ?? 0;
      const width = props.width ?? 0;
      const height = props.height ?? 0;
      const raw = props.value;
      const n = typeof raw === 'number' ? raw : Number(raw);
      if (!Number.isFinite(n)) return null;
      const row = props.payload ?? (typeof props.index === 'number' ? rows[props.index] : undefined);
      const fill = row?.color || fillFallbackColor;
      const label = `${Math.round(n)}%`;
      const amountLabel = formatBrl(row?.amount ?? 0);
      const minWidthForInside = label.length * 7 + 18;
      const showOutside = width < minWidthForInside;
      const labelX = showOutside ? x + width + 8 : x + width - 8;
      const amountX = x + width + 8;
      return (
        <g pointerEvents="none">
          <text
            x={labelX}
            y={y + height / 2}
            textAnchor={showOutside ? 'start' : 'end'}
            dominantBaseline="middle"
            fill={showOutside ? theme.palette.text.secondary : theme.palette.getContrastText(fill)}
            fontWeight={700}
            fontSize={12}
          >
            {showOutside ? `${label} • ${amountLabel}` : label}
          </text>
          {!showOutside && (
            <text
              x={amountX}
              y={y + height / 2}
              textAnchor="start"
              dominantBaseline="middle"
              fill={theme.palette.text.secondary}
              fontWeight={700}
              fontSize={12}
            >
              {amountLabel}
            </text>
          )}
        </g>
      );
    },
    [fillFallbackColor, rows, theme],
  );
  const yAxisTick = React.useCallback(
    (props: { x?: number; y?: number; payload?: { value?: unknown }; index?: number }) => {
      const x = props.x ?? 0;
      const y = props.y ?? 0;
      const labelRaw = String(props.payload?.value ?? '');
      const label = labelRaw.length <= 18 ? labelRaw : `${labelRaw.slice(0, 17)}…`;
      const row = typeof props.index === 'number' ? rows[props.index] : undefined;
      const color = row?.color || fillFallbackColor;
      const iconSize = 14;
      const gap = 8;
      return (
        <g transform={`translate(${x},${y})`} pointerEvents="none">
          <text
            x={-(iconSize + gap)}
            y={0}
            dy={4}
            textAnchor="end"
            fill={theme.palette.text.secondary}
            fontSize={12}
          >
            {label}
          </text>
          <foreignObject
            x={-iconSize}
            y={-iconSize / 2}
            width={iconSize}
            height={iconSize}
            overflow="visible"
            pointerEvents="none"
          >
            <div
              style={{
                width: `${iconSize}px`,
                height: `${iconSize}px`,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                pointerEvents: 'none',
              }}
            >
              <CategoryIcon iconName={row?.iconName} color={color} size={iconSize} />
            </div>
          </foreignObject>
        </g>
      );
    },
    [fillFallbackColor, rows, theme.palette.text.secondary],
  );

  if (loading) {
    return (
      <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 1 }}>
        <Skeleton variant="rectangular" width="100%" height={height} />
      </Box>
    );
  }

  if (rows.length === 0) {
    return (
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1, alignItems: 'center' }}>
        <Typography variant="body2" color="text.secondary" textAlign="center">
          {emptyMessage}
        </Typography>
      </Box>
    );
  }

  return (
    <Box sx={{ width: '100%', display: 'flex', flexDirection: 'column', height: '100%' }}>
      <Box
        sx={{
          width: '100%',
          flexGrow: 1,
          minHeight: height,
          position: 'relative',
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'flex-start',
        }}
      >
        <Box sx={{ width: '100%', flexGrow: 1, height: '100%' }}>
          <ResponsiveContainer width="100%" height="100%">
            <BarChart
              layout="vertical"
              data={rows}
              margin={{ top: 10, right: 140, left: 0, bottom: 0 }}
              barCategoryGap={12}
              barGap={6}
            >
              <CartesianGrid strokeDasharray="3 3" stroke={theme.palette.divider} horizontal={false} />
              <XAxis type="number" domain={[0, domainMax]} hide />
              <YAxis
                type="category"
                dataKey="name"
                width={150}
                tick={yAxisTick as any}
                axisLine={false}
                tickLine={false}
              />
              <Bar
                dataKey="percentage"
                radius={[0, 6, 6, 0]}
                barSize={28}
                maxBarSize={32}
                isAnimationActive={false}
                animationDuration={0}
              >
                {rows.map((row, index) => (
                  <Cell key={`${row.name}-${index}`} fill={row.color || fillFallbackColor} />
                ))}
                <LabelList dataKey="percentage" content={barLabel as any} />
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </Box>
      </Box>
    </Box>
  );
}

export const CategorySummaryWidget: React.FC<WidgetProps> = ({ widget, dragHandleProps }) => {
  const theme = useTheme();
  const { isEditMode, removeWidget, updateWidgetConfig } = useDashboardStore();
  const { t } = useTranslation(personalFinanceTranslations);
  const configuredApplicableTo = widget.config?.applicableTo;
  const selectedApplicableTo = isSupportedApplicableTo(configuredApplicableTo)
    ? configuredApplicableTo
    : CategoryApplicableTo.Expense;
  const {
    breakdown: expenseBreakdown,
    loading: expenseLoading,
    error: expenseError,
    refetch: refetchExpenses,
  } = useCategoryBreakdownForRange(CategoryApplicableTo.Expense, {
    enabled: selectedApplicableTo === CategoryApplicableTo.Expense,
  });
  const {
    breakdown: incomeBreakdown,
    loading: incomeLoading,
    error: incomeError,
    refetch: refetchIncome,
  } = useCategoryBreakdownForRange(CategoryApplicableTo.Income, {
    enabled: selectedApplicableTo === CategoryApplicableTo.Income,
  });
  const definition = getWidgetDefinition(widget.type);

  const loading = selectedApplicableTo === CategoryApplicableTo.Expense ? expenseLoading : incomeLoading;
  const error = selectedApplicableTo === CategoryApplicableTo.Expense ? expenseError : incomeError;

  const expenseRows = React.useMemo(() => toChartRows(expenseBreakdown), [expenseBreakdown]);
  const incomeRows = React.useMemo(() => toChartRows(incomeBreakdown), [incomeBreakdown]);

  const uncategorizedLabel = t('financeTransactions.monthSummary.uncategorized');
  const expenseData = expenseRows.map((s) => ({
    ...s,
    name: s.name || uncategorizedLabel,
  }));
  const incomeData = incomeRows.map((s) => ({
    ...s,
    name: s.name || uncategorizedLabel,
  }));

  const chartData = selectedApplicableTo === CategoryApplicableTo.Expense ? expenseData : incomeData;
  const emptyMessage =
    selectedApplicableTo === CategoryApplicableTo.Expense
      ? t('financeTransactions.monthSummary.noCategorizedExpenses')
      : t('financeTransactions.monthSummary.noCategorizedIncome');
  const fillFallbackColor = theme.palette.action.disabled;

  const expenseLabel = t('financeTransactions.monthSummary.expenses');
  const incomeLabel = t('financeTransactions.monthSummary.income');
  const defaultTitle = definition?.defaultTitleKey ? t(definition.defaultTitleKey) : '';

  const selectedLabel = selectedApplicableTo === CategoryApplicableTo.Expense ? expenseLabel : incomeLabel;
  const selectedCreditDebitType =
    selectedApplicableTo === CategoryApplicableTo.Expense ? CreditDebitType.Debit : CreditDebitType.Credit;

  const typeSelector = (
    <Select<CategoryApplicableTo>
      size="small"
      value={selectedApplicableTo}
      onChange={(e) => updateWidgetConfig(widget.id, { applicableTo: e.target.value as CategoryApplicableTo })}
      inputProps={{ 'aria-label': t('financeTransactions.monthSummary.breakdownType') }}
      renderValue={() => (
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <TransactionDirectionBadge
            creditDebitType={selectedCreditDebitType}
            transactionType={TransactionType.Card}
            size={20}
          />
          <Box component="span" sx={{ fontWeight: 600 }}>
            {selectedLabel}
          </Box>
        </Box>
      )}
      sx={{
        minWidth: 170,
        '& .MuiSelect-select': { py: 0.75 },
        '& .MuiOutlinedInput-notchedOutline': { border: 0 },
        '&:hover .MuiOutlinedInput-notchedOutline': { border: 0 },
        '&.Mui-focused .MuiOutlinedInput-notchedOutline': { border: 0 },
      }}
    >
      <MenuItem value={CategoryApplicableTo.Expense}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <TransactionDirectionBadge
            creditDebitType={CreditDebitType.Debit}
            transactionType={TransactionType.Card}
            size={20}
          />
          {expenseLabel}
        </Box>
      </MenuItem>
      <MenuItem value={CategoryApplicableTo.Income}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <TransactionDirectionBadge
            creditDebitType={CreditDebitType.Credit}
            transactionType={TransactionType.Card}
            size={20}
          />
          {incomeLabel}
        </Box>
      </MenuItem>
    </Select>
  );

  const handleRefresh = () => {
    if (selectedApplicableTo === CategoryApplicableTo.Expense) {
      void refetchExpenses();
      return;
    }
    void refetchIncome();
  };

  return (
    <WidgetWrapper
      title={widget.title || defaultTitle}
      icon={<PieChartIcon />}
      isEditMode={isEditMode}
      onRemove={() => removeWidget(widget.id)}
      onRefresh={handleRefresh}
      dragHandleProps={dragHandleProps}
      headerRight={typeSelector}
    >
      <Box sx={{ px: 2, pt: 1, pb: 2, height: '100%', display: 'flex', flexDirection: 'column' }}>
        {error ? (
          <Typography color="error" variant="body2">
            {error.message}
          </Typography>
        ) : (
          <CategoryBarChart
            data={chartData}
            loading={loading}
            emptyMessage={emptyMessage}
            fillFallbackColor={fillFallbackColor}
          />
        )}
      </Box>
    </WidgetWrapper>
  );
};
