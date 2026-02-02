import { useQuery } from "@apollo/client/react";
import { IndicatorsLatestDocument } from "../api/index.generated";

export type IndicatorLatest = {
  indicator: string;
  variation: string;
  unit: string;
  latest: {
    referenceDate: any;
    value: number;
  } | null;
};

export function useIndicatorsLatest() {
  const { data, loading, error } = useQuery(IndicatorsLatestDocument);
  
  return {
    indicatorsLatest: (data?.indicatorsLatest || []) as IndicatorLatest[],
    loading,
    error
  };
}
