 "use client";
 
 import React from "react";
import { Box, Typography, useTheme } from "@mui/material";
 import Card from "@/shared/components/ui/primitives/Card";
 import { Skeleton } from "@/shared/components/ui/primitives/Skeleton";
 import { BarChart } from "@/shared/components/ui/data-display/BarChart";
 import { useTranslation, i18n } from "@/shared/i18n";
 import { investmentsTranslations } from "@/shared/i18n/domains/investments";
 import type { FixedIncomeComparisonQuery } from "../api/queries.generated";
 
 export interface FixedIncomePerformanceChartProps {
   result?: FixedIncomeComparisonQuery["fixedIncomeComparison"] | null;
   loading?: boolean;
 }
 
 export function FixedIncomePerformanceChart({
   result,
   loading,
 }: FixedIncomePerformanceChartProps) {
   const { t } = useTranslation(investmentsTranslations);
   const ns = "fixedIncomeComparison" as const;
   const locale = i18n?.language || "pt-BR";
  const theme = useTheme();
 
   if (loading) {
     return (
       <Card variant="outlined" sx={{ p: 3, mb: 3 }}>
         <Skeleton variant="rectangular" height={300} sx={{ borderRadius: 2 }} />
       </Card>
     );
   }
 
   if (!result?.performanceSeries || result.performanceSeries.length === 0) {
     return null;
   }
 
   const taxable = result.performanceSeries.find(
     (s) => s.productGroup === "TAXABLE_IR"
   );
   const exempt = result.performanceSeries.find(
     (s) => s.productGroup === "EXEMPT_IR"
   );
 
   if (!taxable && !exempt) {
     return null;
   }
 
   const seriesMap = new Map<string, { date: string; taxableNet?: number; exemptNet?: number }>();
 
   taxable?.points.forEach((p) => {
     const d = String(p.date);
     const existing = seriesMap.get(d) || { date: d };
     existing.taxableNet = p.netValue;
     seriesMap.set(d, existing);
   });
 
   exempt?.points.forEach((p) => {
     const d = String(p.date);
     const existing = seriesMap.get(d) || { date: d };
     existing.exemptNet = p.netValue;
     seriesMap.set(d, existing);
   });
 
   const data = Array.from(seriesMap.values()).sort(
     (a, b) => new Date(a.date).getTime() - new Date(b.date).getTime()
   );
 
  const taxableLabel = t(`${ns}.taxableProductLabel`);
   const exemptLabel = t(`${ns}.exemptProductLabel`);
 
  return (
    <Card variant="outlined" sx={{ p: 3, mb: 3 }}>
      <BarChart
        title={t(`${ns}.performanceChartTitle`)}
        data={data}
        height={300}
        xKey="date"
        xIsDate
        axes={[
          { id: "currency-left", side: "left", format: "currency", currency: "BRL" },
        ]}
        series={[
          { id: "taxable", name: taxableLabel, dataKey: "taxableNet", yAxisId: "currency-left", color: theme.palette.primary.light },
          { id: "exempt", name: exemptLabel, dataKey: "exemptNet", yAxisId: "currency-left", color: theme.palette.success.light },
        ]}
        showLegend={true}
        showGrid={true}
        barSize={34}
        barCategoryGap="0%"
        barGap={2}
        locale={locale}
        scale
        scaleMinPadding={0.05}
        scaleMaxPadding={0.1}
        fadeOlderBars
        gradientOpacityStart={0.7}
        gradientOpacityEnd={1}
        sx={{ p: 0 }}
      />
    </Card>
  );
 }
 
 export default FixedIncomePerformanceChart;
