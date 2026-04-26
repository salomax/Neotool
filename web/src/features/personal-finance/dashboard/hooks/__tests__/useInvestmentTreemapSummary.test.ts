import { describe, expect, it } from 'vitest';
import { buildInvestmentTreemapRows } from '../useDashboardAnalytics';

describe('buildInvestmentTreemapRows', () => {
  it('normalizes, filters and sorts rows by total gross value', () => {
    const rows = buildInvestmentTreemapRows([
      {
        __typename: 'AssetGroupSummary',
        groupKey: 'others',
        label: 'Others',
        count: 2,
        totalGrossValue: 150,
        allocationPercent: 0.1,
        isCash: false,
      },
      {
        __typename: 'AssetGroupSummary',
        groupKey: 'fixedIncome',
        label: 'Fixed Income',
        count: 3,
        totalGrossValue: 500,
        allocationPercent: 0.55,
        isCash: false,
      },
      {
        __typename: 'AssetGroupSummary',
        groupKey: 'cash',
        label: 'Cash',
        count: 0,
        totalGrossValue: -10,
        allocationPercent: 0.35,
        isCash: true,
      },
      {
        __typename: 'AssetGroupSummary',
        groupKey: 'mutualFunds',
        label: 'Funds',
        count: 1,
        totalGrossValue: 40,
        allocationPercent: -2,
        isCash: false,
      },
    ] as any);

    expect(rows).toEqual([
      expect.objectContaining({
        groupKey: 'fixedIncome',
        label: 'Fixed Income',
        count: 3,
        totalGrossValue: 500,
        allocationPercent: 0.55,
      }),
      expect.objectContaining({
        groupKey: 'others',
        label: 'Others',
        count: 2,
        totalGrossValue: 150,
        allocationPercent: 0.1,
      }),
    ]);
  });

  it('returns empty when summary has no valid groups', () => {
    const rowsFromNull = buildInvestmentTreemapRows(null as any);
    const rowsFromEmpty = buildInvestmentTreemapRows([] as any);

    expect(rowsFromNull).toEqual([]);
    expect(rowsFromEmpty).toEqual([]);
  });
});
