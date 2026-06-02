'use client';

import { AssetType } from '@/lib/graphql/types/__generated__/graphql';
import type { HoldingRow } from '../table/investmentTableHelpers';

export type InvestmentAssetClass = 'fixed-income' | 'funds' | 'stocks' | 'reits' | 'crypto';
export type ResolvedAssetSource = 'cvm' | 'b3' | 'manual' | 'existing';

export interface ResolvedAsset {
  id: string;
  name: string;
  ticker?: string;
  network?: string;
  logoUrl?: string;
  assetType: AssetType;
  source: ResolvedAssetSource;
}

export type NewBuyIntent = { kind: 'new-buy'; assetClass: InvestmentAssetClass };
export type NewSellIntent = { kind: 'new-sell'; assetClass: InvestmentAssetClass };
export type RowBuyIntent = {
  kind: 'row-buy';
  assetClass: InvestmentAssetClass;
  holdingId: string;
  asset: ResolvedAsset;
};
export type RowSellIntent = {
  kind: 'row-sell';
  assetClass: InvestmentAssetClass;
  holdingId: string;
  asset: ResolvedAsset;
  maxQty: number;
};
export type EditIntent = {
  kind: 'edit';
  assetClass: InvestmentAssetClass;
  holdingId: string;
  transactionId: string;
  asset: ResolvedAsset;
};

export type TransactionEditorIntent =
  | NewBuyIntent
  | NewSellIntent
  | RowBuyIntent
  | RowSellIntent
  | EditIntent;

export function isNewIntent(intent: TransactionEditorIntent): intent is NewBuyIntent | NewSellIntent {
  return intent.kind === 'new-buy' || intent.kind === 'new-sell';
}

export function isEditIntent(intent: TransactionEditorIntent): intent is EditIntent {
  return intent.kind === 'edit';
}

export function intentAction(intent: TransactionEditorIntent): 'buy' | 'sell' {
  if (intent.kind === 'new-sell' || intent.kind === 'row-sell') return 'sell';
  if (intent.kind === 'edit') return 'buy';
  return 'buy';
}

const FIXED_INCOME_ASSET_TYPES = new Set<AssetType>([
  AssetType.Cdb,
  AssetType.Rdb,
  AssetType.Lci,
  AssetType.Lca,
  AssetType.Lc,
  AssetType.Lf,
  AssetType.Lig,
  AssetType.EtfFixedIncome,
  AssetType.Treasury,
  AssetType.Debentures,
  AssetType.Cri,
  AssetType.Cra,
]);

const FUND_ASSET_TYPES = new Set<AssetType>([
  AssetType.MutualFund,
  AssetType.Pgbl,
  AssetType.Vgbl,
]);

const REIT_ASSET_TYPES = new Set<AssetType>([
  AssetType.RealEstateFund,
  AssetType.AgribusinessFund,
]);

export function mapAssetTypeToTransactionEditorClass(assetType: AssetType | null | undefined): InvestmentAssetClass {
  if (!assetType) return 'stocks';
  if (assetType === AssetType.Crypto) return 'crypto';
  if (REIT_ASSET_TYPES.has(assetType)) return 'reits';
  if (FIXED_INCOME_ASSET_TYPES.has(assetType)) return 'fixed-income';
  if (FUND_ASSET_TYPES.has(assetType)) return 'funds';
  return 'stocks';
}

export function mapHoldingToResolvedAsset(holding: HoldingRow): ResolvedAsset | null {
  const assetType = holding.asset?.assetType;
  const id = holding.asset?.id;
  const name = holding.asset?.name;
  if (!assetType || !id || !name) return null;

  return {
    id,
    name,
    ticker: holding.asset?.ticker ?? undefined,
    logoUrl: holding.asset?.logoAsset?.publicUrl ?? holding.financialAccount?.issuer?.logoAsset?.publicUrl ?? undefined,
    assetType,
    source: 'existing',
  };
}
