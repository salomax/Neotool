import { useQuery } from '@apollo/client/react';
import {
  CashFlowSeriesDocument,
  CashFlowSeriesQuery,
  DashboardPeriodSummaryDocument,
  DashboardPeriodSummaryQuery,
  CategoryBreakdownForRangeDocument,
  CategoryBreakdownForRangeQuery,
  PortfolioNetWorthSeriesDocument,
  PortfolioNetWorthSeriesQuery,
} from '@/lib/graphql/operations/banking/queries.generated';
import { CategoryApplicableTo } from '@/lib/graphql/types/__generated__/graphql';
import { useDashboardDateRangeParams } from './DashboardDateRangeParamsContext';

export type CashFlowDataPoint = CashFlowSeriesQuery['cashFlowSeries'][number];
export type CategoryBreakdownRow = CategoryBreakdownForRangeQuery['categoryBreakdownForRange'][number];
export type PortfolioMonthlyValue = PortfolioNetWorthSeriesQuery['portfolioNetWorthSeries'][number];
export type DashboardPeriodSummary = DashboardPeriodSummaryQuery['dashboardPeriodSummary'];

export function useCashFlowSeries() {
  const range = useDashboardDateRangeParams();
  const { data, loading, error, refetch } = useQuery(CashFlowSeriesDocument, {
    variables: range ?? { startYear: 0, startMonth: 1, endYear: 0, endMonth: 1 },
    skip: range === null,
    fetchPolicy: 'cache-and-network',
  });
  return {
    series: data?.cashFlowSeries ?? [],
    loading: range === null || loading,
    error: error ?? null,
    refetch,
  };
}

const EMPTY_DASHBOARD_PERIOD_SUMMARY: DashboardPeriodSummary = {
  __typename: 'DashboardPeriodSummary',
  totalIncome: 0,
  totalExpenses: 0,
  net: 0,
  monthCount: 0,
  avgMonthlyIncome: 0,
  avgMonthlyExpenses: 0,
  avgMonthlySavings: 0,
  savingsRate: null,
  closingCashBalance: 0,
  closingPortfolioValue: 0,
  totalNetWorth: 0,
  totalInvested: 0,
  avgMonthlyInvested: 0,
  totalReturn: 0,
  avgMonthlyReturn: 0,
  openingPortfolioValue: 0,
  returnPct: null,
  closingBalance: 0,
  openingBalance: 0,
};

export function useDashboardPeriodSummary() {
  const range = useDashboardDateRangeParams();
  const { data, loading, error, refetch } = useQuery(DashboardPeriodSummaryDocument, {
    variables: range ?? { startYear: 0, startMonth: 1, endYear: 0, endMonth: 1 },
    skip: range === null,
    fetchPolicy: 'cache-and-network',
  });
  return {
    summary: data?.dashboardPeriodSummary ?? EMPTY_DASHBOARD_PERIOD_SUMMARY,
    // When range is null, Apollo marks query as skipped (loading=false, data=undefined).
    // Keep loading=true so widgets render skeletons instead of misleading zeroed KPIs.
    loading: range === null || loading,
    error: error ?? null,
    refetch,
  };
}

export type CategoryBreakdownForRangeOptions = {
  enabled?: boolean;
};

export function useCategoryBreakdownForRange(
  applicableTo: CategoryApplicableTo,
  options?: CategoryBreakdownForRangeOptions
) {
  const range = useDashboardDateRangeParams();
  const enabled = options?.enabled ?? true;
  const { data, loading, error, refetch } = useQuery(CategoryBreakdownForRangeDocument, {
    variables: range
      ? { ...range, applicableTo }
      : { startYear: 0, startMonth: 1, endYear: 0, endMonth: 1, applicableTo },
    skip: range === null || !enabled,
    fetchPolicy: 'cache-and-network',
  });
  return {
    breakdown: data?.categoryBreakdownForRange ?? [],
    loading: enabled ? loading : false,
    error: error ?? null,
    refetch,
  };
}

export function usePortfolioValueSeries() {
  const range = useDashboardDateRangeParams();
  const { data, loading, error, refetch } = useQuery(PortfolioNetWorthSeriesDocument, {
    variables: range ?? { startYear: 0, startMonth: 1, endYear: 0, endMonth: 1 },
    skip: range === null,
    fetchPolicy: 'cache-and-network',
  });
  return {
    portfolioSeries: data?.portfolioNetWorthSeries ?? [],
    loading: range === null || loading,
    error: error ?? null,
    refetch,
  };
}
