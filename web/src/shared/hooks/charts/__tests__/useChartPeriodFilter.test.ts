import { describe, it, expect } from "vitest";
import { renderHook } from "@testing-library/react";
import { useChartPeriodFilter } from "../useChartPeriodFilter";

describe("useChartPeriodFilter", () => {
  it("should return empty filteredData and null periodGrowth for undefined data", () => {
    const { result } = renderHook(() =>
      useChartPeriodFilter(undefined, "1Y")
    );
    expect(result.current.filteredData).toEqual([]);
    expect(result.current.periodGrowth).toBeNull();
  });

  it("should return empty filteredData and null periodGrowth for null data", () => {
    const { result } = renderHook(() => useChartPeriodFilter(null, "1Y"));
    expect(result.current.filteredData).toEqual([]);
    expect(result.current.periodGrowth).toBeNull();
  });

  it("should return empty filteredData and null periodGrowth for empty array", () => {
    const { result } = renderHook(() => useChartPeriodFilter([], "1Y"));
    expect(result.current.filteredData).toEqual([]);
    expect(result.current.periodGrowth).toBeNull();
  });

  it("should filter data by period (1Y = 4 quarters)", () => {
    const data = [
      { quarter: "Q1", value: 100 },
      { quarter: "Q2", value: 110 },
      { quarter: "Q3", value: 120 },
      { quarter: "Q4", value: 130 },
    ];
    const { result } = renderHook(() => useChartPeriodFilter(data, "1Y"));
    expect(result.current.filteredData).toHaveLength(4);
    expect(result.current.filteredData).toEqual(data);
  });

  it("should take last N quarters when data length exceeds period", () => {
    const data = [
      { quarter: "Q1", value: 100 },
      { quarter: "Q2", value: 110 },
      { quarter: "Q3", value: 120 },
      { quarter: "Q4", value: 130 },
      { quarter: "Q5", value: 140 },
    ];
    const { result } = renderHook(() => useChartPeriodFilter(data, "1Y"));
    expect(result.current.filteredData).toHaveLength(4);
    expect(result.current.filteredData).toEqual([
      { quarter: "Q2", value: 110 },
      { quarter: "Q3", value: 120 },
      { quarter: "Q4", value: 130 },
      { quarter: "Q5", value: 140 },
    ]);
  });

  it("should calculate periodGrowth when comparison value is not zero", () => {
    const data = [
      { quarter: "Q1", value: 100 },
      { quarter: "Q2", value: 110 },
      { quarter: "Q3", value: 120 },
      { quarter: "Q4", value: 130 },
      { quarter: "Q5", value: 150 },
    ];
    const { result } = renderHook(() => useChartPeriodFilter(data, "1Y"));
    expect(result.current.periodGrowth).toBe(0.5); // (150 - 100) / 100, comparisonIndex = 0
  });

  it("should return null periodGrowth when comparison value is zero", () => {
    const data = [
      { quarter: "Q1", value: 0 },
      { quarter: "Q2", value: 100 },
    ];
    const { result } = renderHook(() => useChartPeriodFilter(data, "1Y"));
    expect(result.current.periodGrowth).toBeNull();
  });

  it("should use default 20 quarters for unknown period", () => {
    const data = Array.from({ length: 25 }, (_, i) => ({
      quarter: `Q${i + 1}`,
      value: 100 + i,
    }));
    const { result } = renderHook(() => useChartPeriodFilter(data, "10Y"));
    expect(result.current.filteredData).toHaveLength(20);
  });
});
