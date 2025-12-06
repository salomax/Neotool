"use client";

import * as React from "react";
import {
  Tooltip as MuiTooltip,
  TooltipProps as MuiTooltipProps,
} from "@mui/material";
import { getTestIdProps } from '@/shared/utils/testid';

export interface TooltipProps extends MuiTooltipProps {
  name?: string;
  'data-testid'?: string;
}

export const Tooltip: React.FC<TooltipProps> = ({ 
  name,
  'data-testid': dataTestId,
  ...props 
}) => {
  // Generate data-testid from component name and optional name prop
  const testIdProps = getTestIdProps('Tooltip', name, dataTestId);
  
  return <MuiTooltip enterDelay={400} arrow {...testIdProps} {...props} />;
};

export default Tooltip;
