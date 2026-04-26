'use client';

import React from 'react';
import ViewModuleIcon from '@mui/icons-material/ViewModule';
import { Box, Skeleton, Stack, Typography, alpha, useTheme } from '@mui/material';
import { ResponsiveContainer, Tooltip as RechartsTooltip, Treemap } from 'recharts';
import { WidgetWrapper } from '../components/WidgetWrapper';
import { getWidgetDefinition } from '../registry';
import { useDashboardStore } from '../store';
import { WidgetProps } from '../types';
import {
  type InvestmentTreemapRow,
  type PortfolioAllocationGroupKey,
  useInvestmentTreemapSummary,
} from '../hooks/useDashboardAnalytics';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '@/app/personal-finance/i18n';
import { getCurrentLocale } from '@/app/personal-finance/investments/components/table/investmentTableHelpers';
import { extractErrorMessage } from '@/shared/utils/error';
import { Label } from '@/shared/components/ui/primitives/Label';

type InvestmentTreemapChartNode = InvestmentTreemapRow & {
  name: string;
  value: number;
  fill: string;
  stroke: string;
  [key: string]: string | number | boolean;
};

const TREEMAP_FILL_ALPHA = 0.7;
const TREEMAP_STROKE_ALPHA = 0;

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

function isPortfolioAllocationGroupKey(value: string): value is PortfolioAllocationGroupKey {
  return (
    value === 'cash' ||
    value === 'variableIncome' ||
    value === 'fixedIncome' ||
    value === 'mutualFunds' ||
    value === 'pension' ||
    value === 'structured' ||
    value === 'others'
  );
}

function resolveGroupColor(
  groupKey: string,
  colors: Record<PortfolioAllocationGroupKey, string>,
): string {
  return isPortfolioAllocationGroupKey(groupKey) ? colors[groupKey] : colors.others;
}

function truncateLabel(value: string, maxChars: number): string {
  if (value.length <= maxChars) return value;
  if (maxChars <= 1) return value.slice(0, maxChars);
  return `${value.slice(0, Math.max(1, maxChars - 1))}…`;
}

export function buildInvestmentTreemapChartNodes(params: {
  rows: readonly InvestmentTreemapRow[];
  resolveLabel: (groupKey: string, fallbackLabel: string) => string;
  resolveColor: (groupKey: string) => string;
}): InvestmentTreemapChartNode[] {
  const { rows, resolveLabel, resolveColor } = params;
  return rows
    .filter((row) => row.totalGrossValue > 0)
    .map((row) => {
      const baseColor = resolveColor(row.groupKey);
      return {
        ...row,
        name: resolveLabel(row.groupKey, row.label),
        value: row.totalGrossValue,
        fill: alpha(baseColor, TREEMAP_FILL_ALPHA),
        stroke: alpha(baseColor, TREEMAP_STROKE_ALPHA),
      };
    });
}

function InvestmentTreemapTooltip(props: {
  active?: boolean;
  payload?: Array<{ payload?: InvestmentTreemapChartNode }>;
  locale: string;
  currencyCode: string;
  t: (key: string, options?: Record<string, unknown>) => string;
}) {
  const { active, payload, locale, currencyCode, t } = props;
  const node = payload?.[0]?.payload;

  if (!active || !node || node.value <= 0) return null;

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
        {node.name}
      </Typography>
      <Stack spacing={0.5} sx={{ mt: 0.75 }}>
        <Box sx={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 2 }}>
          <Label sx={{ fontSize: '.85rem', color: 'text.secondary' }} noWrap>
            {t('dashboard.widgets.investmentTreemap.allocation')}
          </Label>
          <Typography variant="body2" sx={{ fontWeight: 700, color: 'text.primary' }}>
            {formatPercent(locale, node.allocationPercent)}
          </Typography>
        </Box>
        <Box sx={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 2 }}>
          <Label sx={{ fontSize: '.85rem', color: 'text.secondary' }} noWrap>
            {t('dashboard.widgets.investmentTreemap.amount')}
          </Label>
          <Typography variant="body2" sx={{ fontWeight: 700, color: 'text.primary' }}>
            {formatCurrency(locale, currencyCode, node.totalGrossValue)}
          </Typography>
        </Box>
        <Box sx={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 2 }}>
          <Label sx={{ fontSize: '.85rem', color: 'text.secondary' }} noWrap>
            {t('dashboard.widgets.investmentTreemap.assets')}
          </Label>
          <Typography variant="body2" sx={{ fontWeight: 700, color: 'text.primary' }}>
            {t('dashboard.widgets.investmentTreemap.legendCount', { count: node.count })}
          </Typography>
        </Box>
      </Stack>
    </Box>
  );
}

