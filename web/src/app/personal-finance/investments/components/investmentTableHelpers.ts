'use client';

import type { AllInvestmentHoldingsQuery, InvestmentHoldingsQuery } from '@/lib/graphql/operations/banking/queries.generated';
import { formatCurrency as formatCurrencyBr } from '@/shared/components/ui/forms/components/masks/br';
import { i18n } from '@/shared/i18n';
import { getHoldingValueOrigin, type ValueOriginLabel } from '@/shared/utils/holdingValueOrigin';

export type HoldingRow =
  | InvestmentHoldingsQuery['investmentHoldings'][number]
  | AllInvestmentHoldingsQuery['allInvestmentHoldings'][number];

export type AssetGroupKey =
  | 'variableIncome'
  | 'bankFixedIncome'
  | 'creditFixedIncome'
  | 'treasuryDirect'
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
  bankFixedIncome: ['CDB', 'RDB', 'LCI', 'LCA', 'LC', 'LF', 'LIG', 'ETF_FIXED_INCOME'],
  creditFixedIncome: ['DEBENTURES', 'CRI', 'CRA'],
  treasuryDirect: ['TREASURY'],
  mutualFunds: ['MUTUAL_FUND', 'FIXED_INCOME_FUND', 'EQUITY_FUND', 'MULTI_MARKET_FUND', 'FOREX_FUND'],
  pension: ['PGBL', 'VGBL'],
  structured: ['COE'],
  others: ['CRYPTO', 'OPTION', 'DERIVATIVES', 'OTHER'],
};

export const ASSET_GROUP_ORDER: AssetGroupKey[] = [
  'variableIncome',
  'bankFixedIncome',
  'creditFixedIncome',
  'treasuryDirect',
  'mutualFunds',
  'pension',
  'structured',
  'others',
];

export const FIXED_INCOME_GROUP_KEYS = new Set<AssetGroupKey>(['bankFixedIncome', 'creditFixedIncome', 'treasuryDirect']);

export const FUND_GROUP_KEYS = new Set<AssetGroupKey>(['mutualFunds', 'pension']);

export function getAssetGroupKey(assetType: string | null | undefined): AssetGroupKey {
  if (!assetType) return 'others';
  const normalized = String(assetType);
  const match =
    (Object.keys(ASSET_TYPES_BY_GROUP) as AssetGroupKey[]).find((groupKey) =>
      ASSET_TYPES_BY_GROUP[groupKey].includes(normalized),
    ) ?? null;
  return match ?? 'others';
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
