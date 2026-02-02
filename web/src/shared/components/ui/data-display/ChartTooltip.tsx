"use client";

import React from "react";
import { useTheme } from "@mui/material";

export interface ChartTooltipProps {
  active?: boolean;
  payload?: ReadonlyArray<{ value: number | string }>;
  locale?: string;
  formatValue?: (value: number | string) => string;
}

/**
 * Shared tooltip component for chart visualizations
 * Used by MetricAreaChart, MetricBarChart, and other chart components
 */
export function ChartTooltip({
  active,
  payload,
  locale = "pt-BR",
  formatValue,
}: ChartTooltipProps) {
  const theme = useTheme();

  if (!active || !payload || payload.length === 0 || !payload[0]) {
    return null;
  }

  const val = payload[0].value;
  const displayValue = formatValue
    ? formatValue(val)
    : typeof val === "number"
      ? val.toLocaleString(locale)
      : val;

  return (
    <div
      style={{
        backgroundColor: theme.palette.background.paper,
        padding: "4px 8px",
        border: `1px solid ${theme.palette.divider}`,
        borderRadius: "4px",
        fontSize: "12px",
        boxShadow: theme.shadows[1],
        fontWeight: 600,
      }}
    >
      {displayValue}
    </div>
  );
}

/**
 * Creates a tooltip content renderer for Recharts
 * @param locale - Locale for number formatting
 * @param formatValue - Optional custom value formatter
 */
export function createChartTooltipContent(
  locale: string = "pt-BR",
  formatValue?: (value: number | string) => string
) {
  return function TooltipContent({
    active,
    payload,
  }: {
    active?: boolean;
    payload?: ReadonlyArray<{ value: number | string }>;
  }) {
    return (
      <ChartTooltip
        active={active}
        payload={payload}
        locale={locale}
        formatValue={formatValue}
      />
    );
  };
}
