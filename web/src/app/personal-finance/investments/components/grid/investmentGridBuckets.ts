import type { InvestmentGridDataQuery } from "@/lib/graphql/operations/banking/queries.generated";
import { getAssetGroupKey } from "../table/investmentTableHelpers";
import { INVESTMENT_GRID_CLASS_KEYS, type InvestmentGridClassKey } from "./investmentGridConfig";

export type GridHoldingRow = InvestmentGridDataQuery["investmentHoldings"][number];

function emptyBuckets(): Record<InvestmentGridClassKey, GridHoldingRow[]> {
  return {
    cash: [],
    "fixed-income": [],
    funds: [],
    stocks: [],
    reits: [],
    crypto: [],
  };
}

/**
 * Single pass: classifies each holding into at most one grid card bucket.
 */
export function bucketHoldingsByGridClass(holdings: readonly GridHoldingRow[]): Record<
  InvestmentGridClassKey,
  GridHoldingRow[]
> {
  const buckets = emptyBuckets();
  for (const holding of holdings) {
    const type = holding.asset?.assetType;
    const groupKey = getAssetGroupKey(type);

    if (groupKey === "bankFixedIncome" || groupKey === "creditFixedIncome") {
      buckets["fixed-income"].push(holding);
      continue;
    }
    if (groupKey === "mutualFunds" || groupKey === "pension") {
      buckets.funds.push(holding);
      continue;
    }
    if (
      type === "STOCK" ||
      type === "STOCK_RIGHTS" ||
      type === "BDR" ||
      type === "ETF" ||
      type === "ETF_INTERNATIONAL"
    ) {
      buckets.stocks.push(holding);
      continue;
    }
    if (type === "REAL_ESTATE_FUND" || type === "AGRIBUSINESS_FUND") {
      buckets.reits.push(holding);
      continue;
    }
    if (type === "CRYPTO") {
      buckets.crypto.push(holding);
    }
  }
  return buckets;
}

export function computeGroupGrossAndGain(filtered: readonly GridHoldingRow[]): {
  grossValue: number;
  percentageGain: number | null;
} {
  let totalGrossValue = 0;
  let totalPrincipal = 0;
  let hasGrossValue = false;
  let hasPrincipal = false;

  for (const holding of filtered) {
    if (typeof holding.grossValue === "number") {
      totalGrossValue += holding.grossValue;
      hasGrossValue = true;
    }
    if (typeof holding.averageCost === "number" && typeof holding.quantity === "number") {
      totalPrincipal += holding.averageCost * holding.quantity;
      hasPrincipal = true;
    }
  }

  return {
    grossValue: hasGrossValue ? totalGrossValue : 0,
    percentageGain:
      hasPrincipal && totalPrincipal > 0 && hasGrossValue
        ? (totalGrossValue - totalPrincipal) / totalPrincipal
        : null,
  };
}

export interface GridMetricsAndCounts {
  metricsByKey: Record<
    InvestmentGridClassKey,
    { grossValue: number; percentageGain: number | null }
  >;
  assetCountByKey: Record<InvestmentGridClassKey, number | null>;
}

export function buildGridMetricsAndCounts(
  holdings: readonly GridHoldingRow[],
  cashBalance: number | null | undefined,
): GridMetricsAndCounts {
  const buckets = bucketHoldingsByGridClass(holdings);

  const metricsByKey = {} as GridMetricsAndCounts["metricsByKey"];
  const assetCountByKey = {} as GridMetricsAndCounts["assetCountByKey"];

  for (const key of INVESTMENT_GRID_CLASS_KEYS) {
    if (key === "cash") {
      metricsByKey.cash = {
        grossValue: typeof cashBalance === "number" && Number.isFinite(cashBalance) ? cashBalance : 0,
        percentageGain: null,
      };
      assetCountByKey.cash = null;
      continue;
    }
    const list = buckets[key];
    metricsByKey[key] = computeGroupGrossAndGain(list);
    assetCountByKey[key] = list.length;
  }

  return { metricsByKey, assetCountByKey };
}
