"use client";

import * as React from "react";
import { Typography, TypographyProps } from "@mui/material";
import { useTheme } from "@mui/material/styles";
import { formatLargeCurrency, formatCurrency } from "@/shared/components/ui/forms/components/masks/br";

export interface CurrencyDisplayProps extends Omit<TypographyProps, 'children'> {
  value: number | null | undefined;
  currency?: string;
  locale?: string;
  format?: "standard" | "large";
  labels?: { billions: string; millions: string };
  showColor?: boolean;
}

export const CurrencyDisplay: React.FC<CurrencyDisplayProps> = ({
  value,
  currency = "BRL",
  locale,
  format = "standard",
  labels,
  showColor = true,
  sx,
  ...typographyProps
}) => {
  const theme = useTheme();

  // Determine color based on value
  const getColor = () => {
    if (!showColor || value === null || value === undefined) {
      return undefined;
    }
    
    if (value > 0) {
      return (theme as any).custom?.palette?.currencyPositive;
    } else if (value < 0) {
      return (theme as any).custom?.palette?.currencyNegative;
    } else {
      return (theme as any).custom?.palette?.currencyNeutral;
    }
  };

  // Format the currency value
  const formattedValue = React.useMemo(() => {
    if (format === "large") {
      return formatLargeCurrency(value, currency, locale, labels);
    }
    return formatCurrency(value, currency, locale);
  }, [value, currency, locale, format, labels]);

  const color = getColor();

  return (
    <Typography
      sx={{
        color,
        ...sx,
      }}
      {...typographyProps}
    >
      {formattedValue}
    </Typography>
  );
};

export default CurrencyDisplay;