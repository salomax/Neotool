import * as React from "react";
import { Box, Typography } from "@mui/material";
import { useTranslation } from "react-i18next";
import { type CurrencyLabels } from "@/shared/utils/currency";
import { formatMetricValue, type MetricValueType } from "@/shared/utils/formatMetricValue";
import { GrowthChip } from "./GrowthChip";

export type { MetricValueType };

export interface MetricValueDisplayProps {
  actualValue: number | null | undefined;
  valueType: MetricValueType;
  currency?: string;
  locale?: string;
  totalizerFormat?: "short" | "long";
  currencyLabels?: CurrencyLabels;
  growthPercentage?: number | null;
  align?: "left" | "right" | "center";
  valueVariant?: "h1" | "h2" | "h3" | "h4" | "h5" | "h6" | "subtitle1" | "subtitle2" | "body1" | "body2" | "caption" | "overline";
}

export function MetricValueDisplay({
  actualValue,
  valueType,
  currency = "BRL",
  locale = "pt-BR",
  totalizerFormat = "short",
  currencyLabels: providedCurrencyLabels,
  growthPercentage,
  align = "right",
  valueVariant = "h5",
}: MetricValueDisplayProps) {
  const { t: tCommon } = useTranslation("common");

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

  const mainValueParts = React.useMemo(() => {
    return formatMetricValue(actualValue, valueType, {
      currency,
      locale,
      currencyLabels,
      totalizerFormat,
    });
  }, [actualValue, valueType, currency, locale, currencyLabels, totalizerFormat]);

  const isNegative = typeof actualValue === 'number' && actualValue < 0;
  const contentColor = isNegative ? "error.main" : "text.primary";
  const suffixPrefixColor = isNegative ? "error.main" : "text.secondary";
  const suffixPrefixOpacity = isNegative ? 1 : 0.7;

  return (
    <Box
      sx={{
        display: "flex",
        flexDirection: "column",
        alignItems: align === "right" ? "flex-end" : align === "center" ? "center" : "flex-start",
        gap: 0.5,
      }}
    >
      <Box
        sx={{
          display: "flex",
          alignItems: "baseline",
          gap: 0.75,
        }}
      >
        {mainValueParts.prefix ? (
          <Typography
            variant={valueVariant}
            component="span"
            sx={{ fontWeight: 500, color: suffixPrefixColor, opacity: suffixPrefixOpacity, fontSize: '0.8em' }}
          >
            {mainValueParts.prefix}
          </Typography>
        ) : null}

        <Typography variant={valueVariant} fontWeight={700} color={contentColor}>
          {mainValueParts.main}
        </Typography>

        {mainValueParts.suffix ? (
          <Typography
            variant={valueVariant}
            component="span"
            sx={{ fontWeight: 500, color: suffixPrefixColor, opacity: suffixPrefixOpacity, fontSize: '0.8em' }}
          >
            {mainValueParts.suffix}
          </Typography>
        ) : null}
      </Box>

      {growthPercentage != null && (
        <GrowthChip growthPercentage={growthPercentage} />
      )}
    </Box>
  );
}
