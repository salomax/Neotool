/**
 * Factory utility for building metric grid items
 * Reduces boilerplate when creating MetricGridItem objects
 */

import type { MetricValueType } from "./formatMetricValue";
import type { TimeSeriesDataPoint } from "@/shared/types/charts";

/**
 * Threshold values for percentage metrics
 */
export interface PercentageThresholdValues {
  bad: number;
  regular: number;
  good: number;
}

/**
 * Raw metric data from API or query
 */
export interface MetricData {
  /** Current value (latest quarter) */
  actualValue: number | null;
  /** Historical values array (newest first for historical, oldest first for chartData) */
  historical: number[];
  /** Growth percentage from previous period */
  growthPercentage: number | null;
  /** Chart data points (oldest to newest for display) */
  chartData: TimeSeriesDataPoint[];
}

/**
 * Configuration for building a metric item
 */
export interface MetricItemConfig {
  /** Display label for the metric */
  label: string;
  /** How to format the value */
  valueType: MetricValueType;
  /** Raw metric data */
  data: MetricData;
  /** Currency code for currency metrics */
  currency?: string;
  /** Threshold values for percentage metrics */
  percentageThresholdValues?: PercentageThresholdValues;
  /** Whether lower values are better (inverts threshold display) */
  percentageThresholdInverse?: boolean;
}

/**
 * MetricGridItem structure (matches MetricCardProps with extras)
 */
export interface MetricGridItem {
  type: "metric";
  label: string;
  actualValue: number | null;
  valueType: MetricValueType;
  currency?: string;
  growthPercentage: number | null;
  historical: number[];
  chartData: TimeSeriesDataPoint[];
  percentageThresholdValues?: PercentageThresholdValues;
  percentageThresholdInverse?: boolean;
}

/**
 * Builds a MetricGridItem from configuration
 *
 * @example
 * ```ts
 * const item = buildMetricItem({
 *   label: t("lastBalanceSummary.netProfit"),
 *   valueType: "currency",
 *   data: getMetricData("LUCRO_LIQUIDO"),
 * });
 * ```
 */
export function buildMetricItem(config: MetricItemConfig): MetricGridItem {
  const {
    label,
    valueType,
    data,
    currency,
    percentageThresholdValues,
    percentageThresholdInverse,
  } = config;

  return {
    type: "metric",
    label,
    actualValue: data.actualValue,
    valueType,
    currency,
    growthPercentage: data.growthPercentage,
    historical: [...data.historical].reverse(),
    chartData: data.chartData,
    percentageThresholdValues,
    percentageThresholdInverse,
  };
}

/**
 * Extracts metric data from API response for a specific account
 *
 * @param accounts - Array of account objects from API
 * @param accountId - The account ID to find
 * @returns MetricData object with extracted values
 */
export function extractMetricData(
  accounts: Array<{
    id: string;
    valuesByQuarter?: Array<{ quarter?: string; value?: number }>;
  }>,
  accountId: string
): MetricData {
  const account = accounts.find((a) => a.id === accountId);
  const valuesByQuarter = account?.valuesByQuarter ?? [];

  const latestRaw = valuesByQuarter[0]?.value;
  const actualValue =
    typeof latestRaw === "number" && Number.isFinite(latestRaw)
      ? latestRaw
      : null;

  const historical = valuesByQuarter
    .map((entry) => entry?.value)
    .filter(
      (value): value is number =>
        typeof value === "number" && Number.isFinite(value)
    );

  // Clone and reverse for chart (oldest to newest)
  const chartData: TimeSeriesDataPoint[] = [...valuesByQuarter]
    .reverse()
    .map((entry) => ({
      quarter: entry.quarter,
      value: entry.value ?? 0,
    }));

  let growthPercentage: number | null = null;
  const baseRaw = valuesByQuarter[1]?.value;
  if (
    typeof latestRaw === "number" &&
    typeof baseRaw === "number" &&
    Number.isFinite(latestRaw) &&
    Number.isFinite(baseRaw) &&
    baseRaw !== 0
  ) {
    growthPercentage = latestRaw / baseRaw - 1;
  }

  return { actualValue, historical, growthPercentage, chartData };
}
