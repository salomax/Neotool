import { describe, expect, it } from 'vitest';
import { buildPortfolioAllocationLegendRows } from '../InvestmentAllocationChartWidget';
import type { PortfolioAllocationGroupKey, PortfolioAllocationSeriesRow } from '../../hooks/useDashboardAnalytics';

const COLORS: Record<PortfolioAllocationGroupKey, string> = {
  cash: '#111111',
  fixedIncome: '#222222',
  mutualFunds: '#333333',
  pension: '#444444',
  structured: '#555555',
  variableIncome: '#666666',
  others: '#777777',
};

function row(input: Partial<PortfolioAllocationSeriesRow>): PortfolioAllocationSeriesRow {
  return {
    monthKey: input.monthKey ?? '2026-01',
    cash: input.cash ?? 0,
    variableIncome: input.variableIncome ?? 0,
    fixedIncome: input.fixedIncome ?? 0,
    mutualFunds: input.mutualFunds ?? 0,
    pension: input.pension ?? 0,
    structured: input.structured ?? 0,
    others: input.others ?? 0,
    totalGrossValue: input.totalGrossValue ?? 0,
  };
}

describe('buildPortfolioAllocationLegendRows', () => {
  const resolveLabel = (groupKey: PortfolioAllocationGroupKey) =>
    `translated:${groupKey}`;

  it('sorts legend entries by amount desc and applies labels/colors', () => {
    const rows = buildPortfolioAllocationLegendRows({
      row: row({
        cash: 200,
        fixedIncome: 300,
        variableIncome: 500,
        totalGrossValue: 1000,
      }),
      resolveLabel,
      colors: COLORS,
    });

    expect(rows).toHaveLength(3);
    expect(rows[0]).toMatchObject({
      groupKey: 'variableIncome',
      label: 'translated:variableIncome',
      amount: 500,
      color: COLORS.variableIncome,
    });
    expect(rows[1]).toMatchObject({
      groupKey: 'fixedIncome',
      label: 'translated:fixedIncome',
      amount: 300,
      color: COLORS.fixedIncome,
    });
    expect(rows[2]).toMatchObject({
      groupKey: 'cash',
      label: 'translated:cash',
      amount: 200,
      color: COLORS.cash,
    });
  });

  it('returns empty for null or zero-total rows', () => {
    const emptyFromNull = buildPortfolioAllocationLegendRows({
      row: null,
      resolveLabel,
      colors: COLORS,
    });
    const emptyFromZero = buildPortfolioAllocationLegendRows({
      row: row({ cash: 0, totalGrossValue: 0 }),
      resolveLabel,
      colors: COLORS,
    });

    expect(emptyFromNull).toEqual([]);
    expect(emptyFromZero).toEqual([]);
  });

  it('filters out groups with zero amount', () => {
    const rows = buildPortfolioAllocationLegendRows({
      row: row({
        cash: 0,
        fixedIncome: 500,
        mutualFunds: 0,
        variableIncome: 500,
        totalGrossValue: 1000,
      }),
      resolveLabel,
      colors: COLORS,
    });

    expect(rows.map((item) => item.groupKey)).toEqual(['fixedIncome', 'variableIncome']);
  });
});
