'use client';

import React from 'react';
import dynamic from 'next/dynamic';
import { NetworkStatus } from '@apollo/client/core';
import { useApolloClient, useMutation, useQuery } from '@apollo/client/react';
import { Box, Divider, LinearProgress, Paper, TableContainer, Typography } from '@mui/material';
import { useTranslation } from '@/shared/i18n';
import { ConfirmationDialog } from '@/shared/components/ui/feedback';
import { personalFinanceTranslations } from '../../../i18n';
import {
  INVESTMENT_CASH_BALANCE,
  INVESTMENT_GRID_DATA,
  INVESTMENT_HOLDINGS,
  INVESTMENT_PORTFOLIO_SUMMARY,
} from '@/lib/graphql/operations/banking/queries';
import { DELETE_INVESTMENT_HOLDING } from '@/lib/graphql/operations/banking/mutations';
import {
  InvestmentCashBalanceQuery,
  InvestmentCashBalanceQueryVariables,
  InvestmentHoldingsQuery,
  InvestmentHoldingsQueryVariables,
} from '@/lib/graphql/operations/banking/queries.generated';
import { InvestmentTransactionDirection } from '@/lib/graphql/types/__generated__/graphql';
import type { MaturityDrawerHolding } from '../../MaturityConfirmDrawer';
import type { AmortizationDrawerHolding } from '../../RecordAmortizationDrawer';
import type { StockLendingDrawerHolding } from '../../RecordStockLendingDrawer';
import type { TransferCustodyDrawerHolding } from '../../TransferCustodyDrawer';
import type { WithholdingTaxDrawerHolding } from '../../WithholdingTaxDrawer';
import type { InvestmentHoldingDetailKind } from '../InvestmentHoldingDetailDrawer';
import { CashMovementsSection } from './CashMovementsSection';
import { HoldingsGroupsSection } from './HoldingsGroupsSection';
import { InvestmentEventSelectorDrawer } from '../InvestmentEventSelectorDrawer';
import { RecordEquityHoldingDrawer } from '../RecordEquityHoldingDrawer';
import { RecordFundHoldingDrawer } from '../RecordFundHoldingDrawer';
import { InvestmentHoldingActionsMenu } from './InvestmentHoldingActionsMenu';
import { RecordCorporateEventDrawer, type CorporateEventType } from '../corporate-events/RecordCorporateEventDrawer';
import type { IssuerSelectionAccount } from '../IssuerSelection';
import { useInvestmentCardExpansion } from './useInvestmentCardExpansion';
import {
  ASSET_GROUP_ORDER,
  AMORTIZABLE_ASSET_TYPES,
  LENDABLE_ASSET_TYPES,
  formatCurrency,
  getAssetGroupKey,
  getCurrentLocale,
  getHoldingKind,
  pickGroupValueOrigin,
  type HoldingKind,
  type AssetGroupKey,
  type HoldingRow,
  type InvestmentHoldingGroup,
} from './investmentTableHelpers';

const InvestmentHoldingDetailDrawer = dynamic(
  () => import('../InvestmentHoldingDetailDrawer').then((mod) => ({ default: mod.InvestmentHoldingDetailDrawer })),
  { loading: () => null },
);

const MaturityConfirmDrawer = dynamic(
  () => import('../../MaturityConfirmDrawer').then((mod) => ({ default: mod.MaturityConfirmDrawer })),
  { loading: () => null },
);

const WithholdingTaxDrawer = dynamic(
  () => import('../../WithholdingTaxDrawer').then((mod) => ({ default: mod.WithholdingTaxDrawer })),
  { loading: () => null },
);

const RecordAmortizationDrawer = dynamic(
  () => import('../../RecordAmortizationDrawer').then((mod) => ({ default: mod.RecordAmortizationDrawer })),
  { loading: () => null },
);

const RecordStockLendingDrawer = dynamic(
  () => import('../../RecordStockLendingDrawer').then((mod) => ({ default: mod.RecordStockLendingDrawer })),
  { loading: () => null },
);

const TransferCustodyDrawer = dynamic(
  () => import('../../TransferCustodyDrawer').then((mod) => ({ default: mod.TransferCustodyDrawer })),
  { loading: () => null },
);

