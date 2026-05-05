'use client';

import React from 'react';
import ArrowBackIosNewIcon from '@mui/icons-material/ArrowBackIosNew';
import ViewModuleIcon from '@mui/icons-material/ViewModule';
import { Box, IconButton, Skeleton, Stack, Typography, alpha, lighten, useTheme } from '@mui/material';
import { ResponsiveContainer, Tooltip as RechartsTooltip, Treemap } from 'recharts';
import { WidgetWrapper } from '../components/WidgetWrapper';
import { getWidgetDefinition } from '../registry';
import { useDashboardStore } from '../store';
import { WidgetProps } from '../types';
import {
  type HoldingSnapshot,
  type InvestmentTreemapRow,
  type PortfolioAllocationGroupKey,
  usePortfolioHoldingsSnapshot,
  useInvestmentTreemapSummary,
} from '../hooks/useDashboardAnalytics';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '@/app/personal-finance/i18n';
import {
  getAssetGroupKey,
  getCurrentLocale,
} from '@/app/personal-finance/investments/components/table/investmentTableHelpers';
import { extractErrorMessage } from '@/shared/utils/error';
import { Label } from '@/shared/components/ui/primitives/Label';

type InvestmentTreemapChartNode = InvestmentTreemapRow & {
  name: string;
  value: number;
  fill: string;
  stroke: string;
  [key: string]: string | number | boolean;
};

export type HoldingTreemapChartNode = {
  holdingId: string;
  name: string;
  fullName: string;
  ticker: string;
  accountName: string;
  currencyCode: string;
  assetType: string;
  groupKey: string;
  value: number;
  groupValue: number;
  allocationPercent: number;
  fill: string;
  stroke: string;
  [key: string]: string | number | boolean;
};

const TREEMAP_FILL_ALPHA = 0.7;
const TREEMAP_STROKE_ALPHA = 0;
const MAX_HOLDING_LIGHTENING = 0.28;

type HoldingPreviewChild = { name: string; value: number };
type NestedGroupNode = {
  groupKey: string;
  label: string;
  count: number;
  totalGrossValue: number;
  allocationPercent: number;
  isCash: boolean;
  name: string;
  value: number;
  fill: string;
  stroke: string;
  children?: HoldingPreviewChild[];
};

