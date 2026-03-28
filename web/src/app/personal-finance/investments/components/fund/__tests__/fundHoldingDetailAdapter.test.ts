import { describe, expect, it } from "vitest";
import {
  AssetType,
  HoldingPerformanceResolution,
  InvestmentSource,
  InvestmentTransactionDirection,
  InvestmentTransactionType,
  ValuationSource,
} from "@/lib/graphql/types/__generated__/graphql";
import { buildFundHoldingDetailViewModel } from "../fundHoldingDetailAdapter";
import type { FundHoldingDetailQuery } from "../api/queries.generated";

function t(key: string, options?: Record<string, unknown>) {
  return String(options?.defaultValue ?? key);
}

function buildHolding(overrides?: Partial<NonNullable<FundHoldingDetailQuery["investmentHolding"]>>) {
  const base: NonNullable<FundHoldingDetailQuery["investmentHolding"]> = {
    __typename: "InvestmentHolding",
    id: "holding-fund-1",
    quantity: 100,
    averageCost: 10,
    grossValue: 1200,
    netValue: 1180,
    valuationSource: ValuationSource.Estimated,
    valuationReferenceDate: "2026-03-26",
    isTaxExempt: false,
    source: InvestmentSource.Manual,
    financialAccount: {
      __typename: "FinancialAccount",
      id: "account-1",
      name: "Conta corretora",
      issuer: {
        __typename: "Issuer",
        id: "issuer-1",
        name: "Corretora X",
        logoAsset: null,
      },
    },
    asset: {
      __typename: "InvestmentAsset",
      id: "asset-fund-1",
      name: "Fundo Exemplo",
      assetType: AssetType.MutualFund,
      issuerName: "Gestora Y",
      currencyCode: "BRL",
      logoAsset: null,
    },
  };
  return { ...base, ...overrides };
}

function buildTransactions(): FundHoldingDetailQuery["investmentTransactions"] {
  return [
    {
      __typename: "InvestmentTransaction",
      id: "tx-sale",
      type: InvestmentTransactionType.Sale,
      direction: InvestmentTransactionDirection.Outflow,
      quantity: 50,
      unitPrice: 12,
      grossValue: 600,
      netValue: 580,
      incomeTax: 15,
      iof: 5,
      tradeDate: "2026-03-20",
      settlementDate: "2026-03-22",
      source: InvestmentSource.Manual,
    },
    {
      __typename: "InvestmentTransaction",
      id: "tx-buy",
      type: InvestmentTransactionType.Purchase,
      direction: InvestmentTransactionDirection.Inflow,
      quantity: 100,
      unitPrice: 10,
      grossValue: 1000,
      netValue: null,
      incomeTax: null,
      iof: null,
      tradeDate: "2025-06-01",
      settlementDate: null,
      source: InvestmentSource.Manual,
    },
  ];
}

function buildPerformance(): NonNullable<FundHoldingDetailQuery["holdingPerformance"]> {
  return {
    __typename: "HoldingPerformance",
    holdingId: "holding-fund-1",
    assetType: AssetType.MutualFund,
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
      grossValue: 1200,
      netValue: 1180,
      incomeTax: 12,
      iof: 8,
      grossGain: 200,
      netGain: 180,
      grossGainPercent: 0.2,
    },
    points: [
      {
        __typename: "HoldingPerformancePoint",
        timestamp: "2025-06-01T00:00:00Z",
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
        quantity: 100,
      },
    ],
  };
}

