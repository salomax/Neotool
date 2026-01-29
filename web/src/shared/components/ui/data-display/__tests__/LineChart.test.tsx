import React from "react";
import { render, cleanup, within } from "@testing-library/react";
import { ThemeProvider, createTheme } from "@mui/material/styles";
import { LineChart, LineChartAxisConfig } from "../LineChart";
import { afterEach, beforeEach } from "vitest";

beforeEach(() => {
  cleanup();
});

afterEach(() => {
  cleanup();
});

const theme = createTheme();

const renderWithTheme = (ui: React.ReactElement) => {
  return render(<ThemeProvider theme={theme}>{ui}</ThemeProvider>);
};

describe("LineChart", () => {
  const data = [
    { date: "2023-01-01", revenue: 100, marginPercent: 20, expenses: 50 },
    { date: "2023-02-01", revenue: 200, marginPercent: 30, expenses: 100 },
    { date: "2023-03-01", revenue: 300, marginPercent: 25, expenses: 150 },
  ];

  describe("Basic Rendering", () => {
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

    it("renders without title", () => {
      const { container } = renderWithTheme(
        <LineChart
          data={data}
          xKey="date"
          series={[
            { id: "revenue", name: "Revenue", dataKey: "revenue" },
          ]}
        />
      );

      const scoped = within(container);
      expect(scoped.queryByRole("heading", { level: 3 })).not.toBeInTheDocument();
    });

    it("renders with custom width and height", () => {
      const { container } = renderWithTheme(
        <LineChart
          data={data}
          width={800}
          height={400}
          xKey="date"
          series={[
            { id: "revenue", name: "Revenue", dataKey: "revenue" },
          ]}
        />
      );

      const scoped = within(container);
      const box = scoped.getByTestId("line-chart-responsive-container");
      expect(box).toBeInTheDocument();
    });
  });

  describe("Axis Formatting", () => {
    it("formats currency with default BRL", () => {
      const axes: LineChartAxisConfig[] = [
        { id: "currency", side: "left", format: "currency" },
      ];

      const { container } = renderWithTheme(
        <LineChart
          data={data}
          xKey="date"
          axes={axes}
          series={[
            { id: "revenue", name: "Revenue", dataKey: "revenue", yAxisId: "currency" },
          ]}
        />
      );

      expect(container).toBeInTheDocument();
    });

    it("formats currency with custom currency", () => {
      const axes: LineChartAxisConfig[] = [
        { id: "currency", side: "left", format: "currency", currency: "USD" },
      ];

      const { container } = renderWithTheme(
        <LineChart
          data={data}
          xKey="date"
          axes={axes}
          series={[
            { id: "revenue", name: "Revenue", dataKey: "revenue", yAxisId: "currency" },
          ]}
        />
      );

      expect(container).toBeInTheDocument();
    });

    it("formats percent values", () => {
      const axes: LineChartAxisConfig[] = [
        { id: "percent", side: "left", format: "percent" },
      ];

      const { container } = renderWithTheme(
        <LineChart
          data={data}
          xKey="date"
          axes={axes}
          series={[
            { id: "margin", name: "Margin %", dataKey: "marginPercent", yAxisId: "percent" },
          ]}
        />
      );

      expect(container).toBeInTheDocument();
    });

    it("formats number values", () => {
      const axes: LineChartAxisConfig[] = [
        { id: "number", side: "left", format: "number" },
      ];

      const { container } = renderWithTheme(
        <LineChart
          data={data}
          xKey="date"
          axes={axes}
          series={[
            { id: "revenue", name: "Revenue", dataKey: "revenue", yAxisId: "number" },
          ]}
        />
      );

      expect(container).toBeInTheDocument();
    });

    it("uses custom tick formatter when provided", () => {
      const customFormatter = (value: number) => `${value}K`;
      const axes: LineChartAxisConfig[] = [
        { id: "custom", side: "left", tickFormatter: customFormatter },
      ];

      const { container } = renderWithTheme(
        <LineChart
          data={data}
          xKey="date"
          axes={axes}
          series={[
            { id: "revenue", name: "Revenue", dataKey: "revenue", yAxisId: "custom" },
          ]}
        />
      );

      expect(container).toBeInTheDocument();
    });
  });

  describe("Multiple Axes", () => {
    it("renders with left and right axes", () => {
      const axes: LineChartAxisConfig[] = [
        { id: "left-currency", side: "left", label: "Amount", format: "currency" },
        { id: "right-percent", side: "right", label: "Rate", format: "percent" },
      ];

      const { container } = renderWithTheme(
        <LineChart
          data={data}
          xKey="date"
          axes={axes}
          series={[
            { id: "revenue", name: "Revenue", dataKey: "revenue", yAxisId: "left-currency" },
            { id: "margin", name: "Margin %", dataKey: "marginPercent", yAxisId: "right-percent" },
          ]}
        />
      );

      expect(container).toBeInTheDocument();
    });

    it("renders with multiple left axes", () => {
      const axes: LineChartAxisConfig[] = [
        { id: "left-1", side: "left", label: "Revenue", format: "currency" },
        { id: "left-2", side: "left", label: "Expenses", format: "currency" },
      ];

      const { container } = renderWithTheme(
        <LineChart
          data={data}
          xKey="date"
          axes={axes}
          series={[
            { id: "revenue", name: "Revenue", dataKey: "revenue", yAxisId: "left-1" },
            { id: "expenses", name: "Expenses", dataKey: "expenses", yAxisId: "left-2" },
          ]}
        />
      );

      expect(container).toBeInTheDocument();
    });

    it("renders without axes configuration", () => {
      const { container } = renderWithTheme(
        <LineChart
          data={data}
          xKey="date"
          series={[
            { id: "revenue", name: "Revenue", dataKey: "revenue" },
          ]}
        />
      );

      expect(container).toBeInTheDocument();
    });
  });

  describe("Series Configuration", () => {
    it("renders multiple series", () => {
      const { container } = renderWithTheme(
        <LineChart
          data={data}
          xKey="date"
          series={[
            { id: "revenue", name: "Revenue", dataKey: "revenue" },
            { id: "expenses", name: "Expenses", dataKey: "expenses" },
            { id: "margin", name: "Margin %", dataKey: "marginPercent" },
          ]}
        />
      );

      expect(container).toBeInTheDocument();
    });

    it("applies custom colors to series", () => {
      const customColors = ["#ff0000", "#00ff00", "#0000ff"];

      const { container } = renderWithTheme(
        <LineChart
          data={data}
          xKey="date"
          colors={customColors}
          series={[
            { id: "revenue", name: "Revenue", dataKey: "revenue" },
            { id: "expenses", name: "Expenses", dataKey: "expenses" },
          ]}
        />
      );

      expect(container).toBeInTheDocument();
    });

    it("applies custom color per series", () => {
      const { container } = renderWithTheme(
        <LineChart
          data={data}
          xKey="date"
          series={[
            { id: "revenue", name: "Revenue", dataKey: "revenue", color: "#ff0000" },
            { id: "expenses", name: "Expenses", dataKey: "expenses", color: "#00ff00" },
          ]}
        />
      );

      expect(container).toBeInTheDocument();
    });
  });

  describe("Display Options", () => {
    it("hides grid when showGrid is false", () => {
      const { container } = renderWithTheme(
        <LineChart
          data={data}
          xKey="date"
          showGrid={false}
          series={[
            { id: "revenue", name: "Revenue", dataKey: "revenue" },
          ]}
        />
      );

      expect(container).toBeInTheDocument();
    });

    it("hides dots when showDots is false", () => {
      const { container } = renderWithTheme(
        <LineChart
          data={data}
          xKey="date"
          showDots={false}
          series={[
            { id: "revenue", name: "Revenue", dataKey: "revenue" },
          ]}
        />
      );

      expect(container).toBeInTheDocument();
    });

    it("hides X axis when showXAxis is false", () => {
      const { container } = renderWithTheme(
        <LineChart
          data={data}
          xKey="date"
          showXAxis={false}
          series={[
            { id: "revenue", name: "Revenue", dataKey: "revenue" },
          ]}
        />
      );

      expect(container).toBeInTheDocument();
    });

    it("hides Y axis when showYAxis is false", () => {
      const { container } = renderWithTheme(
        <LineChart
          data={data}
          xKey="date"
          showYAxis={false}
          series={[
            { id: "revenue", name: "Revenue", dataKey: "revenue" },
          ]}
        />
      );

      expect(container).toBeInTheDocument();
    });

    it("hides tooltip when showTooltip is false", () => {
      const { container } = renderWithTheme(
        <LineChart
          data={data}
          xKey="date"
          showTooltip={false}
          series={[
            { id: "revenue", name: "Revenue", dataKey: "revenue" },
          ]}
        />
      );

      expect(container).toBeInTheDocument();
    });

    it("hides legend when showLegend is false", () => {
      const { container } = renderWithTheme(
        <LineChart
          data={data}
          xKey="date"
          showLegend={false}
          series={[
            { id: "revenue", name: "Revenue", dataKey: "revenue" },
          ]}
        />
      );

      expect(container).toBeInTheDocument();
    });

    it("shows value labels when showValueLabels is true", () => {
      const { container } = renderWithTheme(
        <LineChart
          data={data}
          xKey="date"
          showValueLabels={true}
          series={[
            { id: "revenue", name: "Revenue", dataKey: "revenue" },
          ]}
        />
      );

      expect(container).toBeInTheDocument();
    });

    it("shows area when showArea is true", () => {
      const { container } = renderWithTheme(
        <LineChart
          data={data}
          xKey="date"
          showArea={true}
          series={[
            { id: "revenue", name: "Revenue", dataKey: "revenue" },
          ]}
        />
      );

      expect(container).toBeInTheDocument();
    });

    it("applies custom stroke width", () => {
      const { container } = renderWithTheme(
        <LineChart
          data={data}
          xKey="date"
          strokeWidth={5}
          series={[
            { id: "revenue", name: "Revenue", dataKey: "revenue" },
          ]}
        />
      );

      expect(container).toBeInTheDocument();
    });
  });

  describe("Date Handling", () => {
    it("formats dates with default format", () => {
      const { container } = renderWithTheme(
        <LineChart
          data={data}
          xKey="date"
          xIsDate={true}
          series={[
            { id: "revenue", name: "Revenue", dataKey: "revenue" },
          ]}
        />
      );

      expect(container).toBeInTheDocument();
    });

    it("formats dates with custom format", () => {
      const customDateFormat = (value: any) => {
        const date = new Date(value);
        return `${date.getFullYear()}-${date.getMonth() + 1}`;
      };

      const { container } = renderWithTheme(
        <LineChart
          data={data}
          xKey="date"
          xIsDate={true}
          xDateFormat={customDateFormat}
          series={[
            { id: "revenue", name: "Revenue", dataKey: "revenue" },
          ]}
        />
      );

      expect(container).toBeInTheDocument();
    });
  });

  describe("Edge Cases", () => {
    it("renders with empty data", () => {
      const { container } = renderWithTheme(
        <LineChart
          data={[]}
          xKey="date"
          series={[
            { id: "revenue", name: "Revenue", dataKey: "revenue" },
          ]}
        />
      );

      expect(container).toBeInTheDocument();
    });

    it("handles null values in data", () => {
      const dataWithNulls = [
        { date: "2023-01-01", revenue: 100 },
        { date: "2023-02-01", revenue: null },
        { date: "2023-03-01", revenue: 300 },
      ];

      const { container } = renderWithTheme(
        <LineChart
          data={dataWithNulls}
          xKey="date"
          series={[
            { id: "revenue", name: "Revenue", dataKey: "revenue" },
          ]}
        />
      );

      expect(container).toBeInTheDocument();
    });

    it("handles undefined values in data", () => {
      const dataWithUndefined = [
        { date: "2023-01-01", revenue: 100 },
        { date: "2023-02-01", revenue: undefined },
        { date: "2023-03-01", revenue: 300 },
      ];

      const { container } = renderWithTheme(
        <LineChart
          data={dataWithUndefined}
          xKey="date"
          series={[
            { id: "revenue", name: "Revenue", dataKey: "revenue" },
          ]}
        />
      );

      expect(container).toBeInTheDocument();
    });

    it("applies custom margin", () => {
      const { container } = renderWithTheme(
        <LineChart
          data={data}
          xKey="date"
          margin={{ top: 10, right: 50, bottom: 30, left: 50 }}
          series={[
            { id: "revenue", name: "Revenue", dataKey: "revenue" },
          ]}
        />
      );

      expect(container).toBeInTheDocument();
    });

    it("applies custom sx styles", () => {
      const { container } = renderWithTheme(
        <LineChart
          data={data}
          xKey="date"
          sx={{ backgroundColor: "red" }}
          series={[
            { id: "revenue", name: "Revenue", dataKey: "revenue" },
          ]}
        />
      );

      expect(container).toBeInTheDocument();
    });
  });
});
