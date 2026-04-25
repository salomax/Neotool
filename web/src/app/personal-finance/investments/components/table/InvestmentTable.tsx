'use client';

import React from 'react';
import dynamic from 'next/dynamic';
import { useMutation } from '@apollo/client/react';
import { Box, Divider, LinearProgress, Paper, TableContainer, Typography } from '@mui/material';
import { useTranslation } from '@/shared/i18n';
import { useToast } from '@/shared/providers';
import { ConfirmationDialog } from '@/shared/components/ui/feedback';
import { extractErrorMessage } from '@/shared/utils/error';
import { personalFinanceTranslations } from '../../../i18n';
import { DELETE_INVESTMENT_HOLDING } from '@/lib/graphql/operations/banking/mutations';
import { InvestmentTransactionDirection } from '@/lib/graphql/types/__generated__/graphql';
import { CashMovementsSection } from './CashMovementsSection';
import { HoldingsGroupsSection } from './HoldingsGroupsSection';
import { InvestmentEventSelectorDrawer } from '../InvestmentEventSelectorDrawer';
import { RecordEquityHoldingDrawer } from '../RecordEquityHoldingDrawer';
import { RecordFundHoldingDrawer } from '../RecordFundHoldingDrawer';
import { InvestmentHoldingActionsMenu } from './InvestmentHoldingActionsMenu';
import { RecordCorporateEventDrawer } from '../corporate-events/RecordCorporateEventDrawer';
import type { IssuerSelectionAccount } from '../IssuerSelection';
import { useInvestmentCardExpansion } from './useInvestmentCardExpansion';
import { InvestmentTableActionsProvider } from './InvestmentTableActionsContext';
import { useInvestmentTableData } from './useInvestmentTableData';
import { useInvestmentTableDrawers } from './useInvestmentTableDrawers';
import {
  getAssetGroupKey,
  type AssetGroupKey,
  type HoldingRow,
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
  const toast = useToast();

  const [deleteInvestmentHolding, { loading: deletingHolding }] = useMutation(DELETE_INVESTMENT_HOLDING);
  const {
    holdings,
    groups,
    showCashCard: showCashCardFromData,
    holdingsFetching,
    holdingsLoading: loading,
    holdingsError: error,
    locale,
    refreshInvestmentData,
  } = useInvestmentTableData({
    selectedAccountId,
    investmentAccounts,
    searchQuery,
    visibleGroupKeys,
    visibleAssetTypes,
  });
  const showCashCard = includeCashCard && showCashCardFromData;

  const resolveFinancialAccountId = React.useCallback(
    (holding: HoldingRow) => selectedAccountId ?? holding.financialAccount?.id ?? null,
    [selectedAccountId],
  );
  const {
    actionMenu,
    handleOpenActionMenu,
    closeActionMenu,
    detailDrawer,
    handleOpenDetail,
    closeDetailDrawer,
    eventSelectorDrawer,
    handleOpenEventSelector,
    closeEventSelectorDrawer,
    holdingToRemove,
    setHoldingToRemove,
    maturityConfirmOpen,
    maturityConfirmHolding,
    handleOpenMaturityConfirmDrawer,
    closeMaturityConfirmDrawer,
    withholdingTaxOpen,
    withholdingTaxHolding,
    handleOpenWithholdingTaxDrawer,
    closeWithholdingTaxDrawer,
    amortizationOpen,
    amortizationHolding,
    handleOpenAmortizationDrawer,
    closeAmortizationDrawer,
    stockLendingOpen,
    stockLendingHolding,
    handleOpenStockLendingDrawer,
    closeStockLendingDrawer,
    transferCustodyOpen,
    transferCustodyHolding,
    handleOpenTransferCustodyDrawer,
    closeTransferCustodyDrawer,
    quickTransaction,
    isQuickEquityOpen,
    isQuickFundOpen,
    handleQuickBuy,
    handleQuickSell,
    closeQuickTransaction,
    corporateEventState,
    handleRecordCorporateEvent,
    closeCorporateEventDrawer,
  } = useInvestmentTableDrawers({
    selectedAccountId,
    resolveFinancialAccountId,
  });

  const handleConfirmRemove = async () => {
    if (!holdingToRemove) return;

    try {
      await deleteInvestmentHolding({ variables: { id: holdingToRemove.id } });
      setHoldingToRemove(null);
      await refreshInvestmentData();
    } catch (error) {
      toast.error(extractErrorMessage(error, t('errors.default', { ns: 'common' })));
      setHoldingToRemove(null);
    }
  };

  const hasSearchQuery = Boolean((searchQuery ?? '').trim());

  const detailHolding = React.useMemo(
    () => holdings.find((holding) => holding.id === detailDrawer.holdingId) ?? null,
    [detailDrawer.holdingId, holdings],
  );

  const detailHoldingIssuerLogoUrl = React.useMemo(
    () => detailHolding?.financialAccount?.issuer?.logoAsset?.publicUrl ?? null,
    [detailHolding],
  );

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
          <InvestmentTableActionsProvider
            actions={{
              onOpenDetail: handleOpenDetail,
              onOpenMaturityConfirm: handleOpenMaturityConfirmDrawer,
              onOpenActions: handleOpenActionMenu,
              onBuy: handleQuickBuy,
              onSell: handleQuickSell,
            }}
          >
            <HoldingsGroupsSection
              groups={groups}
              expandedGroups={expandedGroups}
              hideGroupHeader={hideGroupHeader}
              selectedAccountId={selectedAccountId}
              locale={locale}
              onToggleGroup={toggleGroup}
            />
          </InvestmentTableActionsProvider>
        </Box>
      ) : null}

      <InvestmentHoldingActionsMenu
        anchorEl={actionMenu.anchorEl}
        holding={actionMenu.holding}
        deleting={deletingHolding}
        onClose={closeActionMenu}
        onViewDetails={handleOpenDetail}
        onViewEvents={handleOpenEventSelector}
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
        onClose={closeCorporateEventDrawer}
        holding={corporateEventState.holding}
        eventType={corporateEventState.eventType}
        financialAccounts={investmentAccounts}
        onRecorded={refreshInvestmentData}
      />
    </TableContainer>
  );
}
