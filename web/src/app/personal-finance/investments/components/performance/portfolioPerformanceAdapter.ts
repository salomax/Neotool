"use client";

import type { PortfolioPerformanceQuery } from "@/lib/graphql/operations/banking/queries.generated";
import type { HoldingPerformanceChartPoint } from "./HoldingPerformanceChart";
import { withPartialMonthFlags } from "./withPartialMonthFlags";

type PortfolioPerformance = NonNullable<PortfolioPerformanceQuery["portfolioPerformance"]>;

export function mapPortfolioPerformanceToChartPoints(
  performance: PortfolioPerformance | null | undefined,
): HoldingPerformanceChartPoint[] {
  if (!performance) return [];
  return withPartialMonthFlags(
    performance.points.map((point) => ({
      timestamp: String(point.timestamp),
      isProjected: point.isProjected,
      investedPrincipal: point.investedPrincipal,
      grossValue: point.grossValue,
      netValue: point.netValue,
    })),
  );
}
