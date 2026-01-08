"use client";

import * as React from "react";
import MuiCard from "@mui/material/Card";
import type { CardProps as MuiCardProps } from "@mui/material/Card";
import type { SxProps, Theme } from "@mui/material/styles";

export interface CardProps extends Omit<MuiCardProps, 'sx'> {
  /**
   * Children to render inside the card
   */
  children: React.ReactNode;
  /**
   * Click handler for the card
   */
  onClick?: () => void;
  /**
   * Card variant
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
    const baseStyles: SxProps<Theme> = {
      borderRadius: 2,
      transition: hoverable
        ? 'box-shadow 0.2s ease-in-out, transform 0.2s ease-in-out'
        : undefined,
    };

    if (isClickable) {
      baseStyles.cursor = 'pointer';
    }

    if (hoverable && isClickable) {
      baseStyles['&:hover'] = {
        boxShadow: variant === "outlined" ? 4 : 6,
        transform: 'translateY(-2px)',
      };
    } else if (hoverable) {
      baseStyles['&:hover'] = {
        boxShadow: variant === "outlined" ? 2 : 4,
      };
    }

    return [baseStyles, sx];
  }, [hoverable, isClickable, variant, sx]);

  return (
    <MuiCard
      variant={variant}
      onClick={onClick}
      sx={combinedSx}
      {...props}
    >
      {children}
    </MuiCard>
  );
}

export default Card;