interface InvestmentTableProps {
  selectedAccountId: string | null;
  investmentAccounts?: IssuerSelectionAccount[];
  onRecordHolding?: () => void;
  searchQuery?: string;
  onSelectSearchAsset?: (assetName: string) => void;
  visibleGroupKeys?: AssetGroupKey[];
  visibleAssetTypes?: string[];
  includeCashCard?: boolean;
  hideGroupHeader?: boolean;
  /** When holdings query is fetching/refetching (for parent UI, e.g. tab bar progress). */
  onHoldingsFetchingChange?: (fetching: boolean) => void;
}

interface ActionMenuState {
  anchorEl: HTMLElement | null;
  holding: HoldingRow | null;
}

interface DetailDrawerState {
  holdingId: string | null;
  kind: InvestmentHoldingDetailKind | null;
}

interface EventSelectorDrawerState {
  holding: HoldingRow | null;
  kind: HoldingKind;
  financialAccountId: string | null;
}

interface QuickTransactionState {
  holding: HoldingRow | null;
  direction: 'buy' | 'sell' | null;
}

interface CorporateEventState {
  holding: HoldingRow | null;
  eventType: CorporateEventType | null;
}

const EMPTY_HOLDINGS: HoldingRow[] = [];

export function InvestmentTable({
  selectedAccountId,
  investmentAccounts = [],
  searchQuery,
  onSelectSearchAsset,
  visibleGroupKeys,
  visibleAssetTypes,
  includeCashCard = true,
  hideGroupHeader = false,
  onHoldingsFetchingChange,
}: InvestmentTableProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const locale = getCurrentLocale();
  const apolloClient = useApolloClient();
  const [actionMenu, setActionMenu] = React.useState<ActionMenuState>({ anchorEl: null, holding: null });
  const [holdingToRemove, setHoldingToRemove] = React.useState<HoldingRow | null>(null);
  const [detailDrawer, setDetailDrawer] = React.useState<DetailDrawerState>({ holdingId: null, kind: null });
  const [eventSelectorDrawer, setEventSelectorDrawer] = React.useState<EventSelectorDrawerState>({
    holding: null,
    kind: 'equity',
    financialAccountId: null,
  });
  const [maturityConfirmOpen, setMaturityConfirmOpen] = React.useState(false);
  const [maturityConfirmHolding, setMaturityConfirmHolding] = React.useState<MaturityDrawerHolding | null>(null);
  const [amortizationOpen, setAmortizationOpen] = React.useState(false);
  const [amortizationHolding, setAmortizationHolding] = React.useState<AmortizationDrawerHolding | null>(null);
  const [stockLendingOpen, setStockLendingOpen] = React.useState(false);
  const [stockLendingHolding, setStockLendingHolding] = React.useState<StockLendingDrawerHolding | null>(null);
  const [transferCustodyOpen, setTransferCustodyOpen] = React.useState(false);
  const [transferCustodyHolding, setTransferCustodyHolding] = React.useState<TransferCustodyDrawerHolding | null>(null);
  const [withholdingTaxOpen, setWithholdingTaxOpen] = React.useState(false);
  const [withholdingTaxHolding, setWithholdingTaxHolding] = React.useState<WithholdingTaxDrawerHolding | null>(null);
  const [quickTransaction, setQuickTransaction] = React.useState<QuickTransactionState>({ holding: null, direction: null });
  const [isQuickEquityOpen, setIsQuickEquityOpen] = React.useState(false);
  const [isQuickFundOpen, setIsQuickFundOpen] = React.useState(false);
  const [corporateEventState, setCorporateEventState] = React.useState<CorporateEventState>({ holding: null, eventType: null });

  const [deleteInvestmentHolding, { loading: deletingHolding }] = useMutation(DELETE_INVESTMENT_HOLDING);

  const cashQuery = useQuery<InvestmentCashBalanceQuery, InvestmentCashBalanceQueryVariables>(INVESTMENT_CASH_BALANCE, {
    variables: { financialAccountId: selectedAccountId ?? undefined },
    errorPolicy: 'all',
    notifyOnNetworkStatusChange: true,
  });

  const holdingsQuery = useQuery<InvestmentHoldingsQuery, InvestmentHoldingsQueryVariables>(INVESTMENT_HOLDINGS, {
    variables: { financialAccountId: selectedAccountId ?? null },
    errorPolicy: 'all',
    notifyOnNetworkStatusChange: true,
  });

  const refreshInvestmentData = React.useCallback(async () => {
    await Promise.all([
      holdingsQuery.refetch(),
      cashQuery.refetch(),
      apolloClient.refetchQueries({ include: [INVESTMENT_PORTFOLIO_SUMMARY, INVESTMENT_GRID_DATA] }),
    ]);
  }, [apolloClient, cashQuery, holdingsQuery]);

  const displayCurrency = React.useMemo(() => {
    if (selectedAccountId) {
      return investmentAccounts.find((account) => account.id === selectedAccountId)?.currencyCode ?? 'BRL';
    }

    const codes = [...new Set(investmentAccounts.map((account) => account.currencyCode).filter(Boolean))];
    return codes.length === 1 ? codes[0]! : 'BRL';
  }, [investmentAccounts, selectedAccountId]);

  const resolveFinancialAccountId = React.useCallback(
    (holding: HoldingRow) => selectedAccountId ?? holding.financialAccount?.id ?? null,
    [selectedAccountId],
  );

  const closeActionMenu = React.useCallback(() => {
    setActionMenu({ anchorEl: null, holding: null });
  }, []);

  const handleOpenActionMenu = React.useCallback((anchorEl: HTMLElement, holding: HoldingRow) => {
    setActionMenu({ anchorEl, holding });
  }, []);

  const closeDetailDrawer = React.useCallback(() => {
    setDetailDrawer({ holdingId: null, kind: null });
  }, []);

  const handleOpenDetail = React.useCallback((holding: HoldingRow) => {
    setDetailDrawer({
      holdingId: holding.id,
      kind: getHoldingKind(holding.asset?.assetType),
    });
  }, []);

  const closeEventSelectorDrawer = React.useCallback(() => {
    setEventSelectorDrawer({
      holding: null,
      kind: 'equity',
      financialAccountId: null,
    });
  }, []);

  const handleOpenEdit = React.useCallback(
    async (holding: HoldingRow) => {
      setEventSelectorDrawer({
        holding,
        kind: getHoldingKind(holding.asset?.assetType),
        financialAccountId: resolveFinancialAccountId(holding),
      });
    },
    [resolveFinancialAccountId],
  );

  const closeMaturityConfirmDrawer = React.useCallback(() => {
    setMaturityConfirmOpen(false);
    setMaturityConfirmHolding(null);
  }, []);

  const closeWithholdingTaxDrawer = React.useCallback(() => {
    setWithholdingTaxOpen(false);
    setWithholdingTaxHolding(null);
  }, []);

  const closeAmortizationDrawer = React.useCallback(() => {
    setAmortizationOpen(false);
    setAmortizationHolding(null);
  }, []);

  const closeStockLendingDrawer = React.useCallback(() => {
    setStockLendingOpen(false);
    setStockLendingHolding(null);
  }, []);

  const closeTransferCustodyDrawer = React.useCallback(() => {
    setTransferCustodyOpen(false);
    setTransferCustodyHolding(null);
  }, []);

  const handleOpenMaturityConfirmDrawer = React.useCallback((holding: HoldingRow) => {
    if (!holding.asset?.id) return;

    setMaturityConfirmHolding({
      id: holding.id,
      financialAccountId: holding.financialAccountId,
      maturityDate: holding.maturityDate ? String(holding.maturityDate) : null,
      grossValue: holding.grossValue ?? null,
      netValue: holding.netValue ?? null,
      incomeTax: holding.incomeTax ?? null,
      iof: holding.iof ?? null,
      issuerName: holding.asset.issuerName ?? null,
      issuerLogoUrl: holding.asset.logoAsset?.publicUrl ?? holding.financialAccount?.issuer?.logoAsset?.publicUrl ?? null,
      asset: { id: holding.asset.id, name: holding.asset.name, assetType: holding.asset.assetType ?? null },
    });
    setMaturityConfirmOpen(true);
  }, []);

  const handleOpenWithholdingTaxDrawer = React.useCallback((holding: HoldingRow) => {
    if (!holding.asset?.id || holding.asset.assetType !== 'MUTUAL_FUND') return;

    const currentNavPerQuota =
      typeof holding.grossValue === 'number' && holding.quantity > 0
        ? holding.grossValue / holding.quantity
        : undefined;

    setWithholdingTaxHolding({
      id: holding.id,
      financialAccountId: holding.financialAccountId,
      quantity: holding.quantity,
      averageCost: holding.averageCost,
      currentNavPerQuota,
      asset: {
        id: holding.asset.id,
        name: holding.asset.name,
        assetType: holding.asset.assetType ?? null,
      },
    });
    setWithholdingTaxOpen(true);
  }, []);

  const handleOpenAmortizationDrawer = React.useCallback((holding: HoldingRow) => {
    if (!holding.asset?.id) return;
    if (!AMORTIZABLE_ASSET_TYPES.has(holding.asset.assetType ?? '')) return;

    setAmortizationHolding({
      id: holding.id,
      financialAccountId: holding.financialAccountId,
      grossValue: holding.grossValue ?? null,
      issuerName: holding.asset.issuerName ?? null,
      asset: {
        id: holding.asset.id,
        name: holding.asset.name,
        assetType: holding.asset.assetType ?? null,
        isTaxExempt: holding.isTaxExempt,
      },
    });
    setAmortizationOpen(true);
  }, []);

  const handleOpenStockLendingDrawer = React.useCallback((holding: HoldingRow) => {
    if (!holding.asset?.id) return;
    if (!LENDABLE_ASSET_TYPES.has(holding.asset.assetType ?? '')) return;

    setStockLendingHolding({
      id: holding.id,
      financialAccountId: holding.financialAccountId,
      issuerName: holding.asset.issuerName ?? null,
      asset: {
        id: holding.asset.id,
        name: holding.asset.name,
        assetType: holding.asset.assetType ?? null,
      },
    });
    setStockLendingOpen(true);
  }, []);

  const handleOpenTransferCustodyDrawer = React.useCallback((holding: HoldingRow) => {
    if (!holding.asset?.id) return;
    if (holding.quantity <= 0) return;

    setTransferCustodyHolding({
      id: holding.id,
      financialAccountId: holding.financialAccountId,
      quantity: holding.quantity,
      averageCost: holding.averageCost,
      issuerName: holding.asset.issuerName ?? null,
      asset: {
        id: holding.asset.id,
        name: holding.asset.name,
        assetType: holding.asset.assetType ?? null,
      },
    });
    setTransferCustodyOpen(true);
  }, []);

  const closeQuickTransaction = React.useCallback(() => {
    setQuickTransaction({ holding: null, direction: null });
    setIsQuickEquityOpen(false);
    setIsQuickFundOpen(false);
  }, []);

  const handleQuickBuy = React.useCallback((holding: HoldingRow) => {
    const groupKey = getAssetGroupKey(holding.asset?.assetType);
    setQuickTransaction({ holding, direction: 'buy' });
    if (groupKey === 'variableIncome') {
      setIsQuickEquityOpen(true);
      return;
    }
    if (groupKey === 'mutualFunds' || groupKey === 'pension') {
      setIsQuickFundOpen(true);
    }
  }, []);

  const handleQuickSell = React.useCallback((holding: HoldingRow) => {
    const groupKey = getAssetGroupKey(holding.asset?.assetType);
    if (groupKey === 'fixedIncome') {
      handleOpenMaturityConfirmDrawer(holding);
      return;
    }
    setQuickTransaction({ holding, direction: 'sell' });
    if (groupKey === 'variableIncome') {
      setIsQuickEquityOpen(true);
      return;
    }
    if (groupKey === 'mutualFunds' || groupKey === 'pension') {
      setIsQuickFundOpen(true);
      return;
    }
  }, [handleOpenMaturityConfirmDrawer]);

  const handleRecordCorporateEvent = React.useCallback((holding: HoldingRow, eventType: CorporateEventType) => {
    setCorporateEventState({ holding, eventType });
  }, [setCorporateEventState]);

  const handleConfirmRemove = async () => {
    if (!holdingToRemove) return;

    try {
      await deleteInvestmentHolding({ variables: { id: holdingToRemove.id } });
      setHoldingToRemove(null);
      await refreshInvestmentData();
    } catch {
      setHoldingToRemove(null);
    }
  };

  const holdings = holdingsQuery.data?.investmentHoldings ?? EMPTY_HOLDINGS;

  const normalizedSearchQuery = React.useMemo(() => (searchQuery ?? '').trim().toLowerCase(), [searchQuery]);
  const hasSearchQuery = Boolean(normalizedSearchQuery);
  const cashMatchesSearch = React.useMemo(() => {
    if (!hasSearchQuery) return true;

    const cashSearchText = [
      t('investments.cash.cardTitle'),
      t('investments.summary.cashBalance'),
      'cash',
    ]
      .join(' ')
      .toLowerCase();

    return cashSearchText.includes(normalizedSearchQuery);
  }, [hasSearchQuery, normalizedSearchQuery, t]);

  const displayedHoldings = React.useMemo(() => {
    const scopedByAssetType =
      visibleAssetTypes && visibleAssetTypes.length > 0
        ? holdings.filter((holding) => {
            const assetType = holding.asset?.assetType;
            return assetType ? visibleAssetTypes.includes(assetType) : false;
          })
        : holdings;

    if (!hasSearchQuery) return scopedByAssetType;

    return scopedByAssetType.filter((holding) => {
      const haystack = [
        holding.asset?.name ?? '',
        holding.asset?.ticker ?? '',
        holding.asset?.issuerName ?? '',
        holding.asset?.assetType ?? '',
        holding.financialAccount?.name ?? '',
      ]
        .join(' ')
        .toLowerCase();

      return haystack.includes(normalizedSearchQuery);
    });
  }, [hasSearchQuery, holdings, normalizedSearchQuery, visibleAssetTypes]);

  const scopedHoldings = React.useMemo(() => {
    if (visibleGroupKeys === undefined) return displayedHoldings;
    if (visibleGroupKeys.length === 0) return EMPTY_HOLDINGS;
    return displayedHoldings.filter((holding) => visibleGroupKeys.includes(getAssetGroupKey(holding.asset?.assetType)));
  }, [displayedHoldings, visibleGroupKeys]);

  const detailHolding = React.useMemo(
    () => holdings.find((holding) => holding.id === detailDrawer.holdingId) ?? null,
    [detailDrawer.holdingId, holdings],
  );

  const detailHoldingIssuerLogoUrl = React.useMemo(
    () => detailHolding?.financialAccount?.issuer?.logoAsset?.publicUrl ?? null,
    [detailHolding],
  );

  const loading = holdingsQuery.loading;
  const error = holdingsQuery.error;
  const holdingsNetworkStatus = holdingsQuery.networkStatus;
  const holdingsFetching =
    !error &&
    (loading ||
      holdingsNetworkStatus === NetworkStatus.refetch ||
      holdingsNetworkStatus === NetworkStatus.setVariables);
  const cashBalance = cashQuery.data?.investmentCashBalance;
  const cashFormatted =
    cashBalance != null && Number.isFinite(cashBalance) ? formatCurrency(cashBalance, displayCurrency, locale) : null;
  const showCashCard =
    includeCashCard &&
    !cashQuery.loading &&
    !cashQuery.error &&
    cashBalance != null &&
    Number.isFinite(cashBalance) &&
    cashBalance !== 0 &&
    cashFormatted != null &&
    cashMatchesSearch;

  const groupedHoldings = React.useMemo(() => {
    const base = ASSET_GROUP_ORDER.reduce((accumulator, groupKey) => {
      accumulator[groupKey] = [];
      return accumulator;
    }, {} as Record<AssetGroupKey, HoldingRow[]>);

    scopedHoldings.forEach((holding) => {
      const groupKey = getAssetGroupKey(holding.asset?.assetType);
      base[groupKey].push(holding);
    });

    return base;
  }, [scopedHoldings]);

  const { groups } = React.useMemo(() => {
    let holdingsGrossTotal = 0;
    displayedHoldings.forEach((holding) => {
      if (typeof holding.grossValue === 'number') holdingsGrossTotal += holding.grossValue;
    });

    const portfolioDenominator =
      showCashCard && cashBalance != null && Number.isFinite(cashBalance)
        ? holdingsGrossTotal + cashBalance
        : holdingsGrossTotal;

    const computedGroups: InvestmentHoldingGroup[] = ASSET_GROUP_ORDER.map((groupKey) => {
      const groupHoldings = groupedHoldings[groupKey] ?? [];
      const currencyCodes = new Set(groupHoldings.map((holding) => holding.asset?.currencyCode).filter(Boolean));

      let totalGrossValue = 0;
      let totalPrincipal = 0;
      let hasGrossValue = false;
      let hasPrincipal = false;

      groupHoldings.forEach((holding) => {
        if (typeof holding.grossValue === 'number') {
          totalGrossValue += holding.grossValue;
          hasGrossValue = true;
        }

        if (typeof holding.averageCost === 'number' && typeof holding.quantity === 'number') {
          totalPrincipal += holding.averageCost * holding.quantity;
          hasPrincipal = true;
        }
      });

      const currencyCode = currencyCodes.size === 1 ? Array.from(currencyCodes)[0] : null;
      const total = hasGrossValue && currencyCode ? formatCurrency(totalGrossValue, currencyCode, locale) : '—';
      const appliedTotal = hasPrincipal && currencyCode ? formatCurrency(totalPrincipal, currencyCode, locale) : '—';
      const allocationPercent = portfolioDenominator > 0 && hasGrossValue ? totalGrossValue / portfolioDenominator : null;

      return {
        key: groupKey,
        title: t(`investments.table.groups.${groupKey}`),
        count: groupHoldings.length,
        total,
        appliedTotal,
        valueOriginKey: pickGroupValueOrigin(groupHoldings),
        gainPercent: totalPrincipal > 0 && hasGrossValue ? (totalGrossValue - totalPrincipal) / totalPrincipal : null,
        allocationPercent,
        summary: t('investments.table.groupSummary', {
          count: groupHoldings.length,
          total,
        }),
        holdings: groupHoldings,
      };
    })
      .filter((group) => group.count > 0)
      .filter((group) => (visibleGroupKeys === undefined ? true : visibleGroupKeys.includes(group.key)));

    return {
      groups: computedGroups,
    };
  }, [cashBalance, displayedHoldings, groupedHoldings, locale, showCashCard, t, visibleGroupKeys]);

  const { expandedGroups, toggleGroup } = useInvestmentCardExpansion({
    selectedAccountId,
    groups,
    hasSearchQuery,
    loading,
    hasError: Boolean(error),
  });

  const emptyStateMessage = hasSearchQuery ? t('investments.table.noMatches') : t('investments.table.emptyState');
  const showHoldingsList = !error && groups.length > 0;
  const showHoldingsProgress = holdingsFetching;

  React.useEffect(() => {
    onHoldingsFetchingChange?.(holdingsFetching);
  }, [holdingsFetching, onHoldingsFetchingChange]);

  const actionHoldingGroupKey = actionMenu.holding?.asset?.assetType ? getAssetGroupKey(actionMenu.holding.asset.assetType) : null;
  const actionBuyLabel =
    actionHoldingGroupKey === 'variableIncome'
      ? t('investments.transactions.buy')
      : actionHoldingGroupKey === 'others'
        ? t('investments.transactions.buy')
      : actionHoldingGroupKey === 'mutualFunds' || actionHoldingGroupKey === 'pension'
        ? t('investments.transactions.apply')
        : actionHoldingGroupKey === 'fixedIncome'
          ? t('investments.transactions.apply')
        : undefined;
  const actionSellLabel =
    actionHoldingGroupKey === 'variableIncome'
      ? t('investments.transactions.sell')
      : actionHoldingGroupKey === 'others'
        ? t('investments.transactions.sell')
        : actionHoldingGroupKey === 'mutualFunds' || actionHoldingGroupKey === 'pension' || actionHoldingGroupKey === 'fixedIncome'
          ? t('investments.transactions.redeem')
          : undefined;
  const quickFinancialAccountId = quickTransaction.holding ? resolveFinancialAccountId(quickTransaction.holding) : null;

  const cashSection = React.useMemo(() => {
    if (!showCashCard) return null;

    return (
      <CashMovementsSection
        financialAccountId={selectedAccountId}
        investmentAccounts={investmentAccounts}
        onSelectRelatedAsset={onSelectSearchAsset}
        bottomSpacing={showHoldingsList ? 2 : 0}
      />
    );
  }, [investmentAccounts, onSelectSearchAsset, selectedAccountId, showCashCard, showHoldingsList]);

  return (
    <TableContainer
      component={Paper}
      elevation={0}
      sx={{
        bgcolor: 'transparent',
        background: 'transparent !important',
        border: 'none',
        overflow: 'visible',
        position: 'relative',
      }}
      data-testid="investment-table"
    >
      {showCashCard && !showHoldingsList ? (
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0 }}>{cashSection}</Box>
      ) : null}

      {showHoldingsProgress && !showHoldingsList && !hideGroupHeader ? (
        <Box sx={{ position: 'relative', width: '100%' }}>
          <Divider />
          <Box
            aria-hidden
            sx={{
              position: 'absolute',
              left: 0,
              right: 0,
              bottom: 0,
              height: 4,
              pointerEvents: 'none',
              zIndex: 2,
            }}
          >
            <LinearProgress
              sx={{
                height: '100%',
                opacity: 1,
                transition: 'opacity 150ms linear',
              }}
            />
          </Box>
        </Box>
      ) : null}

      {!loading && error ? (
        <Box sx={{ py: 6, textAlign: 'center' }}>
          <Typography variant="body2" color="error.main">
            {t('investments.table.error')}
          </Typography>
        </Box>
      ) : null}

      {!loading && !error && !showHoldingsList && !showCashCard ? (
        <Box sx={{ py: 6, textAlign: 'center' }}>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            {emptyStateMessage}
          </Typography>
        </Box>
      ) : null}

      {showHoldingsList ? (
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0 }}>
          {showCashCard ? cashSection : null}
          <HoldingsGroupsSection
            groups={groups}
            expandedGroups={expandedGroups}
            hideGroupHeader={hideGroupHeader}
            selectedAccountId={selectedAccountId}
            locale={locale}
            onToggleGroup={toggleGroup}
            onOpenDetail={handleOpenDetail}
            onOpenMaturityConfirm={handleOpenMaturityConfirmDrawer}
            onOpenActions={handleOpenActionMenu}
            onBuy={handleQuickBuy}
            onSell={handleQuickSell}
          />
        </Box>
      ) : null}

      <InvestmentHoldingActionsMenu
        anchorEl={actionMenu.anchorEl}
        holding={actionMenu.holding}
        deleting={deletingHolding}
        onClose={closeActionMenu}
        onViewDetails={handleOpenDetail}
        onEdit={handleOpenEdit}
        onRemove={setHoldingToRemove}
        onBuy={() => {
          if (!actionMenu.holding) return;
          handleQuickBuy(actionMenu.holding);
        }}
        onSell={() => {
          if (!actionMenu.holding) return;
          handleQuickSell(actionMenu.holding);
        }}
        buyLabel={actionBuyLabel}
        sellLabel={actionSellLabel}
        showTransactionActions={Boolean(actionBuyLabel)}
        onRecordProvento={() => {
          if (!actionMenu.holding) return;
          handleRecordCorporateEvent(actionMenu.holding, 'PROVENTO');
        }}
        onRecordSplit={() => {
          if (!actionMenu.holding) return;
          handleRecordCorporateEvent(actionMenu.holding, 'SPLIT');
        }}
        onRecordBonus={() => {
          if (!actionMenu.holding) return;
          handleRecordCorporateEvent(actionMenu.holding, 'BONUS');
        }}
        onRecordSubscription={() => {
          if (!actionMenu.holding) return;
          handleRecordCorporateEvent(actionMenu.holding, 'SUBSCRIPTION');
        }}
        onRecordWithholdingTax={() => {
          if (!actionMenu.holding) return;
          handleOpenWithholdingTaxDrawer(actionMenu.holding);
        }}
        onRecordAmortization={() => {
          if (!actionMenu.holding) return;
          handleOpenAmortizationDrawer(actionMenu.holding);
        }}
        onRecordStockLending={() => {
          if (!actionMenu.holding) return;
          handleOpenStockLendingDrawer(actionMenu.holding);
        }}
        onTransferCustody={() => {
          if (!actionMenu.holding) return;
          handleOpenTransferCustodyDrawer(actionMenu.holding);
        }}
      />

      <ConfirmationDialog
        open={Boolean(holdingToRemove)}
        onClose={() => setHoldingToRemove(null)}
        onConfirm={handleConfirmRemove}
        title={t('investments.deleteConfirmation.title')}
        message={t('investments.deleteConfirmation.message')}
        cancelText={t('actions.cancel')}
        confirmText={t('actions.delete')}
        confirmColor="error"
        loading={deletingHolding}
      />

      <InvestmentHoldingDetailDrawer
        open={Boolean(detailDrawer.holdingId && detailDrawer.kind)}
        holdingId={detailDrawer.holdingId}
        kind={detailDrawer.kind}
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
        investmentAccounts={investmentAccounts}
        onSuccess={refreshInvestmentData}
      />

      <WithholdingTaxDrawer
        open={withholdingTaxOpen}
        onClose={closeWithholdingTaxDrawer}
        holding={withholdingTaxHolding}
        onSuccess={refreshInvestmentData}
      />

      <RecordAmortizationDrawer
        open={amortizationOpen}
        onClose={closeAmortizationDrawer}
        holding={amortizationHolding}
        financialAccounts={investmentAccounts.map((account) => ({
          id: account.id,
          name: account.name,
          institutionName: account.issuer?.name ?? account.issuerName ?? null,
        }))}
        onSuccess={refreshInvestmentData}
      />

      <RecordStockLendingDrawer
        open={stockLendingOpen}
        onClose={closeStockLendingDrawer}
        holding={stockLendingHolding}
        financialAccounts={investmentAccounts.map((account) => ({
          id: account.id,
          name: account.name,
          institutionName: account.issuer?.name ?? account.issuerName ?? null,
        }))}
        onSuccess={refreshInvestmentData}
      />

      <TransferCustodyDrawer
        open={transferCustodyOpen}
        onClose={closeTransferCustodyDrawer}
        holding={transferCustodyHolding}
        investmentAccounts={investmentAccounts.map((account) => ({
          id: account.id,
          name: account.name,
          institutionName: account.issuer?.name ?? account.issuerName ?? null,
          subtype: account.subtype,
        }))}
        onSuccess={refreshInvestmentData}
      />

      <RecordEquityHoldingDrawer
        open={isQuickEquityOpen}
        onClose={closeQuickTransaction}
        financialAccountId={quickFinancialAccountId}
        financialAccounts={investmentAccounts}
        prefillHolding={
          quickTransaction.holding?.asset
            ? {
                id: quickTransaction.holding.id,
                asset: {
                  id: quickTransaction.holding.asset.id,
                  ticker: quickTransaction.holding.asset.ticker ?? null,
                  name: quickTransaction.holding.asset.name,
                  assetType: quickTransaction.holding.asset.assetType,
                },
              }
            : null
        }
        initialDirection={
          quickTransaction.direction === 'sell' ? InvestmentTransactionDirection.Outflow : InvestmentTransactionDirection.Inflow
        }
        onRecorded={refreshInvestmentData}
      />

      <RecordFundHoldingDrawer
        open={isQuickFundOpen}
        onClose={closeQuickTransaction}
        financialAccountId={quickFinancialAccountId}
        financialAccounts={investmentAccounts}
        prefillHolding={
          quickTransaction.holding?.asset
            ? {
                id: quickTransaction.holding.id,
                asset: {
                  id: quickTransaction.holding.asset.id,
                  name: quickTransaction.holding.asset.name,
                  assetType: quickTransaction.holding.asset.assetType,
                },
              }
            : null
        }
        initialDirection={
          quickTransaction.direction === 'sell' ? InvestmentTransactionDirection.Outflow : InvestmentTransactionDirection.Inflow
        }
        onRecorded={refreshInvestmentData}
      />

      <InvestmentEventSelectorDrawer
        open={Boolean(eventSelectorDrawer.holding && eventSelectorDrawer.kind)}
        holding={eventSelectorDrawer.holding}
        kind={eventSelectorDrawer.kind}
        financialAccountId={eventSelectorDrawer.financialAccountId}
        investmentAccounts={investmentAccounts}
        onClose={closeEventSelectorDrawer}
        onRecorded={refreshInvestmentData}
      />

      <RecordCorporateEventDrawer
        open={Boolean(corporateEventState.holding && corporateEventState.eventType)}
        onClose={() => setCorporateEventState({ holding: null, eventType: null })}
        holding={corporateEventState.holding}
        eventType={corporateEventState.eventType}
        financialAccounts={investmentAccounts}
        onRecorded={refreshInvestmentData}
      />
    </TableContainer>
  );
}
