import { useQuery } from "@apollo/client/react";
import { useMemo } from "react";
import { GET_POPULAR_INSTITUTIONS } from "../api";
import { 
  PopularInstitution, 
  TopInstitutionMetric, 
  PopularInstitutionTopMetricItem,
  Query
} from "@/lib/graphql/types/__generated__/graphql";

export interface RankingTableData {
  metric: TopInstitutionMetric;
  items: PopularInstitutionTopMetricItem[];
}

export function usePopularInstitutions(anoMes?: string) {
  const { data, loading, error } = useQuery<Query>(GET_POPULAR_INSTITUTIONS, {
    variables: { anoMes },
    fetchPolicy: "cache-and-network",
  });

  const popularInstitutions = useMemo(() => {
    return data?.popularInstitutions || [];
  }, [data]);

  const rankings = useMemo(() => {
    // Initialize result object with empty arrays for each metric
    const result: Record<TopInstitutionMetric, PopularInstitutionTopMetricItem[]> = {
      [TopInstitutionMetric.Profit]: [],
      [TopInstitutionMetric.BasileiaIndex]: [],
      [TopInstitutionMetric.MobilizationIndex]: [],
    };

    if (!popularInstitutions.length) return result;

    // Collect metrics from all popular institutions
    popularInstitutions.forEach((inst: PopularInstitution) => {
        if (inst.popularInstitutionTopMetrics) {
            inst.popularInstitutionTopMetrics.forEach((metricData) => {
                if (metricData.popularInstitutionTopMetricItem && metricData.metric) {
                    // Push to the correct list based on metric
                    const list = result[metricData.metric as TopInstitutionMetric];
                    if (list) {
                        list.push(metricData.popularInstitutionTopMetricItem);
                    }
                }
            });
        }
    });

    // Sort and deduplicate each list
    Object.values(TopInstitutionMetric).forEach((metric) => {
        const metricKey = metric as TopInstitutionMetric;
        if (result[metricKey]) {
            // Deduplicate by rank
            const unique = new Map<number, PopularInstitutionTopMetricItem>();
            result[metricKey].forEach(item => {
                if (!unique.has(item.rank)) {
                    unique.set(item.rank, item);
                }
            });
            
            // Sort by rank ascending (1 is best)
            result[metricKey] = Array.from(unique.values())
                .sort((a, b) => a.rank - b.rank)
                .slice(0, 10);
        }
    });

    return result;
  }, [popularInstitutions]);

  const quarterInfo = useMemo(() => {
    if (!popularInstitutions.length) return null;
    const first = popularInstitutions[0];
    // Cast to any because the types might not be generated yet, but the query fetches them
    const item = first as any;
    return {
      quarter: item.quarter,
      year: item.year,
      month: item.month,
    };
  }, [popularInstitutions]);

  return {
    popularInstitutions,
    rankings,
    quarterInfo,
    loading,
    error,
  };
}
