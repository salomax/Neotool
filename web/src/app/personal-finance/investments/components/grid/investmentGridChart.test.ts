import { describe, expect, it } from "vitest";
import { buildChartSeriesByClass, twrPointsToSparklineSeries } from "./investmentGridChart";

describe("twrPointsToSparklineSeries", () => {
  it("returns empty for empty input", () => {
    expect(twrPointsToSparklineSeries([])).toEqual([]);
  });

  it("returns empty for single point", () => {
    expect(twrPointsToSparklineSeries([{ date: "2026-01-01", quotaValue: 1 }])).toEqual([]);
  });

  it("normalizes values from first point", () => {
    expect(
      twrPointsToSparklineSeries([
        { date: "2026-01-01", quotaValue: 1 },
        { date: "2026-01-02", quotaValue: 2 },
      ]),
    ).toEqual([
      { quarter: "2026-01-01", value: 0 },
      { quarter: "2026-01-02", value: 1 },
    ]);
  });

  it("returns empty when first quota is zero", () => {
    expect(
      twrPointsToSparklineSeries([
        { date: "2026-01-01", quotaValue: 0 },
        { date: "2026-01-02", quotaValue: 1 },
      ]),
    ).toEqual([]);
  });
});

describe("buildChartSeriesByClass", () => {
  it("returns empty arrays when grid data is null", () => {
    expect(buildChartSeriesByClass(null)).toEqual({
      cash: [],
      "fixed-income": [],
      funds: [],
      stocks: [],
      reits: [],
      crypto: [],
    });
  });

  it("falls back to legacy fixed-income perf series when twr is empty", () => {
    const result = buildChartSeriesByClass({
      investmentCashBalance: 0,
      investmentHoldings: [],
      twrCash: [],
      twrFixedIncome: [],
      twrFunds: [],
      twrStocks: [],
      twrReits: [],
      twrCrypto: [],
      perfCash: null,
      perfBankFixedIncome: {
        groupKey: "fixedIncome",
        label: "Bank fixed income",
        includedHoldingsCount: 1,
        points: [
          { timestamp: "2020-01-31T23:59:59.000Z", grossValue: 1500, __typename: "HoldingPerformancePoint" },
          { timestamp: "2020-02-29T23:59:59.000Z", grossValue: 1528.30736872, __typename: "HoldingPerformancePoint" },
        ],
        __typename: "PortfolioPerformanceSeries",
      },
      perfCreditFixedIncome: null,
      perfMutualFunds: null,
      perfPension: null,
      perfStocks: null,
      perfReits: null,
      perfCrypto: null,
    } as any);

    expect(result["fixed-income"]).toEqual([
      { quarter: "2020-01-31T23:59:59.000Z", value: 0 },
      { quarter: "2020-02-29T23:59:59.000Z", value: 1528.30736872 / 1500 - 1 },
    ]);
  });
});
