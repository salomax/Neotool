import React from "react";
import { render } from "@testing-library/react";
import { ThemeProvider, createTheme } from "@mui/material/styles";
import { LineChart } from "../LineChart";

const renderWithTheme = (ui: React.ReactElement) => {
  const theme = createTheme();
  return render(<ThemeProvider theme={theme}>{ui}</ThemeProvider>);
};

describe("LineChart", () => {
  const data = [
    { date: "2023-01-01", revenue: 100, marginPercent: 20 },
    { date: "2023-02-01", revenue: 200, marginPercent: 30 },
  ];

  it("renders without crashing", () => {
    const { getByText } = renderWithTheme(
      <LineChart
        data={data}
        title="Test Chart"
        xKey="date"
        xIsDate
        axes={[
          { id: "left", side: "left", label: "Amount", format: "number" },
          { id: "right", side: "right", label: "Margin %", format: "percent" },
        ]}
        series={[
          { id: "revenue", name: "Revenue", dataKey: "revenue", yAxisId: "left" },
          { id: "margin", name: "Margin %", dataKey: "marginPercent", yAxisId: "right" },
        ]}
      />
    );

    expect(getByText("Test Chart")).toBeInTheDocument();
  });
});

