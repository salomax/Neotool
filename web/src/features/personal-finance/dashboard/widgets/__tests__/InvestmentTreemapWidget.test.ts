import { describe, expect, it } from 'vitest';
import {
  buildHoldingTreemapNodes,
  buildInvestmentTreemapChartNodes,
} from '../InvestmentTreemapWidget';

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

describe('buildHoldingTreemapNodes', () => {
  it('filters holdings by selected group and sorts by value desc', () => {
    const nodes = buildHoldingTreemapNodes({
      holdings: [
        {
          id: 'h-1',
          grossValue: 100,
          asset: {
            id: 'a-1',
            name: 'PETR4',
            ticker: 'PETR4',
            assetType: 'STOCK',
            currencyCode: 'BRL',
            __typename: 'InvestmentAsset',
          },
          financialAccount: {
            id: 'fa-1',
            name: 'XP',
            currencyCode: 'BRL',
            __typename: 'FinancialAccount',
          },
          __typename: 'InvestmentHolding',
        },
        {
          id: 'h-2',
          grossValue: 300,
          asset: {
            id: 'a-2',
            name: 'VALE3',
            ticker: 'VALE3',
            assetType: 'STOCK',
            currencyCode: 'BRL',
            __typename: 'InvestmentAsset',
          },
          financialAccount: {
            id: 'fa-2',
            name: 'Rico',
            currencyCode: 'BRL',
            __typename: 'FinancialAccount',
          },
          __typename: 'InvestmentHolding',
        },
        {
          id: 'h-3',
          grossValue: 200,
          asset: {
            id: 'a-3',
            name: 'Tesouro Selic',
            ticker: null,
            assetType: 'TREASURY',
            currencyCode: 'BRL',
            __typename: 'InvestmentAsset',
          },
          financialAccount: {
            id: 'fa-3',
            name: 'Inter',
            currencyCode: 'BRL',
            __typename: 'FinancialAccount',
          },
          __typename: 'InvestmentHolding',
        },
      ],
      cashBalance: 0,
      selectedGroupKey: 'variableIncome',
      baseColor: '#123456',
      groupTotalGrossValue: 400,
    });

    expect(nodes).toHaveLength(2);
    expect(nodes.map((node) => node.holdingId)).toEqual(['h-2', 'h-1']);
    expect(nodes[0]?.allocationPercent).toBeCloseTo(0.75);
    expect(nodes[1]?.allocationPercent).toBeCloseTo(0.25);
  });

  it('excludes non-positive holdings and returns single cash leaf for cash group', () => {
    const nonCashNodes = buildHoldingTreemapNodes({
      holdings: [
        {
          id: 'h-1',
          grossValue: 0,
          asset: {
            id: 'a-1',
            name: 'PETR4',
            ticker: 'PETR4',
            assetType: 'STOCK',
            currencyCode: 'BRL',
            __typename: 'InvestmentAsset',
          },
          financialAccount: {
            id: 'fa-1',
            name: 'XP',
            currencyCode: 'BRL',
            __typename: 'FinancialAccount',
          },
          __typename: 'InvestmentHolding',
        },
      ],
      cashBalance: 0,
      selectedGroupKey: 'variableIncome',
      baseColor: '#123456',
      groupTotalGrossValue: 0,
    });

    const cashNodes = buildHoldingTreemapNodes({
      holdings: [],
      cashBalance: 500,
      selectedGroupKey: 'cash',
      baseColor: '#123456',
      groupTotalGrossValue: 0,
    });

    expect(nonCashNodes).toHaveLength(0);
    expect(cashNodes).toHaveLength(1);
    expect(cashNodes[0]).toMatchObject({
      holdingId: 'cash-balance',
      value: 500,
      allocationPercent: 1,
      groupKey: 'cash',
    });
  });
});
