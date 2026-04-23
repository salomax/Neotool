import React from 'react';
import { useQuery } from '@apollo/client/react';
import { useDashboardStore } from '../store';
import { buildMonthRange, MonthYear, useTodayKey } from './useDashboardDateRange';
import {
  FinancialAccountsDocument,
  FinancialAccountsQuery,
} from '@/lib/graphql/operations/banking/queries.generated';

export interface DashboardDateRangeParams {
  startYear: number;
  startMonth: number;
  endYear: number;
  endMonth: number;
}

const DashboardDateRangeParamsContext = React.createContext<DashboardDateRangeParams | null | undefined>(undefined);

function toDateRange(months: MonthYear[]): DashboardDateRangeParams | null {
  if (months.length === 0) return null;
  const first = months[0]!;
  const last = months[months.length - 1]!;
  return {
    startYear: first.year,
    startMonth: first.month,
    endYear: last.year,
    endMonth: last.month,
  };
}

function toComparableKey(monthYear: MonthYear): number {
  return monthYear.year * 100 + monthYear.month;
}

function resolveGlobalDataSince(accounts: FinancialAccountsQuery['financialAccounts'] | undefined): MonthYear | null {
  if (!accounts || accounts.length === 0) return null;
  const months = accounts
    .map((account) => account.dataSince)
    .filter((value): value is NonNullable<typeof value> => value !== null);

  if (months.length === 0) return null;
  return months.reduce((min, current) =>
    toComparableKey(current) < toComparableKey(min) ? current : min
  );
}

export function DashboardDateRangeParamsProvider(props: { children: React.ReactNode }) {
  const { children } = props;
  const { option } = useDashboardStore((s) => s.dateRange);
  const todayKey = useTodayKey();
  const { data: accountsData } = useQuery(FinancialAccountsDocument, {
    fetchPolicy: 'cache-first',
  });

  const today = React.useMemo(() => {
    const [y, m, d] = todayKey.split('-').map((part) => Number(part));
    if (!y || !m || !d) return new Date();
    return new Date(y, m - 1, d);
  }, [todayKey]);

  const dataSince = React.useMemo(
    () => resolveGlobalDataSince(accountsData?.financialAccounts),
    [accountsData?.financialAccounts],
  );

  // Return null while accounts haven't loaded yet so chart queries stay
  // skipped (skip: range === null in useDashboardAnalytics) and fire exactly
  // once — with the correct range — instead of twice with wrong then right.
  const value = React.useMemo(
    () => (accountsData ? toDateRange(buildMonthRange(option, today, dataSince)) : null),
    [accountsData, dataSince, option, today],
  );

  return <DashboardDateRangeParamsContext.Provider value={value}>{children}</DashboardDateRangeParamsContext.Provider>;
}

export function useDashboardDateRangeParams(): DashboardDateRangeParams | null {
  const value = React.useContext(DashboardDateRangeParamsContext);
  if (value === undefined) {
    throw new Error('useDashboardDateRangeParams must be used within DashboardDateRangeParamsProvider');
  }
  return value;
}
