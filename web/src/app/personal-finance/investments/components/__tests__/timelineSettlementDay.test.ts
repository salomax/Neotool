import { describe, expect, it } from "vitest";
import {
  investmentTransactionCalendarDayKey,
  isSameInvestmentEventAndSettlementDay,
} from "../timelineSettlementDay";

describe("timelineSettlementDay", () => {
  it("parses leading YYYY-MM-DD from ISO strings", () => {
    expect(investmentTransactionCalendarDayKey("2026-03-20T12:00:00.000Z")).toBe("2026-03-20");
  });

  it("returns same key for trade and settlement on the same calendar day", () => {
    expect(isSameInvestmentEventAndSettlementDay("2026-01-01", "2026-01-01")).toBe(true);
  });

  it("returns false when settlement is a different day", () => {
    expect(isSameInvestmentEventAndSettlementDay("2026-01-01", "2026-01-03")).toBe(false);
  });

  it("treats ISO timestamps on the same UTC date as same day", () => {
    expect(
      isSameInvestmentEventAndSettlementDay("2026-01-01T08:00:00.000Z", "2026-01-01T20:00:00.000Z"),
    ).toBe(true);
  });
});
