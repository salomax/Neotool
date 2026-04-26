import { describe, expect, it } from 'vitest';
import { buildPortfolioAllocationSeriesRows } from '../useDashboardAnalytics';

describe('buildPortfolioAllocationSeriesRows', () => {
  it('merges monthly points from multiple groups and sorts by month', () => {
    const rows = buildPortfolioAllocationSeriesRows({
      perfCash: {
        __typename: 'PortfolioPerformanceSeries',
        groupKey: 'cash',
        label: 'Cash',
        currencyCode: 'BRL',
        points: [
          { __typename: 'HoldingPerformancePoint', timestamp: '2026-01-31T00:00:00Z', grossValue: 100 },
          { __typename: 'HoldingPerformancePoint', timestamp: '2026-02-28T00:00:00Z', grossValue: 120 },
        ],
      },
      perfFixedIncome: {
        __typename: 'PortfolioPerformanceSeries',
        groupKey: 'fixedIncome',
        label: 'Fixed Income',
        currencyCode: 'BRL',
        points: [
          { __typename: 'HoldingPerformancePoint', timestamp: '2026-02-15T00:00:00Z', grossValue: 300 },
        ],
      },
      perfVariableIncome: null,
      perfMutualFunds: null,
      perfPension: null,
      perfStructured: null,
      perfOthers: null,
    } as any);

    expect(rows).toEqual([
      expect.objectContaining({
        monthKey: '2026-01',
        cash: 100,
        fixedIncome: 0,
        totalGrossValue: 100,
      }),
      expect.objectContaining({
        monthKey: '2026-02',
        cash: 120,
        fixedIncome: 300,
        totalGrossValue: 420,
      }),
    ]);
  });

  it('ignores invalid/negative values and zero-total months', () => {
    const rows = buildPortfolioAllocationSeriesRows({
      perfCash: {
        __typename: 'PortfolioPerformanceSeries',
        groupKey: 'cash',
        label: 'Cash',
        currencyCode: 'BRL',
        points: [
          { __typename: 'HoldingPerformancePoint', timestamp: '2026-01-31T00:00:00Z', grossValue: -50 },
          { __typename: 'HoldingPerformancePoint', timestamp: 'invalid', grossValue: 100 },
          { __typename: 'HoldingPerformancePoint', timestamp: '2026-02-28T00:00:00Z', grossValue: 50 },
        ],
      },
      perfFixedIncome: null,
      perfVariableIncome: null,
      perfMutualFunds: null,
      perfPension: null,
      perfStructured: null,
      perfOthers: null,
    } as any);

    expect(rows).toEqual([
      expect.objectContaining({
        monthKey: '2026-02',
        cash: 50,
        totalGrossValue: 50,
      }),
    ]);
  });
});
