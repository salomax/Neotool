"use client";

import * as React from "react";
import { Chip, ChipProps } from "@mui/material";
import { getTestIdProps } from '@/shared/utils/testid';
import { Chip as CustomChip } from './Chip';

export type BadgeColor = "default" | "success" | "warning" | "error" | "info";

export interface BadgeProps extends Omit<ChipProps, "color" | "size"> {
  color?: BadgeColor;
  size?: 'small' | 'medium' | 'large';
  name?: string;
  'data-testid'?: string;
}

export const Badge: React.FC<BadgeProps> = ({ 
  color = "default",
  size = "small",
  name,
  'data-testid': dataTestId,
  ...rest 
}) => {
  // Generate data-testid from component name and optional name prop
  const testIdProps = getTestIdProps('Badge', name, dataTestId);
  
  const map: Record<BadgeColor, ChipProps["color"]> = {
    default: "default",
    success: "success",
    warning: "warning",
    error: "error",
    info: "info",
  };
  const chipColor = map[color] ?? "default";
  return <CustomChip size={size} color={chipColor} variant="outlined" {...testIdProps} {...rest} />;
};

export default Badge;
