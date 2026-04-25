import { describe, expect, it } from 'vitest';
import { buildNetWorthChartData } from '../NetWorthWidget';
import type { CashFlowDataPoint, PortfolioMonthlyValue } from '../../hooks/useDashboardAnalytics';

function cashFlowPoint(input: {
  year: number;
  month: number;
  closingBalance: number;
  totalIncome?: number;
  totalExpenses?: number;
  net?: number;
}): CashFlowDataPoint {
  return {
    __typename: 'CashFlowDataPoint',
    year: input.year,
    month: input.month,
    totalIncome: input.totalIncome ?? 0,
    totalExpenses: input.totalExpenses ?? 0,
    net: input.net ?? 0,
    closingBalance: input.closingBalance,
  };
}

function portfolioPoint(input: {
  year: number;
  month: number;
  grossValue: number;
  isEstimated?: boolean;
}): PortfolioMonthlyValue {
  return {
    __typename: 'PortfolioMonthlyValue',
    year: input.year,
    month: input.month,
    grossValue: input.grossValue,
    isEstimated: input.isEstimated ?? false,
  };
}

describe('buildNetWorthChartData', () => {
  const labelFormatter = (year: number, month: number) => `${year}-${String(month).padStart(2, '0')}`;

  it('returns empty when both series are empty', () => {
    const rows = buildNetWorthChartData({ cashFlowSeries: [], portfolioSeries: [], labelFormatter });
    expect(rows).toEqual([]);
  });

  it('handles cashFlow-only scenario (total = closingBalance)', () => {
    const rows = buildNetWorthChartData({
      cashFlowSeries: [cashFlowPoint({ year: 2026, month: 4, closingBalance: 91502.19 })],
      portfolioSeries: [],
      labelFormatter,
    });

    expect(rows).toHaveLength(1);
    expect(rows[0]).toMatchObject({
      label: '2026-04',
      year: 2026,
      month: 4,
      bankingBalance: 91502.19,
      portfolioValue: 0,
      totalNetWorth: 91502.19,
      isEstimated: false,
    });
  });

  it('handles portfolio-only scenario (total = grossValue)', () => {
    const rows = buildNetWorthChartData({
      cashFlowSeries: [],
      portfolioSeries: [portfolioPoint({ year: 2026, month: 4, grossValue: 5000 })],
      labelFormatter,
    });

    expect(rows).toHaveLength(1);
    expect(rows[0]).toMatchObject({
      label: '2026-04',
      year: 2026,
      month: 4,
      bankingBalance: 0,
      portfolioValue: 5000,
      totalNetWorth: 5000,
      isEstimated: false,
    });
  });

  it('handles combined scenario (total = cash + portfolio) and preserves isEstimated', () => {
    const rows = buildNetWorthChartData({
      cashFlowSeries: [cashFlowPoint({ year: 2026, month: 4, closingBalance: 100 })],
      portfolioSeries: [portfolioPoint({ year: 2026, month: 4, grossValue: 250, isEstimated: true })],
      labelFormatter,
    });

    expect(rows).toHaveLength(1);
    expect(rows[0]).toMatchObject({
      bankingBalance: 100,
      portfolioValue: 250,
      totalNetWorth: 350,
      isEstimated: true,
    });
  });

  it('builds a union of months across both series and sorts ascending', () => {
    const rows = buildNetWorthChartData({
      cashFlowSeries: [cashFlowPoint({ year: 2026, month: 3, closingBalance: 10 })],
      portfolioSeries: [portfolioPoint({ year: 2026, month: 4, grossValue: 20 })],
      labelFormatter,
    });

    expect(rows).toHaveLength(2);
    expect(rows.map((r) => r.label)).toEqual(['2026-03', '2026-04']);
    expect(rows[0].totalNetWorth).toBe(10);
    expect(rows[1].totalNetWorth).toBe(20);
  });
});

