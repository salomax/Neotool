"use client";

import * as React from "react";
import MuiCard from "@mui/material/Card";
import type { CardProps as MuiCardProps } from "@mui/material/Card";
import type { SxProps, Theme } from "@mui/material/styles";

export interface CardProps extends Omit<MuiCardProps, 'sx' | 'variant'> {
  /**
   * Children to render inside the card
   */
  children: React.ReactNode;
  /**
   * Click handler for the card
   */
  onClick?: () => void;
  /**
   * Card variant. "elevated" maps to MUI's "elevation".
   * @default "outlined"
   */
  variant?: "outlined" | "elevated";
  /**
   * Enable smooth hover effect
   * @default true
   */
  hoverable?: boolean;
  /**
   * Additional sx styles
   */
  sx?: SxProps<Theme>;
}

/**
 * Generic Card component with consistent styling and smooth hover effects.
 * 
 * This is a reusable wrapper around MUI Card that provides:
 * - Consistent border radius
 * - Smooth hover transitions (elevation/shadow changes)
 * - Cursor pointer when onClick is provided
 * 
 * @example
 * ```tsx
 * <Card onClick={() => navigate('/detail')} hoverable>
 *   <CardContent>
 *     Content here
 *   </CardContent>
 * </Card>
 * ```
 */
export function Card({
  children,
  onClick,
  variant = "outlined",
  hoverable = true,
  sx,
  ...props
}: CardProps) {
  const isClickable = !!onClick;

  const combinedSx: SxProps<Theme> = React.useMemo(() => {
    const baseStyles: Record<string, unknown> = {
      borderRadius: 2,
      boxShadow:
        variant === "outlined"
          ? "0 10px 30px rgba(15, 23, 42, 0.06)"
          : "0 5px 15px rgba(15, 23, 42, 0.08)",
      transition: hoverable
        ? 'box-shadow 0.2s ease-in-out, transform 0.2s ease-in-out'
        : undefined,
    };

    if (isClickable) {
      baseStyles.cursor = 'pointer';
    }

    if (hoverable && isClickable) {
      baseStyles['&:hover'] = {
        boxShadow:
          variant === "outlined"
            ? "0 14px 40px rgba(15, 23, 42, 0.08)"
            : "0 18px 46px rgba(15, 23, 42, 0.10)",
        transform: 'translateY(-2px)',
      };
    } else if (hoverable) {
      baseStyles['&:hover'] = {
        boxShadow:
          variant === "outlined"
            ? "0 12px 34px rgba(15, 23, 42, 0.07)"
            : "0 16px 42px rgba(15, 23, 42, 0.09)",
      };
    }

    return (sx != null ? [baseStyles as SxProps<Theme>, sx] : baseStyles) as SxProps<Theme>;
  }, [hoverable, isClickable, variant, sx]);

  return (
    <MuiCard
      variant={variant === 'elevated' ? 'elevation' : variant}
      onClick={onClick}
      sx={combinedSx}
      {...props}
    >
      {children}
    </MuiCard>
  );
}

export default Card;
