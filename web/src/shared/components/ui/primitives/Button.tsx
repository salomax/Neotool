"use client";
import React from "react";
import {
  Button as MUIButton,
  ButtonProps as MUIButtonProps,
  CircularProgress,
} from "@mui/material";
import { getTestIdProps } from '@/shared/utils/testid';

export type ButtonSize = "small" | "medium" | "large";
export interface ButtonProps extends Omit<MUIButtonProps, "size"> {
  size?: ButtonSize;
  loading?: boolean;
  loadingText?: string;
  loadingIconSize?: number;
  name?: string;
  'data-testid'?: string;
}

const sizeMap: Record<ButtonSize, MUIButtonProps["size"]> = {
  small: "small",
  medium: "medium",
  large: "large",
};

export const Button: React.FC<ButtonProps> = ({
  size = "medium",
  loading = false,
  loadingText,
  loadingIconSize = 20,
  children,
  startIcon,
  disabled,
  name,
  'data-testid': dataTestId,
  ...rest
}) => {
  const muiSize = sizeMap[size] ?? "medium";
  
  // Generate data-testid from component name and optional name prop
  const testIdProps = getTestIdProps('Button', name, dataTestId);
  
  const getLoadingIcon = () => {
    if (!loading) return startIcon;
    return <CircularProgress size={loadingIconSize} />;
  };

  const getButtonText = () => {
    if (loading && loadingText) return loadingText;
    return children;
  };

  return (
    <MUIButton
      size={muiSize}
      startIcon={getLoadingIcon()}
      disabled={disabled || loading}
      {...testIdProps}
      {...rest}
    >
      {getButtonText()}
    </MUIButton>
  );
};

export default Button;
