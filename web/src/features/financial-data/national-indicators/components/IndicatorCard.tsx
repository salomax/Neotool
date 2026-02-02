import React, { useState, useMemo } from "react";
import { Card } from "@/shared/components/ui/primitives";
import { Box, Typography, Skeleton, Tooltip } from "@mui/material";
import InfoOutlinedIcon from "@mui/icons-material/InfoOutlined";
import { SegmentedControl } from "@/shared/components/ui/inputs";
import { EconomicIndicator } from "../hooks/useEconomicIndicators";
import { IndicatorLatest } from "../hooks/useIndicatorsLatest";
import { useTranslation } from "@/shared/i18n";
import { financialDataTranslations } from "@/shared/i18n/domains/financial-data";
import { formatMetricValue, MetricValueType } from "@/shared/utils/formatMetricValue";

interface IndicatorCardProps {
  indicator: EconomicIndicator;
  latestValues: Record<string, IndicatorLatest>;
  loading?: boolean;
}

export const IndicatorCard: React.FC<IndicatorCardProps> = ({ indicator, latestValues, loading = false }) => {
  const { t } = useTranslation(financialDataTranslations);
  
  const defaultVariation = indicator.variations.find(v => v.isDefault) || indicator.variations[0];
  const [selectedVariationId, setSelectedVariationId] = useState<string>(defaultVariation?.id || "");

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

  // Derive value type
  let valueType: MetricValueType = "number";
  if (indicator.unit === "PERCENT" || indicator.unit === "%") valueType = "percentage";
  else if (indicator.unit === "BRL" || indicator.unit === "BRL_MILLION") valueType = "currency";

  // Format value
  const { prefix, main, suffix } = formatMetricValue(latest?.value, valueType, {
      currency: "BRL", 
      locale: "pt-BR",
      decimals: valueType === "percentage" ? 2 : undefined
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
        label: t(`nationalIndicators.variations.${v.label}`, { defaultValue: v.label })
      }));
  }, [indicator.variations, t]);

  return (
    <Card sx={{ height: "100%", p: 3, display: "flex", flexDirection: "column", justifyContent: "space-between" }}>
        <Box>
            <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", mb: 1 }}>
                <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                    <Typography variant="h6" fontWeight="bold" color="text.primary">
                        {t(`nationalIndicators.indicators.${indicator.label}`, { defaultValue: indicator.label })}
                    </Typography>
                    {indicator.description && (
                        <Tooltip title={indicator.description} arrow placement="top">
                            <InfoOutlinedIcon 
                                fontSize="small" 
                                sx={{ color: "text.secondary", cursor: "help", width: 18, height: 18 }} 
                            />
                        </Tooltip>
                    )}
                </Box>
                 {variationOptions.length > 1 && (
                    <SegmentedControl
                        options={variationOptions}
                        value={selectedVariationId}
                        onChange={setSelectedVariationId}
                        size="small"
                    />
                )}
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
                
                {latest?.referenceDate && (
                     <Typography variant="caption" color="text.secondary" sx={{ mt: 1, display: "block" }}>
                        {new Date(latest.referenceDate).toLocaleDateString("pt-BR", { month: 'long', year: 'numeric', timeZone: 'UTC' })}
                     </Typography>
                )}
            </Box>
        </Box>
    </Card>
  );
}
