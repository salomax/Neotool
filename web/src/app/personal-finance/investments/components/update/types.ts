'use client';

import {
  AssetType,
  HoldingPricingMode,
  InvestmentTransactionDirection,
  InvestmentTransactionType,
} from '@/lib/graphql/types/__generated__/graphql';
import type { InvestmentHoldingsQuery } from '@/lib/graphql/operations/banking/queries.generated';

export type UpdateHolding = InvestmentHoldingsQuery['investmentHoldings'][number];

export type UpdateHoldingAssetClass = 'fixedIncome' | 'fund' | 'equity' | 'crypto';

export type UpdateHoldingFormValues = {
  tradeDate?: Date;
  settlementDate?: Date;
  grossValue?: number;
  netValue?: number;
  quantity?: number;
  unitPrice?: number;
  incomeTax?: number;
  iof?: number;
  totalFees?: number;
  valuedAt?: Date;
};

export interface UpdateHoldingEditTransaction {
  id: string;
  direction: InvestmentTransactionDirection;
  type?: InvestmentTransactionType | null;
  tradeDate: unknown;
  settlementDate?: unknown | null;
  grossValue: number;
  netValue?: number | null;
  quantity?: number | null;
  unitPrice?: number | null;
  incomeTax?: number | null;
  iof?: number | null;
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

export function getUpdateHoldingAssetClass(assetType: AssetType | null | undefined): UpdateHoldingAssetClass {
  if (!assetType) return 'equity';
  if (assetType === AssetType.Crypto) return 'crypto';
  if (FIXED_INCOME_ASSET_TYPES.has(assetType)) return 'fixedIncome';
  if (FUND_ASSET_TYPES.has(assetType)) return 'fund';
  return 'equity';
}

export function isFundManualPricingMode(pricingMode: HoldingPricingMode | null | undefined): boolean {
  return pricingMode === HoldingPricingMode.Manual;
}
