'use client';

import * as React from 'react';
import type { MaturityDrawerHolding } from '../../MaturityConfirmDrawer';
import type { AmortizationDrawerHolding } from '../../RecordAmortizationDrawer';
import type { StockLendingDrawerHolding } from '../../RecordStockLendingDrawer';
import type { TransferCustodyDrawerHolding } from '../../TransferCustodyDrawer';
import type { InvestmentHoldingDetailKind } from '../InvestmentHoldingDetailDrawer';
import type { CorporateEventType } from '../corporate-events/RecordCorporateEventDrawer';
import {
  mapAssetTypeToTransactionEditorClass,
  mapHoldingToResolvedAsset,
  type RowBuyIntent,
  type RowSellIntent,
  type TransactionEditorIntent,
} from '../transaction-editor/intent';
import {
  AMORTIZABLE_ASSET_TYPES,
  LENDABLE_ASSET_TYPES,
  getHoldingKind,
  type HoldingRow,
} from './investmentTableHelpers';

export interface DetailDrawerState {
  holdingId: string | null;
  kind: InvestmentHoldingDetailKind | null;
  initialTab: 'summary' | 'transactions';
}

export interface TransactionEditorDrawerState {
  intent: TransactionEditorIntent | null;
  financialAccountId: string | null;
}

export interface ActionMenuState {
  anchorEl: HTMLElement | null;
  holding: HoldingRow | null;
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
  handleOpenDetail: (holding: HoldingRow, options?: { initialTab?: 'summary' | 'transactions' }) => void;
  closeDetailDrawer: () => void;
  transactionEditorDrawer: TransactionEditorDrawerState;
  openTransactionEditorIntent: (intent: TransactionEditorIntent, financialAccountId: string | null) => void;
  openRowBuyEditor: (holding: HoldingRow) => void;
  openRowSellEditor: (holding: HoldingRow) => void;
  closeTransactionEditor: () => void;
  holdingToRemove: HoldingRow | null;
  setHoldingToRemove: (holding: HoldingRow | null) => void;
  maturityConfirmOpen: boolean;
  maturityConfirmHolding: MaturityDrawerHolding | null;
  handleOpenMaturityConfirmDrawer: (holding: HoldingRow) => void;
  closeMaturityConfirmDrawer: () => void;
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
  corporateEventState: CorporateEventState;
  handleRecordCorporateEvent: (holding: HoldingRow, eventType: CorporateEventType) => void;
  closeCorporateEventDrawer: () => void;
}

export function useInvestmentTableDrawers(): UseInvestmentTableDrawersReturn {
  const [actionMenu, setActionMenu] = React.useState<ActionMenuState>({ anchorEl: null, holding: null });
  const [holdingToRemove, setHoldingToRemoveState] = React.useState<HoldingRow | null>(null);
  const [detailDrawer, setDetailDrawer] = React.useState<DetailDrawerState>({
    holdingId: null,
    kind: null,
    initialTab: 'summary',
  });
  const [transactionEditorDrawer, setTransactionEditorDrawer] = React.useState<TransactionEditorDrawerState>({
    intent: null,
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
  const [corporateEventState, setCorporateEventState] = React.useState<CorporateEventState>({ holding: null, eventType: null });

  const closeActionMenu = React.useCallback(() => {
    setActionMenu({ anchorEl: null, holding: null });
  }, []);

  const handleOpenActionMenu = React.useCallback((anchorEl: HTMLElement, holding: HoldingRow) => {
    setActionMenu({ anchorEl, holding });
  }, []);

  const closeDetailDrawer = React.useCallback(() => {
    setDetailDrawer({ holdingId: null, kind: null, initialTab: 'summary' });
  }, []);

  const handleOpenDetail = React.useCallback((
    holding: HoldingRow,
    options?: { initialTab?: 'summary' | 'transactions' },
  ) => {
    setDetailDrawer({
      holdingId: holding.id,
      kind: getHoldingKind(holding.asset?.assetType),
      initialTab: options?.initialTab ?? 'summary',
    });
  }, []);

  const closeTransactionEditor = React.useCallback(() => {
    setTransactionEditorDrawer({ intent: null, financialAccountId: null });
  }, []);

  const openTransactionEditorIntent = React.useCallback(
    (intent: TransactionEditorIntent, financialAccountId: string | null) => {
      setTransactionEditorDrawer({ intent, financialAccountId });
    },
    [],
  );

  const openRowBuyEditor = React.useCallback((holding: HoldingRow) => {
    const resolvedAsset = mapHoldingToResolvedAsset(holding);
    if (!resolvedAsset) return;
    const intent: RowBuyIntent = {
      kind: 'row-buy',
      assetClass: mapAssetTypeToTransactionEditorClass(holding.asset?.assetType),
      holdingId: holding.id,
      asset: resolvedAsset,
    };
    setTransactionEditorDrawer({
      financialAccountId: holding.financialAccountId,
      intent,
    });
  }, []);

  const openRowSellEditor = React.useCallback((holding: HoldingRow) => {
    const resolvedAsset = mapHoldingToResolvedAsset(holding);
    if (!resolvedAsset) return;
    const intent: RowSellIntent = {
      kind: 'row-sell',
      assetClass: mapAssetTypeToTransactionEditorClass(holding.asset?.assetType),
      holdingId: holding.id,
      asset: resolvedAsset,
      maxQty: holding.quantity,
    };
    setTransactionEditorDrawer({
      financialAccountId: holding.financialAccountId,
      intent,
    });
  }, []);

  const closeMaturityConfirmDrawer = React.useCallback(() => {
    setMaturityConfirmOpen(false);
    setMaturityConfirmHolding(null);
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
    transactionEditorDrawer,
    openTransactionEditorIntent,
    openRowBuyEditor,
    openRowSellEditor,
    closeTransactionEditor,
    holdingToRemove,
    setHoldingToRemove,
    maturityConfirmOpen,
    maturityConfirmHolding,
    handleOpenMaturityConfirmDrawer,
    closeMaturityConfirmDrawer,
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
    corporateEventState,
    handleRecordCorporateEvent,
    closeCorporateEventDrawer,
  };
}
