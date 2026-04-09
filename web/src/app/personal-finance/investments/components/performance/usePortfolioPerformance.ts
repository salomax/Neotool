"use client";

import { useQuery } from "@apollo/client/react";
import {
  HoldingPerformanceResolution,
} from "@/lib/graphql/types/__generated__/graphql";
import {
  PortfolioPerformanceDocument,
  type PortfolioPerformanceQuery,
  type PortfolioPerformanceQueryVariables,
} from "@/lib/graphql/operations/banking/queries.generated";
import { mapPortfolioPerformanceToChartPoints } from "./portfolioPerformanceAdapter";

export function usePortfolioPerformance(groupKey?: string, includeProjection = false) {
  const query = useQuery<PortfolioPerformanceQuery, PortfolioPerformanceQueryVariables>(
    PortfolioPerformanceDocument,
    {
      variables: {
        groupKey,
        includeProjection,
        resolution: HoldingPerformanceResolution.Month,
      },
    },
  );

  return {
    ...query,
    series: query.data?.portfolioPerformance,
    chartPoints: mapPortfolioPerformanceToChartPoints(query.data?.portfolioPerformance),
  };
}
