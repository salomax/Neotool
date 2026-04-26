import { describe, expect, it } from 'vitest';
import { buildInvestmentTreemapChartNodes } from '../InvestmentTreemapWidget';

describe('buildInvestmentTreemapChartNodes', () => {
  it('maps rows to chart nodes with localized labels and non-zero values', () => {
    const nodes = buildInvestmentTreemapChartNodes({
      rows: [
        {
          groupKey: 'fixedIncome',
          label: 'Fixed Income',
          count: 2,
          totalGrossValue: 1000,
          allocationPercent: 0.7,
          isCash: false,
        },
        {
          groupKey: 'cash',
          label: 'Cash',
          count: 0,
          totalGrossValue: 0,
          allocationPercent: 0.3,
          isCash: true,
        },
      ],
      resolveLabel: (groupKey) => `translated:${groupKey}`,
      resolveColor: () => '#123456',
    });

    expect(nodes).toHaveLength(1);
    expect(nodes[0]).toMatchObject({
      groupKey: 'fixedIncome',
      name: 'translated:fixedIncome',
      value: 1000,
    });
    expect(nodes[0].fill).toContain('rgba(');
    expect(nodes[0].stroke).toContain('rgba(');
  });
});
