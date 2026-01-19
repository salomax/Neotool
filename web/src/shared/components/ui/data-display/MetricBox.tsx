"use client";

import * as React from "react";
import { Box, Paper, Typography, Skeleton } from "@mui/material";
import { useTranslation as useReactI18nTranslation } from "react-i18next";
import { parseCurrencyValue, CurrencyLabels } from "@/shared/utils/currency";

export interface MetricBoxProps {
  label: string;
  value: number | null | undefined;
  isLoading?: boolean;
  currency?: string;
  isNumber?: boolean; // For non-currency numeric values like branch count
  totalizerFormat?: "short" | "long"; // Format for totalizer (bi/mi vs bilhões/milhões)
  locale?: string;
  currencyLabels?: CurrencyLabels;
}

/**
 * MetricBox component for displaying metric values (currency or numbers)
 * 
 * @example
 * ```tsx
 * <MetricBox
 *   label="Total Assets"
 *   value={1000000000}
 *   currency="BRL"
 *   totalizerFormat="long"
 * />
 * ```
 */
export const MetricBox: React.FC<MetricBoxProps> = ({ 
  label, 
  value, 
  isLoading = false,
  currency = "BRL",
  isNumber = false,
  totalizerFormat = "long",
  locale = "pt-BR",
  currencyLabels: providedCurrencyLabels
}) => {
  const { t: tCommon } = useReactI18nTranslation("common");

  // Use provided labels or fall back to common translations
  const currencyLabels = React.useMemo<CurrencyLabels>(() => {
    if (providedCurrencyLabels) {
      return providedCurrencyLabels;
    }
    return {
      billions: tCommon("currency.billions"),
      millions: tCommon("currency.millions"),
      trillions: tCommon("currency.trillions"),
      billionsLong: tCommon("currency.billionsLong"),
      millionsLong: tCommon("currency.millionsLong"),
      trillionsLong: tCommon("currency.trillionsLong"),
    };
  }, [providedCurrencyLabels, tCommon]);

  if (isLoading) {
    return (
      <Paper variant="outlined" sx={{ p: 2, height: "100%" }}>
        <Skeleton width="60%" height={20} sx={{ mb: 1 }} />
        <Skeleton width="80%" height={32} />
        <Skeleton width="40%" height={20} />
      </Paper>
    );
  }

  const currencyParts = !isNumber && value != null
    ? parseCurrencyValue(value, currency, locale, currencyLabels, totalizerFormat)
    : null;

  return (
    <Paper variant="outlined" sx={{ p: 2, height: "100%", display: 'flex', flexDirection: 'column', justifyContent: 'center' }}>
      <Typography variant="body2" color="text.secondary" gutterBottom>
        {label}
      </Typography>
      {isNumber ? (
        <Typography variant="h5" component="div" sx={{ fontWeight: "bold" }}>
          {value != null ? value.toLocaleString(locale) : "-"}
        </Typography>
      ) : (
        <Box>
          {value != null && currencyParts ? (
            <>
              <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 0.5 }}>
                <Typography 
                  variant="body1" 
                  component="span" 
                  sx={{ color: 'text.secondary', fontWeight: 'normal' }}
                >
                  {currencyParts.symbol}
                </Typography>
                <Typography 
                  variant="h5" 
                  component="span" 
                  sx={{ fontWeight: "bold" }}
                >
                  {currencyParts.number}
                </Typography>
              </Box>
              {currencyParts.totalizer && (
                <Typography 
                  variant="body2" 
                  component="div" 
                  sx={{ color: 'text.secondary', mt: 0.5 }}
                >
                  {currencyParts.totalizer}
                </Typography>
              )}
            </>
          ) : (
            <Typography variant="h5" component="div" sx={{ fontWeight: "bold" }}>
              -
            </Typography>
          )}
        </Box>
      )}
    </Paper>
  );
};
