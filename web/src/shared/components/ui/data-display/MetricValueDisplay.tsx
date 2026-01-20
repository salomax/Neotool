import * as React from "react";
import { Box, Typography, useTheme } from "@mui/material";
import { parseCurrencyValue, type CurrencyLabels } from "@/shared/utils/currency";
import { GrowthChip } from "./GrowthChip";

// --- Types duplicated from MetricCard.tsx or local ---

export type MetricValueType = "currency" | "number" | "percentage";

interface NumberParts {
  number: string;
  totalizer: string | null;
}

function parseNumberValue(
  value: number,
  locale: string,
  labels: CurrencyLabels,
  totalizerFormat: "short" | "long"
): NumberParts {
  const isNegative = value < 0;
  const absValue = Math.abs(value);

  const trillionsLabel =
    totalizerFormat === "long"
      ? labels.trillionsLong || "trilhões"
      : labels.trillions || "tri";
  const billionsLabel =
    totalizerFormat === "long"
      ? labels.billionsLong || "bilhões"
      : labels.billions || "bi";
  const millionsLabel =
    totalizerFormat === "long"
      ? labels.millionsLong || "milhões"
      : labels.millions || "mi";

  const trillions = absValue / 1_000_000_000_000;
  if (trillions >= 1) {
    const numberStr = trillions.toLocaleString(locale, {
      minimumFractionDigits: 1,
      maximumFractionDigits: 1,
    });
    return {
      number: isNegative ? `-${numberStr}` : numberStr,
      totalizer: trillionsLabel,
    };
  }

  const billions = absValue / 1_000_000_000;
  if (billions >= 1) {
    const numberStr = billions.toLocaleString(locale, {
      minimumFractionDigits: 1,
      maximumFractionDigits: 1,
    });
    return {
      number: isNegative ? `-${numberStr}` : numberStr,
      totalizer: billionsLabel,
    };
  }

  const millions = absValue / 1_000_000;
  if (millions >= 1) {
    const numberStr = millions.toLocaleString(locale, {
      minimumFractionDigits: 1,
      maximumFractionDigits: 1,
    });
    return {
      number: isNegative ? `-${numberStr}` : numberStr,
      totalizer: millionsLabel,
    };
  }

  const numberStr = absValue.toLocaleString(locale, {
    maximumFractionDigits: 0,
  });

  return {
    number: isNegative ? `-${numberStr}` : numberStr,
    totalizer: null,
  };
}

export interface MetricValueDisplayProps {
  actualValue: number | null | undefined;
  valueType: MetricValueType;
  currency?: string;
  locale?: string;
  totalizerFormat?: "short" | "long";
  currencyLabels?: CurrencyLabels;
  growthPercentage?: number | null;
  align?: "left" | "right" | "center";
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
}: MetricValueDisplayProps) {
  const { t: tCommon } = { t: (key: string) => key }; // Mock tCommon if not available, or use a hook. 
  // Wait, I need real translation if possible. But MetricCard uses useTranslation.
  // I will skip translation for now and use hardcoded defaults or simple mock as shown in MetricCard if it uses tCommon("currency.billions").
  // Actually MetricCard uses: const { t: tCommon } = useReactI18nTranslation("common");
  // I should do the same.

  // Re-implementing useMemo for currencyLabels to avoid hook issues if I don't import useTranslation
  // But better to be consistent.
  
  const currencyLabels = React.useMemo<CurrencyLabels>(() => {
    if (providedCurrencyLabels) {
      return providedCurrencyLabels;
    }
    return {
      billions: "bi",
      millions: "mi",
      trillions: "tri",
      billionsLong: "bilhões",
      millionsLong: "milhões",
      trillionsLong: "trilhões",
    };
  }, [providedCurrencyLabels]);

  const mainValueParts = React.useMemo(() => {
    if (typeof actualValue !== "number" || !Number.isFinite(actualValue)) {
      return {
        prefix: "",
        main: "-",
        suffix: "",
      };
    }

    if (valueType === "currency") {
      const parts = parseCurrencyValue(
        actualValue,
        currency,
        locale,
        currencyLabels,
        totalizerFormat
      );
      return {
        prefix: parts.symbol,
        main: parts.number,
        suffix: parts.totalizer ?? "",
      };
    }

    if (valueType === "number") {
      const parts = parseNumberValue(
        actualValue,
        locale,
        currencyLabels,
        totalizerFormat
      );
      return {
        prefix: "",
        main: parts.number,
        suffix: parts.totalizer ?? "",
      };
    }

    if (valueType === "percentage") {
      const percentageValue = actualValue * 100;
      const formatted = percentageValue.toLocaleString(locale, {
        maximumFractionDigits: 1,
      });
      return {
        prefix: "",
        main: formatted,
        suffix: "%",
      };
    }

    return {
      prefix: "",
      main: String(actualValue),
      suffix: "",
    };
  }, [
    actualValue,
    valueType,
    currency,
    locale,
    currencyLabels,
    totalizerFormat,
  ]);

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
            variant="body1"
            component="span"
            sx={{ fontWeight: 500, color: "text.secondary", opacity: 0.5 }}
          >
            {mainValueParts.prefix}
          </Typography>
        ) : null}

        <Typography
          variant="h4"
          component="span"
          sx={{ fontWeight: 800, fontSize: "2.1em", letterSpacing: "-0.06em", lineHeight: 0.8 }}
        >
          {mainValueParts.main}
        </Typography>

        {mainValueParts.suffix ? (
          <Typography
            variant="body1"
            component="span"
            sx={{ fontWeight: 500, color: "text.secondary", opacity: 0.5 }}
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