function computeHoldingFill(baseColor: string, rank: number, total: number): string {
  const factor = total <= 1 ? 0 : (rank / (total - 1)) * MAX_HOLDING_LIGHTENING;
  return alpha(lighten(baseColor, factor), TREEMAP_FILL_ALPHA);
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

function buildNestedTreemapNodes(params: {
  chartData: InvestmentTreemapChartNode[];
  holdings: readonly HoldingSnapshot[];
  cashBalance: number;
}): NestedGroupNode[] {
  const { chartData, holdings, cashBalance } = params;
  return chartData.map((groupNode) => {
    if (groupNode.isCash) {
      if (cashBalance > 0) {
        return { ...groupNode, children: [{ name: 'Caixa', value: cashBalance }] };
      }
      return { ...groupNode };
    }
    const groupHoldings = holdings
      .filter((h) => {
        const v = h.grossValue ?? 0;
        return (
          Number.isFinite(v) &&
          v > 0 &&
          getAssetGroupKey(h.asset?.assetType ?? null) === groupNode.groupKey
        );
      })
      .sort((a, b) => (b.grossValue ?? 0) - (a.grossValue ?? 0));
    if (groupHoldings.length === 0) return { ...groupNode };
    return {
      ...groupNode,
      children: groupHoldings.map((h) => {
        const ticker = h.asset?.ticker ?? '';
        const fullName = h.asset?.name ?? ticker ?? h.id;
        return { name: ticker || fullName, value: h.grossValue ?? 0 };
      }),
    };
  });
}

export function buildHoldingTreemapNodes(params: {
  holdings: readonly HoldingSnapshot[];
  cashBalance: number;
  selectedGroupKey: string;
  baseColor: string;
  groupTotalGrossValue: number;
}): HoldingTreemapChartNode[] {
  const { holdings, cashBalance, selectedGroupKey, baseColor, groupTotalGrossValue } = params;
  const stroke = alpha(baseColor, TREEMAP_STROKE_ALPHA);

  if (selectedGroupKey === 'cash') {
    if (!Number.isFinite(cashBalance) || cashBalance <= 0) return [];
    return [
      {
        holdingId: 'cash-balance',
        name: 'Caixa',
        fullName: 'Caixa',
        ticker: '',
        accountName: '',
        currencyCode: 'BRL',
        assetType: 'CASH',
        groupKey: 'cash',
        value: cashBalance,
        groupValue: cashBalance,
        allocationPercent: 1,
        fill: computeHoldingFill(baseColor, 0, 1),
        stroke,
      },
    ];
  }

  const sorted = holdings
    .filter((holding) => {
      const grossValue = holding.grossValue ?? 0;
      if (!Number.isFinite(grossValue) || grossValue <= 0) return false;
      return getAssetGroupKey(holding.asset?.assetType ?? null) === selectedGroupKey;
    })
    .map((holding) => {
      const grossValue = holding.grossValue ?? 0;
      const ticker = holding.asset?.ticker ?? '';
      const fullName = holding.asset?.name ?? ticker ?? holding.id;
      return {
        holdingId: holding.id,
        name: ticker || fullName,
        fullName,
        ticker,
        accountName: holding.financialAccount?.name ?? '',
        currencyCode: holding.asset?.currencyCode ?? holding.financialAccount?.currencyCode ?? 'BRL',
        assetType: holding.asset?.assetType ?? '',
        groupKey: selectedGroupKey,
        value: grossValue,
        groupValue: groupTotalGrossValue,
        allocationPercent: groupTotalGrossValue > 0 ? grossValue / groupTotalGrossValue : 0,
        stroke,
      };
    })
    .filter((node) => node.value > 0)
    .sort((a, b) => b.value - a.value);

  const total = sorted.length;
  return sorted.map((node, rank) => ({
    ...node,
    fill: computeHoldingFill(baseColor, rank, total),
  }));
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

function HoldingTreemapTooltip(props: {
  active?: boolean;
  payload?: Array<{ payload?: HoldingTreemapChartNode }>;
  locale: string;
  t: (key: string, options?: Record<string, unknown>) => string;
}) {
  const { active, payload, locale, t } = props;
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
        minWidth: 200,
        maxWidth: 280,
      }}
    >
      <Typography variant="body2" sx={{ fontWeight: 700 }}>
        {node.fullName}
      </Typography>
      {node.ticker && node.ticker !== node.fullName ? (
        <Typography variant="caption" color="text.secondary">
          {node.ticker}
        </Typography>
      ) : null}
      <Stack spacing={0.5} sx={{ mt: 0.75 }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 2 }}>
          <Label sx={{ fontSize: '.85rem', color: 'text.secondary' }} noWrap>
            {t('dashboard.widgets.investmentTreemap.amount')}
          </Label>
          <Typography variant="body2" sx={{ fontWeight: 700, color: 'text.primary' }}>
            {formatCurrency(locale, node.currencyCode, node.value)}
          </Typography>
        </Box>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 2 }}>
          <Label sx={{ fontSize: '.85rem', color: 'text.secondary' }} noWrap>
            {t('dashboard.widgets.investmentTreemap.groupAllocation')}
          </Label>
          <Typography variant="body2" sx={{ fontWeight: 700, color: 'text.primary' }}>
            {formatPercent(locale, node.allocationPercent)}
          </Typography>
        </Box>
        {node.accountName ? (
          <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 2 }}>
            <Label sx={{ fontSize: '.85rem', color: 'text.secondary' }} noWrap>
              {t('dashboard.widgets.investmentTreemap.account')}
            </Label>
            <Typography variant="body2" sx={{ fontWeight: 700, color: 'text.primary' }}>
              {node.accountName}
            </Typography>
          </Box>
        ) : null}
      </Stack>
    </Box>
  );
}

