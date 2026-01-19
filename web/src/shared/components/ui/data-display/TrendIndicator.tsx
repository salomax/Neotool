"use client";

import * as React from "react";
import { Stack, Typography } from "@mui/material";
import TrendingUpIcon from "@mui/icons-material/TrendingUp";
import TrendingDownIcon from "@mui/icons-material/TrendingDown";
import TrendingFlatIcon from "@mui/icons-material/TrendingFlat";
import { useTheme } from "@mui/material/styles";
import { formatPercentageWithSign } from "@/shared/utils/percentage";
import type { SxProps, Theme } from "@mui/material/styles";

export interface TrendIndicatorProps {
  /**
   * The numeric value to display (e.g., 0.12 for 12%)
   */
  value: number | null | undefined;
  /**
   * Format type
   * @default "percentage"
   */
  format?: "percentage";
  /**
   * Number of decimal places
   * @default 0
   */
  decimals?: number;
  /**
   * Font size for the icon
   * Uses theme typography tokens
   */
  iconSize?: string | number;
  /**
   * Font variant for the value text
   * @default "body2"
   */
  variant?: "body1" | "body2" | "caption" | "h6";
  /**
   * Custom sx props for the value Typography
   */
  valueSx?: SxProps<Theme>;
  /**
   * Show icon for zero values
   * @default false
   */
  showZeroIcon?: boolean;
}

/**
 * TrendIndicator component
 *
 * Displays a trend icon (up/down/flat) with a formatted value.
 * Automatically colors the icon and text based on positive/negative/zero values.
 *
 * @example
 * ```tsx
 * <TrendIndicator value={0.12} /> // Shows trending up icon with +12%
 * <TrendIndicator value={-0.05} decimals={1} /> // Shows trending down icon with -5.0%
 * <TrendIndicator value={0} showZeroIcon /> // Shows flat icon with 0%
 * ```
 */
export const TrendIndicator: React.FC<TrendIndicatorProps> = ({
  value,
  format = "percentage",
  decimals = 0,
  iconSize,
  variant = "body2",
  valueSx,
  showZeroIcon = false,
}) => {
  const theme = useTheme();
  const customTheme = theme as any;

  // Memoize color and icon selection
  const { color, Icon } = React.useMemo(() => {
    const positiveColor = customTheme.custom?.palette?.currencyPositive || "success.main";
    const negativeColor = customTheme.custom?.palette?.currencyNegative || "error.main";
    const neutralColor = customTheme.custom?.palette?.currencyNeutral || "text.secondary";

    if (value == null || value === 0) {
      return {
        color: neutralColor,
        Icon: TrendingFlatIcon,
      };
    }

    if (value > 0) {
      return {
        color: positiveColor,
        Icon: TrendingUpIcon,
      };
    }

    return {
      color: negativeColor,
      Icon: TrendingDownIcon,
    };
  }, [value, customTheme]);

  // Memoize formatted value
  const formattedValue = React.useMemo(() => {
    if (format === "percentage") {
      return formatPercentageWithSign(value, { decimals });
    }
    return String(value ?? 0);
  }, [value, format, decimals]);

  // Determine if we should show the icon
  const shouldShowIcon = value !== 0 || showZeroIcon;

  // Use theme typography for icon size if not provided
  const defaultIconSize = theme.typography.body1.fontSize; // 16px (1rem)
  const computedIconSize = iconSize ?? defaultIconSize;

  return (
    <Stack direction="row" spacing={0.5} alignItems="center">
      {shouldShowIcon && (
        <Icon
          sx={{
            fontSize: computedIconSize,
            color,
          }}
        />
      )}
      <Typography
        variant={variant}
        sx={{
          fontWeight: 700,
          color,
          ...valueSx,
        }}
      >
        {formattedValue}
      </Typography>
    </Stack>
  );
};

export default TrendIndicator;
