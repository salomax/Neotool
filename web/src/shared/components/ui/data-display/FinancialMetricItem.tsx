"use client";

import * as React from "react";
import { Box, Typography } from "@mui/material";
import { useTheme } from "@mui/material/styles";
import type { SxProps, Theme } from "@mui/material/styles";

export interface FinancialMetricItemProps {
  /**
   * Label text for the metric
   */
  label: string;
  /**
   * Value content to display (can be text, number, or component)
   */
  children: React.ReactNode;
  /**
   * Custom sx props for the label
   */
  labelSx?: SxProps<Theme>;
  /**
   * Custom sx props for the value
   */
  valueSx?: SxProps<Theme>;
  /**
   * Custom sx props for the container
   */
  containerSx?: SxProps<Theme>;
}

/**
 * Get metric label styling
 * Uses theme tokens for consistency
 */
const getMetricLabelSx = (theme: Theme): SxProps<Theme> => {
  // small token (14px) maps to body2 in MUI theme
  const smallSize = theme.typography.body2.fontSize ?? 14;
  const size = typeof smallSize === 'string'
    ? parseFloat(smallSize)
    : smallSize;
  const fontSize = `${(size * 10) / 14}px`; // 10px derived from small (14px)

  return {
    fontSize,
    fontWeight: theme.typography.fontWeightMedium || 600,
    textTransform: "uppercase",
    letterSpacing: "0.05em",
    color: "text.secondary",
    display: "block",
    mb: 0.5,
  };
};

/**
 * Get metric value styling
 * Uses theme typography tokens
 */
const getMetricValueSx = (theme: Theme): SxProps<Theme> => ({
  fontWeight: 700,
  fontSize: theme.typography.body2.fontSize, // 14px (0.875rem)
});

/**
 * FinancialMetricItem component
 *
 * Displays a labeled financial metric with consistent styling.
 * Used for showing key financial data like profit, equity, etc.
 *
 * @example
 * ```tsx
 * <FinancialMetricItem label="Net Profit">
 *   <CurrencyDisplay value={1000000} currency="BRL" />
 * </FinancialMetricItem>
 *
 * <FinancialMetricItem label="YoY Growth">
 *   <TrendIndicator value={0.12} />
 * </FinancialMetricItem>
 * ```
 */
export const FinancialMetricItem: React.FC<FinancialMetricItemProps> = ({
  label,
  children,
  labelSx,
  valueSx,
  containerSx,
}) => {
  const theme = useTheme();

  const finalLabelSx = React.useMemo(
    () => ({
      ...getMetricLabelSx(theme),
      ...labelSx,
    }),
    [theme, labelSx]
  );

  const finalValueSx = React.useMemo(
    () => ({
      ...getMetricValueSx(theme),
      ...valueSx,
    }),
    [theme, valueSx]
  );

  return (
    <Box sx={containerSx}>
      <Typography variant="caption" sx={finalLabelSx}>
        {label}
      </Typography>
      <Typography variant="body2" component="div" sx={finalValueSx}>
        {children}
      </Typography>
    </Box>
  );
};

export default FinancialMetricItem;
