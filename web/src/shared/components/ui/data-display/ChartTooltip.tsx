"use client";

import React from "react";
import { useTheme } from "@mui/material";

export interface ChartTooltipProps {
  active?: boolean;
  payload?: ReadonlyArray<any>;
  locale?: string;
  formatValue?: (value: unknown) => string;
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

  const rawValue: unknown = payload[0]?.value;
  const value =
    Array.isArray(rawValue) && rawValue.length > 0 ? rawValue[0] : rawValue;
  const displayValue =
    formatValue
      ? formatValue(value)
      : typeof value === "number"
        ? value.toLocaleString(locale)
        : value == null
          ? "—"
          : String(value);

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
  formatValue?: (value: unknown) => string
) {
  return function TooltipContent(props: any) {
    const active = props?.active as boolean | undefined;
    const payload = props?.payload as ReadonlyArray<any> | undefined;
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
