"use client";

import * as React from "react";
import { Box, Typography, IconButton } from "@mui/material";
import { useTheme } from "@mui/material/styles";
import type { SxProps, Theme } from "@mui/material/styles";
import {
  AreaChart as RechartsAreaChart,
  Area,
  ResponsiveContainer,
  XAxis,
  YAxis,
} from "recharts";
import Card, {
  CardProps,
} from "@/shared/components/ui/primitives/Card";
import { type CurrencyLabels } from "@/shared/utils/currency";
import { formatMetricValue, type MetricValueType } from "@/shared/utils/formatMetricValue";
import { useTranslation as useReactI18nTranslation } from "react-i18next";
import OpenInNewIcon from "@mui/icons-material/OpenInNew";
import { GrowthChip } from "./GrowthChip";

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
  ...rest
}: MetricCardProps) {
  const { t: tCommon } = useReactI18nTranslation("common");
  const theme = useTheme();
  const chartId = React.useId().replace(/:/g, "_");

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
    });
  }, [actualValue, valueType, currency, locale, currencyLabels, totalizerFormat]);

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

  const chartData = React.useMemo(
    () =>
      (historical ?? []).map((value, index) => ({
        index,
        value,
      })),
    [historical]
  );

  const chartDomain = React.useMemo(() => {
    if (!historical || historical.length === 0) return ["auto", "auto"];
    const values = historical;
    const min = Math.min(...values);
    const max = Math.max(...values);

    const range = max - min;
    if (range === 0) {
      const margin = Math.abs(min) * 0.2 || 1;
      return [min - margin, max + margin];
    }

    const margin = range * 0.2;
    return [min - margin, max + margin];
  }, [historical]);

  const showChart = chartData.length > 1;

  const rawPercentageValue =
    valueType === "percentage" &&
    typeof actualValue === "number" &&
    Number.isFinite(actualValue)
      ? actualValue * 100
      : null;

  const hasPercentageThresholdBar =
    rawPercentageValue != null && !!percentageThresholdValues;

  const segmentWidths = React.useMemo(() => {
    if (
      !hasPercentageThresholdBar ||
      !percentageThresholdValues
    ) {
      return { bad: 0, regular: 0, good: 100 };
    }

    if (percentageThresholdInverse) {
      const goodWidth = percentageThresholdValues.good;
      const regularWidth =
        percentageThresholdValues.regular - percentageThresholdValues.good;
      const badWidth = 100 - percentageThresholdValues.regular;
      return {
        bad: Math.max(0, Math.min(100, badWidth)),
        regular: Math.max(0, Math.min(100, regularWidth)),
        good: Math.max(0, Math.min(100, goodWidth)),
      };
    }

    const badWidth = percentageThresholdValues.bad;
    const regularWidth =
      percentageThresholdValues.regular - percentageThresholdValues.bad;
    const goodWidth = 100 - percentageThresholdValues.regular;
    return {
      bad: Math.max(0, Math.min(100, badWidth)),
      regular: Math.max(0, Math.min(100, regularWidth)),
      good: Math.max(0, Math.min(100, goodWidth)),
    };
  }, [
    hasPercentageThresholdBar,
    percentageThresholdValues,
    percentageThresholdInverse,
  ]);

  const progressValue = React.useMemo(() => {
    if (!hasPercentageThresholdBar || rawPercentageValue == null) {
      return 0;
    }
    const clamped = Math.min(Math.max(rawPercentageValue, 0), 100);
    return clamped;
  }, [hasPercentageThresholdBar, rawPercentageValue]);

  const percentageStatus = React.useMemo<
    "good" | "regular" | "bad" | "neutral"
  >(() => {
    if (
      !hasPercentageThresholdBar ||
      rawPercentageValue == null ||
      !percentageThresholdValues
    ) {
      return "neutral";
    }

    const value = rawPercentageValue;

    if (percentageThresholdInverse) {
      if (value < percentageThresholdValues.good) {
        return "good";
      }
      if (value <= percentageThresholdValues.regular) {
        return "regular";
      }
      if (value > percentageThresholdValues.regular) {
        return "bad";
      }
      return "neutral";
    }

    if (value < percentageThresholdValues.bad) {
      return "bad";
    }
    if (value <= percentageThresholdValues.regular) {
      return "regular";
    }
    return "good";
  }, [
    hasPercentageThresholdBar,
    rawPercentageValue,
    percentageThresholdValues,
    percentageThresholdInverse,
  ]);

  const thresholdColors = React.useMemo(() => {
    const customTheme = theme as any;
    return {
      bad: customTheme.custom?.palette?.thresholdBad || "#F99797",
      regular: customTheme.custom?.palette?.thresholdRegular || "#FAD957",
      good: customTheme.custom?.palette?.thresholdGood || "#61D48A",
    };
  }, [theme]);

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
      {showChart && (
        <Box
          sx={{
            position: "absolute",
            left: 0,
            right: 0,
            bottom: 0,
            height: (theme) => (theme as any).custom?.layout?.metricCard?.chartHeight ?? 100,
            pointerEvents: "none",
          }}
        >
          <ResponsiveContainer width="100%" height="100%">
            <RechartsAreaChart
              data={chartData}
              margin={{ top: 0, right: 0, bottom: 0, left: 0 }}
            >
              <defs>
                <linearGradient id={`${chartId}-stroke`} x1="0" y1="0" x2="1" y2="0">
                  <stop offset="0%" stopColor={growthColor} stopOpacity={0} />
                  <stop offset="50%" stopColor={growthColor} stopOpacity={0.1} />
                  <stop offset="100%" stopColor={growthColor} stopOpacity={0.3} />
                </linearGradient>
                <linearGradient id={`${chartId}-fill`} x1="0" y1="0" x2="1" y2="0">
                  <stop offset="0%" stopColor={growthColor} stopOpacity={0} />
                  <stop offset="50%" stopColor={growthColor} stopOpacity={0.02} />
                  <stop offset="100%" stopColor={growthColor} stopOpacity={0.1} />
                </linearGradient>
              </defs>
              <XAxis
                dataKey="index"
                type="number"
                domain={["dataMin", "dataMax"]}
                hide
                padding={{ left: 0, right: 0 }}
                axisLine={false}
                tickLine={false}
              />
              <YAxis domain={chartDomain} hide />
              <Area
                type="monotone"
                dataKey="value"
                stroke={`url(#${chartId}-stroke)`}
                fill={`url(#${chartId}-fill)`}
                strokeWidth={2}
                baseValue={chartDomain[0] as number}
                isAnimationActive
              />
            </RechartsAreaChart>
          </ResponsiveContainer>
        </Box>
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
              display: "flex",
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

        {hasPercentageThresholdBar && (
          <Box sx={{ mt: 1 }}>
            <Box sx={{ position: "relative" }}>
              <Box
                sx={{
                  height: 8,
                  borderRadius: 4,
                  overflow: "hidden",
                  position: "relative",
                  display: "flex",
                }}
              >
                {percentageThresholdInverse ? (
                  <>
                    <Box
                      sx={{
                        height: "100%",
                        width: `${segmentWidths.good}%`,
                        bgcolor: thresholdColors.good,
                      }}
                    />
                    <Box
                      sx={{
                        height: "100%",
                        width: `${segmentWidths.regular}%`,
                        bgcolor: thresholdColors.regular,
                      }}
                    />
                    <Box
                      sx={{
                        height: "100%",
                        width: `${segmentWidths.bad}%`,
                        bgcolor: thresholdColors.bad,
                      }}
                    />
                  </>
                ) : (
                  <>
                    <Box
                      sx={{
                        height: "100%",
                        width: `${segmentWidths.bad}%`,
                        bgcolor: thresholdColors.bad,
                      }}
                    />
                    <Box
                      sx={{
                        height: "100%",
                        width: `${segmentWidths.regular}%`,
                        bgcolor: thresholdColors.regular,
                      }}
                    />
                    <Box
                      sx={{
                        height: "100%",
                        width: `${segmentWidths.good}%`,
                        bgcolor: thresholdColors.good,
                      }}
                    />
                  </>
                )}
              </Box>

              {percentageStatus !== "neutral" && (
                <Box
                  sx={{
                    position: "absolute",
                    left: `${progressValue}%`,
                    top: -8,
                    width: 0,
                    height: 0,
                    borderLeft: "10px solid transparent",
                    borderRight: "10px solid transparent",
                    borderBottom: `8px solid ${
                      thresholdColors[
                        percentageStatus as "bad" | "regular" | "good"
                      ]
                    }`,
                    transform: "translateX(-50%)",
                    zIndex: 1,
                  }}
                />
              )}
            </Box>
          </Box>
        )}
      </Box>
    </Card>
  );
}

export default MetricCard;
