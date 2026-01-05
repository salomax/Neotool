"use client";

import React from "react";
import { Box, Button, type ButtonProps } from "@mui/material";
import { managementHeaderActionButtonSx } from "./headerStyles";

export interface ManagementHeaderActionsProps {
  children: React.ReactNode;
  gap?: number;
  alignItems?: string;
}

/**
 * Layout container for header search/actions in management screens.
 * Ensures consistent gap and vertical alignment.
 */
export const ManagementHeaderActions: React.FC<ManagementHeaderActionsProps> = ({
  children,
  gap = 1.5,
  alignItems = "flex-end",
}) => (
  <Box sx={{ display: "flex", gap, alignItems }}>
    {children}
  </Box>
);

export interface ManagementHeaderActionButtonProps extends ButtonProps {
  /**
   * Optional bottom offset to align with inputs that include their own margin.
   */
  offsetBottom?: number;
}

/**
 * Standard action button for management headers.
 * Applies shared sizing + radius and optional bottom offset.
 */
export const ManagementHeaderActionButton: React.FC<ManagementHeaderActionButtonProps> = ({
  offsetBottom = 2,
  sx,
  children,
  ...rest
}) => {
  const mergedSx = Array.isArray(sx)
    ? [managementHeaderActionButtonSx, ...sx]
    : [managementHeaderActionButtonSx, sx];

  return (
    <Box sx={{ mb: offsetBottom }}>
      <Button sx={mergedSx} {...rest}>
        {children}
      </Button>
    </Box>
  );
};
