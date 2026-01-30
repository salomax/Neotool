import React from "react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { render, screen, cleanup } from "@testing-library/react";
import { ThemeProvider, createTheme } from "@mui/material/styles";
import { CurrencyDisplay } from "../CurrencyDisplay";


afterEach(() => {
  cleanup();
  document.body.innerHTML = "";
});

const mockFormatCurrency = vi.fn(
  (value: number | null | undefined) =>
    value != null ? `R$ ${Number(value).toFixed(2)}` : "-"
);
const mockFormatLargeCurrency = vi.fn(
  (
    value: number | null | undefined,
    _currency?: string,
    _locale?: string,
    _labels?: { billions: string; millions: string }
  ) =>
    value != null && Math.abs(value) >= 1e9
      ? `R$ ${(value / 1e9).toFixed(1)} bi`
      : value != null
        ? `R$ ${Number(value).toFixed(2)}`
        : "-"
);

vi.mock("react-i18next", () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { language: "en" },
  }),
}));

vi.mock("@/shared/components/ui/forms/components/masks/br", () => ({
  formatCurrency: (v: number | null | undefined) => mockFormatCurrency(v),
  formatLargeCurrency: (
    v: number | null | undefined,
    _c?: string,
    _l?: string,
    labels?: { billions: string; millions: string }
  ) => mockFormatLargeCurrency(v, _c, _l, labels),
}));

const theme = createTheme({
  custom: {
    palette: {
      currencyPositive: "green",
      currencyNegative: "red",
      currencyNeutral: "gray",
    },
  },
} as any);

const wrapper = ({ children }: { children: React.ReactNode }) => (
  <ThemeProvider theme={theme}>{children}</ThemeProvider>
);

describe.sequential("CurrencyDisplay", () => {
  it("should render formatted currency value", () => {
    mockFormatCurrency.mockReturnValue("R$ 1234.56");
    render(<CurrencyDisplay value={1234.56} />, { wrapper });
    expect(screen.getByText("R$ 1234.56")).toBeInTheDocument();
  });

  it("should render dash for null value", () => {
    mockFormatCurrency.mockReturnValue("-");
    render(<CurrencyDisplay value={null} />, { wrapper });
    expect(screen.getByText("-")).toBeInTheDocument();
  });

  it("should render dash for undefined value", () => {
    mockFormatCurrency.mockReturnValue("-");
    render(<CurrencyDisplay value={undefined} />, { wrapper });
    expect(screen.getByText("-")).toBeInTheDocument();
  });

  it("should use large format when format is large", () => {
    mockFormatLargeCurrency.mockReturnValue("R$ 1.5 bi");
    render(<CurrencyDisplay value={1_500_000_000} format="large" />, {
      wrapper,
    });
    expect(screen.getByText("R$ 1.5 bi")).toBeInTheDocument();
  });

  it("should pass custom labels to formatLargeCurrency when format is large", () => {
    mockFormatLargeCurrency.mockReturnValue("R$ 2 B");
    render(
      <CurrencyDisplay
        value={2_000_000_000}
        format="large"
        labels={{ billions: "B", millions: "M" }}
      />,
      { wrapper }
    );
    expect(mockFormatLargeCurrency).toHaveBeenCalledWith(
      expect.any(Number),
      "BRL",
      undefined,
      { billions: "B", millions: "M" }
    );
  });
});
