"use client";

import React from "react";
import { BarChart, Bar, ResponsiveContainer, Cell, Tooltip } from "recharts";
import { useTheme } from "@mui/material";
import type { TimeSeriesDataPoint } from "@/shared/types/charts";
import { createChartTooltipContent } from "./ChartTooltip";

export interface MetricBarChartProps {
  data: TimeSeriesDataPoint[];
  dataKey?: string;
  height?: number;
  width?: number | string;
  /** Maximum number of data points to display (default: 8) */
  maxPoints?: number;
  /** Locale for tooltip number formatting (default: "pt-BR") */
  locale?: string;
}

export function MetricBarChart({
  data,
  dataKey = "value",
  height = 32,
  width = 120,
  maxPoints = 8,
  locale = "pt-BR",
}: MetricBarChartProps) {
  const theme = useTheme();

  // Data is expected to be newest first (index 0 is latest).
  // We want to display oldest to newest (left to right).
  const chartData = React.useMemo(() => {
    // Take up to maxPoints recent periods and reverse to show oldest -> newest
    return [...data].slice(0, maxPoints).reverse();
  }, [data, maxPoints]);

  const TooltipContent = React.useMemo(
    () => createChartTooltipContent(locale),
    [locale]
  );

  if (!data || data.length === 0) return null;

  const widthProp: number | `${number}%` =
    typeof width === "string" && width.endsWith("%")
      ? (width as `${number}%`)
      : (typeof width === "number" ? width : Number(width) || 120);
  const heightProp: number | `${number}%` =
    typeof height === "string" && String(height).endsWith("%")
      ? (height as `${number}%`)
      : (typeof height === "number" ? height : Number(height) || 32);

  return (
    <ResponsiveContainer width={widthProp} height={heightProp}>
      <BarChart data={chartData} margin={{ top: 0, right: 0, bottom: 0, left: 0 }}>
        <Tooltip
          cursor={{ fill: 'transparent' }}
          content={TooltipContent}
        />
        <Bar dataKey={dataKey} radius={[2, 2, 0, 0]}>
          {chartData.map((_, index) => (
            <Cell
              key={`cell-${index}`}
              fill={
                index === chartData.length - 1
                  ? theme.palette.primary.main // Latest value
                  : theme.palette.action.hover // Historical values
              }
            />
          ))}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
}
