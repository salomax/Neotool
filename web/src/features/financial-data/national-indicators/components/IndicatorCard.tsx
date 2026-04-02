import React, { useState, useMemo, useCallback } from "react";
import { Card } from "@/shared/components/ui/primitives";
import { Box, Typography, Skeleton, Tooltip, IconButton, useTheme, alpha } from "@mui/material";
import InfoOutlinedIcon from "@mui/icons-material/InfoOutlined";
import OpenInNewIcon from "@mui/icons-material/OpenInNew";
import { SegmentedControl } from "@/shared/components/ui/inputs";
import { EconomicIndicator } from "../hooks/useEconomicIndicators";
import { IndicatorLatest } from "../hooks/useIndicatorsLatest";
import { useTranslation, i18n } from "@/shared/i18n";
import { financialDataTranslations } from "@/shared/i18n/domains/financial-data";
import { formatMetricValue, MetricValueType } from "@/shared/utils/formatMetricValue";
import { formatTableDate } from "@/shared/utils/date";
import {
  AreaChart as RechartsAreaChart,
  Area,
  ResponsiveContainer,
  YAxis,
} from "recharts";
import { MetricDetailsDrawer } from "@/shared/components/ui/data-display/MetricDetailsDrawer";

interface IndicatorCardProps {
  indicator: EconomicIndicator;
  latestValues: Record<string, IndicatorLatest>;
  loading?: boolean;
  displayName?: string;
  displayDescription?: string;
}

