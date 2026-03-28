import { describe, expect, it } from "vitest";
import {
  AssetType,
  HoldingPerformanceResolution,
  Indexer,
  InvestmentSource,
  InvestmentTransactionDirection,
  InvestmentTransactionType,
  ValuationSource,
} from "../../../../../../lib/graphql/types/__generated__/graphql";
import { buildFixedIncomeHoldingDetailViewModel } from "../fixedIncomeHoldingDetailAdapter";
import type { FixedIncomeHoldingDetailQuery } from "../api/queries.generated";

function t(key: string, options?: Record<string, unknown>) {
  return String(options?.defaultValue ?? key);
}

function buildHolding(): NonNullable<FixedIncomeHoldingDetailQuery["investmentHolding"]> {
  return {
    __typename: "InvestmentHolding",
    id: "holding-1",
    quantity: 1,
    averageCost: 1000,
    grossValue: 1120,
    netValue: 1101,
    incomeTax: 15,
    iof: 4,
    valuationSource: ValuationSource.Calculated,
    valuationReferenceDate: "2026-03-26",
    lastValuedAt: "2026-03-26T00:00:00Z",
    issueDate: "2025-01-10",
    gracePeriodDate: "2025-02-10",
    maturityDate: "2027-01-10",
    isExpired: false,
    isTaxExempt: false,
    indexer: Indexer.Cdi,
    indexerPercentage: 1.15,
    fixedAnnualRate: null,
    source: InvestmentSource.Manual,
    financialAccount: {
      __typename: "FinancialAccount",
      id: "account-1",
      name: "Conta XP",
      issuerName: "XP",
      issuer: {
        __typename: "Issuer",
        id: "issuer-1",
        name: "XP Investimentos",
        logoAsset: null,
      },
    },
    asset: {
      __typename: "InvestmentAsset",
      id: "asset-1",
      name: "CDB Banco X",
      assetType: AssetType.Cdb,
      issuerName: "Banco X",
      currencyCode: "BRL",
      logoAsset: null,
    },
  };
}

function buildTransactions(): FixedIncomeHoldingDetailQuery["investmentTransactions"] {
  return [
    {
      __typename: "InvestmentTransaction",
      id: "tx-2",
      type: InvestmentTransactionType.Sale,
      direction: InvestmentTransactionDirection.Outflow,
      quantity: 1,
      unitPrice: 1120,
      grossValue: 1120,
      netValue: 1101,
      incomeTax: 15,
      iof: 4,
      indexerPercentage: 1.15,
      fixedAnnualRate: null,
      tradeDate: "2026-03-20",
      settlementDate: "2026-03-21",
      source: InvestmentSource.Manual,
    },
    {
      __typename: "InvestmentTransaction",
      id: "tx-1",
      type: InvestmentTransactionType.Purchase,
      direction: InvestmentTransactionDirection.Inflow,
      quantity: 1,
      unitPrice: 1000,
      grossValue: 1000,
      netValue: null,
      incomeTax: null,
      iof: null,
      indexerPercentage: 1.15,
      fixedAnnualRate: null,
      tradeDate: "2025-01-10",
      settlementDate: "2025-01-11",
      source: InvestmentSource.Manual,
    },
  ];
}

