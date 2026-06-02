import { describe, expect, it } from "vitest";
import {
  type HoldingPerformanceChartPoint,
  withProjectionMonthBridge,
} from "../HoldingPerformanceChart";

function point(
  overrides: Partial<HoldingPerformanceChartPoint> & Pick<HoldingPerformanceChartPoint, "timestamp" | "isProjected">,
): HoldingPerformanceChartPoint {
  return {
    investedPrincipal: null,
    grossValue: null,
    netValue: null,
    isPartialMonth: false,
    ...overrides,
  };
}

describe("withProjectionMonthBridge", () => {
  it("does not duplicate when first projected is already month-end of the partial month", () => {
    const filtered: HoldingPerformanceChartPoint[] = [
      point({ timestamp: "2026-03-26", isProjected: false, netValue: 1035 }),
      point({ timestamp: "2026-04-10", isProjected: false, netValue: 1036 }),
      point({ timestamp: "2026-04-30", isProjected: true, netValue: 1050 }),
    ];
    const out = withProjectionMonthBridge(filtered, 2);
    expect(out).toHaveLength(3);
    expect(out.map((p) => p.timestamp.slice(0, 10))).toEqual([
      "2026-03-26",
      "2026-04-10",
      "2026-04-30",
    ]);
  });

  it("inserts interpolated bridge when first projected is after month-end", () => {
    const filtered: HoldingPerformanceChartPoint[] = [
      point({ timestamp: "2026-04-10", isProjected: false, netValue: 1000, grossValue: 1000 }),
      point({ timestamp: "2026-05-31", isProjected: true, netValue: 1100, grossValue: 1100 }),
    ];
    const out = withProjectionMonthBridge(filtered, 1);
    expect(out).toHaveLength(3);
    expect(out[1]?.timestamp.startsWith("2026-04-")).toBe(true);
    expect(out[1]?.isProjected).toBe(true);
    // April 30 is ~20/51 of the way from Apr 10 to May 31 in calendar days
    expect(out[1]?.netValue).toBeGreaterThan(1000);
    expect(out[1]?.netValue).toBeLessThan(1100);
  });

  it("does not insert when last actual is already on month-end", () => {
    const filtered: HoldingPerformanceChartPoint[] = [
      point({ timestamp: "2026-04-30", isProjected: false, netValue: 1036 }),
      point({ timestamp: "2026-05-31", isProjected: true, netValue: 1100 }),
    ];
    expect(withProjectionMonthBridge(filtered, 1)).toHaveLength(2);
  });
});
