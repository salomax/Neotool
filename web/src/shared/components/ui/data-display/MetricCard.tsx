"use client";

import * as React from "react";
import { Box, Typography, IconButton } from "@mui/material";
import { useTheme } from "@mui/material/styles";
import type { SxProps, Theme } from "@mui/material/styles";
import Card, {
  CardProps,
} from "@/shared/components/ui/primitives/Card";
import { type CurrencyLabels } from "@/shared/utils/currency";
import { formatMetricValue, type MetricValueType } from "@/shared/utils/formatMetricValue";
import { useTranslation as useReactI18nTranslation } from "react-i18next";
import OpenInNewIcon from "@mui/icons-material/OpenInNew";
import { GrowthChip } from "./GrowthChip";
import { MetricSparkline } from "./MetricSparkline";
import { MetricThresholdBar } from "./MetricThresholdBar";

export type { MetricValueType };

export interface MetricCardProps extends Omit<CardProps, "children"> {
  label: string;
  actualValue: number | null | undefined;
  valueType: MetricValueType;
  currency?: string;
  locale?: string;
  totalizerFormat?: "short" | "long";
  currencyLabels?: CurrencyLabels;
  historical?: number[];
  growthPercentage?: number | null;
  growthLabel?: string;
  contentSx?: SxProps<Theme>;
  percentageThresholdValues?: {
    bad: number;
    regular: number;
    good: number;
  };
  percentageThresholdInverse?: boolean;
  /**
   * Callback when the open/external link icon is clicked.
   * If provided, an icon will be displayed at the top right.
   */
  onOpen?: () => void;
  /**
   * Whether to disable multiplying percentage values by 100.
   * Useful when the data is already in percentage format (e.g. 10.5 for 10.5%).
   */
  disablePercentageScaling?: boolean;
}

export function MetricCard({
  label,
  actualValue,
  valueType,
  currency = "BRL",
  locale = "pt-BR",
  totalizerFormat = "short",
  currencyLabels: providedCurrencyLabels,
  historical,
  growthPercentage,
  growthLabel,
  contentSx,
  percentageThresholdValues,
  percentageThresholdInverse = false,
  sx,
  onOpen,
  disablePercentageScaling = false,
  ...rest
}: MetricCardProps) {
  const { t: tCommon } = useReactI18nTranslation("common");
  const theme = useTheme();

  const { variant, hoverable, onClick, ...restProps } = rest;
  // Filter out props that shouldn't be passed to the DOM/Card
  // chartData and type come from MetricGridItem spreading but aren't part of MetricCardProps
  const { chartData: _ignoredChartData, type: _ignoredType, ...cardRest } = restProps as any;

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
      disablePercentageScaling,
    });
  }, [actualValue, valueType, currency, locale, currencyLabels, totalizerFormat, disablePercentageScaling]);

  const hasGrowth = growthPercentage != null;

  // Determine growth color for chart gradient
  const growthColor = React.useMemo(() => {
    if (!hasGrowth) {
      return theme.palette.primary.main;
    }
    if ((growthPercentage ?? 0) > 0) {
      return theme.palette.success.main;
    }
    if ((growthPercentage ?? 0) < 0) {
      return theme.palette.error.main;
    }
    return theme.palette.text.secondary;
  }, [growthPercentage, hasGrowth, theme.palette]);

  const rawPercentageValue =
    valueType === "percentage" &&
    typeof actualValue === "number" &&
    Number.isFinite(actualValue)
      ? actualValue * 100
      : null;

  const hasPercentageThresholdBar =
    rawPercentageValue != null && !!percentageThresholdValues;

  return (
    <Card
      variant={variant ?? "outlined"}
      hoverable={hoverable ?? !!onClick}
      onClick={onClick}
      sx={{
        p: 2.5,
        display: "flex",
        flexDirection: "column",
        justifyContent: "space-between",
        position: "relative",
        overflow: "hidden",
        minHeight: (theme) => (theme as any).custom?.layout?.metricCard?.minHeight ?? 168,
        ...sx,
      }}
      {...cardRest}
    >
      {onOpen && (
        <Box
          sx={{
            position: "absolute",
            top: 8,
            right: 8,
            zIndex: 10,
          }}
        >
          <IconButton
            onClick={(e) => {
              e.stopPropagation();
              onOpen();
            }}
            size="small"
            aria-label={tCommon("actions.openDetails", "Open details")}
          >
            <OpenInNewIcon fontSize="small" />
          </IconButton>
        </Box>
      )}

      {historical && historical.length > 1 && (
        <MetricSparkline
          historical={historical}
          color={growthColor}
          height={(theme as any).custom?.layout?.metricCard?.chartHeight ?? 100}
        />
      )}

      <Box
        sx={{
          p: 1,
          display: "flex",
          flexDirection: "column",
          gap: 1.25,
          position: "relative",
          ...contentSx,
        }}
      >
        <Typography
          variant="body2"
          sx={{
            fontSize: "0.8em",
            fontWeight: 600,
            textTransform: "uppercase",
            letterSpacing: "0.01em",
            color: "text.secondary",
          }}
        >
          {label}
        </Typography>

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

        {hasGrowth && (
          <Box
            sx={{
              display: { xs: "none", md: "flex" },
              alignItems: "center",
              gap: 1,
            }}
          >
            <GrowthChip growthPercentage={growthPercentage} />
            {growthLabel ? (
              <Typography variant="body2" sx={{ color: "text.secondary" }}>
                {growthLabel}
              </Typography>
            ) : null}
          </Box>
        )}

        {hasPercentageThresholdBar && percentageThresholdValues && rawPercentageValue != null && (
          <MetricThresholdBar
            value={rawPercentageValue}
            thresholds={percentageThresholdValues}
            inverse={percentageThresholdInverse}
          />
        )}
      </Box>
    </Card>
  );
}

export default MetricCard;