export const InvestmentTreemapWidget: React.FC<WidgetProps> = ({ widget, dragHandleProps }) => {
  const theme = useTheme();
  const { t } = useTranslation(personalFinanceTranslations);
  const { isEditMode, removeWidget } = useDashboardStore();
  const definition = getWidgetDefinition(widget.type);
  const locale = React.useMemo(() => getCurrentLocale(), []);

  const { rows, currencyCode, loading, error, refetch } = useInvestmentTreemapSummary();

  const allocationColors = theme.custom.portfolioAllocationColors;
  const resolveGroupLabel = React.useCallback(
    (groupKey: string, fallbackLabel: string) =>
      t(`investments.table.groups.${groupKey}`, { defaultValue: fallbackLabel }),
    [t],
  );
  const chartData = React.useMemo(
    () =>
      buildInvestmentTreemapChartNodes({
        rows,
        resolveLabel: resolveGroupLabel,
        resolveColor: (groupKey) => resolveGroupColor(groupKey, allocationColors),
      }),
    [allocationColors, resolveGroupLabel, rows],
  );

  const hasData = chartData.length > 0;

  const treemapContent = React.useCallback(
    (node: any): React.ReactElement => {
      const x = Number(node?.x ?? 0);
      const y = Number(node?.y ?? 0);
      const width = Number(node?.width ?? 0);
      const height = Number(node?.height ?? 0);
      if (width <= 0 || height <= 0) return <g />;

      const label = typeof node?.name === 'string' ? node.name : '';
      const allocationPercent =
        typeof node?.allocationPercent === 'number' && Number.isFinite(node.allocationPercent)
          ? node.allocationPercent
          : 0;
      const fill = typeof node?.fill === 'string' ? node.fill : alpha(theme.palette.primary.main, TREEMAP_FILL_ALPHA);
      const stroke =
        typeof node?.stroke === 'string'
          ? node.stroke
          : alpha(theme.palette.primary.main, TREEMAP_STROKE_ALPHA);
      if (!label) return <g />;

      const textColor = theme.palette.getContrastText(fill);
      const showLabel = width >= 88 && height >= 42;
      const showPercent = width >= 108 && height >= 58;
      const maxChars = Math.max(5, Math.floor((width - 10) / 7));

      return (
        <g>
          <rect x={x} y={y} width={width} height={height} fill={fill} stroke={stroke} strokeWidth={1} />
          {showLabel ? (
            <text x={x + 6} y={y + 16} fill={textColor} fontSize={12} fontWeight={200}>
              {truncateLabel(label, maxChars)}
            </text>
          ) : null}
          {showPercent ? (
            <text x={x + 6} y={y + 31} fill={textColor} fontSize={10}>
              {formatPercent(locale, allocationPercent)}
            </text>
          ) : null}
        </g>
      );
    },
    [locale, theme.palette],
  );

  return (
    <WidgetWrapper
      title={widget.title || (definition?.defaultTitleKey ? t(definition.defaultTitleKey) : '')}
      icon={<ViewModuleIcon />}
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
            {extractErrorMessage(error) || t('dashboard.widgets.investmentTreemap.error')}
          </Typography>
        ) : loading && !hasData ? (
          <Stack spacing={1.5}>
            <Skeleton variant="rounded" height={240} />
            <Skeleton variant="text" width="65%" />
            <Skeleton variant="text" width="78%" />
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
              {t('dashboard.widgets.investmentTreemap.noData')}
            </Typography>
          </Box>
        ) : (
          <Box sx={{ width: '100%', flex: 1, minHeight: 240, display: 'flex', flexDirection: 'column' }}>
            <Box sx={{ width: '100%', flex: 1, minHeight: 0 }}>
              <ResponsiveContainer width="100%" height="100%">
                <Treemap
                  data={chartData}
                  dataKey="value"
                  stroke={theme.palette.divider}
                  isAnimationActive={false}
                  content={treemapContent}
                >
                  <RechartsTooltip
                    content={
                      <InvestmentTreemapTooltip
                        locale={locale}
                        currencyCode={currencyCode}
                        t={t}
                      />
                    }
                  />
                </Treemap>
              </ResponsiveContainer>
            </Box>
          </Box>
        )}
      </Box>
    </WidgetWrapper>
  );
};
