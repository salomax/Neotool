import { useQuery } from "@apollo/client/react";
import { GetEconomicIndicatorsDocument } from "../api/index.generated";

export type EconomicIndicator = {
  id: string;
  label: string;
  description?: string | null;
  unit: string;
  frequency: string;
  variations: {
    id: string;
    label: string;
    isDefault: boolean;
  }[];
};

export function useEconomicIndicators() {
  const { data, loading, error } = useQuery(GetEconomicIndicatorsDocument);
  
  return {
    indicators: (data?.economicIndicators || []) as EconomicIndicator[],
    loading,
    error
  };
}
