'use client';

import type { InvestmentHoldingsQuery } from '@/lib/graphql/operations/banking/queries.generated';
import { formatCurrency as formatCurrencyBr } from '@/shared/components/ui/forms/components/masks/br';
import { i18n } from '@/shared/i18n';
import { getHoldingValueOrigin, type ValueOriginLabel } from '@/shared/utils/holdingValueOrigin';

export type HoldingRow = InvestmentHoldingsQuery['investmentHoldings'][number];

export type HoldingKind = 'fixedIncome' | 'fund' | 'equity';

export type AssetGroupKey =
  | 'variableIncome'
  | 'fixedIncome'
  | 'mutualFunds'
  | 'pension'
  | 'structured'
  | 'others';

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
  fixedIncome: ['CDB', 'RDB', 'LCI', 'LCA', 'LC', 'LF', 'LIG', 'ETF_FIXED_INCOME', 'TREASURY', 'DEBENTURES', 'CRI', 'CRA'],
  mutualFunds: ['MUTUAL_FUND', 'FIXED_INCOME_FUND', 'EQUITY_FUND', 'MULTI_MARKET_FUND', 'FOREX_FUND'],
  pension: ['PGBL', 'VGBL'],
  structured: ['COE'],
  others: ['CRYPTO', 'OPTION', 'DERIVATIVES', 'OTHER'],
};

export const ASSET_GROUP_ORDER: AssetGroupKey[] = [
  'variableIncome',
  'fixedIncome',
  'mutualFunds',
  'pension',
  'structured',
  'others',
];

export const FIXED_INCOME_GROUP_KEYS = new Set<AssetGroupKey>(['fixedIncome']);

export const FUND_GROUP_KEYS = new Set<AssetGroupKey>(['mutualFunds', 'pension']);

export const AMORTIZABLE_ASSET_TYPES = new Set([
  'DEBENTURES',
  'CRI',
  'CRA',
  'TREASURY',
  'REAL_ESTATE_FUND',
  'AGRIBUSINESS_FUND',
  'ETF_FIXED_INCOME',
]);

export const LENDABLE_ASSET_TYPES = new Set([
  'STOCK',
  'ETF',
  'BDR',
  'REAL_ESTATE_FUND',
  'AGRIBUSINESS_FUND',
]);

export interface InvestmentHoldingGroup {
  key: AssetGroupKey;
  title: string;
  count: number;
  total: string;
  appliedTotal: string;
  valueOriginKey: ValueOriginLabel | null;
  gainPercent: number | null;
  allocationPercent: number | null;
  summary: string;
  holdings: HoldingRow[];
}

export function getAssetGroupKey(assetType: string | null | undefined): AssetGroupKey {
  if (!assetType) return 'others';
  const normalized = String(assetType);
  const match =
    (Object.keys(ASSET_TYPES_BY_GROUP) as AssetGroupKey[]).find((groupKey) =>
      ASSET_TYPES_BY_GROUP[groupKey].includes(normalized),
    ) ?? null;
  return match ?? 'others';
}

export function getHoldingKind(assetType: string | null | undefined): HoldingKind {
  const groupKey = getAssetGroupKey(assetType);
  if (FIXED_INCOME_GROUP_KEYS.has(groupKey)) return 'fixedIncome';
  if (FUND_GROUP_KEYS.has(groupKey)) return 'fund';
  return 'equity';
}

export function getCurrentLocale(): string {
  return i18n.language || Intl.DateTimeFormat().resolvedOptions().locale || 'pt-BR';
}

export function formatCurrency(amount: number, currencyCode: string, locale: string): string {
  return formatCurrencyBr(amount, currencyCode, locale);
}

const VALUE_ORIGIN_GROUP_TIE_ORDER: ValueOriginLabel[] = [
  'indisponivel',
  'estimado_app',
  'manual',
  'calculado_app',
  'mercado_app',
  'sincronizado_provider',
  'importado',
  'externo',
];

export function pickGroupValueOrigin(holdings: HoldingRow[]): ValueOriginLabel | null {
  const valued = holdings.filter((h) => typeof h.grossValue === 'number');
  if (valued.length === 0) return null;

  const counts = new Map<ValueOriginLabel, number>();
  for (const h of valued) {
    const label = getHoldingValueOrigin(h);
    counts.set(label, (counts.get(label) ?? 0) + 1);
  }

  let max = 0;
  for (const c of counts.values()) {
    if (c > max) max = c;
  }

  const top = [...counts.entries()].filter(([, c]) => c === max).map(([l]) => l);
  if (top.length === 1) return top[0] ?? null;

  for (const pref of VALUE_ORIGIN_GROUP_TIE_ORDER) {
    if (top.includes(pref)) return pref;
  }
  return top[0] ?? null;
}
