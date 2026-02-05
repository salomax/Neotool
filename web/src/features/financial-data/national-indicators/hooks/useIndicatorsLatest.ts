import { useQuery } from "@apollo/client/react";
import { useMemo } from "react";
import { IndicatorsLatestDocument } from "../api/index.generated";

export type IndicatorLatest = {
  indicator: string;
  variation: string;
  unit: string;
  latest: {
    referenceDate: any;
    value: number;
  } | null;
  series: {
    referenceDate: any;
    value: number;
  }[];
};

export function useIndicatorsLatest() {
  const { data, loading, error } = useQuery(IndicatorsLatestDocument);
  
  const indicatorsLatest: IndicatorLatest[] = useMemo(() => {
    return (data?.economicIndicators || []).flatMap(indicator => 
    indicator.variations.map(variation => {
      const series = variation.series;
      const seriesValues = series?.values || [];
      
      // Sort by date descending (newest first) to ensure latest value is at index 0
      const sortedSeriesValues = [...seriesValues].sort((a, b) => {
        return new Date(b.referenceDate as any).getTime() - new Date(a.referenceDate as any).getTime();
      });
      
      const latestValue = sortedSeriesValues[0];
      
      return {
        indicator: indicator.label,
        variation: variation.label,
        unit: series?.unit || "",
        latest: latestValue ? {
          referenceDate: latestValue.referenceDate,
          value: latestValue.value
        } : null,
        series: sortedSeriesValues.map(v => ({
            referenceDate: v.referenceDate,
            value: v.value
        }))
      };
    })
  );
  }, [data]);

  return {
    indicatorsLatest,
    loading,
    error
  };
}
