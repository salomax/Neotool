import { describe, expect, it } from "vitest";
import { buildMonthRange } from "../useDashboardDateRange";

describe("buildMonthRange", () => {
  const now = new Date(2026, 3, 22); // 2026-04-22 (month is 0-based)

  it("uses dynamic since for all-period when available", () => {
    const months = buildMonthRange("all", now, { year: 2023, month: 3 });
    expect(months[0]).toEqual({ year: 2023, month: 3 });
    expect(months[months.length - 1]).toEqual({ year: 2026, month: 4 });
    expect(months).toHaveLength(38);
  });

  it("does not cap all-period to 60 months when since is older", () => {
    const months = buildMonthRange("all", now, { year: 2019, month: 1 });
    expect(months[0]).toEqual({ year: 2019, month: 1 });
    expect(months[months.length - 1]).toEqual({ year: 2026, month: 4 });
    expect(months.length).toBeGreaterThan(60);
  });

  it("falls back to 60 months when since is missing", () => {
    const months = buildMonthRange("all", now);
    expect(months[0]).toEqual({ year: 2021, month: 5 });
    expect(months[months.length - 1]).toEqual({ year: 2026, month: 4 });
    expect(months).toHaveLength(60);
  });

  it("falls back to current month when since is in the future", () => {
    const months = buildMonthRange("all", now, { year: 2030, month: 1 });
    expect(months).toEqual([{ year: 2026, month: 4 }]);
  });

  it("throws when since has an invalid month", () => {
    expect(() => buildMonthRange("all", now, { year: 2024, month: 13 })).toThrow(
      "Invalid since.month value: 13",
    );
  });
});
