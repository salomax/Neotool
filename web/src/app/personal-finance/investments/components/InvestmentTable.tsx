'use client';

import React from 'react';
import dynamic from 'next/dynamic';
import { useApolloClient, useMutation, useQuery } from '@apollo/client/react';
import {
  alpha,
  Box,
  CircularProgress,
  IconButton,
  Menu,
  MenuItem,
  Paper,
  TableContainer,
  Typography,
  useTheme,
} from '@mui/material';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import { useTranslation } from '@/shared/i18n';
import { Logo } from '@/shared/components/ui/primitives';
import { Label } from '@/shared/components/ui/primitives/Label';
import { PercentageGain, ValueOriginIndicator } from '@/shared/components/ui/data-display';
import { personalFinanceTranslations } from '../../i18n';
import { ALL_INVESTMENT_HOLDINGS, INVESTMENT_HOLDINGS, INVESTMENT_TRANSACTIONS } from '@/lib/graphql/operations/banking/queries';
import {
  DELETE_INVESTMENT_HOLDING,
} from '@/lib/graphql/operations/banking/mutations';
import {
  AllInvestmentHoldingsQuery,
  FinancialAccountsQuery,
  InvestmentHoldingsQuery,
  InvestmentHoldingsQueryVariables,
} from '@/lib/graphql/operations/banking/queries.generated';
import {
  AssetType,
  InvestmentSource,
  InvestmentTransaction,
} from '@/lib/graphql/types/__generated__/graphql';
import { AssetTypeIcon, type AssetTypeIconKind } from '@/shared/components/ui/icons/AssetTypeIcon';
import { ConfirmationDialog } from '@/shared/components/ui/feedback';
import { RecordFixedIncomeDrawer } from './RecordFixedIncomeDrawer';
import { RecordFundHoldingDrawer } from './RecordFundHoldingDrawer';
import { RecordEquityHoldingDrawer } from './RecordEquityHoldingDrawer';
import type { InvestmentHoldingDetailKind } from './InvestmentHoldingDetailDrawer';
import { InvestmentGroupSection } from './InvestmentGroupSection';
import {
  ASSET_GROUP_ORDER,
  FIXED_INCOME_GROUP_KEYS,
  FUND_GROUP_KEYS,
  formatCurrency,
  getAssetGroupKey,
  getCurrentLocale,
  pickGroupValueOrigin,
  type AssetGroupKey,
  type HoldingRow,
} from './investmentTableHelpers';
import type { MaturityDrawerHolding } from '../MaturityConfirmDrawer';
import { formatTableDate } from '@/shared/utils/date';

const InvestmentHoldingDetailDrawer = dynamic(
  () => import('./InvestmentHoldingDetailDrawer').then((mod) => ({ default: mod.InvestmentHoldingDetailDrawer })),
  { loading: () => null },
);

const MaturityConfirmDrawer = dynamic(
  () => import('../MaturityConfirmDrawer').then((mod) => ({ default: mod.MaturityConfirmDrawer })),
  { loading: () => null },
);

type BankAccountRow = NonNullable<FinancialAccountsQuery['financialAccounts']>[number];

interface InvestmentTableProps {
  selectedAccountId: string | null;
  bankAccounts: BankAccountRow[];
  onRecordHolding?: () => void;
  searchQuery?: string;
}

const EMPTY_HOLDINGS: HoldingRow[] = [];

type EditDrawerKind = 'fixedIncome' | 'fund' | 'equity';

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

function formatQuantity(value: unknown, locale: string): string {
  if (typeof value !== 'number' || Number.isNaN(value)) return '—';
  return new Intl.NumberFormat(locale, { maximumFractionDigits: 8 }).format(value);
}

function computePrincipal(holding: HoldingRow): number | null {
  if (typeof holding.averageCost !== 'number' || typeof holding.quantity !== 'number') return null;
  return holding.averageCost * holding.quantity;
}

