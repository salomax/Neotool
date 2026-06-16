 "use client";
 
 import React from "react";
import { Box, Typography, useTheme } from "@mui/material";
import Card from "@/shared/components/ui/primitives/Card";
 import { Skeleton } from "@/shared/components/ui/primitives/Skeleton";
import { LineChart } from "@/shared/components/ui/data-display/LineChart";
 import { useTranslation, i18n } from "@/shared/i18n";
 import { investmentsTranslations } from "@/shared/i18n/domains/investments";
 import type { FixedIncomeComparisonQuery } from "../api/queries.generated";
import { FixedIncomeProductGroup } from "@/lib/graphql/types/__generated__/graphql";
import { FormSectionLabel } from "./FormSectionLabel";
 
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
      <Card variant="outlined" hoverable={false} sx={{ p: 3, mb: 3, boxShadow: "none" }}>
         <Skeleton variant="rectangular" height={300} sx={{ borderRadius: 2 }} />
       </Card>
     );
   }
 
   if (!result?.performanceSeries || result.performanceSeries.length === 0) {
     return null;
   }
 
   const taxable = result.performanceSeries.find(
     (s) => s.productGroup === FixedIncomeProductGroup.TaxableIr
   );
   const committed = result.performanceSeries.find(
     (s) => s.productGroup === FixedIncomeProductGroup.TaxableIrNoIof
   );
   const exempt = result.performanceSeries.find(
     (s) => s.productGroup === FixedIncomeProductGroup.ExemptIr
   );

   if (!taxable && !committed && !exempt) {
     return null;
   }

   const seriesMap = new Map<
     string,
     { date: string; taxableNet?: number; committedNet?: number; exemptNet?: number }
   >();
 
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

   committed?.points.forEach((p) => {
     const d = String(p.date);
     const existing = seriesMap.get(d) || { date: d };
     existing.committedNet = p.netValue;
     seriesMap.set(d, existing);
   });
 
   const data = Array.from(seriesMap.values()).sort(
     (a, b) => new Date(a.date).getTime() - new Date(b.date).getTime()
   );
 
  const taxableLabel = t(`${ns}.taxableProductLabel`);
  const committedLabel = t(`${ns}.taxableCommittedProductLabel`);
   const exemptLabel = t(`${ns}.exemptProductLabel`);

  const series = [
    taxable
      ? {
          id: "taxable",
          name: taxableLabel,
          dataKey: "taxableNet",
          yAxisId: "currency-left",
          color: theme.palette.primary.light,
        }
      : null,
    committed
      ? {
          id: "committed",
          name: committedLabel,
          dataKey: "committedNet",
          yAxisId: "currency-left",
          color: theme.palette.warning.main,
        }
      : null,
    exempt
      ? {
          id: "exempt",
          name: exemptLabel,
          dataKey: "exemptNet",
          yAxisId: "currency-left",
          color: theme.palette.success.light,
        }
      : null,
  ].filter(Boolean);
 
  return (
    <>
      <Box sx={{ px: 2, pt: 3 }}>
        <FormSectionLabel sx={{ fontSize: 14 }}>{t(`${ns}.performanceChartTitle`)}</FormSectionLabel>
      </Box>
      <Card
        variant="outlined"
        hoverable={false}
        sx={{
          p: 3,
          mb: 3,
          boxShadow: "none",
          "& .MuiTypography-root": { fontSize: 14 },
        }}
      >
        <LineChart
          data={data}
          height={300}
          xKey="date"
          xIsDate
          axes={[
            { id: "currency-left", side: "left", format: "currency", currency: "BRL" },
          ]}
          series={series as Array<{
            id: string;
            name: string;
            dataKey: string;
            yAxisId: string;
            color: string;
          }>}
          showLegend={true}
          showGrid={true}
          showHorizontalGridLines={true}
          showVerticalGridLines={true}
          yAxisLabelsInside={true}
          showDots={false}
          strokeWidth={3}
          xDateFormat={(value) => {
            const date = value instanceof Date ? value : new Date(value);
            if (Number.isNaN(date.getTime())) {
              return String(value);
            }
            const monthRaw = new Intl.DateTimeFormat(locale, {
              month: "short",
            })
              .format(date)
              .replace(".", "")
              .trim();
            const month = monthRaw
              ? `${monthRaw.charAt(0).toUpperCase()}${monthRaw.slice(1).toLowerCase()}`.slice(
                  0,
                  3
                )
              : "";
            const year = new Intl.DateTimeFormat(locale, {
              year: "2-digit",
            }).format(date);
            return `${month}/${year}`;
          }}
          locale={locale}
          scale
          scaleMinPadding={0.05}
          scaleMaxPadding={0.1}
          sx={{ p: 0 }}
        />
      </Card>
    </>
  );
 }
 
 export default FixedIncomePerformanceChart;
