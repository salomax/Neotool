import { describe, it, expect } from "vitest";
import { buildMetricItem, extractMetricData } from "../buildMetricItem";

describe("buildMetricItem", () => {
  it("should build metric item from config", () => {
    const item = buildMetricItem({
      label: "Net Profit",
      valueType: "currency",
      data: {
        actualValue: 1000000,
        historical: [900000, 1000000],
        growthPercentage: 0.111,
        chartData: [
          { quarter: "Q1", value: 900000 },
          { quarter: "Q2", value: 1000000 },
        ],
      },
      currency: "BRL",
    });

    expect(item.type).toBe("metric");
    expect(item.label).toBe("Net Profit");
    expect(item.actualValue).toBe(1000000);
    expect(item.valueType).toBe("currency");
    expect(item.currency).toBe("BRL");
    expect(item.growthPercentage).toBe(0.111);
    expect(item.historical).toEqual([1000000, 900000]); // reversed
    expect(item.chartData).toEqual([
      { quarter: "Q1", value: 900000 },
      { quarter: "Q2", value: 1000000 },
    ]);
  });

  it("should include percentageThresholdValues and percentageThresholdInverse when provided", () => {
    const item = buildMetricItem({
      label: "Basel Index",
      valueType: "percentage",
      data: {
        actualValue: 15,
        historical: [14, 15],
        growthPercentage: null,
        chartData: [],
      },
      percentageThresholdValues: { bad: 11, regular: 14, good: 100 },
      percentageThresholdInverse: false,
    });

    expect(item.percentageThresholdValues).toEqual({
      bad: 11,
      regular: 14,
      good: 100,
    });
    expect(item.percentageThresholdInverse).toBe(false);
  });

  it("should not mutate input data historical array", () => {
    const historical = [1, 2, 3];
    buildMetricItem({
      label: "Test",
      valueType: "number",
      data: {
        actualValue: 3,
        historical,
        growthPercentage: null,
        chartData: [],
      },
    });
    expect(historical).toEqual([1, 2, 3]);
  });
});

describe("extractMetricData", () => {
  it("should extract metric data from accounts", () => {
    const accounts = [
      {
        id: "acc1",
        valuesByQuarter: [
          { quarter: "Q2", value: 200 },
          { quarter: "Q1", value: 100 },
        ],
      },
    ];

    const data = extractMetricData(accounts, "acc1");

    expect(data.actualValue).toBe(200);
    expect(data.historical).toEqual([200, 100]);
    expect(data.growthPercentage).toBe(1); // 200/100 - 1
    expect(data.chartData).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ quarter: "Q1", value: 100 }),
        expect.objectContaining({ quarter: "Q2", value: 200 }),
      ])
    );
  });

  it("should return null actualValue and empty arrays when account not found", () => {
    const data = extractMetricData([], "missing");
    expect(data.actualValue).toBeNull();
    expect(data.historical).toEqual([]);
    expect(data.growthPercentage).toBeNull();
    expect(data.chartData).toEqual([]);
  });

  it("should return null actualValue when latest value is not a number", () => {
    const data = extractMetricData(
      [{ id: "acc1", valuesByQuarter: [{ quarter: "Q1", value: undefined }] }],
      "acc1"
    );
    expect(data.actualValue).toBeNull();
  });

  it("should filter out non-finite values from historical", () => {
    const data = extractMetricData(
      [
        {
          id: "acc1",
          valuesByQuarter: [
            { quarter: "Q2", value: 50 },
            { quarter: "Q1", value: NaN },
            { quarter: "Q0", value: 10 },
          ],
        },
      ],
      "acc1"
    );
    expect(data.historical).toEqual([50, 10]);
  });

  it("should set growthPercentage to null when base is 0", () => {
    const data = extractMetricData(
      [
        {
          id: "acc1",
          valuesByQuarter: [
            { quarter: "Q2", value: 100 },
            { quarter: "Q1", value: 0 },
          ],
        },
      ],
      "acc1"
    );
    expect(data.growthPercentage).toBeNull();
  });
});
