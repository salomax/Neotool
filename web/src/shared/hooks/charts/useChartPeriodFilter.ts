import { useMemo } from "react";
import { getQuartersForPeriod, type TimeSeriesDataPoint } from "@/shared/types/charts";

export interface ChartPeriodFilterResult {
  /**
   * Filtered chart data for the selected period
   */
  filteredData: TimeSeriesDataPoint[];
  /**
   * Growth percentage for the selected period (current vs comparison)
   */
  periodGrowth: number | null;
}

/**
 * Hook to filter chart data by period and calculate growth
 *
 * @param data - Array of data points (oldest to newest)
 * @param period - Period string (e.g., "1Y", "3Y", "5Y")
 * @returns Filtered data and calculated growth for the period
 *
 * @example
 * ```tsx
 * const { filteredData, periodGrowth } = useChartPeriodFilter(chartData, "3Y");
 * ```
 */
export function useChartPeriodFilter(
  data: TimeSeriesDataPoint[] | undefined | null,
  period: string
): ChartPeriodFilterResult {
  return useMemo(() => {
    if (!data || data.length === 0) {
      return { filteredData: [], periodGrowth: null };
    }

    const quarters = getQuartersForPeriod(period);

    // Filter chart data - take the last N quarters
    const startIndex = Math.max(0, data.length - quarters);
    const filteredData = data.slice(startIndex);

    // Calculate growth
    const currentItem = data[data.length - 1];
    const comparisonIndex = data.length - 1 - quarters;
    const comparisonItem = data[comparisonIndex];

    let periodGrowth: number | null = null;
    if (
      currentItem?.value != null &&
      comparisonItem?.value != null &&
      comparisonItem.value !== 0
    ) {
      periodGrowth =
        (currentItem.value - comparisonItem.value) / Math.abs(comparisonItem.value);
    }

    return { filteredData, periodGrowth };
  }, [data, period]);
}
