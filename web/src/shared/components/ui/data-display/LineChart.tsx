"use client";

import React from "react";
import {
  LineChart as RechartsLineChart,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
  Line,
} from "recharts";
import { Box, Paper, Typography, useTheme } from "@mui/material";

type AxisType = "number" | "category";

type AxisFormat = "number" | "currency" | "percent";

export interface LineChartSeries {
  id: string;
  name: string;
  dataKey: string;
  color?: string;
  yAxisId?: string;
}

export interface LineChartAxisConfig {
  id: string;
  side: "left" | "right";
  label?: string;
  type?: AxisType;
  format?: AxisFormat;
  currency?: string;
  tickFormatter?: (value: number) => string;
}

export interface LineChartProps {
  data: Array<Record<string, any>>;
  title?: string;
  width?: number | string;
  height?: number | string;
  xKey?: string;
  xIsDate?: boolean;
  xDateFormat?: (value: any) => string;
  axes?: LineChartAxisConfig[];
  series: LineChartSeries[];
  showXAxis?: boolean;
  showYAxis?: boolean;
  showGrid?: boolean;
  showDots?: boolean;
  showValueLabels?: boolean;
  showTooltip?: boolean;
  showLegend?: boolean;
  strokeWidth?: number;
  showArea?: boolean;
  colors?: string[];
  margin?: {
    top?: number;
    right?: number;
    bottom?: number;
    left?: number;
  };
  sx?: any;
}

const buildTickFormatter = (
  axis: LineChartAxisConfig | undefined,
  locale: string
) => {
  if (!axis) {
    return undefined;
  }

  if (axis.tickFormatter) {
    return axis.tickFormatter;
  }

  if (axis.format === "currency") {
    return (value: number) =>
      new Intl.NumberFormat(locale, {
        style: "currency",
        currency: axis.currency ?? "BRL",
        maximumFractionDigits: 0,
      }).format(value ?? 0);
  }

  if (axis.format === "percent") {
    return (value: number) =>
      new Intl.NumberFormat(locale, {
        style: "percent",
        maximumFractionDigits: 1,
      }).format((value ?? 0) / 100);
  }

  if (axis.format === "number") {
    return (value: number) =>
      new Intl.NumberFormat(locale, {
        maximumFractionDigits: 2,
      }).format(value ?? 0);
  }

  return undefined;
};

const defaultPalette = (theme: any): string[] => [
  theme.palette.primary.main,
  theme.palette.secondary.main,
  theme.palette.success.main,
  theme.palette.info.main,
  theme.palette.warning.main,
  theme.palette.error.main,
];

export const LineChart: React.FC<LineChartProps> = ({
  data,
  title,
  width = "100%",
  height = 300,
  xKey = "name",
  xIsDate = false,
  xDateFormat,
  axes,
  series,
  showXAxis = true,
  showYAxis = true,
  showGrid = true,
  showDots = true,
  showValueLabels = false,
  showTooltip = true,
  showLegend = true,
  strokeWidth = 2,
  showArea = false,
  colors,
  margin = { top: 5, right: 40, left: 40, bottom: 20 },
  sx,
}) => {
  const theme = useTheme();

  const palette = colors && colors.length > 0 ? colors : defaultPalette(theme);

  const leftAxes = (axes || []).filter((axis) => axis.side === "left");
  const rightAxes = (axes || []).filter((axis) => axis.side === "right");

  const getAxis = (id: string | undefined): LineChartAxisConfig | undefined => {
    if (!id) {
      return axes && axes.length > 0 ? axes[0] : undefined;
    }

    return axes?.find((axis) => axis.id === id);
  };

  const xTickFormatter = xIsDate
    ? (value: any) => {
        if (xDateFormat) {
          return xDateFormat(value);
        }

        const date = value instanceof Date ? value : new Date(value);
        if (Number.isNaN(date.getTime())) {
          return value;
        }

        return new Intl.DateTimeFormat("pt-BR", {
          year: "2-digit",
          month: "2-digit",
        }).format(date);
      }
    : undefined;

  const locale =
    typeof window !== "undefined" && window.navigator?.language
      ? window.navigator.language
      : "pt-BR";

  return (
    <Paper sx={{ p: 2, ...sx }}>
      {title && (
        <Typography variant="h6" component="h3" gutterBottom>
          {title}
        </Typography>
      )}
      <Box sx={{ width, height }}>
        <ResponsiveContainer width="100%" height="100%">
          <RechartsLineChart
            data={data}
            margin={{
              top: margin?.top ?? 5,
              right: margin?.right ?? 40,
              left: margin?.left ?? 40,
              bottom: margin?.bottom ?? 20,
            }}
          >
            {showGrid && <CartesianGrid strokeDasharray="3 3" />}

            {showXAxis && (
              <XAxis
                dataKey={xKey}
                tickFormatter={xTickFormatter}
              />
            )}

            {showYAxis &&
              (axes && axes.length > 0 ? (
                <>
                  {leftAxes.map((axis) => (
                    <YAxis
                      key={axis.id}
                      yAxisId={axis.id}
                      orientation="left"
                      label={
                        axis.label
                          ? {
                              value: axis.label,
                              angle: -90,
                              position: "insideLeft",
                            }
                          : undefined
                      }
                      tickFormatter={buildTickFormatter(axis, locale)}
                    />
                  ))}
                  {rightAxes.map((axis) => (
                    <YAxis
                      key={axis.id}
                      yAxisId={axis.id}
                      orientation="right"
                      label={
                        axis.label
                          ? {
                              value: axis.label,
                              angle: -90,
                              position: "insideRight",
                            }
                          : undefined
                      }
                      tickFormatter={buildTickFormatter(axis, locale)}
                    />
                  ))}
                </>
              ) : (
                <YAxis />
              ))}

            {showTooltip && <Tooltip />}
            {showLegend && <Legend />}

            {series.map((serie, index) => {
              const color = serie.color || palette[index % palette.length];

              return (
                <Line
                  key={serie.id}
                  type="monotone"
                  dataKey={serie.dataKey}
                  name={serie.name}
                  stroke={color}
                  strokeWidth={strokeWidth}
                  yAxisId={getAxis(serie.yAxisId)?.id}
                  dot={showDots}
                  isAnimationActive={true}
                  connectNulls={true}
                  label={
                    showValueLabels
                      ? {
                          position: "top",
                          formatter: (value: number) =>
                            new Intl.NumberFormat(locale, {
                              maximumFractionDigits: 2,
                            }).format(value ?? 0),
                        }
                      : undefined
                  }
                  fill={showArea ? color : undefined}
                  fillOpacity={showArea ? 0.1 : undefined}
                />
              );
            })}
          </RechartsLineChart>
        </ResponsiveContainer>
      </Box>

      {/* Usage example:
      <LineChart
        data={data}
        title="Performance"
        xKey="date"
        xIsDate
        axes={[
          { id: "left-currency", side: "left", label: "Amount", format: "currency" },
          { id: "right-percent", side: "right", label: "Rate", format: "percent" },
        ]}
        series={[
          { id: "revenue", name: "Revenue", dataKey: "revenue", yAxisId: "left-currency" },
          { id: "margin", name: "Margin %", dataKey: "marginPercent", yAxisId: "right-percent" },
        ]}
      />
      */}
    </Paper>
  );
};

export default LineChart;

