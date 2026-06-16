import React from "react";
import { render, screen } from "@testing-library/react";
import { vi, describe, it, expect, beforeEach } from "vitest";
import { FixedIncomeProductGroup, FixedIncomeRateModel } from "@/lib/graphql/types/__generated__/graphql";
import {
  FixedIncomeSimulatorForm,
  resolveEffectiveProductGroup,
} from "../FixedIncomeSimulatorForm";

const runComparisonMock = vi.fn();

vi.mock("@apollo/client/react", () => ({
  useLazyQuery: () => [runComparisonMock, { data: undefined, loading: false }],
}));

vi.mock("../../hooks/useEconomicIndicatorsForSimulator", () => ({
  useEconomicIndicatorsForSimulator: () => ({
    cdiPercent: 12,
    ipcaPercent: 6,
    loading: false,
  }),
}));

vi.mock("@/shared/i18n", () => ({
  useTranslation: () => ({
    t: (key: string) => key,
  }),
}));

describe("FixedIncomeSimulatorForm", () => {
  beforeEach(() => {
    runComparisonMock.mockClear();
  });

  it("resolves effective product group from IOF selection", () => {
    expect(
      resolveEffectiveProductGroup(FixedIncomeProductGroup.TaxableIr, false)
    ).toBe(FixedIncomeProductGroup.TaxableIrNoIof);
    expect(
      resolveEffectiveProductGroup(FixedIncomeProductGroup.TaxableIr, true)
    ).toBe(FixedIncomeProductGroup.TaxableIr);
    expect(
      resolveEffectiveProductGroup(FixedIncomeProductGroup.ExemptIr, false)
    ).toBe(FixedIncomeProductGroup.ExemptIr);
  });

  it("renders IOF checkbox for taxable products by default", () => {
    render(<FixedIncomeSimulatorForm />);

    expect(screen.getByText("fixedIncomeComparison.taxableWithIofLabel")).toBeInTheDocument();
    expect(screen.getByRole("checkbox")).toBeInTheDocument();
  });
});
