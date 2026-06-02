"use client";

import * as React from "react";
import MuiTabs, { type TabsProps } from "@mui/material/Tabs";
import { useTheme, type Theme } from "@mui/material/styles";

export type FormTabsProps = TabsProps;

function formTabsSx(theme: Theme) {
  return {
    borderBottom: `1px solid ${theme.palette.divider}`,
    "& .MuiTab-root": {
      textTransform: "none" as const,
      fontWeight: theme.typography.button.fontWeight,
      fontSize: theme.typography.button.fontSize,
      letterSpacing: theme.typography.button.letterSpacing,
      lineHeight: theme.typography.button.lineHeight,
      px: 0,
      mr: 3,
      minHeight: 48,
      minWidth: "auto",
    },
    "& .MuiTabs-indicator": {
      height: 3,
      borderRadius: 2,
    },
  };
}

/**
 * Opinionated `Tabs` for forms: bottom border, button-like tab labels, compact indicator.
 * Pass `sx` to extend or override; it merges after the defaults.
 */
export const FormTabs = React.forwardRef<HTMLDivElement, FormTabsProps>(function FormTabs(
  { sx, ...props },
  ref,
) {
  const theme = useTheme();
  const base = formTabsSx(theme);
  const mergedSx = sx == null ? base : Array.isArray(sx) ? [base, ...sx] : [base, sx];

  return <MuiTabs ref={ref} sx={mergedSx} {...props} />;
});

export default FormTabs;
