"use client";

import React from "react";
import { AreaChart, Area, ResponsiveContainer, Tooltip, YAxis } from "recharts";
import { useTheme } from "@mui/material";
import type { TimeSeriesDataPoint } from "@/shared/types/charts";
import { createChartTooltipContent } from "./ChartTooltip";

export interface MetricAreaChartProps {
  data: TimeSeriesDataPoint[];
  dataKey?: string;
  height?: number;
  width?: number | string;
  color?: string;
  /** Maximum number of data points to display (default: 12) */
  maxPoints?: number;
  /** Locale for tooltip number formatting (default: "pt-BR") */
  locale?: string;
}

export function MetricAreaChart({
  data,
  dataKey = "value",
  height = 32,
  width = 120,
  color,
  maxPoints = 12,
  locale = "pt-BR",
}: MetricAreaChartProps) {
  const theme = useTheme();
  const chartColor = color || theme.palette.primary.main;
  const gradientId = React.useId().replace(/:/g, "");
  const uniqueId = `color-${dataKey}-${gradientId}`;

  // Data is expected to be newest first (index 0 is latest).
  // We want to display oldest to newest (left to right).
  const chartData = React.useMemo(() => {
    // Take up to maxPoints recent periods and reverse to show oldest -> newest
    return [...data].slice(0, maxPoints).reverse().map(item => ({
      ...item,
      // Ensure value is a number
      [dataKey]: Number(item[dataKey as keyof typeof item])
    }));
  }, [data, dataKey, maxPoints]);

  const TooltipContent = React.useMemo(
    () => createChartTooltipContent(locale),
    [locale]
  );

  if (!data || data.length === 0) return null;

  // Calculate min/max to adjust domain if needed
  const values = chartData.map(d => d[dataKey as keyof typeof d] as number);
  const lastValue = values[values.length - 1];
  const isNegative = values.length > 0 && lastValue !== undefined && lastValue < 0;

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
      <AreaChart data={chartData} margin={{ top: 2, right: 0, bottom: 2, left: 0 }}>
        <defs>
          <linearGradient id={uniqueId} x1="0" y1="0" x2="0" y2="1">
            <stop offset="5%" stopColor={chartColor} stopOpacity={isNegative ? 0 : 0.3} />
            <stop offset="95%" stopColor={chartColor} stopOpacity={isNegative ? 0.3 : 0} />
          </linearGradient>
        </defs>
        <Tooltip
          cursor={{ stroke: theme.palette.divider, strokeWidth: 1 }}
          content={TooltipContent}
        />
        <YAxis domain={['auto', 'auto']} hide />
        <Area
          type="monotone"
          dataKey={dataKey}
          stroke={chartColor}
          fillOpacity={1}
          fill={`url(#${uniqueId})`}
          strokeWidth={1.5}
        />
      </AreaChart>
    </ResponsiveContainer>
  );
}