function buildPerformance(): NonNullable<FixedIncomeHoldingDetailQuery["holdingPerformance"]> {
  return {
    __typename: "HoldingPerformance",
    holdingId: "holding-1",
    assetType: AssetType.Cdb,
    currencyCode: "BRL",
    resolution: HoldingPerformanceResolution.Month,
    valuationSource: ValuationSource.Estimated,
    valuationReferenceDate: "2026-03-26",
    capabilities: {
      __typename: "HoldingPerformanceCapabilities",
      supportsProjection: true,
      projectionDefaultEnabled: false,
      supportedResolutions: [HoldingPerformanceResolution.Month],
      defaultResolution: HoldingPerformanceResolution.Month,
    },
    summary: {
      __typename: "HoldingPerformanceSummary",
      investedPrincipal: 1000,
      netContributions: 1000,
      currentCostBasis: 1000,
      grossValue: 1120,
      netValue: 1101,
      incomeTax: 15,
      iof: 4,
      grossGain: 120,
      netGain: 101,
      grossGainPercent: 0.12,
    },
    points: [
      {
        __typename: "HoldingPerformancePoint",
        timestamp: "2025-01-10T00:00:00Z",
        isProjected: false,
        investedPrincipal: 1000,
        netContributions: 1000,
        currentCostBasis: 1000,
        grossValue: 1000,
        netValue: 1000,
        incomeTax: 0,
        iof: 0,
        grossGain: 0,
        netGain: 0,
        quantity: 1,
      },
      {
        __typename: "HoldingPerformancePoint",
        timestamp: "2026-03-26T00:00:00Z",
        isProjected: false,
        investedPrincipal: 1000,
        netContributions: 1000,
        currentCostBasis: 1000,
        grossValue: 1120,
        netValue: 1101,
        incomeTax: 15,
        iof: 4,
        grossGain: 120,
        netGain: 101,
        quantity: 1,
      },
      {
        __typename: "HoldingPerformancePoint",
        timestamp: "2027-01-10T00:00:00Z",
        isProjected: true,
        investedPrincipal: 1000,
        netContributions: 1000,
        currentCostBasis: 1000,
        grossValue: 1190,
        netValue: 1164,
        incomeTax: 22,
        iof: 4,
        grossGain: 190,
        netGain: 164,
        quantity: 1,
      },
    ],
  };
}

describe("buildFixedIncomeHoldingDetailViewModel", () => {
  it("maps current valuation metrics from the holding snapshot", () => {
    const viewModel = buildFixedIncomeHoldingDetailViewModel(
      buildHolding(),
      buildTransactions(),
      buildPerformance(),
      t,
      "pt-BR",
    );

    expect(viewModel.title).toBe("CDB Banco X");
    expect(viewModel.principal).toBe(1000);
    expect(viewModel.currentCostBasis).toBe(1000);
    expect(viewModel.grossValue).toBe(1120);
    expect(viewModel.netValue).toBe(1101);
    expect(viewModel.grossGain).toBe(120);
    expect(viewModel.netGain).toBe(101);
    expect(viewModel.grossGainPercent).toBeCloseTo(0.12);
    expect(viewModel.netGainPercent).toBeCloseTo(0.101);
    expect(viewModel.indexerLabel).toBe("CDI 115,0%");
    expect(viewModel.valueOrigin).toBe("estimado_app");
    expect(viewModel.chartPoints).toHaveLength(3);
    expect(viewModel.supportsProjection).toBe(true);
  });

  it("normalizes chart grossValue for tax-exempt holdings when grossValue is missing", () => {
    const holding = { ...buildHolding(), isTaxExempt: true };
    const performance = buildPerformance();
    const points = performance.points.map((point, index) =>
      index === 1
        ? { ...point, grossValue: null, netValue: 1101 }
        : point
    );

    const viewModel = buildFixedIncomeHoldingDetailViewModel(
      holding,
      buildTransactions(),
      { ...performance, points },
      t,
      "pt-BR",
    );

    expect(viewModel.chartPoints[1]?.grossValue).toBe(1101);
    expect(viewModel.chartPoints[1]?.netValue).toBe(1101);
  });

  it("orders timeline items by latest trade date first", () => {
    const viewModel = buildFixedIncomeHoldingDetailViewModel(
      buildHolding(),
      buildTransactions(),
      buildPerformance(),
      t,
      "pt-BR",
    );

    expect(viewModel.timelineItems).toHaveLength(2);
    expect(viewModel.timelineItems[0]?.id).toBe("tx-2");
    expect(viewModel.timelineItems[0]?.title).toBe("investments.detailDrawer.transactionTypes.SALE");
    expect(viewModel.timelineItems[1]?.id).toBe("tx-1");
    expect(viewModel.timelineItems[1]?.title).toBe("investments.detailDrawer.transactionTypes.PURCHASE");
  });
});
