import type { InvestmentGridDataQuery } from "@/lib/graphql/operations/banking/queries.generated";
import type { TimeSeriesDataPoint } from "@/shared/types/charts";
import type { InvestmentGridClassKey } from "./investmentGridConfig";

type TwrPoint = { date?: unknown; quotaValue?: number | null };
type LegacyPerfPoint = { timestamp?: unknown; grossValue?: number | null };
type LegacyPerfSeries = { points?: readonly LegacyPerfPoint[] | null } | null | undefined;

/**
 * Normalizes quota history into sparkline return points.
 */
export function twrPointsToSparklineSeries(
  points: readonly TwrPoint[] | null | undefined,
): TimeSeriesDataPoint[] {
  const valid = (points ?? [])
    .map((point, idx) => {
      const rawDate = point?.date;
      const label = rawDate != null && rawDate !== "" ? String(rawDate) : `P${idx + 1}`;
      const value = typeof point?.quotaValue === "number" ? point.quotaValue : null;
      return { quarter: label, value };
    })
    .filter(
      (point): point is { quarter: string; value: number } =>
        typeof point.value === "number" && Number.isFinite(point.value),
    )
    .map(({ quarter, value }) => ({ quarter, value }));

  if (valid.length < 2) return [];
  const base = valid[0]?.value;
  if (base == null || !Number.isFinite(base) || base === 0) return [];

  return valid.map((point) => ({
    quarter: point.quarter,
    value: point.value / base - 1,
  }));
}

function perfPointsToSparklineSeries(points: readonly LegacyPerfPoint[] | null | undefined): TimeSeriesDataPoint[] {
  const valid = (points ?? [])
    .map((point, idx) => {
      const rawDate = point?.timestamp;
      const label = rawDate != null && rawDate !== "" ? String(rawDate) : `P${idx + 1}`;
      const value = typeof point?.grossValue === "number" ? point.grossValue : null;
      return { quarter: label, value };
    })
    .filter(
      (point): point is { quarter: string; value: number } =>
        typeof point.value === "number" && Number.isFinite(point.value),
    );

  if (valid.length < 2) return [];
  const base = valid[0]?.value;
  if (base == null || !Number.isFinite(base) || base === 0) return [];

  return valid.map((point) => ({
    quarter: point.quarter,
    value: point.value / base - 1,
  }));
}

function mergeLegacyPerfSeries(seriesList: readonly LegacyPerfSeries[]): LegacyPerfPoint[] {
  const summedByTimestamp = new Map<string, number>();
  for (const series of seriesList) {
    for (const point of series?.points ?? []) {
      const rawTimestamp = point?.timestamp;
      if (rawTimestamp == null || rawTimestamp === "") continue;
      const timestamp = String(rawTimestamp);
      const grossValue = typeof point?.grossValue === "number" ? point.grossValue : null;
      if (grossValue == null || !Number.isFinite(grossValue)) continue;
      summedByTimestamp.set(timestamp, (summedByTimestamp.get(timestamp) ?? 0) + grossValue);
    }
  }

  return Array.from(summedByTimestamp.entries())
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([timestamp, grossValue]) => ({ timestamp, grossValue }));
}

function twrOrLegacySeries(
  twrPoints: readonly TwrPoint[] | null | undefined,
  legacySeriesList: readonly LegacyPerfSeries[],
): TimeSeriesDataPoint[] {
  const twrSeries = twrPointsToSparklineSeries(twrPoints);
  if (twrSeries.length > 0) return twrSeries;
  return perfPointsToSparklineSeries(mergeLegacyPerfSeries(legacySeriesList));
}

export function buildChartSeriesByClass(
  gridData: InvestmentGridDataQuery | undefined | null,
): Record<InvestmentGridClassKey, TimeSeriesDataPoint[]> {
  return {
    cash: twrOrLegacySeries(gridData?.twrCash, [gridData?.perfCash]),
    "fixed-income": twrOrLegacySeries(gridData?.twrFixedIncome, [gridData?.perfBankFixedIncome, gridData?.perfCreditFixedIncome]),
    funds: twrOrLegacySeries(gridData?.twrFunds, [gridData?.perfMutualFunds, gridData?.perfPension]),
    stocks: twrOrLegacySeries(gridData?.twrStocks, [gridData?.perfStocks]),
    reits: twrOrLegacySeries(gridData?.twrReits, [gridData?.perfReits]),
    crypto: twrOrLegacySeries(gridData?.twrCrypto, [gridData?.perfCrypto]),
  };
}
