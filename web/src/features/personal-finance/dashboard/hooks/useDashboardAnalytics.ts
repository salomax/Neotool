import { useQuery } from '@apollo/client/react';
import {
  CashFlowSeriesDocument,
  CashFlowSeriesQuery,
  DashboardPeriodSummaryDocument,
  DashboardPeriodSummaryQuery,
  CategoryBreakdownForRangeDocument,
  CategoryBreakdownForRangeQuery,
  InvestmentPortfolioSummaryDocument,
  InvestmentPortfolioSummaryQuery,
  InvestmentPortfolioSummaryQueryVariables,
  PortfolioAllocationSeriesDocument,
  PortfolioAllocationSeriesQuery,
  PortfolioAllocationSeriesQueryVariables,
  PortfolioNetWorthSeriesDocument,
  PortfolioNetWorthSeriesQuery,
} from '@/lib/graphql/operations/banking/queries.generated';
import {
  CategoryApplicableTo,
  PerformancePeriod,
} from '@/lib/graphql/types/__generated__/graphql';
import { useDashboardDateRangeParams } from './DashboardDateRangeParamsContext';

export type CashFlowDataPoint = CashFlowSeriesQuery['cashFlowSeries'][number];
export type CategoryBreakdownRow = CategoryBreakdownForRangeQuery['categoryBreakdownForRange'][number];
export type PortfolioMonthlyValue = PortfolioNetWorthSeriesQuery['portfolioNetWorthSeries'][number];
export type DashboardPeriodSummary = DashboardPeriodSummaryQuery['dashboardPeriodSummary'];
export type PortfolioAllocationGroupKey =
  | 'cash'
  | 'variableIncome'
  | 'fixedIncome'
  | 'mutualFunds'
  | 'pension'
  | 'structured'
  | 'others';

export interface PortfolioAllocationSeriesRow {
  monthKey: string;
  cash: number;
  variableIncome: number;
  fixedIncome: number;
  mutualFunds: number;
  pension: number;
  structured: number;
  others: number;
  totalGrossValue: number;
}

export interface InvestmentTreemapRow {
  groupKey: string;
  label: string;
  count: number;
  totalGrossValue: number;
  allocationPercent: number;
  isCash: boolean;
}
type InvestmentTreemapGroup =
  InvestmentPortfolioSummaryQuery['investmentPortfolioSummary']['byAssetGroup'][number];

const ALLOCATION_GROUP_FIELDS = [
  { groupKey: 'cash', field: 'perfCash' },
  { groupKey: 'variableIncome', field: 'perfVariableIncome' },
  { groupKey: 'fixedIncome', field: 'perfFixedIncome' },
  { groupKey: 'mutualFunds', field: 'perfMutualFunds' },
  { groupKey: 'pension', field: 'perfPension' },
  { groupKey: 'structured', field: 'perfStructured' },
  { groupKey: 'others', field: 'perfOthers' },
] as const satisfies ReadonlyArray<{
  groupKey: PortfolioAllocationGroupKey;
  field: keyof PortfolioAllocationSeriesQuery;
}>;

function toMonthKey(timestamp: unknown): string | null {
  if (typeof timestamp !== 'string' || timestamp.length === 0) return null;
  const parsed = new Date(timestamp);
  if (Number.isFinite(parsed.getTime())) {
    // Backend always returns ISO 8601 UTC timestamps (Z suffix); getUTC* is intentional.
    const year = parsed.getUTCFullYear();
    const month = String(parsed.getUTCMonth() + 1).padStart(2, '0');
    return `${year}-${month}`;
  }
  const fallback = timestamp.slice(0, 7);
  return /^\d{4}-\d{2}$/.test(fallback) ? fallback : null;
}

function asFiniteNonNegative(value: unknown): number {
  const n = typeof value === 'number' ? value : Number.NaN;
  if (!Number.isFinite(n)) return 0;
  return Math.max(0, n);
}

