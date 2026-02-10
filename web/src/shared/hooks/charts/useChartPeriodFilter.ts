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

export interface UseChartPeriodFilterOptions {
  valueType?: string;
  disablePercentageScaling?: boolean;
}

/**
 * Hook to filter chart data by period and calculate growth
 *
 * @param data - Array of data points (oldest to newest)
 * @param period - Period string (e.g., "1Y", "3Y", "5Y")
 * @param options - Options for calculation
 * @returns Filtered data and calculated growth for the period
 *
 * @example
 * ```tsx
 * const { filteredData, periodGrowth } = useChartPeriodFilter(chartData, "3Y");
 * ```
 */
export function useChartPeriodFilter(
  data: TimeSeriesDataPoint[] | undefined | null,
  period: string,
  options?: UseChartPeriodFilterOptions
): ChartPeriodFilterResult {
  const { valueType, disablePercentageScaling } = options || {};

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
    const startItem = filteredData[0];

    let periodGrowth: number | null = null;
    if (
      currentItem?.value != null &&
      startItem?.value != null
    ) {
      if (valueType === "percentage") {
        const diff = currentItem.value - startItem.value;
        // If disablePercentageScaling is true, values are like 10.5 (%), so diff is percentage points.
        // We divide by 100 to get a ratio for the GrowthChip (which expects 0.1 for 10%).
        // If disablePercentageScaling is false, values are like 0.105, so diff is 0.0...
        // Wait, if disablePercentageScaling is true, then diff is e.g. 5 (5%).
        // GrowthChip expects ratio. So 5% -> 0.05.
        // So we divide by 100.
        // If disablePercentageScaling is false, values are like 0.15. Diff is 0.05.
        // GrowthChip expects ratio. So 0.05 is correct.
        periodGrowth = disablePercentageScaling ? diff / 100 : diff;
      } else if (startItem.value !== 0) {
        periodGrowth =
          (currentItem.value - startItem.value) / Math.abs(startItem.value);
      }
    }

    return { filteredData, periodGrowth };
  }, [data, period, valueType, disablePercentageScaling]);
}
