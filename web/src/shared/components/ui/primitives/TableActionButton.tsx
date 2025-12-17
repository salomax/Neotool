"use client";

import React from "react";
import { IconButton, Tooltip, alpha, useTheme } from "@mui/material";
import EditOutlinedIcon from "@mui/icons-material/EditOutlined";
import DeleteOutlinedIcon from "@mui/icons-material/DeleteOutlined";
import type { IconButtonProps as MuiIconButtonProps } from "@mui/material";

export interface EditActionButtonProps
  extends Omit<MuiIconButtonProps, "color" | "children"> {
  onClick: () => void;
  tooltipTitle: string;
  ariaLabel: string;
  "data-testid"?: string;
  size?: "small" | "medium" | "large";
}

export interface DeleteActionButtonProps
  extends Omit<MuiIconButtonProps, "color" | "children"> {
  onClick: () => void;
  tooltipTitle: string;
  ariaLabel: string;
  "data-testid"?: string;
  size?: "small" | "medium" | "large";
}

/**
 * EditActionButton - Shared component for edit actions in table rows
 * Features outlined icon with hover effect (background = border color but slightly lighter)
 */
export const EditActionButton: React.FC<EditActionButtonProps> = ({
  onClick,
  tooltipTitle,
  ariaLabel,
  "data-testid": dataTestId,
  size = "small",
  sx,
  ...props
}) => {
  const theme = useTheme();

  return (
    <Tooltip title={tooltipTitle}>
      <IconButton
        onClick={onClick}
        size={size}
        aria-label={ariaLabel}
        data-testid={dataTestId}
        sx={{
          color: theme.palette.primary.main,
          "&:hover": {
            backgroundColor: alpha(theme.palette.primary.main, 0.08),
          },
          ...sx,
        }}
        {...props}
      >
        <EditOutlinedIcon />
      </IconButton>
    </Tooltip>
  );
};

/**
 * DeleteActionButton - Shared component for delete actions in table rows
 * Features outlined icon with hover effect (background = border color but slightly lighter)
 */
export const DeleteActionButton: React.FC<DeleteActionButtonProps> = ({
  onClick,
  tooltipTitle,
  ariaLabel,
  "data-testid": dataTestId,
  size = "small",
  sx,
  ...props
}) => {
  const theme = useTheme();

  return (
    <Tooltip title={tooltipTitle}>
      <IconButton
        onClick={onClick}
        size={size}
        aria-label={ariaLabel}
        data-testid={dataTestId}
        sx={{
          color: theme.palette.error.main,
          "&:hover": {
            backgroundColor: alpha(theme.palette.error.main, 0.08),
          },
          ...sx,
        }}
        {...props}
      >
        <DeleteOutlinedIcon />
      </IconButton>
    </Tooltip>
  );
};
