import * as React from "react";
import { Box } from "@mui/material";
import {
  AreaChart as RechartsAreaChart,
  Area,
  ResponsiveContainer,
  XAxis,
  YAxis,
} from "recharts";

export interface MetricSparklineProps {
  historical: number[];
  color: string;
  height?: number | string;
  isAnimationActive?: boolean;
  /** Recharts Area curve interpolation. Default `monotone`. */
  curveType?: React.ComponentProps<typeof Area>["type"];
}

export const MetricSparkline = React.memo(function MetricSparkline({
  historical,
  color,
  height = 100,
  isAnimationActive = true,
  curveType = "monotone",
}: MetricSparklineProps) {
  const chartId = React.useId().replace(/:/g, "_");

  const seriesValues = React.useMemo(() => {
    if (historical.length === 1) {
      const v = historical[0];
      if (v == null || !Number.isFinite(v)) return [];
      return [v, v];
    }
    return historical;
  }, [historical]);

  const chartData = React.useMemo(
    () =>
      seriesValues.map((value, index) => ({
        index,
        value,
      })),
    [seriesValues],
  );

  const chartDomain = React.useMemo(() => {
    if (seriesValues.length === 0) return ["auto", "auto"];
    const min = Math.min(...seriesValues);
    const max = Math.max(...seriesValues);

    const range = max - min;
    if (range === 0) {
      const margin = Math.abs(min) * 0.2 || 1;
      return [min - margin, max + margin];
    }

    const margin = range * 0.2;
    return [min - margin, max + margin];
  }, [seriesValues]);

  if (historical.length < 1) {
    return null;
  }

  return (
    <Box
      sx={{
        display: "block",
        position: "absolute",
        left: 0,
        right: 0,
        bottom: 0,
        height,
        pointerEvents: "none",
      }}
    >
      <ResponsiveContainer width="100%" height="100%">
        <RechartsAreaChart
          data={chartData}
          margin={{ top: 0, right: 0, bottom: 0, left: 0 }}
        >
          <defs>
            <linearGradient id={`${chartId}-stroke`} x1="0" y1="0" x2="1" y2="0">
              <stop offset="0%" stopColor={color} stopOpacity={0} />
              <stop offset="50%" stopColor={color} stopOpacity={0.1} />
              <stop offset="100%" stopColor={color} stopOpacity={0.3} />
            </linearGradient>
            <linearGradient id={`${chartId}-fill`} x1="0" y1="0" x2="1" y2="0">
              <stop offset="0%" stopColor={color} stopOpacity={0} />
              <stop offset="50%" stopColor={color} stopOpacity={0.02} />
              <stop offset="100%" stopColor={color} stopOpacity={0.1} />
            </linearGradient>
          </defs>
          <XAxis
            dataKey="index"
            type="number"
            domain={["dataMin", "dataMax"]}
            hide
            padding={{ left: 0, right: 0 }}
            axisLine={false}
            tickLine={false}
          />
          <YAxis domain={chartDomain as [number, number]} hide />
          <Area
            type={curveType}
            dataKey="value"
            stroke={`url(#${chartId}-stroke)`}
            fill={`url(#${chartId}-fill)`}
            strokeWidth={2}
            baseValue={chartDomain[0] as number}
            isAnimationActive={isAnimationActive}
          />
        </RechartsAreaChart>
      </ResponsiveContainer>
    </Box>
  );
});
