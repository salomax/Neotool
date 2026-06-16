import React from "react";
import { render } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import { FixedIncomeProductGroup } from "@/lib/graphql/types/__generated__/graphql";
import { FixedIncomePerformanceChart } from "../FixedIncomePerformanceChart";

const lineChartSpy = vi.fn(() => null);

vi.mock("@/shared/i18n", () => ({
  useTranslation: () => ({
    t: (key: string) => key,
  }),
  i18n: { language: "pt-BR" },
}));

vi.mock("@/shared/components/ui/data-display/LineChart", () => ({
  LineChart: (props: unknown) => {
    lineChartSpy(props);
    return null;
  },
}));

describe("FixedIncomePerformanceChart", () => {
  it("passes committed line to chart when series exists", () => {
    lineChartSpy.mockClear();

    render(
      <FixedIncomePerformanceChart
        result={{
          __typename: "FixedIncomeComparisonResult",
          summary: {
            __typename: "FixedIncomeSummary",
            baseProduct: "CDB",
            baseProductGroup: FixedIncomeProductGroup.TaxableIr,
            baseRateModel: "CDI",
            baseEquivalentRateBps: 10000,
            baseRateDisplay: "100% CDI",
            principal: 10000,
            gross: 10100,
            ir: 10,
            net: 10090,
            realGainPercent: 0.9,
          },
          rows: [],
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
          performanceSeries: [
            {
              __typename: "FixedIncomePerformanceSeries",
              productGroup: FixedIncomeProductGroup.TaxableIr,
              points: [{ __typename: "FixedIncomePerformancePoint", date: "2024-01-01", netValue: 10000 }],
            },
            {
              __typename: "FixedIncomePerformanceSeries",
              productGroup: FixedIncomeProductGroup.TaxableIrNoIof,
              points: [{ __typename: "FixedIncomePerformancePoint", date: "2024-01-01", netValue: 10001 }],
            },
            {
              __typename: "FixedIncomePerformanceSeries",
              productGroup: FixedIncomeProductGroup.ExemptIr,
              points: [{ __typename: "FixedIncomePerformancePoint", date: "2024-01-01", netValue: 10002 }],
            },
          ],
        }}
      />
    );

    const props = lineChartSpy.mock.calls[0]?.[0] as {
      series: Array<{ id: string }>;
    };
    expect(props.series.map((item) => item.id)).toEqual(["taxable", "committed", "exempt"]);
  });
});
