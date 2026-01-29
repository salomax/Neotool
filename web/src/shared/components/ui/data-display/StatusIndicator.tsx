"use client";

import * as React from "react";
import { Box, Stack, Typography } from "@mui/material";
import { useTheme } from "@mui/material/styles";
import type { SxProps, Theme } from "@mui/material/styles";

export interface StatusIndicatorProps {
  /**
   * The color of the status dot
   * Supports theme palette keys (e.g., "error.main", "success.main")
   */
  color: "error.main" | "warning.main" | "success.main" | string;
  /**
   * Optional label/value to display next to the indicator
   */
  label?: React.ReactNode;
  /**
   * Size of the indicator dot in theme spacing units
   * @default 1 (8px)
   */
  size?: number;
  /**
   * Whether to show a glow effect around the dot
   * @default true
   */
  showGlow?: boolean;
  /**
   * Aria label for accessibility
   */
  ariaLabel?: string;
  /**
   * Font variant for the label text
   * @default "body2"
   */
  variant?: "body1" | "body2" | "caption" | "h6";
  /**
   * Custom sx props for the label Typography
   */
  labelSx?: SxProps<Theme>;
  /**
   * Spacing between indicator and label
   * @default 1
   */
  spacing?: number;
}

/**
 * StatusIndicator component
 *
 * Displays a colored dot with optional glow effect and label.
 * Useful for showing status, health, or threshold-based indicators.
 *
 * @example
 * ```tsx
 * <StatusIndicator
 *   color="success.main"
 *   label="15.2%"
 *   ariaLabel="Basel Index: 15.2%"
 * />
 * <StatusIndicator
 *   color="error.main"
 *   label="Critical"
 *   showGlow={false}
 * />
 * ```
 */
export const StatusIndicator: React.FC<StatusIndicatorProps> = ({
  color,
  label,
  size = 1,
  showGlow = true,
  ariaLabel,
  variant = "body2",
  labelSx,
  spacing = 1,
}) => {
  const theme = useTheme();

  // Memoize color map for resolving theme palette keys
  const resolvedColor = React.useMemo(() => {
    const colorMap: Record<string, string> = {
      "error.main": theme.palette.error.main,
      "warning.main": theme.palette.warning.main,
      "success.main": theme.palette.success.main,
      "info.main": theme.palette.info.main,
      "primary.main": theme.palette.primary.main,
      "secondary.main": theme.palette.secondary.main,
    };

    return colorMap[color] || color;
  }, [color, theme]);

  // Compute box shadow for glow effect
  const boxShadow = React.useMemo(() => {
    if (!showGlow) return undefined;
    return `0 0 ${theme.spacing(1)} ${resolvedColor}40`;
  }, [showGlow, theme, resolvedColor]);

  const indicator = (
    <Box
      sx={{
        width: theme.spacing(size),
        height: theme.spacing(size),
        borderRadius: "50%",
        bgcolor: resolvedColor,
        boxShadow,
        flexShrink: 0,
      }}
      aria-label={ariaLabel}
      role={ariaLabel ? "status" : undefined}
    />
  );

  // If no label, just return the indicator
  if (!label) {
    return indicator;
  }

  return (
    <Stack direction="row" spacing={spacing} alignItems="center">
      {indicator}
      <Typography
        variant={variant}
        sx={{
          fontWeight: 700,
          ...labelSx,
        }}
      >
        {label}
      </Typography>
    </Stack>
  );
};

export default StatusIndicator;