export function buildPortfolioAllocationSeriesRows(
  data: PortfolioAllocationSeriesQuery | null | undefined,
): PortfolioAllocationSeriesRow[] {
  type MutableRow = Omit<PortfolioAllocationSeriesRow, 'totalGrossValue'>;
  const byMonth = new Map<string, MutableRow>();

  for (const group of ALLOCATION_GROUP_FIELDS) {
    const series = data?.[group.field];
    for (const point of series?.points ?? []) {
      const monthKey = toMonthKey(point?.timestamp);
      if (!monthKey) continue;

      const row =
        byMonth.get(monthKey) ??
        {
          monthKey,
          cash: 0,
          variableIncome: 0,
          fixedIncome: 0,
          mutualFunds: 0,
          pension: 0,
          structured: 0,
          others: 0,
        };

      row[group.groupKey] += asFiniteNonNegative(point?.grossValue);
      byMonth.set(monthKey, row);
    }
  }

  return [...byMonth.values()]
    .map((row) => {
      const totalGrossValue =
        row.cash +
        row.variableIncome +
        row.fixedIncome +
        row.mutualFunds +
        row.pension +
        row.structured +
        row.others;
      return {
        ...row,
        totalGrossValue,
      };
    })
    .filter((row) => row.totalGrossValue > 0)
    .sort((a, b) => a.monthKey.localeCompare(b.monthKey));
}

function resolvePortfolioAllocationCurrencyCode(
  data: PortfolioAllocationSeriesQuery | null | undefined,
): string {
  for (const group of ALLOCATION_GROUP_FIELDS) {
    const currencyCode = data?.[group.field]?.currencyCode;
    if (typeof currencyCode === 'string' && currencyCode.length > 0) return currencyCode;
  }
  return 'BRL';
}

function asFinite(value: unknown): number {
  const n = typeof value === 'number' ? value : Number.NaN;
  return Number.isFinite(n) ? n : 0;
}

function asFiniteUnitInterval(value: unknown): number {
  const n = asFinite(value);
  return Math.min(1, Math.max(0, n));
}

export function buildInvestmentTreemapRows(
  groups: readonly InvestmentTreemapGroup[] | null | undefined,
): InvestmentTreemapRow[] {
  return (groups ?? [])
    .map((group) => ({
      groupKey: group.groupKey,
      label: group.label,
      count: Math.max(0, asFinite(group.count)),
      totalGrossValue: Math.max(0, asFinite(group.totalGrossValue)),
      allocationPercent: asFiniteUnitInterval(group.allocationPercent),
      isCash: Boolean(group.isCash),
    }))
    .filter((row) => row.totalGrossValue > 0 && row.allocationPercent > 0)
    .sort((a, b) => b.totalGrossValue - a.totalGrossValue);
}

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

export function usePortfolioAllocationSeries(financialAccountId?: string | null) {
  const { data, loading, error, refetch } = useQuery<
    PortfolioAllocationSeriesQuery,
    PortfolioAllocationSeriesQueryVariables
  >(PortfolioAllocationSeriesDocument, {
    variables: { financialAccountId: financialAccountId ?? undefined },
    fetchPolicy: 'cache-and-network',
  });

  return {
    series: buildPortfolioAllocationSeriesRows(data),
    currencyCode: resolvePortfolioAllocationCurrencyCode(data),
    loading,
    error: error ?? null,
    refetch,
  };
}

export function useInvestmentTreemapSummary(financialAccountId?: string | null) {
  const { data, loading, error, refetch } = useQuery<
    InvestmentPortfolioSummaryQuery,
    InvestmentPortfolioSummaryQueryVariables
  >(InvestmentPortfolioSummaryDocument, {
    variables: {
      financialAccountId: financialAccountId ?? undefined,
      period: PerformancePeriod.All,
    },
    fetchPolicy: 'cache-and-network',
  });

  const summary = data?.investmentPortfolioSummary;

  return {
    rows: buildInvestmentTreemapRows(summary?.byAssetGroup),
    currencyCode: summary?.currencyCode ?? 'BRL',
    totalPortfolioValue: asFinite(summary?.totalPortfolioValue),
    loading,
    error: error ?? null,
    refetch,
  };
}