export function InvestmentTable({
  selectedAccountId,
  bankAccounts,
  onRecordHolding: _onRecordHolding,
  searchQuery,
}: InvestmentTableProps) {
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
  const [maturityConfirmOpen, setMaturityConfirmOpen] = React.useState(false);
  const [maturityConfirmHolding, setMaturityConfirmHolding] = React.useState<MaturityDrawerHolding | null>(null);

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

  const closeMaturityConfirmDrawer = () => {
    setMaturityConfirmOpen(false);
    setMaturityConfirmHolding(null);
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

  const handleOpenMaturityConfirmDrawer = React.useCallback((holding: HoldingRow) => {
    if (!holding.asset?.id) return;

    setMaturityConfirmHolding({
      id: holding.id,
      maturityDate: holding.maturityDate ? String(holding.maturityDate) : null,
      grossValue: holding.grossValue ?? null,
      netValue: holding.netValue ?? null,
      incomeTax: 'incomeTax' in holding ? ((holding as unknown as { incomeTax?: number | null }).incomeTax ?? null) : null,
      iof: 'iof' in holding ? ((holding as unknown as { iof?: number | null }).iof ?? null) : null,
      issuerName:
        (holding.asset as unknown as { issuerName?: string | null; issuer?: { name?: string | null } | null }).issuerName ??
        (holding.asset as unknown as { issuerName?: string | null; issuer?: { name?: string | null } | null }).issuer?.name ??
        null,
      issuerLogoUrl:
        (holding.asset as unknown as { logoAsset?: { publicUrl?: string | null } | null }).logoAsset?.publicUrl ?? null,
      asset: { id: holding.asset.id, name: holding.asset.name },
    });
    setMaturityConfirmOpen(true);
  }, []);

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
            const assetColSx = isFixedIncomeGroup ? { flex: '1 1 0', minWidth: 240, pr: 1 } : { width: colWidth, flexShrink: 0 };
            const maturityColSx = isFixedIncomeGroup ? { width: fixedIncomeMaturityColWidth, flexShrink: 0 } : { width: colWidth };
            const grossValueColSx = isFixedIncomeGroup ? { width: fixedIncomeGrossValueColWidth, flexShrink: 0 } : { width: colWidth };
            const gainColSx = isFixedIncomeGroup ? { width: fixedIncomeGainColWidth, flexShrink: 0 } : { width: colWidth };
            const indexerColSx = isFixedIncomeGroup ? { width: fixedIncomeIndexerColWidth, flexShrink: 0 } : { width: colWidth };

            return (
              <InvestmentGroupSection
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
                headerCells={[
                  {
                    key: 'asset',
                    sx: assetColSx,
                    content: <Label>{t('investments.table.columns.asset')}</Label>,
                  },
                  ...(isFixedIncomeGroup
                    ? [
                        {
                          key: 'indexer',
                          sx: indexerColSx,
                          content: <Label>{t('investments.table.columns.indexer')}</Label>,
                        },
                      ]
                    : [
                        {
                          key: 'type',
                          sx: { width: colWidth },
                          content: <Label>{t('investments.table.columns.type')}</Label>,
                        },
                      ]),
                  ...(isFundGroup
                    ? [
                        {
                          key: 'gain',
                          align: 'right' as const,
                          sx: { width: colWidth },
                          content: <Label>{t('investments.table.columns.gain')}</Label>,
                        },
                        {
                          key: 'grossValue',
                          align: 'right' as const,
                          sx: { width: colWidth },
                          content: <Label>{t('investments.table.columns.grossValue')}</Label>,
                        },
                      ]
                    : [
                        ...(isVariableIncomeGroup
                          ? [
                              {
                                key: 'quantity',
                                sx: maturityColSx,
                                content: <Label>{t('investments.table.columns.quantity')}</Label>,
                              },
                              {
                                key: 'averagePrice',
                                align: 'right' as const,
                                sx: { width: colWidth },
                                content: <Label>{t('investments.table.columns.averagePrice')}</Label>,
                              },
                              {
                                key: 'priceD1',
                                align: 'right' as const,
                                sx: { width: colWidth },
                                content: <Label>{t('investments.table.columns.priceD1')}</Label>,
                              },
                            ]
                          : [
                              {
                                key: 'maturityDate',
                                sx: maturityColSx,
                                content: <Label>{t('investments.table.columns.maturityDate')}</Label>,
                              },
                            ]),
                        {
                          key: 'gain',
                          align: 'right' as const,
                          sx: gainColSx,
                          content: <Label>{t('investments.table.columns.gain')}</Label>,
                        },
                        {
                          key: 'grossValue',
                          align: 'right' as const,
                          sx: grossValueColSx,
                          content: <Label>{t('investments.table.columns.grossValue')}</Label>,
                        },
                      ])]
                }
                actionColWidth={actionColWidth}
                tableRadius={tableRadius}
              >
                {group.holdings.map((holding) => {
                  const assetType = holding.asset?.assetType ?? null;
                  const assetTypeLabel =
                    assetType != null ? (t(`investments.assetTypes.${assetType}` as string) as string) : '—';
                  const issuerLabel = holding.asset?.issuerName ?? '—';
                  const maturityLabel = holding.maturityDate ? formatTableDate(String(holding.maturityDate), locale) : '—';

                  const principal = computePrincipal(holding);
                  const gross = typeof holding.grossValue === 'number' ? holding.grossValue : null;
                  const gain = gross != null && principal != null ? gross - principal : null;
                  const gainPercent = gross != null && principal != null && principal > 0 ? (gross - principal) / principal : null;

                  const isExpiredPending = Boolean((holding as any).isExpiredPending);
                  const isExpired = Boolean((holding as any).isExpired);
                  const showMaturityBadge = isFixedIncomeGroup && (isExpiredPending || isExpired);
                  const maturityBadgeLabel = isExpiredPending
                    ? t('investments.table.maturityPendingBadge')
                    : t('investments.table.expiredBadge');
                  const indexerLabel =
                    holding.indexer != null
                      ? (t(`investments.recordHolding.indexers.${holding.indexer}` as string) as string)
                      : null;
                  const indexerRateLabel =
                    typeof holding.indexerPercentage === 'number'
                      ? new Intl.NumberFormat(locale, { style: 'percent', maximumFractionDigits: 2 }).format(holding.indexerPercentage)
                      : typeof holding.fixedAnnualRate === 'number'
                        ? new Intl.NumberFormat(locale, { style: 'percent', maximumFractionDigits: 2 }).format(holding.fixedAnnualRate)
                        : null;
                  const indexerDisplay = [indexerLabel, indexerRateLabel].filter(Boolean).join(' ');
                  const assetLogoUrl =
                    (holding.asset as unknown as { logoAsset?: { publicUrl?: string | null } | null })?.logoAsset?.publicUrl ??
                    (
                      holding as AllInvestmentHoldingsQuery['allInvestmentHoldings'][number]
                    )?.financialAccount?.issuer?.logoAsset?.publicUrl ??
                    undefined;
                  const assetName = holding.asset?.name ?? '—';
                  const secondaryRowSx = { mt: 0.25, minHeight: 16, display: 'flex', alignItems: 'center' } as const;

                  const handleRowClick = () => handleOpenDetail(holding);

                  return (
                    <Box
                      key={holding.id}
                      role="row"
                      onClick={handleRowClick}
                      sx={{
                        display: 'flex',
                        alignItems: 'center',
                        px: 2,
                        py: 1.5,
                        bgcolor: 'background.paper',
                        borderTop: `1px solid ${divider}`,
                        cursor: 'pointer',
                        '&:hover': { bgcolor: alpha(theme.palette.primary.main, 0.035) },
                      }}
                    >
                      <Box sx={{ ...assetColSx, minWidth: 0 }}>
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.25, minWidth: 0 }}>
                          <Logo
                            src={assetLogoUrl}
                            alt={assetName}
                            size="lg"
                            sx={{ flexShrink: 0 }}
                          >
                            {assetName.charAt(0).toUpperCase()}
                          </Logo>
                          <Box sx={{ minWidth: 0 }}>
                            <Typography variant="body2" noWrap>
                              {assetName}
                            </Typography>
                            <Typography variant="caption" color="text.secondary" noWrap sx={{ lineHeight: 1.2 }}>
                              {issuerLabel}
                            </Typography>
                          </Box>
                        </Box>
                      </Box>

                      {isFixedIncomeGroup ? (
                        <Box sx={{ ...indexerColSx, minWidth: 0 }}>
                          <Typography variant="body2" noWrap>
                            {indexerDisplay || '—'}
                          </Typography>
                          <Box sx={secondaryRowSx}>
                            {null}
                          </Box>
                        </Box>
                      ) : (
                        <Box sx={{ width: colWidth, minWidth: 0 }}>
                          <Typography variant="body2" noWrap>
                            {assetTypeLabel}
                          </Typography>
                        </Box>
                      )}

                      {isFundGroup ? null : isVariableIncomeGroup ? (
                        <>
                          <Box sx={{ ...maturityColSx, minWidth: 0 }}>
                            <Typography variant="body2" noWrap>
                              {formatQuantity(holding.quantity, locale)}
                            </Typography>
                            <Box sx={secondaryRowSx} />
                          </Box>
                          <Box sx={{ width: colWidth, minWidth: 0, textAlign: 'right' }}>
                            <Typography variant="body2" noWrap>
                              {holding.averageCost != null && holding.asset?.currencyCode
                                ? formatCurrency(holding.averageCost, holding.asset.currencyCode, locale)
                                : '—'}
                            </Typography>
                            <Box sx={{ ...secondaryRowSx, justifyContent: 'flex-end' }} />
                          </Box>
                          <Box sx={{ width: colWidth, minWidth: 0, textAlign: 'right' }}>
                            <Typography variant="body2" noWrap>
                              —
                            </Typography>
                            <Box sx={{ ...secondaryRowSx, justifyContent: 'flex-end' }} />
                          </Box>
                        </>
                      ) : (
                        <Box sx={{ ...maturityColSx, minWidth: 0 }}>
                          <Typography variant="body2" noWrap>
                            {maturityLabel}
                          </Typography>
                          <Box sx={secondaryRowSx}>
                            {showMaturityBadge ? (
                              <Box
                                onClick={(e) => {
                                  e.stopPropagation();
                                  handleOpenMaturityConfirmDrawer(holding);
                                }}
                                sx={{ display: 'inline-flex' }}
                              >
                                <Label>{maturityBadgeLabel}</Label>
                              </Box>
                            ) : null}
                          </Box>
                        </Box>
                      )}

                      <Box sx={{ ...gainColSx, minWidth: 0, textAlign: 'right' }}>
                        <Typography variant="body2" noWrap>
                          {gain != null && holding.asset?.currencyCode ? formatCurrency(gain, holding.asset.currencyCode, locale) : '—'}
                        </Typography>
                        <Box sx={{ ...secondaryRowSx, justifyContent: 'flex-end' }}>
                          {gainPercent != null ? (
                            <PercentageGain
                              value={gainPercent}
                              locale={locale}
                              textSx={{ lineHeight: 1.2 }}
                            />
                          ) : null}
                        </Box>
                      </Box>

                      <Box sx={{ ...grossValueColSx, minWidth: 0, textAlign: 'right' }}>
                        <Typography variant="body2" noWrap>
                          {gross != null && holding.asset?.currencyCode ? formatCurrency(gross, holding.asset.currencyCode, locale) : '—'}
                        </Typography>
                        <Box sx={{ ...secondaryRowSx, justifyContent: 'flex-end' }} />
                      </Box>

                      <Box sx={{ width: actionColWidth, flexShrink: 0, display: 'flex', justifyContent: 'flex-end' }}>
                        <IconButton
                          size="small"
                          aria-label={t('investments.table.rowActions.ariaLabel')}
                          onClick={(e) => {
                            e.stopPropagation();
                            setActionMenuAnchorEl(e.currentTarget);
                            setActionMenuHolding(holding);
                          }}
                        >
                          <MoreVertIcon fontSize="small" />
                        </IconButton>
                      </Box>
                    </Box>
                  );
                })}
              </InvestmentGroupSection>
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
          disabled={deleting}
          onClick={() => {
            const holding = actionMenuHolding;
            closeActionMenu();
            if (!holding) return;
            handleOpenDetail(holding);
          }}
        >
          {t('investments.table.rowActions.viewDetails')}
        </MenuItem>
        <MenuItem
          disabled={deleting}
          onClick={async () => {
            const holding = actionMenuHolding;
            closeActionMenu();
            if (!holding) return;
            await handleOpenEdit(holding);
          }}
        >
          {t('investments.table.rowActions.edit')}
        </MenuItem>
        <MenuItem
          disabled={deleting}
          onClick={() => {
            const holding = actionMenuHolding;
            closeActionMenu();
            if (!holding) return;
            setHoldingToRemove(holding);
          }}
          sx={isActionMenuFixedIncome ? { borderTop: (theme) => `1px solid ${alpha(theme.palette.divider, 0.7)}` } : undefined}
        >
          {t('investments.table.rowActions.remove')}
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

      <MaturityConfirmDrawer
        open={maturityConfirmOpen}
        onClose={closeMaturityConfirmDrawer}
        holding={maturityConfirmHolding}
        bankAccounts={bankAccounts}
        onSuccess={async () => {
          if (selectedAccountId) await accountHoldingsQuery.refetch();
          else await consolidatedHoldingsQuery.refetch();
        }}
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
