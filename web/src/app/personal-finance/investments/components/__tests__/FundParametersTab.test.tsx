import { describe, expect, it } from "vitest";
import { buildUpdateFundHoldingParamsInput } from "../InvestmentHoldingDetailDrawer";

describe("FundParametersTab", () => {
  it("maps zero fee to null and disabled come-cotas to false", () => {
    expect(
      buildUpdateFundHoldingParamsInput("holding-fund-1", {
        administrationFeeAnnualPercent: 0,
        comeCotasMode: "disabled",
      }),
    ).toEqual({
      input: {
        holdingId: "holding-fund-1",
        administrationFeeAnnualPercent: null,
        comeCotasEnabled: false,
      },
    });
  });

  it("maps empty cleared fee to null so GraphQL receives Float null", () => {
    expect(
      buildUpdateFundHoldingParamsInput("holding-fund-1", {
        // PercentField historically stored "" on clear; payload must still be null.
        administrationFeeAnnualPercent: "" as unknown as number,
        comeCotasMode: "default",
      }),
    ).toEqual({
      input: {
        holdingId: "holding-fund-1",
        administrationFeeAnnualPercent: null,
        comeCotasEnabled: null,
      },
    });
  });

  it("maps numeric fee and enabled come-cotas for mutation payload", () => {
    expect(
      buildUpdateFundHoldingParamsInput("holding-fund-1", {
        administrationFeeAnnualPercent: 1.75,
        comeCotasMode: "enabled",
      }),
    ).toEqual({
      input: {
        holdingId: "holding-fund-1",
        administrationFeeAnnualPercent: 1.75,
        comeCotasEnabled: true,
      },
    });
  });
});
