'use client';

import React from 'react';
import dynamic from 'next/dynamic';
import { NetworkStatus } from '@apollo/client/core';
import { useApolloClient, useMutation, useQuery } from '@apollo/client/react';
import { Box, Divider, LinearProgress, Paper, TableContainer, Typography } from '@mui/material';
import { useTranslation } from '@/shared/i18n';
import { useToast } from '@/shared/providers';
import { ConfirmationDialog } from '@/shared/components/ui/feedback';
import { personalFinanceTranslations } from '../../../i18n';
import {
  INVESTMENT_CASH_BALANCE,
  INVESTMENT_HOLDINGS,
  INVESTMENT_PORTFOLIO_SUMMARY,
  INVESTMENT_TRANSACTIONS,
} from '@/lib/graphql/operations/banking/queries';
import { DELETE_INVESTMENT_HOLDING } from '@/lib/graphql/operations/banking/mutations';
import {
  FinancialAccountsQuery,
  InvestmentCashBalanceQuery,
  InvestmentCashBalanceQueryVariables,
  InvestmentHoldingsQuery,
  InvestmentHoldingsQueryVariables,
} from '@/lib/graphql/operations/banking/queries.generated';
import {
  InvestmentSource,
  InvestmentTransaction,
  InvestmentTransactionDirection,
} from '@/lib/graphql/types/__generated__/graphql';
import type { MaturityDrawerHolding } from '../../MaturityConfirmDrawer';
import type { InvestmentHoldingDetailKind } from '../InvestmentHoldingDetailDrawer';
import { CashMovementsSection } from './CashMovementsSection';
import { HoldingsGroupsSection } from './HoldingsGroupsSection';
import { InvestmentEditDrawers } from '../InvestmentEditDrawers';
import { RecordEquityHoldingDrawer } from '../RecordEquityHoldingDrawer';
import { RecordFundHoldingDrawer } from '../RecordFundHoldingDrawer';
import { RecordFixedIncomeRedemptionDrawer } from '../fixed-income/RecordFixedIncomeRedemptionDrawer';
import { InvestmentHoldingActionsMenu } from './InvestmentHoldingActionsMenu';
import { RecordCorporateEventDrawer, type CorporateEventType } from '../corporate-events/RecordCorporateEventDrawer';
import type { IssuerSelectionAccount } from '../IssuerSelection';
import { useInvestmentCardExpansion } from './useInvestmentCardExpansion';
import {
  ASSET_GROUP_ORDER,
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

type BankAccountRow = NonNullable<FinancialAccountsQuery['financialAccounts']>[number];

interface InvestmentTableProps {
  selectedAccountId: string | null;
  bankAccounts: BankAccountRow[];
  investmentAccounts?: IssuerSelectionAccount[];
  onRecordHolding?: () => void;
  searchQuery?: string;
  onSelectSearchAsset?: (assetName: string) => void;
  visibleGroupKeys?: AssetGroupKey[];
  visibleAssetTypes?: string[];
  includeCashCard?: boolean;
  hideGroupHeader?: boolean;
}

interface ActionMenuState {
  anchorEl: HTMLElement | null;
  holding: HoldingRow | null;
}

interface DetailDrawerState {
  holdingId: string | null;
  kind: InvestmentHoldingDetailKind | null;
}

interface EditDrawerState {
  holding: HoldingRow | null;
  transaction: InvestmentTransaction | null;
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

function getTransactionTimestamp(transaction: InvestmentTransaction): number {
  const rawValue = transaction.settlementDate ?? transaction.tradeDate;
  if (!rawValue) return Number.NEGATIVE_INFINITY;

  const timestamp = new Date(String(rawValue)).getTime();
  return Number.isNaN(timestamp) ? Number.NEGATIVE_INFINITY : timestamp;
}

function sortTransactionsByMostRecent(transactions: InvestmentTransaction[]): InvestmentTransaction[] {
  return [...transactions].sort((left, right) => {
    const timestampDelta = getTransactionTimestamp(right) - getTransactionTimestamp(left);
    if (timestampDelta !== 0) return timestampDelta;
    return right.id.localeCompare(left.id);
  });
}

export function InvestmentTable({
  selectedAccountId,
  bankAccounts,
  investmentAccounts = [],
  searchQuery,
  onSelectSearchAsset,
  visibleGroupKeys,
  visibleAssetTypes,
  includeCashCard = true,
  hideGroupHeader = false,
}: InvestmentTableProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const toast = useToast();
  const locale = getCurrentLocale();
  const apolloClient = useApolloClient();
  const [actionMenu, setActionMenu] = React.useState<ActionMenuState>({ anchorEl: null, holding: null });
  const [holdingToRemove, setHoldingToRemove] = React.useState<HoldingRow | null>(null);
  const [detailDrawer, setDetailDrawer] = React.useState<DetailDrawerState>({ holdingId: null, kind: null });
  const [editDrawer, setEditDrawer] = React.useState<EditDrawerState>({
    holding: null,
    transaction: null,
    kind: 'equity',
    financialAccountId: null,
  });
  const [maturityConfirmOpen, setMaturityConfirmOpen] = React.useState(false);
  const [maturityConfirmHolding, setMaturityConfirmHolding] = React.useState<MaturityDrawerHolding | null>(null);
  const [quickTransaction, setQuickTransaction] = React.useState<QuickTransactionState>({ holding: null, direction: null });
  const [isQuickEquityOpen, setIsQuickEquityOpen] = React.useState(false);
  const [isQuickFundOpen, setIsQuickFundOpen] = React.useState(false);
  const [isQuickRedemptionOpen, setIsQuickRedemptionOpen] = React.useState(false);
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
      apolloClient.refetchQueries({ include: [INVESTMENT_PORTFOLIO_SUMMARY] }),
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

  const resolveManualTransactions = React.useCallback(
    async (holdingId: string) => {
      const result = await apolloClient.query<{ investmentTransactions: InvestmentTransaction[] }>({
        query: INVESTMENT_TRANSACTIONS,
        variables: { holdingId },
        fetchPolicy: 'network-only',
        errorPolicy: 'all',
      });

      const manualTransactions = (result.data?.investmentTransactions ?? []).filter(
        (transaction) => transaction.source === InvestmentSource.Manual,
      );

      return sortTransactionsByMostRecent(manualTransactions);
    },
    [apolloClient],
  );

  const resolveLatestManualTransaction = React.useCallback(
    async (holdingId: string) => {
      const transactions = await resolveManualTransactions(holdingId);
      return transactions[0] ?? null;
    },
    [resolveManualTransactions],
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

  const closeEditDrawer = React.useCallback(() => {
    setEditDrawer({
      holding: null,
      transaction: null,
      kind: 'equity',
      financialAccountId: null,
    });
  }, []);

  const handleOpenEdit = React.useCallback(
    async (holding: HoldingRow) => {
      try {
        const transaction = await resolveLatestManualTransaction(holding.id);

        if (!transaction) {
          toast.info('Esta posição não possui lançamento manual editável.');
          return;
        }

        setEditDrawer({
          holding,
          transaction,
          kind: getHoldingKind(holding.asset?.assetType),
          financialAccountId: resolveFinancialAccountId(holding),
        });
      } catch {
        toast.error('Não foi possível carregar os dados para edição.');
      }
    },
    [resolveFinancialAccountId, resolveLatestManualTransaction, toast],
  );

  const closeMaturityConfirmDrawer = React.useCallback(() => {
    setMaturityConfirmOpen(false);
    setMaturityConfirmHolding(null);
  }, []);

  const handleOpenMaturityConfirmDrawer = React.useCallback((holding: HoldingRow) => {
    if (!holding.asset?.id) return;

    setMaturityConfirmHolding({
      id: holding.id,
      maturityDate: holding.maturityDate ? String(holding.maturityDate) : null,
      grossValue: holding.grossValue ?? null,
      netValue: holding.netValue ?? null,
      incomeTax: holding.incomeTax ?? null,
      iof: holding.iof ?? null,
      issuerName: holding.asset.issuerName ?? null,
      issuerLogoUrl: holding.asset.logoAsset?.publicUrl ?? holding.financialAccount?.issuer?.logoAsset?.publicUrl ?? null,
      asset: { id: holding.asset.id, name: holding.asset.name },
    });
    setMaturityConfirmOpen(true);
  }, []);

  const closeQuickTransaction = React.useCallback(() => {
    setQuickTransaction({ holding: null, direction: null });
    setIsQuickEquityOpen(false);
    setIsQuickFundOpen(false);
    setIsQuickRedemptionOpen(false);
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
    setQuickTransaction({ holding, direction: 'sell' });
    if (groupKey === 'variableIncome') {
      setIsQuickEquityOpen(true);
      return;
    }
    if (groupKey === 'mutualFunds' || groupKey === 'pension') {
      setIsQuickFundOpen(true);
      return;
    }
    if (groupKey === 'bankFixedIncome' || groupKey === 'creditFixedIncome') {
      setIsQuickRedemptionOpen(true);
    }
  }, []);

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

    displayedHoldings.forEach((holding) => {
      const groupKey = getAssetGroupKey(holding.asset?.assetType);
      base[groupKey].push(holding);
    });

    return base;
  }, [displayedHoldings]);

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
  const showHoldingsList = !error && displayedHoldings.length > 0;
  const showHoldingsProgress = holdingsFetching;
  const actionHoldingGroupKey = actionMenu.holding?.asset?.assetType ? getAssetGroupKey(actionMenu.holding.asset.assetType) : null;
  const actionBuyLabel =
    actionHoldingGroupKey === 'variableIncome'
      ? t('investments.transactions.buy')
      : actionHoldingGroupKey === 'mutualFunds' || actionHoldingGroupKey === 'pension'
        ? t('investments.transactions.apply')
        : undefined;
  const actionSellLabel =
    actionHoldingGroupKey === 'variableIncome'
      ? t('investments.transactions.sell')
      : actionHoldingGroupKey === 'mutualFunds' ||
          actionHoldingGroupKey === 'pension' ||
          actionHoldingGroupKey === 'bankFixedIncome' ||
          actionHoldingGroupKey === 'creditFixedIncome'
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

      {showHoldingsProgress && !showHoldingsList ? (
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

      {!loading && !error && displayedHoldings.length === 0 ? (
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
            locale={locale}
            showHeaderProgress={showHoldingsProgress}
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
        showTransactionActions={Boolean(actionBuyLabel || actionSellLabel)}
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
        bankAccounts={bankAccounts}
        onSuccess={refreshInvestmentData}
      />

      <InvestmentEditDrawers
        holding={editDrawer.holding}
        transaction={editDrawer.transaction}
        kind={editDrawer.kind}
        financialAccountId={editDrawer.financialAccountId}
        financialAccounts={investmentAccounts}
        onClose={closeEditDrawer}
        onRecorded={refreshInvestmentData}
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

      <RecordFixedIncomeRedemptionDrawer
        open={isQuickRedemptionOpen}
        onClose={closeQuickTransaction}
        holding={
          quickTransaction.holding?.asset?.id && quickTransaction.holding?.financialAccount?.id
            ? {
                id: quickTransaction.holding.id,
                quantity: quickTransaction.holding.quantity,
                grossValue: quickTransaction.holding.grossValue ?? null,
                netValue: quickTransaction.holding.netValue ?? null,
                isTaxExempt: Boolean(quickTransaction.holding.isTaxExempt),
                financialAccountId: quickTransaction.holding.financialAccount.id,
                assetId: quickTransaction.holding.asset.id,
              }
            : null
        }
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
