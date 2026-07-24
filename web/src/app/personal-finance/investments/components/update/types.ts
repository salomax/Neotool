'use client';

import {
  AssetType,
  HoldingPricingMode,
  InvestmentTransactionDirection,
  InvestmentTransactionType,
} from '@/lib/graphql/types/__generated__/graphql';
import type { InvestmentHoldingsQuery } from '@/lib/graphql/operations/banking/queries.generated';
import { formatDateOnly } from '@/shared/utils/date/dateOnly';

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

export function toFiniteNumber(value: unknown): number | undefined {
  if (typeof value !== 'number' || !Number.isFinite(value)) return undefined;
  return value;
}

/**
 * Completes the fund trade triad: amount ↔ quantity ↔ unit price.
 * Mirrors create-fund submit derivation.
 */
export function deriveFundTradeLegs(input: {
  grossValue?: number;
  quantity?: number;
  unitPrice?: number;
  netValueFallback?: number;
}): { quantity?: number; grossValue: number; unitPrice?: number } {
  const unitPrice = toFiniteNumber(input.unitPrice);
  const grossFromForm = toFiniteNumber(input.grossValue);
  const quantityFromForm = toFiniteNumber(input.quantity);

  let quantity = quantityFromForm;
  let grossValue = grossFromForm ?? toFiniteNumber(input.netValueFallback) ?? 0;

  if (unitPrice != null && unitPrice > 0) {
    if ((quantity == null || !(quantity > 0)) && grossFromForm != null && grossFromForm > 0) {
      quantity = grossFromForm / unitPrice;
    }
    if ((grossFromForm == null || !(grossFromForm > 0)) && quantity != null && quantity > 0) {
      grossValue = quantity * unitPrice;
    }
  }

  return { quantity, grossValue, unitPrice };
}

/**
 * Fund redemption net value: gross − IR − IOF (floored at 0).
 */
export function deriveFundOutflowNetValue(input: {
  grossValue?: number;
  incomeTax?: number;
  iof?: number;
}): number | undefined {
  if (typeof input.grossValue !== 'number' || !Number.isFinite(input.grossValue) || input.grossValue <= 0) {
    return undefined;
  }
  const tax = typeof input.incomeTax === 'number' && Number.isFinite(input.incomeTax) ? Math.max(input.incomeTax, 0) : 0;
  const iofAmount = typeof input.iof === 'number' && Number.isFinite(input.iof) ? Math.max(input.iof, 0) : 0;
  return Math.max(input.grossValue - tax - iofAmount, 0);
}

/**
 * Manual valuation must only be submitted for MANUAL-priced fund holdings.
 * Seeded valuedAt defaults must not force market-data funds into manual valuation.
 */
export function buildFundManualValuationInput(params: {
  direction: InvestmentTransactionDirection;
  pricingMode: HoldingPricingMode | null | undefined;
  valuedAt?: Date;
  grossValue: number;
}): { grossValue: number; valuedAt: string } | undefined {
  if (params.direction !== InvestmentTransactionDirection.Inflow) return undefined;
  if (!isFundManualPricingMode(params.pricingMode)) return undefined;
  if (!params.valuedAt) return undefined;
  if (!Number.isFinite(params.grossValue)) return undefined;

  return {
    grossValue: params.grossValue,
    valuedAt: formatDateOnly(params.valuedAt),
  };
}