describe("buildFundHoldingDetailViewModel", () => {
  it("maps basic fields including title, subtitle, currency, issuer, and account labels", () => {
    const viewModel = buildFundHoldingDetailViewModel(buildHolding(), [], buildPerformance(), t, "pt-BR");

    expect(viewModel.title).toBe("Fundo Exemplo");
    expect(viewModel.subtitle).toBe("investments.assetTypes.MUTUAL_FUND");
    expect(viewModel.currencyCode).toBe("BRL");
    expect(viewModel.issuerLabel).toBe("Corretora X");
    expect(viewModel.accountLabel).toBe("Conta corretora");
  });

  it("maps performance summary fields including incomeTax, iof, and grossGainPercent", () => {
    const viewModel = buildFundHoldingDetailViewModel(buildHolding(), [], buildPerformance(), t, "pt-BR");

    expect(viewModel.principal).toBe(1000);
    expect(viewModel.currentCostBasis).toBe(1000);
    expect(viewModel.grossValue).toBe(1200);
    expect(viewModel.netValue).toBe(1180);
    expect(viewModel.incomeTax).toBe(12);
    expect(viewModel.iof).toBe(8);
    expect(viewModel.grossGain).toBe(200);
    expect(viewModel.netGain).toBe(180);
    expect(viewModel.grossGainPercent).toBeCloseTo(0.2);
    expect(viewModel.netGainPercent).toBeCloseTo(0.18);
  });

  it("includes six summaryMetrics with incomeTax entry using negative tone", () => {
    const viewModel = buildFundHoldingDetailViewModel(buildHolding(), [], buildPerformance(), t, "pt-BR");

    expect(viewModel.summaryMetrics).toHaveLength(6);
    const taxMetric = viewModel.summaryMetrics.find((m) => m.id === "incomeTax");
    expect(taxMetric?.tone).toBe("negative");
    expect(taxMetric?.value).toBe(12);
  });

  it("maps timeline direction, amounts, tax fields, and settlement date", () => {
    const viewModel = buildFundHoldingDetailViewModel(buildHolding(), buildTransactions(), buildPerformance(), t, "pt-BR");

    expect(viewModel.timelineItems).toHaveLength(2);
    const sale = viewModel.timelineItems[0];
    expect(sale?.id).toBe("tx-sale");
    expect(sale?.direction).toBe(InvestmentTransactionDirection.Outflow);
    expect(sale?.amount).toBe(600);
    expect(sale?.incomeTax).toBe(15);
    expect(sale?.iof).toBe(5);
    expect(sale?.settlementDateLabel).toBeTruthy();

    const purchase = viewModel.timelineItems[1];
    expect(purchase?.id).toBe("tx-buy");
    expect(purchase?.direction).toBe(InvestmentTransactionDirection.Inflow);
    expect(purchase?.amount).toBe(1000);
    expect(purchase?.settlementDateLabel).toBeNull();
  });

  it("sorts timeline items newest trade date first", () => {
    const txs: FundHoldingDetailQuery["investmentTransactions"] = [
      {
        __typename: "InvestmentTransaction",
        id: "tx-old",
        type: InvestmentTransactionType.Purchase,
        direction: InvestmentTransactionDirection.Inflow,
        quantity: 1,
        unitPrice: 10,
        grossValue: 10,
        netValue: 10,
        incomeTax: null,
        iof: null,
        tradeDate: "2025-01-01",
        settlementDate: null,
        source: InvestmentSource.Manual,
      },
      {
        __typename: "InvestmentTransaction",
        id: "tx-new",
        type: InvestmentTransactionType.Purchase,
        direction: InvestmentTransactionDirection.Inflow,
        quantity: 1,
        unitPrice: 10,
        grossValue: 10,
        netValue: 10,
        incomeTax: null,
        iof: null,
        tradeDate: "2026-01-01",
        settlementDate: null,
        source: InvestmentSource.Manual,
      },
    ];
    const viewModel = buildFundHoldingDetailViewModel(buildHolding(), txs, buildPerformance(), t, "pt-BR");

    expect(viewModel.timelineItems[0]?.id).toBe("tx-new");
    expect(viewModel.timelineItems[1]?.id).toBe("tx-old");
  });

  it("returns null numeric summaries and empty chart when performance is missing", () => {
    const viewModel = buildFundHoldingDetailViewModel(buildHolding(), [], undefined, t, "pt-BR");

    expect(viewModel.principal).toBeNull();
    expect(viewModel.currentCostBasis).toBeNull();
    expect(viewModel.grossValue).toBeNull();
    expect(viewModel.netValue).toBeNull();
    expect(viewModel.incomeTax).toBeNull();
    expect(viewModel.iof).toBeNull();
    expect(viewModel.grossGain).toBeNull();
    expect(viewModel.netGain).toBeNull();
    expect(viewModel.grossGainPercent).toBeNull();
    expect(viewModel.chartPoints).toEqual([]);
  });

  it("defaults currencyCode to BRL when asset currency is falsy", () => {
    const holding = buildHolding({
      asset: {
        __typename: "InvestmentAsset",
        id: "a",
        name: "Fundo",
        assetType: AssetType.MutualFund,
        issuerName: null,
        currencyCode: "",
        logoAsset: null,
      },
    });
    const viewModel = buildFundHoldingDetailViewModel(holding, [], buildPerformance(), t, "pt-BR");

    expect(viewModel.currencyCode).toBe("BRL");
  });

  it("sets valueOrigin from getHoldingValueOrigin for holding and performance valuation", () => {
    const holding = buildHolding({ valuationSource: ValuationSource.Calculated });
    const perf = buildPerformance();
    const viewModel = buildFundHoldingDetailViewModel(holding, [], perf, t, "pt-BR");

    expect(viewModel.valueOrigin).toBe("estimado_app");
  });
});
