'use client';

import React from 'react';
import dynamic from 'next/dynamic';
import { useApolloClient, useMutation, useQuery } from '@apollo/client/react';
import i18n from 'i18next';
import {
  alpha,
  Avatar,
  Box,
  Chip,
  CircularProgress,
  Divider,
  IconButton,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  ListItemIcon,
  ListItemText,
  Menu,
  MenuItem,
  Typography,
  useTheme,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import QueryStatsIcon from '@mui/icons-material/QueryStats';
import EditIcon from '@mui/icons-material/Edit';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import { useTranslation } from '@/shared/i18n';
import { Label } from '@/shared/components/ui/primitives/Label';
import { PercentageGain, Amount, ValueOriginIndicator } from '@/shared/components/ui/data-display';
import { FINANCE_TABLE_BODY_FONT_SIZE, FINANCE_TABLE_BODY_LINE_HEIGHT, FINANCE_TABLE_TYPOGRAPHY_SX } from '@/shared/components/ui/data-display/Table';
import { personalFinanceTranslations } from '../../i18n';
import { ALL_INVESTMENT_HOLDINGS, INVESTMENT_HOLDINGS, INVESTMENT_TRANSACTIONS } from '@/lib/graphql/operations/banking/queries';
import {
  DELETE_INVESTMENT_HOLDING,
} from '@/lib/graphql/operations/banking/mutations';
import {
  AllInvestmentHoldingsQuery,
  InvestmentHoldingsQuery,
  InvestmentHoldingsQueryVariables,
} from '@/lib/graphql/operations/banking/queries.generated';
import {
  AssetType,
  Indexer,
  InvestmentSource,
  InvestmentTransaction,
} from '@/lib/graphql/types/__generated__/graphql';
import { AssetGroupCard } from './AssetGroupCard';
import { formatFundAssetType } from '@/lib/utils/mapCvmFundAssetType';
import { AssetTypeIcon, type AssetTypeIconKind } from '@/shared/components/ui/icons/AssetTypeIcon';
import { ConfirmationDialog } from '@/shared/components/ui/feedback';
import { AssetTypeChip } from './AssetTypeChip';
import { RecordFixedIncomeDrawer } from './RecordFixedIncomeDrawer';
import { RecordFundHoldingDrawer } from './RecordFundHoldingDrawer';
import { RecordEquityHoldingDrawer } from './RecordEquityHoldingDrawer';
import { getHoldingValueOrigin, type ValueOriginLabel } from '@/shared/utils/holdingValueOrigin';
import type { InvestmentHoldingDetailKind } from './InvestmentHoldingDetailDrawer';

const InvestmentHoldingDetailDrawer = dynamic(
  () => import('./InvestmentHoldingDetailDrawer').then((mod) => ({ default: mod.InvestmentHoldingDetailDrawer })),
  { loading: () => null },
);

interface InvestmentTableProps {
  selectedAccountId: string | null;
  onRecordHolding?: () => void;
  searchQuery?: string;
}

type HoldingRow =
  | InvestmentHoldingsQuery['investmentHoldings'][number]
  | AllInvestmentHoldingsQuery['allInvestmentHoldings'][number];

const INDEXER_LABELS: Partial<Record<Indexer, string>> = {
  [Indexer.Cdi]: 'CDI',
  [Indexer.Di]: 'DI',
  [Indexer.Ipca]: 'IPCA+',
  [Indexer.IgpM]: 'IGP-M',
  [Indexer.IgpDi]: 'IGP-DI',
  [Indexer.Inpc]: 'INPC',
  [Indexer.Tr]: 'TR',
  [Indexer.Selic]: 'SELIC',
  [Indexer.PreFixado]: 'Prefixado',
};

const EMPTY_HOLDINGS: HoldingRow[] = [];
const FUND_GROUP_KEYS = new Set<AssetGroupKey>(['mutualFunds', 'pension']);
const FIXED_INCOME_GROUP_KEYS = new Set<AssetGroupKey>(['bankFixedIncome', 'creditFixedIncome', 'treasuryDirect']);

type EditDrawerKind = 'fixedIncome' | 'fund' | 'equity';

type AssetGroupKey =
  | 'variableIncome'
  | 'bankFixedIncome'
  | 'creditFixedIncome'
  | 'treasuryDirect'
  | 'mutualFunds'
  | 'pension'
  | 'structured'
  | 'others';

const ICON_KIND_BY_GROUP: Record<AssetGroupKey, AssetTypeIconKind> = {
  bankFixedIncome: 'FIXED_INCOME',
  creditFixedIncome: 'FIXED_INCOME',
  treasuryDirect: 'FIXED_INCOME',
  variableIncome: 'STOCK',
  mutualFunds: 'FUND',
  pension: 'FUND',
  structured: 'STRUCTURED',
  others: 'OTHER',
};

function AssetGroupIcon({
  groupKey,
  ...props
}: { groupKey: AssetGroupKey } & Omit<React.ComponentProps<typeof AssetTypeIcon>, 'kind' | 'assetType'>) {
  return <AssetTypeIcon kind={ICON_KIND_BY_GROUP[groupKey]} {...props} />;
}

const ASSET_GROUP_ORDER: readonly AssetGroupKey[] = [
  'bankFixedIncome',
  'creditFixedIncome',
  'treasuryDirect',
  'variableIncome',
  'mutualFunds',
  'pension',
  'structured',
  'others',
] as const;

const ASSET_TYPES_BY_GROUP: Record<AssetGroupKey, readonly string[]> = {
  variableIncome: [
    'STOCK',
    'STOCK_RIGHTS',
    'BDR',
    'REAL_ESTATE_FUND',
    'AGRIBUSINESS_FUND',
    'ETF',
    'ETF_INTERNATIONAL',
  ],
  bankFixedIncome: ['CDB', 'RDB', 'LCI', 'LCA', 'LC', 'LF', 'LIG', 'ETF_FIXED_INCOME'],
  creditFixedIncome: ['DEBENTURES', 'CRI', 'CRA'],
  treasuryDirect: ['TREASURY'],
  mutualFunds: ['MUTUAL_FUND', 'FIXED_INCOME_FUND', 'EQUITY_FUND', 'MULTI_MARKET_FUND', 'FOREX_FUND'],
  pension: ['PGBL', 'VGBL'],
  structured: ['COE'],
  others: ['CRYPTO', 'OPTION', 'DERIVATIVES', 'OTHER'],
};

const VALUE_ORIGIN_RELIABILITY_ORDER: Record<ValueOriginLabel, number> = {
  sincronizado_provider: 1,
  importado: 2,
  externo: 3,
  mercado_app: 4,
  calculado_app: 4,
  manual: 5,
  estimado_app: 6,
  indisponivel: 7,
};

function pickGroupValueOrigin(holdings: HoldingRow[]): ValueOriginLabel | null {
  const valued = holdings.filter((h) => typeof h.grossValue === 'number');
  if (valued.length === 0) return null;

  const origins = valued.map((h) =>
    getHoldingValueOrigin({
      valuationSource: h.valuationSource,
      source: h.source,
    }),
  );

  const unique = new Set(origins);
  if (unique.size === 1) return origins[0] ?? null;

  let picked: ValueOriginLabel | null = null;
  let maxScore = -Infinity;
  unique.forEach((origin) => {
    const score = VALUE_ORIGIN_RELIABILITY_ORDER[origin] ?? 0;
    if (score > maxScore) {
      picked = origin;
      maxScore = score;
    }
  });
  return picked;
}

function getAssetGroupKey(assetType: string | null | undefined): AssetGroupKey {
  if (!assetType) return 'others';

  const normalized = String(assetType);
  const match = ASSET_GROUP_ORDER.find((groupKey) => ASSET_TYPES_BY_GROUP[groupKey].includes(normalized));
  return match || 'others';
}

function getCurrentLocale(): string {
  return i18n.language || Intl.DateTimeFormat().resolvedOptions().locale || 'pt-BR';
}

function formatCurrency(value: number | null | undefined, currencyCode = 'BRL', locale = getCurrentLocale()): string {
  if (value == null) return '—';
  return new Intl.NumberFormat(locale, { style: 'currency', currency: currencyCode }).format(value);
}

function formatDate(value: unknown, locale = getCurrentLocale()): string {
  if (!value) return '—';

  const raw = String(value);
  const localDateMatch = /^(\d{4})-(\d{2})-(\d{2})$/.exec(raw);
  if (localDateMatch) {
    const [, year, month, day] = localDateMatch;
    const date = new Date(Number(year), Number(month) - 1, Number(day));
    return date.toLocaleDateString(locale);
  }

  const date = new Date(raw);
  if (Number.isNaN(date.getTime())) return '—';
  return date.toLocaleDateString(locale);
}

function formatAssetType(assetType: string, t: (key: string, options?: Record<string, unknown>) => string): string {
  return t(`investments.assetTypes.${assetType}` as string);
}

function formatIndexer(holding: HoldingRow, locale = getCurrentLocale()): string {
  if (!holding.indexer) return '—';

  if (holding.indexer === Indexer.PreFixado) {
    if (holding.fixedAnnualRate == null) return INDEXER_LABELS[Indexer.PreFixado] || 'Prefixado';
    const annualRate = holding.fixedAnnualRate * 100;
    return `${annualRate.toLocaleString(locale, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}% a.a.`;
  }

  const label = INDEXER_LABELS[holding.indexer] || holding.indexer;
  if (holding.indexerPercentage == null) return label;

  // `indexerPercentage` comes as a fraction (e.g. 1.2 == 120%). Show at least 1 decimal digit.
  const percentValue = holding.indexerPercentage * 100;
  if (!Number.isFinite(percentValue)) return label;

  const formattedPercent = percentValue.toLocaleString(locale, { minimumFractionDigits: 1, maximumFractionDigits: 2 });
  return `${label} ${formattedPercent}%`;
}

function formatIssuerLabel(holding: HoldingRow): string {
  const consolidated = holding as AllInvestmentHoldingsQuery['allInvestmentHoldings'][number];
  return consolidated.financialAccount?.issuer?.name ?? consolidated.financialAccount?.name ?? '—';
}

function getHoldingAvatarProps(holding: HoldingRow) {
  const assetName = holding.asset?.name ?? holding.asset?.ticker ?? '';
  const assetLogoUrl = holding.asset?.logoAsset?.publicUrl ?? undefined;
  if (assetLogoUrl) {
    return {
      src: assetLogoUrl,
      alt: assetName || undefined,
      label: assetName ? assetName.slice(0, 1).toUpperCase() : '—',
      backgroundColor: 'grey.100',
    };
  }

  const consolidated = holding as AllInvestmentHoldingsQuery['allInvestmentHoldings'][number];
  const financialAccount = consolidated.financialAccount;
  const name = financialAccount?.name ?? '';

  return {
    src: financialAccount?.issuer?.logoAsset?.publicUrl ?? undefined,
    alt: name || undefined,
    label: name ? name.slice(0, 1).toUpperCase() : '—',
    backgroundColor: financialAccount?.themeColor ?? 'grey.300',
  };
}

export function InvestmentTable({ selectedAccountId, onRecordHolding, searchQuery }: InvestmentTableProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();
  const locale = getCurrentLocale();
  const apolloClient = useApolloClient();
  const [expandedGroups, setExpandedGroups] = React.useState<Set<AssetGroupKey>>(() => new Set());
  const didInitExpansionRef = React.useRef(false);
  const [actionMenuAnchorEl, setActionMenuAnchorEl] = React.useState<HTMLElement | null>(null);
  const [actionMenuHolding, setActionMenuHolding] = React.useState<HoldingRow | null>(null);
  const [holdingToRemove, setHoldingToRemove] = React.useState<HoldingRow | null>(null);
  const [editDrawerHolding, setEditDrawerHolding] = React.useState<HoldingRow | null>(null);
  const [editDrawerTransaction, setEditDrawerTransaction] = React.useState<InvestmentTransaction | null>(null);
  const [editDrawerKind, setEditDrawerKind] = React.useState<EditDrawerKind>('equity');
  const [editDrawerFinancialAccountId, setEditDrawerFinancialAccountId] = React.useState<string | null>(null);
  const [detailHoldingId, setDetailHoldingId] = React.useState<string | null>(null);
  const [detailDrawerKind, setDetailDrawerKind] = React.useState<InvestmentHoldingDetailKind | null>(null);

  const [deleteInvestmentHolding, { loading: deletingHolding }] = useMutation(DELETE_INVESTMENT_HOLDING);
  const deleting = deletingHolding;

  const closeActionMenu = () => {
    setActionMenuAnchorEl(null);
    setActionMenuHolding(null);
  };

  const resolveManualTransactions = async (holdingId: string) => {
    const result = await apolloClient.query<{
      investmentTransactions: InvestmentTransaction[];
    }>({
      query: INVESTMENT_TRANSACTIONS,
      variables: { holdingId },
      fetchPolicy: 'network-only',
      errorPolicy: 'all',
    });

    const list = result.data?.investmentTransactions ?? [];
    return list.filter((tx) => tx.source === InvestmentSource.Manual);
  };

  const resolveLatestManualTransaction = async (holdingId: string) => {
    const list = await resolveManualTransactions(holdingId);
    return list[0] ?? null;
  };

  const resolveFinancialAccountId = (holding: HoldingRow) => {
    if (selectedAccountId) return selectedAccountId;
    const consolidated = holding as AllInvestmentHoldingsQuery['allInvestmentHoldings'][number];
    return consolidated.financialAccount?.id ?? null;
  };

  const closeDetailDrawer = React.useCallback(() => {
    setDetailHoldingId(null);
    setDetailDrawerKind(null);
  }, []);

  const handleOpenDetail = React.useCallback((holding: HoldingRow) => {
    const assetType = holding.asset?.assetType ?? null;
    const groupKey = getAssetGroupKey(assetType);
    const kind: InvestmentHoldingDetailKind =
      FIXED_INCOME_GROUP_KEYS.has(groupKey) ? 'fixedIncome' : FUND_GROUP_KEYS.has(groupKey) ? 'fund' : 'equity';

    setDetailHoldingId(holding.id);
    setDetailDrawerKind(kind);
  }, []);

  const handleConfirmRemove = async () => {
    const holding = holdingToRemove;
    if (!holding) return;

    try {
      await deleteInvestmentHolding({ variables: { id: holding.id } });
      setHoldingToRemove(null);

      if (selectedAccountId) await accountHoldingsQuery.refetch();
      else await consolidatedHoldingsQuery.refetch();
    } catch {
      setHoldingToRemove(null);
    }
  };

  const closeEditDrawer = () => {
    setEditDrawerHolding(null);
    setEditDrawerTransaction(null);
    setEditDrawerFinancialAccountId(null);
    setEditDrawerKind('equity');
  };

  const handleOpenEdit = async (holding: HoldingRow) => {
    const tx = await resolveLatestManualTransaction(holding.id);
    if (!tx) return;

    const assetType = holding.asset?.assetType ?? null;
    const groupKey = getAssetGroupKey(assetType);
    const kind: EditDrawerKind = FIXED_INCOME_GROUP_KEYS.has(groupKey) ? 'fixedIncome' : FUND_GROUP_KEYS.has(groupKey) ? 'fund' : 'equity';

    setEditDrawerHolding(holding);
    setEditDrawerTransaction(tx);
    setEditDrawerKind(kind);
    setEditDrawerFinancialAccountId(resolveFinancialAccountId(holding));
  };

  const accountHoldingsQuery = useQuery<InvestmentHoldingsQuery, InvestmentHoldingsQueryVariables>(INVESTMENT_HOLDINGS, {
    variables: { financialAccountId: selectedAccountId || '' },
    skip: !selectedAccountId,
    errorPolicy: 'all',
  });

  const consolidatedHoldingsQuery = useQuery<AllInvestmentHoldingsQuery>(ALL_INVESTMENT_HOLDINGS, {
    skip: Boolean(selectedAccountId),
    errorPolicy: 'all',
  });

  const holdings = React.useMemo(() => {
    if (selectedAccountId) return accountHoldingsQuery.data?.investmentHoldings ?? EMPTY_HOLDINGS;
    return consolidatedHoldingsQuery.data?.allInvestmentHoldings ?? EMPTY_HOLDINGS;
  }, [accountHoldingsQuery.data?.investmentHoldings, consolidatedHoldingsQuery.data?.allInvestmentHoldings, selectedAccountId]);

  const normalizedSearchQuery = React.useMemo(() => (searchQuery ?? '').trim().toLowerCase(), [searchQuery]);
  const hasSearchQuery = Boolean(normalizedSearchQuery);

  const displayedHoldings = React.useMemo(() => {
    if (!hasSearchQuery) return holdings;

    return holdings.filter((holding) => {
      const assetName = holding.asset?.name ?? '';
      const ticker = holding.asset?.ticker ?? '';
      const issuerName = holding.asset?.issuerName ?? '';
      const assetType = holding.asset?.assetType ?? '';
      const financialAccountName = (holding as AllInvestmentHoldingsQuery['allInvestmentHoldings'][number]).financialAccount?.name ?? '';

      const haystack = `${assetName} ${ticker} ${issuerName} ${assetType} ${financialAccountName}`.toLowerCase();
      return haystack.includes(normalizedSearchQuery);
    });
  }, [hasSearchQuery, holdings, normalizedSearchQuery]);

  const detailHolding = React.useMemo(
    () => holdings.find((holding) => holding.id === detailHoldingId) ?? null,
    [detailHoldingId, holdings],
  );

  const detailHoldingIssuerLogoUrl = React.useMemo(() => {
    if (!detailHolding || !("financialAccount" in detailHolding)) return null;
    return detailHolding.financialAccount?.issuer?.logoAsset?.publicUrl ?? null;
  }, [detailHolding]);

  const loading = selectedAccountId ? accountHoldingsQuery.loading : consolidatedHoldingsQuery.loading;
  const error = selectedAccountId ? accountHoldingsQuery.error : consolidatedHoldingsQuery.error;

  const groupedHoldings = React.useMemo(() => {
    const base = ASSET_GROUP_ORDER.reduce((acc, groupKey) => {
      acc[groupKey] = [];
      return acc;
    }, {} as Record<AssetGroupKey, HoldingRow[]>);

    base.others = [];

    displayedHoldings.forEach((holding) => {
      const groupKey = getAssetGroupKey(holding.asset?.assetType);
      base[groupKey].push(holding);
    });

    return base;
  }, [displayedHoldings]);

  const groups = React.useMemo(() => {
    let portfolioTotal = 0;
    displayedHoldings.forEach((holding) => {
      if (typeof holding.grossValue === 'number') portfolioTotal += holding.grossValue;
    });

    const computed = ASSET_GROUP_ORDER.map((groupKey) => {
      const groupHoldings = groupedHoldings[groupKey] || [];
      const currencyCodes = new Set(groupHoldings.map((holding) => holding.asset?.currencyCode).filter(Boolean));

      let sum = 0;
      let principalSum = 0;
      let hasValue = false;
      let hasPrincipal = false;

      groupHoldings.forEach((holding) => {
        if (typeof holding.grossValue === 'number') {
          sum += holding.grossValue;
          hasValue = true;
        }
        if (typeof holding.averageCost === 'number' && typeof holding.quantity === 'number') {
          principalSum += holding.averageCost * holding.quantity;
          hasPrincipal = true;
        }
      });

      const currencyCode = currencyCodes.size === 1 ? Array.from(currencyCodes)[0] : null;
      const total = hasValue && currencyCode ? formatCurrency(sum, currencyCode, locale) : '—';
      const appliedTotal = hasPrincipal && currencyCode ? formatCurrency(principalSum, currencyCode, locale) : '—';
      const allocationPercent = portfolioTotal > 0 && hasValue ? sum / portfolioTotal : null;
      const valueOriginKey = pickGroupValueOrigin(groupHoldings);

      return {
        key: groupKey,
        title: t(`investments.table.groups.${groupKey}`),
        count: groupHoldings.length,
        total,
        appliedTotal,
        valueOriginKey,
        gainPercent: principalSum > 0 && hasValue ? (sum - principalSum) / principalSum : null,
        allocationPercent,
        summary: t('investments.table.groupSummary', {
          count: groupHoldings.length,
          total,
        }),
        holdings: groupHoldings,
      };
    });
    return computed.filter((group) => group.count > 0);
  }, [displayedHoldings, groupedHoldings, locale, t]);

  React.useEffect(() => {
    if (loading || error) return;

    if (groups.length === 0) {
      if (expandedGroups.size > 0) setExpandedGroups(new Set());
      didInitExpansionRef.current = false;
      return;
    }

    if (hasSearchQuery) {
      const nextExpandedGroups = new Set(groups.map((group) => group.key));
      const isSame =
        nextExpandedGroups.size === expandedGroups.size &&
        Array.from(nextExpandedGroups).every((key) => expandedGroups.has(key));

      if (!isSame) setExpandedGroups(nextExpandedGroups);
      didInitExpansionRef.current = true;
      return;
    }

    const firstNonEmpty = groups[0]?.key;

    if (!didInitExpansionRef.current) {
      if (firstNonEmpty) setExpandedGroups(new Set([firstNonEmpty]));
      didInitExpansionRef.current = true;
      return;
    }

    if (expandedGroups.size === 0) return;

    const availableKeys = new Set(groups.map((group) => group.key));
    const nextExpandedGroups = new Set(Array.from(expandedGroups).filter((key) => availableKeys.has(key)));

    if (nextExpandedGroups.size !== expandedGroups.size) {
      if (nextExpandedGroups.size === 0 && firstNonEmpty) {
        setExpandedGroups(new Set([firstNonEmpty]));
      } else {
        setExpandedGroups(nextExpandedGroups);
      }
    }
  }, [error, expandedGroups, groups, hasSearchQuery, loading]);

  React.useEffect(() => {
    setExpandedGroups(new Set());
    didInitExpansionRef.current = false;
  }, [selectedAccountId]);

  React.useEffect(() => {
    if (hasSearchQuery) return;
    setExpandedGroups(new Set());
    didInitExpansionRef.current = false;
  }, [hasSearchQuery]);

  const isEditDrawerOpen = Boolean(editDrawerHolding && editDrawerTransaction);

  const handleEditRecorded = React.useCallback(async () => {
    if (selectedAccountId) await accountHoldingsQuery.refetch();
    else await consolidatedHoldingsQuery.refetch();
  }, [accountHoldingsQuery, consolidatedHoldingsQuery, selectedAccountId]);

  const isActionMenuFixedIncome = actionMenuHolding?.asset?.assetType
    ? FIXED_INCOME_GROUP_KEYS.has(getAssetGroupKey(actionMenuHolding.asset.assetType))
    : false;

  const emptyStateMessage = hasSearchQuery ? t('investments.table.noMatches') : t('investments.table.emptyState');

  return (
    <TableContainer
      component={Paper}
      elevation={0}
      sx={{
        bgcolor: 'transparent',
        background: 'transparent !important',
        border: 'none',
      }}
      data-testid="investment-table"
    >
      {loading && (
        <Box sx={{ py: 6, display: 'flex', justifyContent: 'center' }}>
          <CircularProgress size={20} />
        </Box>
      )}

      {!loading && error && (
        <Box sx={{ py: 6, textAlign: 'center' }}>
          <Typography variant="body2" color="error.main">
            {t('investments.table.error')}
          </Typography>
        </Box>
      )}

      {!loading && !error && displayedHoldings.length === 0 && (
        <Box sx={{ py: 6, textAlign: 'center' }}>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            {emptyStateMessage}
          </Typography>
        </Box>
      )}

      {!loading && !error && displayedHoldings.length > 0 && (
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0 }}>
          {groups.map((group) => {
            const isExpanded = expandedGroups.has(group.key);
            const leadingIcon = (
              <AssetGroupIcon groupKey={group.key} sx={{ fontSize: 16 }} />
            );
            const groupValueOrigin =
              group.valueOriginKey && group.total !== '—' ? (
                <ValueOriginIndicator
                  origin={group.valueOriginKey}
                  title={t(`investments.table.valueOrigin.labels.${group.valueOriginKey}`)}
                  description={t(`investments.table.valueOrigin.descriptions.${group.valueOriginKey}`)}
                  ariaLabel={t('investments.table.valueOrigin.ariaLabel')}
                  offsetPx={18}
                />
              ) : null;
            const divider = alpha(theme.palette.divider, 0.7);
            const tableRadius = (theme as any).custom?.radius?.table ?? theme.shape.borderRadius;
            const isFundGroup = FUND_GROUP_KEYS.has(group.key);
            const isFixedIncomeGroup = FIXED_INCOME_GROUP_KEYS.has(group.key);
            const isVariableIncomeGroup = group.key === 'variableIncome';
            const dataColumnCount = isFundGroup ? 4 : isFixedIncomeGroup ? 4 : isVariableIncomeGroup ? 7 : 5;
            const colWidth = `calc((100% - 52px) / ${dataColumnCount})`;
            const fixedIncomeMaturityColWidth = 140;
            const fixedIncomeGrossValueColWidth = 170;
            const fixedIncomeGainColWidth = 160;
            const fixedIncomeIndexerColWidth = 160;
            const actionColWidth = 88;
            const assetColSx = isFixedIncomeGroup ? { width: 'auto' } : { width: colWidth };
            const maturityColSx = { width: isFixedIncomeGroup ? fixedIncomeMaturityColWidth : colWidth };
            const grossValueColSx = { width: isFixedIncomeGroup ? fixedIncomeGrossValueColWidth : colWidth };
            const gainColSx = { width: isFixedIncomeGroup ? fixedIncomeGainColWidth : colWidth };
            const indexerColSx = { width: isFixedIncomeGroup ? fixedIncomeIndexerColWidth : colWidth };

            return (
              <AssetGroupCard
                key={group.key}
                expanded={isExpanded}
                onToggle={(expanded) =>
                  setExpandedGroups((prev) => {
                    const next = new Set(prev);
                    if (expanded) next.add(group.key);
                    else next.delete(group.key);
                    return next;
                  })
                }
                title={group.title}
                summary={group.summary}
                leadingIcon={leadingIcon}
                summaryAdornment={groupValueOrigin}
                gainPercent={group.gainPercent}
                allocationPercent={group.allocationPercent}
                appliedLabel={t('investments.table.appliedValue')}
                appliedValue={group.appliedTotal}
              >
                <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.5 }}>
                  <Table
                    sx={{
                      width: '100%',
                      tableLayout: 'fixed',
                      borderCollapse: 'collapse',
                      ...FINANCE_TABLE_TYPOGRAPHY_SX,
                      '& .MuiTableCell-root': {
                        px: 1.5,
                        pb: 0.75,
                        borderBottom: 'none',
                        bgcolor: 'transparent',
                      },
                    }}
                    aria-label="investments table header"
                  >
                    <TableHead>
                      <TableRow>
                        <TableCell sx={assetColSx}>
                          <Label>{t('investments.table.columns.asset')}</Label>
                        </TableCell>
                        {isFixedIncomeGroup ? (
                          <TableCell sx={indexerColSx}>
                            <Label>{t('investments.table.columns.indexer')}</Label>
                          </TableCell>
                        ) : (
                          <TableCell sx={{ width: colWidth }}>
                            <Label>{t('investments.table.columns.type')}</Label>
                          </TableCell>
                        )}
                        {isFundGroup ? (
                          <>
                            <TableCell align="right" sx={{ width: colWidth }}>
                              <Label>{t('investments.table.columns.gain')}</Label>
                            </TableCell>
                            <TableCell align="right" sx={{ width: colWidth }}>
                              <Label>{t('investments.table.columns.grossValue')}</Label>
                            </TableCell>
                          </>
                        ) : (
                          <>
                            {isVariableIncomeGroup ? (
                              <>
                                <TableCell sx={maturityColSx}>
                                  <Label>{t('investments.table.columns.quantity')}</Label>
                                </TableCell>
                                <TableCell align="right" sx={{ width: colWidth }}>
                                  <Label>{t('investments.table.columns.averagePrice')}</Label>
                                </TableCell>
                                <TableCell align="right" sx={{ width: colWidth }}>
                                  <Label>{t('investments.table.columns.priceD1')}</Label>
                                </TableCell>
                              </>
                            ) : (
                              <TableCell sx={maturityColSx}>
                                <Label>{t('investments.table.columns.maturityDate')}</Label>
                              </TableCell>
                            )}
                            <TableCell align="right" sx={gainColSx}>
                              <Label>{t('investments.table.columns.gain')}</Label>
                            </TableCell>
                            <TableCell align="right" sx={grossValueColSx}>
                              <Label>{t('investments.table.columns.grossValue')}</Label>
                            </TableCell>
                          </>
                        )}
                        <TableCell align="center" sx={{ width: actionColWidth }} />
                      </TableRow>
                    </TableHead>
                  </Table>

                  <Box
                    sx={{
                      borderRadius: `${tableRadius}px`,
                      overflow: 'hidden',
                      bgcolor: 'background.paper',
                    }}
                  >
                    <Table
                      sx={{
                        width: '100%',
                        tableLayout: 'fixed',
                        borderCollapse: 'collapse',
                        ...FINANCE_TABLE_TYPOGRAPHY_SX,
                        '& .MuiTableCell-root': {
                          px: 1.5,
                        },
                      }}
                      aria-label="investments table"
                    >
                      <TableBody>
                    {group.holdings.map((holding) => {
                      const isExpired = holding.isExpired;
                      const currencyCode = holding.asset?.currencyCode ?? 'BRL';

                      const valueUnavailable = typeof holding.grossValue !== 'number';
                      const valueOriginKey = getHoldingValueOrigin({
                        valuationSource: holding.valuationSource,
                        source: holding.source,
                      });
                      const valueOriginTitle = t(`investments.table.valueOrigin.labels.${valueOriginKey}`);
                      const valueOriginDescription = t(`investments.table.valueOrigin.descriptions.${valueOriginKey}`);
                      const valueOriginAriaLabel = t('investments.table.valueOrigin.ariaLabel');

                      const principal = holding.averageCost * holding.quantity;
                      const grossVal = typeof holding.grossValue === 'number' ? holding.grossValue : null;
                      const absolute = principal > 0 && grossVal != null ? grossVal - principal : null;
                      const growthRatio = absolute != null && principal > 0 ? absolute / principal : null;
                      const gainUnavailable = grossVal == null;
                      const canMutate = holding.source === InvestmentSource.Manual;
                      const issuerLabel = formatIssuerLabel(holding);
                      const maturityDateStr = holding.maturityDate ? formatDate(holding.maturityDate, locale) : '';
                      const expiredNudge =
                        isExpired && maturityDateStr
                          ? (t('investments.table.expiredNudge', { maturityDate: maturityDateStr }) as string)
                          : null;
                      const assetSecondaryLabel = isVariableIncomeGroup
                        ? holding.asset?.name ?? '—'
                        : expiredNudge
                        ? `${issuerLabel} • ${expiredNudge}`
                        : issuerLabel;
                      const gainPercentNode =
                        growthRatio != null && principal > 0 && absolute != null ? (
                          <PercentageGain value={growthRatio} locale={locale} />
                        ) : null;

                      return (
                        <TableRow
                          key={holding.id}
                          sx={{
                            '& td': { borderBottom: `1px solid ${divider}`, pt: 2.5, pb: 1.5 },
                            '&:last-of-type td': { borderBottom: 'none' },
                          }}
                        >
                          <TableCell sx={assetColSx}>
                            <Box
                              sx={{
                                display: 'grid',
                                gridTemplateColumns: '32px minmax(0, 1fr)',
                                gridTemplateRows: 'auto auto',
                                columnGap: 1,
                                alignItems: 'center',
                                minWidth: 0,
                              }}
                            >
                              {(() => {
                                const avatar = getHoldingAvatarProps(holding);
                                return (
                                  <Avatar
                                    src={avatar.src}
                                    alt={avatar.alt}
                                    variant="rounded"
                                    sx={{
                                      width: 32,
                                      height: 32,
                                      bgcolor: avatar.src ? 'transparent' : avatar.backgroundColor,
                                      borderRadius: '.3rem',
                                      fontSize: '0.75rem',
                                      gridRow: '1 / span 2',
                                      alignSelf: 'center',
                                      '& img': { objectFit: 'contain' },
                                    }}
                                  >
                                    {!avatar.src && avatar.label}
                                  </Avatar>
                                );
                              })()}
                              <Typography variant="body2" noWrap sx={{ minWidth: 0 }}>
                                {(() => {
                                  const name = holding.asset?.name ?? '—';
                                  if (!isFixedIncomeGroup) {
                                    if (isVariableIncomeGroup) {
                                      const ticker = holding.asset?.ticker ?? null;
                                      return ticker || name;
                                    }
                                    return name;
                                  }
                                  const typeLabel = holding.asset ? formatAssetType(holding.asset.assetType, t) : '—';
                                  return `${name} • ${typeLabel}`;
                                })()}
                              </Typography>
                              <Typography variant="caption" color="text.secondary" noWrap sx={{ mt: 0.25, minWidth: 0 }}>
                                {assetSecondaryLabel}
                              </Typography>
                            </Box>
                          </TableCell>
                          {isFundGroup ? (
                            <TableCell sx={{ width: colWidth }}>
                              <Box sx={{ display: 'flex', flexDirection: 'column', minWidth: 0 }}>
                                {holding.asset ? (
                                  <AssetTypeChip assetType={holding.asset.assetType} size="small" />
                                ) : (
                                  <Typography variant="body2" noWrap>—</Typography>
                                )}
                                <Typography variant="caption" color="text.secondary" noWrap sx={{ fontVariantNumeric: 'tabular-nums' }}>
                                  {holding.quantity.toLocaleString(locale, { maximumFractionDigits: 6 })}
                                </Typography>
                              </Box>
                            </TableCell>
                          ) : (
                            isFixedIncomeGroup ? (
                              <TableCell sx={indexerColSx}>
                                <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', gap: 0.25, minWidth: 0 }}>
                                  <Typography variant="body2" noWrap>
                                    {formatIndexer(holding, locale)}
                                  </Typography>
                                  <Typography variant="caption" sx={{ mt: 0.25, visibility: 'hidden' }}>
                                    —
                                  </Typography>
                                </Box>
                              </TableCell>
                            ) : (
                              <TableCell sx={{ width: colWidth }}>
                                {holding.asset ? (
                                  <AssetTypeChip assetType={holding.asset.assetType} size="small" />
                                ) : (
                                  <Typography variant="body2">—</Typography>
                                )}
                              </TableCell>
                            )
                          )}
                          {isFundGroup ? (
                            <>
                              <TableCell align="right" sx={{ width: colWidth }}>
                                <Box
                                  sx={{
                                    display: 'flex',
                                    flexDirection: 'column',
                                    alignItems: 'flex-end',
                                    justifyContent: 'center',
                                    gap: 0.25,
                                  }}
                                >
                                  {gainUnavailable ? (
                                    <>
                                      <Typography variant="body2">—</Typography>
                                      <Typography variant="caption" sx={{ mt: 0.25, visibility: 'hidden' }}>
                                        —
                                      </Typography>
                                    </>
                                  ) : (
                                    <>
                                      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 0.25 }}>
                                          {absolute != null ? (
                                            <Amount value={absolute} currency={currencyCode} locale={locale} />
                                          ) : (
                                            <Typography variant="body2">—</Typography>
                                          )}
                                      </Box>
                                      {gainPercentNode ? (
                                        <Box sx={{ mt: 0.25 }}>{gainPercentNode}</Box>
                                      ) : (
                                        <Typography variant="caption" sx={{ mt: 0.25, visibility: 'hidden' }}>
                                          —
                                        </Typography>
                                      )}
                                    </>
                                  )}
                                </Box>
                              </TableCell>
                              <TableCell align="right" sx={{ width: colWidth }}>
                                <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 0.25 }}>
                                  <Box
                                    sx={{
                                      position: 'relative',
                                      display: 'flex',
                                      alignItems: 'center',
                                      justifyContent: 'flex-end',
                                      width: '100%',
                                      overflow: 'visible',
                                    }}
                                  >
                                    {valueUnavailable ? (
                                      <Typography variant="body2" component="span" fontWeight="medium">—</Typography>
                                    ) : (
                                      <Amount value={holding.grossValue as number} currency={currencyCode} locale={locale} tone="neutral" />
                                    )}
                                    <ValueOriginIndicator
                                      origin={valueOriginKey}
                                      title={valueOriginTitle}
                                      description={valueOriginDescription}
                                      ariaLabel={valueOriginAriaLabel}
                                      offsetPx={18}
                                    />
                                  </Box>
                                  <Typography variant="caption" sx={{ mt: 0.25, visibility: 'hidden' }}>
                                    —
                                  </Typography>
                                </Box>
                              </TableCell>
                            </>
                          ) : (
                            <>
                              {isVariableIncomeGroup ? (
                                <>
                                  <TableCell sx={{ ...maturityColSx, verticalAlign: 'top' }}>
                                    <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', gap: 0.25, minWidth: 0 }}>
                                      <Typography variant="body2" sx={{ fontVariantNumeric: 'tabular-nums' }}>
                                        {holding.quantity.toLocaleString(locale, { maximumFractionDigits: 6 })}
                                      </Typography>
                                      <Typography variant="caption" sx={{ mt: 0.25, visibility: 'hidden' }}>
                                        —
                                      </Typography>
                                    </Box>
                                  </TableCell>
                                  <TableCell align="right" sx={{ width: colWidth, verticalAlign: 'top' }}>
                                    <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 0.25 }}>
                                      <Typography variant="body2" sx={{ visibility: 'hidden', fontVariantNumeric: 'tabular-nums' }}>
                                        —
                                      </Typography>
                                      <Typography variant="caption" sx={{ mt: 0.25, visibility: 'hidden' }}>
                                        —
                                      </Typography>
                                    </Box>
                                  </TableCell>
                                  <TableCell align="right" sx={{ width: colWidth, verticalAlign: 'top' }}>
                                    <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 0.25 }}>
                                      <Typography variant="body2" sx={{ visibility: 'hidden', fontVariantNumeric: 'tabular-nums' }}>
                                        —
                                      </Typography>
                                      <Typography variant="caption" sx={{ mt: 0.25, visibility: 'hidden' }}>
                                        —
                                      </Typography>
                                    </Box>
                                  </TableCell>
                                </>
                              ) : (
                                <TableCell sx={{ ...maturityColSx, verticalAlign: 'top' }}>
                                  <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', gap: 0.25, minWidth: 0 }}>
                                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, flexWrap: 'wrap', minWidth: 0 }}>
                                      <Typography variant="body2">{formatDate(holding.maturityDate, locale)}</Typography>
                                      {isExpired && (
                                        <Chip
                                          size="small"
                                          label={
                                            <Typography
                                              variant="body2"
                                              sx={{ fontSize: FINANCE_TABLE_BODY_FONT_SIZE, lineHeight: FINANCE_TABLE_BODY_LINE_HEIGHT, opacity: 0.7 }}
                                            >
                                              {t('investments.table.expiredBadge')}
                                            </Typography>
                                          }
                                          sx={{
                                            height: 28,
                                            bgcolor: 'action.selected',
                                            borderColor: 'divider',
                                            '& .MuiChip-label': { px: 1 },
                                          }}
                                          color="default"
                                          variant="outlined"
                                        />
                                      )}
                                    </Box>
                                    <Typography variant="caption" sx={{ mt: 0.25, visibility: 'hidden' }}>
                                      —
                                    </Typography>
                                  </Box>
                                </TableCell>
                              )}

                              {isFixedIncomeGroup ? (
                                <TableCell align="right" sx={gainColSx}>
                                  <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', justifyContent: 'center', gap: 0.25 }}>
                                    {gainUnavailable ? (
                                      <>
                                        <Typography variant="body2">—</Typography>
                                        <Typography variant="caption" sx={{ mt: 0.25, visibility: 'hidden' }}>
                                          —
                                        </Typography>
                                      </>
                                    ) : (
                                      <>
                                        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 0.25 }}>
                                          {absolute != null ? (
                                            <Amount value={absolute} currency={currencyCode} locale={locale} />
                                          ) : (
                                            <Typography variant="body2">—</Typography>
                                          )}
                                        </Box>
                                        {gainPercentNode ? (
                                          <Box sx={{ mt: 0.25 }}>{gainPercentNode}</Box>
                                        ) : (
                                          <Typography variant="caption" sx={{ mt: 0.25, visibility: 'hidden' }}>
                                            —
                                          </Typography>
                                        )}
                                      </>
                                    )}
                                  </Box>
                                </TableCell>
                              ) : (
                                <TableCell align="right" sx={gainColSx}>
                                  <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', justifyContent: 'center', gap: 0.25 }}>
                                    {gainUnavailable ? (
                                      <>
                                        <Typography variant="body2">—</Typography>
                                        <Typography variant="caption" sx={{ mt: 0.25, visibility: 'hidden' }}>
                                          —
                                        </Typography>
                                      </>
                                    ) : (
                                      <>
                                        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 0.25 }}>
                                          {absolute != null ? (
                                            <Amount value={absolute} currency={currencyCode} locale={locale} />
                                          ) : (
                                            <Typography variant="body2">—</Typography>
                                          )}
                                        </Box>
                                        {gainPercentNode ? (
                                          <Box sx={{ mt: 0.25 }}>{gainPercentNode}</Box>
                                        ) : (
                                          <Typography variant="caption" sx={{ mt: 0.25, visibility: 'hidden' }}>
                                            —
                                          </Typography>
                                        )}
                                      </>
                                    )}
                                  </Box>
                                </TableCell>
                              )}
                              <TableCell align="right" sx={grossValueColSx}>
                                <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 0.25 }}>
                                  <Box
                                    sx={{
                                      position: 'relative',
                                      display: 'flex',
                                      alignItems: 'center',
                                      justifyContent: 'flex-end',
                                      width: '100%',
                                      overflow: 'visible',
                                    }}
                                  >
                                    {valueUnavailable ? (
                                      <Typography variant="body2" component="span" fontWeight="medium">—</Typography>
                                    ) : (
                                      <Amount value={holding.grossValue as number} currency={currencyCode} locale={locale} tone="neutral" />
                                    )}
                                    <ValueOriginIndicator
                                      origin={valueOriginKey}
                                      title={valueOriginTitle}
                                      description={valueOriginDescription}
                                      ariaLabel={valueOriginAriaLabel}
                                      offsetPx={18}
                                    />
                                  </Box>
                                  <Typography variant="caption" sx={{ mt: 0.25, visibility: 'hidden' }}>
                                    —
                                  </Typography>
                                </Box>
                              </TableCell>
                            </>
                          )}
                          <TableCell align="center" sx={{ width: actionColWidth, verticalAlign: 'middle' }}>
                            <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 0.5, height: '100%' }}>
                              <IconButton
                                size="small"
                                onClick={(e) => {
                                  e.stopPropagation();
                                  if (!canMutate) return;
                                  setActionMenuAnchorEl(e.currentTarget);
                                  setActionMenuHolding(holding);
                                }}
                                aria-label={t('investments.table.rowActions.ariaLabel')}
                                disabled={!canMutate}
                              >
                                <MoreVertIcon fontSize="small" />
                              </IconButton>
                            </Box>
                          </TableCell>
                        </TableRow>
                      );
                    })}
                      </TableBody>
                    </Table>
                  </Box>
                </Box>
              </AssetGroupCard>
            );
          })}
        </Box>
      )}

      <Menu
        open={Boolean(actionMenuAnchorEl)}
        anchorEl={actionMenuAnchorEl}
        onClose={closeActionMenu}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
        transformOrigin={{ vertical: 'top', horizontal: 'right' }}
      >
        <MenuItem
          onClick={() => {
            const holding = actionMenuHolding;
            closeActionMenu();
            if (!holding) return;
            handleOpenDetail(holding);
          }}
          disabled={deleting}
        >
          <ListItemIcon>
            <QueryStatsIcon fontSize="small" />
          </ListItemIcon>
          <ListItemText>{t('investments.table.rowActions.viewDetails')}</ListItemText>
        </MenuItem>
        <MenuItem
          onClick={async () => {
            const holding = actionMenuHolding;
            closeActionMenu();
            if (!holding) return;
            await handleOpenEdit(holding);
          }}
          disabled={deleting}
        >
          <ListItemIcon>
            <EditIcon fontSize="small" />
          </ListItemIcon>
          <ListItemText>{t('investments.table.rowActions.edit')}</ListItemText>
        </MenuItem>
        {isActionMenuFixedIncome && <Divider />}
        <MenuItem
          onClick={() => {
            const holding = actionMenuHolding;
            closeActionMenu();
            if (!holding) return;
            setHoldingToRemove(holding);
          }}
          sx={{
            color: 'error.main',
            ...(isActionMenuFixedIncome
              ? {
                  '&:hover': { bgcolor: alpha(theme.palette.error.main, 0.08) },
                  '&.Mui-focusVisible': { bgcolor: alpha(theme.palette.error.main, 0.12) },
                }
              : {}),
          }}
          disabled={deleting}
        >
          <ListItemIcon sx={{ color: 'inherit' }}>
            <DeleteOutlineIcon fontSize="small" />
          </ListItemIcon>
          <ListItemText>{t('investments.table.rowActions.remove')}</ListItemText>
        </MenuItem>
      </Menu>

      <ConfirmationDialog
        open={Boolean(holdingToRemove)}
        onClose={() => setHoldingToRemove(null)}
        onConfirm={handleConfirmRemove}
        title={t('investments.deleteConfirmation.title')}
        message={t('investments.deleteConfirmation.message')}
        cancelText={t('actions.cancel')}
        confirmText={t('actions.delete')}
        confirmColor="error"
        loading={deleting}
      />

      <InvestmentHoldingDetailDrawer
        open={Boolean(detailHoldingId && detailDrawerKind)}
        holdingId={detailHoldingId}
        kind={detailDrawerKind}
        initialTitle={detailHolding?.asset?.name ?? null}
        initialAssetType={detailHolding?.asset?.assetType ?? null}
        initialLogoUrl={detailHolding?.asset?.logoAsset?.publicUrl ?? null}
        initialIssuerLogoUrl={detailHoldingIssuerLogoUrl}
        onClose={closeDetailDrawer}
      />

      <RecordFixedIncomeDrawer
        open={isEditDrawerOpen && editDrawerKind === 'fixedIncome'}
        onClose={closeEditDrawer}
        financialAccountId={editDrawerFinancialAccountId}
        financialAccounts={[]}
        editHolding={
          editDrawerHolding
            ? {
                id: editDrawerHolding.id,
                asset: {
                  id: editDrawerHolding.asset.id,
                  name: editDrawerHolding.asset.name,
                  assetType: editDrawerHolding.asset.assetType,
                  issuerName: editDrawerHolding.asset.issuerName ?? null,
                },
                indexer: editDrawerHolding.indexer ?? null,
                indexerPercentage: editDrawerHolding.indexerPercentage ?? null,
                fixedAnnualRate: editDrawerHolding.fixedAnnualRate ?? null,
                maturityDate: editDrawerHolding.maturityDate ?? null,
                isTaxExempt: editDrawerHolding.isTaxExempt ?? false,
              }
            : null
        }
        editTransaction={editDrawerTransaction}
        onRecorded={handleEditRecorded}
      />

      <RecordFundHoldingDrawer
        open={isEditDrawerOpen && editDrawerKind === 'fund'}
        onClose={closeEditDrawer}
        financialAccountId={editDrawerFinancialAccountId}
        financialAccounts={[]}
        editHolding={
          editDrawerHolding
            ? {
                id: editDrawerHolding.id,
                pricingMode: editDrawerHolding.pricingMode ?? null,
                valuationReferenceDate: editDrawerHolding.valuationReferenceDate ?? null,
                grossValue: editDrawerHolding.grossValue ?? null,
                netValue: editDrawerHolding.netValue ?? null,
                asset: {
                  id: editDrawerHolding.asset.id,
                  name: editDrawerHolding.asset.name,
                  assetType: editDrawerHolding.asset.assetType as AssetType,
                },
              }
            : null
        }
        editTransaction={editDrawerTransaction}
        onRecorded={handleEditRecorded}
      />

      <RecordEquityHoldingDrawer
        open={isEditDrawerOpen && editDrawerKind === 'equity'}
        onClose={closeEditDrawer}
        financialAccountId={editDrawerFinancialAccountId}
        financialAccounts={[]}
        editHolding={
          editDrawerHolding
            ? {
                id: editDrawerHolding.id,
                asset: {
                  id: editDrawerHolding.asset.id,
                  ticker: editDrawerHolding.asset.ticker ?? null,
                  name: editDrawerHolding.asset.name,
                  assetType: editDrawerHolding.asset.assetType as AssetType,
                },
              }
            : null
        }
        editTransaction={editDrawerTransaction}
        onRecorded={handleEditRecorded}
      />
    </TableContainer>
  );
}