function BreadcrumbRow(props: {
  groupLabel: string;
  onBack: () => void;
  t: (key: string) => string;
}) {
  const { groupLabel, onBack, t } = props;
  return (
    <Box
      sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 0.5,
        mb: 0.75,
        minHeight: 28,
      }}
    >
      <IconButton
        size="small"
        onClick={onBack}
        sx={{ p: 0.5, mr: 0.25 }}
        aria-label={t('dashboard.widgets.investmentTreemap.backToPortfolio')}
      >
        <ArrowBackIosNewIcon sx={{ fontSize: 13 }} />
      </IconButton>
      <Typography
        variant="caption"
        color="text.secondary"
        sx={{ cursor: 'pointer', '&:hover': { color: 'text.primary' } }}
        onClick={onBack}
      >
        {t('dashboard.widgets.investmentTreemap.portfolioBreadcrumb')}
      </Typography>
      <Typography variant="caption" color="text.secondary">
        {'>'}
      </Typography>
      <Typography variant="caption" color="text.primary" sx={{ fontWeight: 600 }}>
        {groupLabel}
      </Typography>
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
  const {
    holdings,
    cashBalance,
    loading: holdingsLoading,
    error: holdingsError,
    refetch: refetchHoldings,
  } = usePortfolioHoldingsSnapshot();
  const [selectedGroupKey, setSelectedGroupKey] = React.useState<string | null>(null);

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
  const selectedGroupRow = React.useMemo(
    () => rows.find((row) => row.groupKey === selectedGroupKey) ?? null,
    [rows, selectedGroupKey],
  );
  const isHoldingsView = selectedGroupKey !== null;
  const holdingChartData = React.useMemo<HoldingTreemapChartNode[]>(() => {
    if (!selectedGroupKey) return [];
    return buildHoldingTreemapNodes({
      holdings,
      cashBalance,
      selectedGroupKey,
      baseColor: resolveGroupColor(selectedGroupKey, allocationColors),
      groupTotalGrossValue: selectedGroupRow?.totalGrossValue ?? 0,
    });
  }, [allocationColors, cashBalance, holdings, selectedGroupKey, selectedGroupRow]);

  const nestedData = React.useMemo<NestedGroupNode[]>(
    () => buildNestedTreemapNodes({ chartData, holdings, cashBalance }),
    [chartData, holdings, cashBalance],
  );

  const isLoading = loading || holdingsLoading;
  const combinedError = error ?? holdingsError ?? null;
  const handleRefresh = React.useCallback(() => {
    void refetch();
    void refetchHoldings();
  }, [refetch, refetchHoldings]);

  const hasData = isHoldingsView ? holdingChartData.length > 0 : chartData.length > 0;

  React.useEffect(() => {
    if (!isHoldingsView) return;
    const handler = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setSelectedGroupKey(null);
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [isHoldingsView]);

  const treemapContent = React.useCallback(
    (node: any): React.ReactElement => {
      const depth = Number(node?.depth ?? 0);
      const x = Number(node?.x ?? 0);
      const y = Number(node?.y ?? 0);
      const width = Number(node?.width ?? 0);
      const height = Number(node?.height ?? 0);
      if (width <= 0 || height <= 0) return <g />;

      // depth=2: holding preview subdivision lines — transparent fill, white stroke
      if (depth === 2) {
        return (
          <g>
            <rect
              x={x}
              y={y}
              width={width}
              height={height}
              fill="transparent"
              stroke="rgba(255,255,255,0.55)"
              strokeWidth={1}
              style={{ pointerEvents: 'none' }}
            />
          </g>
        );
      }

      // depth=1 (or flat leaf): group rectangle with label
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
        <g
          style={{ cursor: node?.isCash ? 'default' : 'pointer' }}
          onClick={() => {
            const groupKey = typeof node?.groupKey === 'string' ? node.groupKey : null;
            const isCash = Boolean(node?.isCash);
            if (!groupKey || isCash) return;
            setSelectedGroupKey(groupKey);
          }}
        >
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
    [locale, theme.palette, setSelectedGroupKey],
  );

  const holdingContent = React.useCallback(
    (node: any): React.ReactElement => {
      const x = Number(node?.x ?? 0);
      const y = Number(node?.y ?? 0);
      const width = Number(node?.width ?? 0);
      const height = Number(node?.height ?? 0);
      if (width <= 0 || height <= 0) return <g />;

      const label = typeof node?.name === 'string' ? node.name : '';
      if (!label) return <g />;

      const fill = typeof node?.fill === 'string' ? node.fill : alpha(theme.palette.primary.main, TREEMAP_FILL_ALPHA);
      const stroke =
        typeof node?.stroke === 'string'
          ? node.stroke
          : alpha(theme.palette.primary.main, TREEMAP_STROKE_ALPHA);
      const textColor = theme.palette.getContrastText(fill);

      const showLabel = width >= 44 && height >= 28;
      const showValue = width >= 72 && height >= 46;
      const maxChars = Math.max(3, Math.floor((width - 8) / 7));
      const currencyCode = typeof node?.currencyCode === 'string' ? node.currencyCode : 'BRL';
      const value = typeof node?.value === 'number' ? node.value : 0;

      return (
        <g>
          <rect x={x} y={y} width={width} height={height} fill={fill} stroke={stroke} strokeWidth={1} />
          {showLabel ? (
            <text x={x + 5} y={y + 15} fill={textColor} fontSize={11} fontWeight={700}>
              {truncateLabel(label, maxChars)}
            </text>
          ) : null}
          {showValue ? (
            <text x={x + 5} y={y + 29} fill={textColor} fontSize={10} opacity={0.85}>
              {formatCurrency(locale, currencyCode, value)}
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
      onRefresh={handleRefresh}
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
        {combinedError && !hasData ? (
          <Typography color="error" variant="body2">
            {extractErrorMessage(combinedError) || t('dashboard.widgets.investmentTreemap.error')}
          </Typography>
        ) : isLoading && !hasData ? (
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
            {isHoldingsView && selectedGroupRow ? (
              <BreadcrumbRow
                groupLabel={resolveGroupLabel(selectedGroupRow.groupKey, selectedGroupRow.label)}
                onBack={() => setSelectedGroupKey(null)}
                t={t}
              />
            ) : null}
            {isHoldingsView && !hasData ? (
              <Box sx={{ height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <Typography variant="body2" color="text.secondary">
                  {t('dashboard.widgets.investmentTreemap.noHoldingsInGroup')}
                </Typography>
              </Box>
            ) : null}
            <Box sx={{ width: '100%', flex: 1, minHeight: 0 }} key={selectedGroupKey ?? 'groups'}>
              <ResponsiveContainer width="100%" height="100%">
                <Treemap
                  data={isHoldingsView ? holdingChartData : nestedData}
                  dataKey="value"
                  isAnimationActive={false}
                  content={isHoldingsView ? holdingContent : treemapContent}
                >
                  <RechartsTooltip
                    content={
                      isHoldingsView ? (
                        <HoldingTreemapTooltip locale={locale} t={t} />
                      ) : (
                        <InvestmentTreemapTooltip locale={locale} currencyCode={currencyCode} t={t} />
                      )
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
