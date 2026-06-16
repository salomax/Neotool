import React from "react";
import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import { FixedIncomeProduct, FixedIncomeProductGroup, FixedIncomeRateModel } from "@/lib/graphql/types/__generated__/graphql";
import { FixedIncomeComparisonTable } from "../FixedIncomeComparisonTable";

vi.mock("@/shared/i18n", () => ({
  useTranslation: () => ({
    t: (key: string, params?: Record<string, unknown>) =>
      params ? `${key}:${JSON.stringify(params)}` : key,
  }),
  i18n: { language: "pt-BR" },
}));

describe("FixedIncomeComparisonTable", () => {
  it("renders committed taxable row label", () => {
    render(
      <FixedIncomeComparisonTable
        result={{
          summary: {
            baseProduct: FixedIncomeProduct.Cdb,
            baseProductGroup: FixedIncomeProductGroup.TaxableIr,
            baseRateModel: FixedIncomeRateModel.Cdi,
            baseEquivalentRateBps: 10000,
            baseRateDisplay: "100% CDI",
            principal: 10000,
            gross: 10100,
            ir: 10,
            net: 10090,
            realGainPercent: 0.9,
            __typename: "FixedIncomeSummary",
          },
          rows: [
            {
              __typename: "FixedIncomeComparisonRow",
              label: "base",
              productGroup: FixedIncomeProductGroup.TaxableIr,
              rateModel: FixedIncomeRateModel.Cdi,
              equivalentRateBps: 10000,
              contractedRateDisplay: "100% CDI",
              gross: 10100,
              ir: 10,
              net: 10090,
              realGainPercent: 0.9,
              isExempt: false,
              isBase: true,
            },
            {
              __typename: "FixedIncomeComparisonRow",
              label: "committed",
              productGroup: FixedIncomeProductGroup.TaxableIrNoIof,
              rateModel: FixedIncomeRateModel.Cdi,
              equivalentRateBps: 9800,
              contractedRateDisplay: "98% CDI",
              gross: 10100,
              ir: 12,
              net: 10088,
              realGainPercent: 0.88,
              isExempt: false,
              isBase: false,
            },
          ],
          performanceSeries: [],
          methodology: {
            __typename: "FixedIncomeMethodology",
            dayCount: {
              __typename: "FixedIncomeDayCountMethodology",
              accrualConvention: "BUSINESS_252",
              calendarDays: 10,
              businessDays: 8,
            },
            tax: {
              __typename: "FixedIncomeTaxMethodology",
              appliesOnProfitOnly: true,
              activeBracket: {
                __typename: "FixedIncomeTaxBracket",
                fromCalendarDays: 1,
                toCalendarDays: 180,
                rateDecimal: 0.225,
              },
              bracketTable: [],
            },
          },
          __typename: "FixedIncomeComparisonResult",
        }}
      />
    );

    expect(
      screen.getByText("fixedIncomeComparison.taxableCommittedProductLabel")
    ).toBeInTheDocument();
  });
});