export const IndicatorCard: React.FC<IndicatorCardProps> = ({ indicator, latestValues, loading = false, displayName, displayDescription }) => {
  const { t, tCommon } = useTranslation(financialDataTranslations);
  const theme = useTheme();
  const chartId = React.useId().replace(/:/g, "_");
  
  // Use provided displayName or fallback to translation key or raw label
  const label = displayName || t(`nationalIndicators.indicators.${indicator.label}.label`);
  
  // Use provided displayDescription or fallback to translation key or raw description
  const description = displayDescription || t(`nationalIndicators.indicators.${indicator.label}.description`);
  const issuer = t(`nationalIndicators.indicators.${indicator.label}.issuer`);

  const defaultVariation = indicator.variations.find(v => v.isDefault) || indicator.variations[0];
  const [selectedVariationId, setSelectedVariationId] = useState<string>(defaultVariation?.id || "");
  const [drawerOpen, setDrawerOpen] = useState(false);

  // Robust lookup for latest data (handles ID vs Label mismatch and case sensitivity)
  const latestData = useMemo(() => {
    if (!selectedVariationId) return undefined;
    
    // 1. Direct match (ID)
    if (latestValues[selectedVariationId]) return latestValues[selectedVariationId];

    const selectedVariation = indicator.variations.find(v => v.id === selectedVariationId);
    if (!selectedVariation) return undefined;

    // 2. Case-insensitive ID match
    const values = Object.values(latestValues);
    const idMatch = values.find(v => v.variation.toUpperCase() === selectedVariation.id.toUpperCase());
    if (idMatch) return idMatch;

    // 3. Label match (exact or case-insensitive)
    return values.find(v => 
        v.variation === selectedVariation.label || 
        v.variation.toUpperCase() === selectedVariation.label.toUpperCase()
    );
  }, [latestValues, selectedVariationId, indicator.variations]);

  const latest = latestData?.latest;
  const series = useMemo(() => latestData?.series ?? [], [latestData?.series]);

  // Derive value type and currency
  let valueType: MetricValueType = "number";
  let currencyCode: "BRL" | "USD" = "BRL";
  if (indicator.unit === "PERCENT" || indicator.unit === "%") valueType = "percentage";
  else if (indicator.unit === "BRL" || indicator.unit === "BRL_MILLION") valueType = "currency";
  else if (indicator.unit === "USD_MILLION") { valueType = "currency"; currencyCode = "USD"; }

  const isMillionUnit = indicator.unit === "BRL_MILLION" || indicator.unit === "USD_MILLION";
  const multiplier = isMillionUnit ? 1_000_000 : 1;
  const displayValue = latest?.value != null ? latest.value * multiplier : latest?.value;

  // Format value
  const { prefix, main, suffix } = formatMetricValue(displayValue, valueType, {
      currency: currencyCode,
      locale: i18n.language,
      decimals: valueType === "percentage" ? 2 : undefined,
      disablePercentageScaling: true
  });

  const variationOptions = useMemo(() => {
    const sortOrder = ["MONTHLY_CHANGE", "YTD", "ROLLING_12M", "EFFECTIVE_RATE", "TARGET_RATE"];
    
    return [...indicator.variations]
      .sort((a, b) => {
        const indexA = sortOrder.indexOf(a.label);
        const indexB = sortOrder.indexOf(b.label);
        
        if (indexA !== -1 && indexB !== -1) return indexA - indexB;
        if (indexA !== -1) return -1;
        if (indexB !== -1) return 1;
        
        return a.label.localeCompare(b.label);
      })
      .map(v => ({
        value: v.id,
        label: t(`nationalIndicators.variations.${v.label}`)
      }));
  }, [indicator.variations, t]);

  // Chart Logic
  const chartDomain = useMemo(() => {
    if (!series || series.length === 0) return ["auto", "auto"];
    const values = series.map(s => s.value * multiplier);
    const min = Math.min(...values);
    const max = Math.max(...values);

    const range = max - min;
    if (range === 0) {
      const margin = Math.abs(min) * 0.2 || 1;
      return [min - margin, max + margin];
    }

    const margin = range * 0.2;
    return [min - margin, max + margin];
  }, [series, multiplier]);

  const chartData = useMemo(() => {
      // Sort by date ascending for the chart
      const sortedSeries = [...series].sort((a, b) => new Date(a.referenceDate).getTime() - new Date(b.referenceDate).getTime());
      
      return sortedSeries.map((item, index) => ({
          index,
          value: item.value * multiplier,
          date: item.referenceDate,
          quarter: item.referenceDate // Required by MetricDetailsDrawer components (MetricHistoryTable, MetricLineChart)
      }));
  }, [series, multiplier]);

  const showChart = chartData.length > 1;
  const growthColor = theme.palette.primary.main; // Default color

  const isQuarterly = indicator.frequency?.toUpperCase() === "QUARTERLY";
  const isMonthly = indicator.frequency?.toUpperCase() === "MONTHLY";

  const formatPeriod = useCallback((dateStr: string, formatType: 'full' | 'short' = 'full') => {
      const date = new Date(dateStr);
      
      if (isQuarterly) {
          const quarter = Math.floor((date.getUTCMonth() + 3) / 3);
          const year = date.getUTCFullYear();
          const quarterKey = `q${quarter}`;
          const quarterLabel = tCommon(`calendar.quarters.${quarterKey}`);

          if (formatType === 'short') {
              return `${quarterLabel} ${String(year).slice(-2)}`;
          }
          return `${quarterLabel} ${year}`;
      }

      if (isMonthly) {
        const month = date.toLocaleString(i18n.language, { month: 'short', timeZone: 'UTC' });
        const year = date.getUTCFullYear();
        const formattedMonth = month.charAt(0).toUpperCase() + month.slice(1).replace('.', '');
        
        if (formatType === 'short') {
             return `${formattedMonth} ${String(year).slice(-2)}`;
        }
        return `${formattedMonth} ${year}`;
      }

      return formatTableDate(dateStr, i18n.language);
  }, [isQuarterly, isMonthly, tCommon]);

  const chartXTickFormatter = useCallback((value: string) => {
      return formatPeriod(value, 'short');
  }, [formatPeriod]);

  return (
    <>
    <Card sx={{ height: "100%", display: "flex", flexDirection: "column", position: "relative", overflow: "hidden", p: 0 }}>
        
        <Box sx={{ p: 3, flex: 1, position: "relative", zIndex: 1 }}>
            <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", mb: 1 }}>
                <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                    <Typography variant="h6" fontWeight="bold" color="text.primary">
                        {label}
                    </Typography>
                </Box>
                <Box sx={{ display: "flex", alignItems: "center", gap: 0.5 }}>
                     {description && (
                        <Tooltip title={description} arrow placement="top">
                            <InfoOutlinedIcon 
                                fontSize="small" 
                                sx={{ color: "text.secondary", cursor: "help", width: 18, height: 18 }} 
                            />
                        </Tooltip>
                    )}
                    <IconButton
                        onClick={(e) => {
                        e.stopPropagation();
                        setDrawerOpen(true);
                        }}
                        size="small"
                        aria-label="Open details"
                        sx={{ p: 0.5 }}
                    >
                        <OpenInNewIcon fontSize="small" />
                    </IconButton>
                </Box>
            </Box>

            <Box sx={{ mt: 2 }}>
                {loading ? (
                    <Skeleton width="60%" height={40} />
                ) : (
                    <Box sx={{ display: "flex", alignItems: "baseline", gap: 0.5 }}>
                        {prefix && <Typography variant="h5" color="text.secondary">{prefix}</Typography>}
                        <Typography variant="h3" fontWeight="bold" color="text.primary">
                            {main}
                        </Typography>
                        {suffix && <Typography variant="h5" color="text.secondary">{suffix}</Typography>}
                    </Box>
                )}
                
                {(latest?.referenceDate || issuer) && (
                     <Box sx={{ mt: 1, display: "flex", justifyContent: "space-between", alignItems: "flex-end" }}>
                        {latest?.referenceDate && (
                            <Typography variant="caption" color="text.secondary" sx={{ textTransform: "uppercase" }}>
                                {formatPeriod(latest.referenceDate, 'full')}
                            </Typography>
                        )}
                        {issuer && (
                            <Typography variant="caption" color="text.secondary">
                                {t('nationalIndicators.source', { issuer })}
                            </Typography>
                        )}
                     </Box>
                )}
            </Box>
        </Box>

        {/* Background Chart */}
        {showChart && (
            <Box
              sx={{
                position: "absolute",
                left: 0,
                right: 0,
                bottom: variationOptions.length > 1 ? 40 : 0, // Adjust bottom if segments are present
                height: (theme) => (theme as any).custom?.layout?.metricCard?.chartHeight ?? 100,
                pointerEvents: "none",
                zIndex: 0
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
                      <stop offset="50%" stopColor={growthColor} stopOpacity={0.05} />
                      <stop offset="100%" stopColor={growthColor} stopOpacity={0.15} />
                    </linearGradient>
                  </defs>
                  <YAxis domain={chartDomain} hide />
                  <Area
                    type="monotone"
                    dataKey="value"
                    stroke={`url(#${chartId}-stroke)`}
                    strokeWidth={2}
                    fill={`url(#${chartId}-fill)`}
                    baseValue={chartDomain[0] as number}
                    isAnimationActive={false}
                  />
                </RechartsAreaChart>
              </ResponsiveContainer>
            </Box>
          )}

        {variationOptions.length > 1 && (
            <Box sx={{ position: "relative", zIndex: 2, mt: "auto" }}>
                <SegmentedControl
                    options={variationOptions}
                    value={selectedVariationId}
                    onChange={setSelectedVariationId}
                    size="small"
                    fullWidth
                    sx={{
                        p: 0,
                        border: 0,
                        borderTop: 1,
                        borderColor: "divider",
                        borderRadius: 0,
                        backgroundColor: "background.paper",
                        "& .MuiToggleButtonGroup-grouped": {
                            border: 0,
                            borderRadius: 0,
                            m: 0,
                            py: 1.5,
                            "&:not(:first-of-type)": {
                                borderRadius: 0,
                                borderLeft: 1,
                                borderColor: "divider"
                            },
                             "&:first-of-type": {
                                borderRadius: 0,
                            },
                            "&.Mui-selected": {
                                backgroundColor: alpha(theme.palette.primary.main, 0.12) ,
                                color: "primary.main",
                                boxShadow: "none",
                                mb: "-1px" // Overlap border
                            }
                        }
                    }}
                />
            </Box>
        )}
    </Card>

    <MetricDetailsDrawer
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        title={label}
        metric={{
            label: t(`nationalIndicators.variations.${latestData?.variation}`),
            actualValue: displayValue,
            valueType,
            currency: currencyCode,
            locale: i18n.language
        }}
        disablePercentageScaling={true}
        chartData={chartData}
        historyTablePeriodLabel={
            isQuarterly ? tCommon("calendar.period.quarter") :
            isMonthly ? tCommon("calendar.month") :
            tCommon("date")
        }
        chartXTickFormatter={chartXTickFormatter}
        onFormatHistoryPeriod={(isQuarterly || isMonthly) ? (value) => formatPeriod(value, 'full') : undefined}
        period={variationOptions.length > 1 ? selectedVariationId : undefined}
        onPeriodChange={variationOptions.length > 1 ? setSelectedVariationId : undefined}
        periodOptions={variationOptions.length > 1 ? variationOptions : undefined}
    />
    </>
  );
}
