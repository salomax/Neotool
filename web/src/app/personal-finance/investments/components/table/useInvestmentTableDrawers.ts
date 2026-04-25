'use client';

import * as React from 'react';
import type { MaturityDrawerHolding } from '../../MaturityConfirmDrawer';
import type { AmortizationDrawerHolding } from '../../RecordAmortizationDrawer';
import type { StockLendingDrawerHolding } from '../../RecordStockLendingDrawer';
import type { TransferCustodyDrawerHolding } from '../../TransferCustodyDrawer';
import type { WithholdingTaxDrawerHolding } from '../../WithholdingTaxDrawer';
import type { InvestmentHoldingDetailKind } from '../InvestmentHoldingDetailDrawer';
import type { CorporateEventType } from '../corporate-events/RecordCorporateEventDrawer';
import {
  AMORTIZABLE_ASSET_TYPES,
  LENDABLE_ASSET_TYPES,
  getAssetGroupKey,
  getHoldingKind,
  type HoldingKind,
  type HoldingRow,
} from './investmentTableHelpers';

export interface DetailDrawerState {
  holdingId: string | null;
  kind: InvestmentHoldingDetailKind | null;
}

export interface EventSelectorDrawerState {
  holding: HoldingRow | null;
  kind: HoldingKind;
  financialAccountId: string | null;
}

export interface ActionMenuState {
  anchorEl: HTMLElement | null;
  holding: HoldingRow | null;
}

export interface QuickTransactionState {
  holding: HoldingRow | null;
  direction: 'buy' | 'sell' | null;
}

export interface CorporateEventState {
  holding: HoldingRow | null;
  eventType: CorporateEventType | null;
}

export interface UseInvestmentTableDrawersReturn {
  actionMenu: ActionMenuState;
  handleOpenActionMenu: (anchorEl: HTMLElement, holding: HoldingRow) => void;
  closeActionMenu: () => void;
  detailDrawer: DetailDrawerState;
  handleOpenDetail: (holding: HoldingRow) => void;
  closeDetailDrawer: () => void;
  eventSelectorDrawer: EventSelectorDrawerState;
  handleOpenEventSelector: (holding: HoldingRow) => Promise<void>;
  closeEventSelectorDrawer: () => void;
  holdingToRemove: HoldingRow | null;
  setHoldingToRemove: (holding: HoldingRow | null) => void;
  maturityConfirmOpen: boolean;
  maturityConfirmHolding: MaturityDrawerHolding | null;
  handleOpenMaturityConfirmDrawer: (holding: HoldingRow) => void;
  closeMaturityConfirmDrawer: () => void;
  withholdingTaxOpen: boolean;
  withholdingTaxHolding: WithholdingTaxDrawerHolding | null;
  handleOpenWithholdingTaxDrawer: (holding: HoldingRow) => void;
  closeWithholdingTaxDrawer: () => void;
  amortizationOpen: boolean;
  amortizationHolding: AmortizationDrawerHolding | null;
  handleOpenAmortizationDrawer: (holding: HoldingRow) => void;
  closeAmortizationDrawer: () => void;
  stockLendingOpen: boolean;
  stockLendingHolding: StockLendingDrawerHolding | null;
  handleOpenStockLendingDrawer: (holding: HoldingRow) => void;
  closeStockLendingDrawer: () => void;
  transferCustodyOpen: boolean;
  transferCustodyHolding: TransferCustodyDrawerHolding | null;
  handleOpenTransferCustodyDrawer: (holding: HoldingRow) => void;
  closeTransferCustodyDrawer: () => void;
  quickTransaction: QuickTransactionState;
  isQuickEquityOpen: boolean;
  isQuickFundOpen: boolean;
  handleQuickBuy: (holding: HoldingRow) => void;
  handleQuickSell: (holding: HoldingRow) => void;
  closeQuickTransaction: () => void;
  corporateEventState: CorporateEventState;
  handleRecordCorporateEvent: (holding: HoldingRow, eventType: CorporateEventType) => void;
  closeCorporateEventDrawer: () => void;
}

interface UseInvestmentTableDrawersOptions {
  selectedAccountId: string | null;
  resolveFinancialAccountId: (holding: HoldingRow) => string | null;
}

export function useInvestmentTableDrawers(
  options: UseInvestmentTableDrawersOptions,
): UseInvestmentTableDrawersReturn {
  const { resolveFinancialAccountId, selectedAccountId } = options;

  const [actionMenu, setActionMenu] = React.useState<ActionMenuState>({ anchorEl: null, holding: null });
  const [holdingToRemove, setHoldingToRemoveState] = React.useState<HoldingRow | null>(null);
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

  const handleOpenEventSelector = React.useCallback(
    async (holding: HoldingRow) => {
      setEventSelectorDrawer({
        holding,
        kind: getHoldingKind(holding.asset?.assetType),
        financialAccountId: selectedAccountId ?? resolveFinancialAccountId(holding),
      });
    },
    [resolveFinancialAccountId, selectedAccountId],
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
      financialAccountName: holding.financialAccount?.name ?? null,
      financialAccountIssuerName: holding.financialAccount?.issuer?.name ?? holding.financialAccount?.issuerName ?? null,
      financialAccountIssuerLogoUrl: holding.financialAccount?.issuer?.logoAsset?.publicUrl ?? null,
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

  const handleQuickSell = React.useCallback(
    (holding: HoldingRow) => {
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
    },
    [handleOpenMaturityConfirmDrawer],
  );

  const handleRecordCorporateEvent = React.useCallback((holding: HoldingRow, eventType: CorporateEventType) => {
    setCorporateEventState({ holding, eventType });
  }, []);

  const closeCorporateEventDrawer = React.useCallback(() => {
    setCorporateEventState({ holding: null, eventType: null });
  }, []);

  const setHoldingToRemove = React.useCallback((holding: HoldingRow | null) => {
    setHoldingToRemoveState(holding);
  }, []);

  return {
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
  };
}
