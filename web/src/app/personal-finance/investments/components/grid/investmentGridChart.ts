import type { InvestmentGridDataQuery } from "@/lib/graphql/operations/banking/queries.generated";
import type { TimeSeriesDataPoint } from "@/shared/types/charts";
import type { InvestmentGridClassKey } from "./investmentGridConfig";

type PerfPoint = { timestamp?: unknown; grossValue?: number | null };

function timestampToSortable(ts: string): number {
  const ms = new Date(ts).getTime();
  return Number.isFinite(ms) ? ms : NaN;
}

/**
 * Merges multiple performance series by timestamp (sums grossValue per bucket).
 */
export function mergePerformancePointGroups(
  ...groups: Array<readonly PerfPoint[] | null | undefined>
): Array<{ timestamp: string; grossValue: number }> {
  const acc = new Map<string, number>();
  for (const group of groups) {
    for (const point of group ?? []) {
      const rawTs = point?.timestamp;
      const ts = rawTs != null && rawTs !== "" ? String(rawTs) : null;
      const value = typeof point?.grossValue === "number" ? point.grossValue : null;
      if (!ts || value == null || !Number.isFinite(value)) continue;
      if (!Number.isFinite(timestampToSortable(ts))) continue;
      acc.set(ts, (acc.get(ts) ?? 0) + value);
    }
  }
  return Array.from(acc.entries())
    .map(([timestamp, grossValue]) => ({ timestamp, grossValue }))
    .sort((a, b) => timestampToSortable(a.timestamp) - timestampToSortable(b.timestamp));
}

/**
 * Normalizes API performance points into sparkline-friendly series (labels reuse `quarter` field for timestamps).
 */
export function performancePointsToSparklineSeries(
  points: readonly PerfPoint[] | null | undefined,
): TimeSeriesDataPoint[] {
  const parsed = (points ?? [])
    .map((point, idx) => {
      const rawTs = point?.timestamp;
      const label = rawTs != null && rawTs !== "" ? String(rawTs) : `P${idx + 1}`;
      const value = typeof point?.grossValue === "number" ? point.grossValue : null;
      return { quarter: label, value, ts: rawTs != null && rawTs !== "" ? String(rawTs) : null };
    })
    .filter(
      (point): point is { quarter: string; value: number; ts: string | null } =>
        typeof point.value === "number" &&
        Number.isFinite(point.value) &&
        (point.ts == null || Number.isFinite(timestampToSortable(point.ts))),
    )
    .map(({ quarter, value }) => ({ quarter, value }));
  return parsed.length >= 1 ? parsed : [];
}

export function buildChartSeriesByClass(
  gridData: InvestmentGridDataQuery | undefined | null,
): Record<InvestmentGridClassKey, TimeSeriesDataPoint[]> {
  const mergedFixedIncome = mergePerformancePointGroups(
    gridData?.perfBankFixedIncome?.points,
    gridData?.perfCreditFixedIncome?.points,
  );
  const mergedFunds = mergePerformancePointGroups(
    gridData?.perfMutualFunds?.points,
    gridData?.perfPension?.points,
  );

  return {
    cash: performancePointsToSparklineSeries(gridData?.perfCash?.points),
    "fixed-income": performancePointsToSparklineSeries(mergedFixedIncome),
    funds: performancePointsToSparklineSeries(mergedFunds),
    stocks: performancePointsToSparklineSeries(gridData?.perfStocks?.points),
    reits: performancePointsToSparklineSeries(gridData?.perfReits?.points),
    crypto: performancePointsToSparklineSeries(gridData?.perfCrypto?.points),
  };
}
