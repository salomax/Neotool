 "use client";
 
 import React from "react";
import {
  BarChart as RechartsBarChart,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  Bar,
  ReferenceLine,
  Cell,
} from "recharts";
 import { Box, Typography, useTheme } from "@mui/material";
 import { alpha as colorAlpha } from "@mui/material/styles";
 
 type AxisType = "number" | "category";
 
 type AxisFormat = "number" | "currency" | "percent";
 
 export interface BarChartSeries {
   id: string;
   name: string;
   dataKey: string;
   color?: string;
   yAxisId?: string;
   stackId?: string;
 }
 
 export interface BarChartReferenceLine {
   y?: number;
   x?: string | number;
   stroke?: string;
   strokeDasharray?: string;
   label?: string;
   yAxisId?: string;
 }
 
 export interface BarChartAxisConfig {
   id: string;
   side: "left" | "right";
   label?: string;
   type?: AxisType;
   format?: AxisFormat;
   currency?: string;
   tickFormatter?: (value: number) => string;
 }
 
 export interface BarChartProps {
   data: Array<Record<string, any>>;
   title?: string;
   width?: number | string;
   height?: number | string;
   xKey?: string;
   xIsDate?: boolean;
   xDateFormat?: (value: any) => string;
   xLabel?: string;
   axes?: BarChartAxisConfig[];
   series: BarChartSeries[];
   showXAxis?: boolean;
   showYAxis?: boolean;
   showXAxisTitle?: boolean;
   showYAxisTitles?: boolean;
   showGrid?: boolean;
   showTooltip?: boolean;
   showLegend?: boolean;
   barSize?: number;
   barCategoryGap?: number | string;
   barGap?: number | string;
   consolidateDecimal?: boolean;
   colors?: string[];
   margin?: {
     top?: number;
     right?: number;
     bottom?: number;
     left?: number;
   };
   referenceLines?: BarChartReferenceLine[];
   sx?: any;
   locale?: string;
   scale?: boolean;
   scaleMinPadding?: number;
   scaleMaxPadding?: number;
   fadeOlderBars?: boolean;
   gradientOpacityStart?: number;
   gradientOpacityEnd?: number;
 }
 
 const buildTickFormatter = (
   axis: BarChartAxisConfig | undefined,
   locale: string
 ) => {
   if (!axis) return undefined;
   if (axis.tickFormatter) return axis.tickFormatter;
   if (axis.format === "currency") {
     return (value: number) =>
       new Intl.NumberFormat(locale, {
         style: "currency",
         currency: axis.currency ?? "BRL",
         maximumFractionDigits: 0,
       }).format(value ?? 0);
   }
   if (axis.format === "percent") {
     return (value: number) =>
       new Intl.NumberFormat(locale, {
         style: "percent",
         maximumFractionDigits: 1,
       }).format((value ?? 0) / 100);
   }
   if (axis.format === "number") {
     return (value: number) =>
       new Intl.NumberFormat(locale, {
         maximumFractionDigits: 2,
       }).format(value ?? 0);
   }
   return undefined;
 };
 
 const defaultPalette = (theme: any): string[] => [
   theme.palette.primary.main,
   theme.palette.secondary.main,
   theme.palette.success.main,
   theme.palette.info.main,
   theme.palette.warning.main,
   theme.palette.error.main,
 ];
 
 interface CustomTooltipProps {
   active?: boolean;
   payload?: readonly any[];
   label?: any;
   theme: any;
   palette: string[];
   xTickFormatter?: (value: any) => string;
   tooltipFormatter: (value: any, name: any, tooltipProps: any) => [string, string];
 }
 
 const CustomTooltip: React.FC<CustomTooltipProps> = ({
   active,
   payload,
   label,
   theme,
   palette,
   xTickFormatter,
   tooltipFormatter,
 }) => {
   if (!active || !payload || payload.length === 0) return null;
   const formattedLabel = xTickFormatter ? xTickFormatter(label) : label;
   return (
     <Box
       sx={{
         bgcolor: theme.palette.background.paper,
         border: `1px solid ${theme.palette.divider}`,
         p: 1.5,
       }}
     >
       <Typography variant="caption" color="text.secondary">
         {formattedLabel}
       </Typography>
       {payload.map((entry: any, index: number) => {
         const [formattedValue] = tooltipFormatter(
           entry.value,
           entry.name,
           entry
         );
         const color = entry.color || palette[index % palette.length];
         return (
           <Box
             key={entry.dataKey || index}
             sx={{
               display: "flex",
               alignItems: "center",
               mt: index === 0 ? 0.5 : 0.25,
             }}
           >
             <Box
               sx={{
                 width: 8,
                 height: 8,
                 borderRadius: "50%",
                 bgcolor: color,
                 mr: 1,
               }}
             />
             <Typography variant="body2">
               {entry.name && (
                 <Box component="span" sx={{ mr: 0.5 }}>
                   {entry.name}
                 </Box>
               )}
               {formattedValue}
             </Typography>
           </Box>
         );
       })}
     </Box>
   );
 };
 
 export const BarChart: React.FC<BarChartProps> = ({
   data,
   title,
   width = "100%",
   height = 300,
   xKey = "name",
   xIsDate = false,
   xDateFormat,
   xLabel,
   axes,
   series,
   showXAxis = true,
   showYAxis = true,
   showXAxisTitle = false,
   showYAxisTitles = false,
   showGrid = true,
   showTooltip = true,
   showLegend = true,
   barSize = 16,
   barCategoryGap = "8%",
   barGap = "8%",
   consolidateDecimal = true,
   colors,
   margin = { top: 0, right: 24, left: 0, bottom: 24 },
   referenceLines,
   sx,
   locale: localeProp,
   scale = true,
   scaleMinPadding = 0.05,
   scaleMaxPadding = 0.1,
   fadeOlderBars = false,
   gradientOpacityStart = 0.2,
   gradientOpacityEnd = 1,
 }) => {
   const theme = useTheme();
   const locale =
     localeProp ||
     (typeof window !== "undefined" && window.navigator?.language
       ? window.navigator.language
       : "pt-BR");
  const palette: string[] = (colors && colors.length > 0 ? colors : defaultPalette(theme)) as string[];
 
   const axisPadding = { top: 16, bottom: 4 };
   const leftAxes = (axes || []).filter((axis) => axis.side === "left");
   const rightAxes = (axes || []).filter((axis) => axis.side === "right");
 
   const getAxis = (id: string | undefined): BarChartAxisConfig | undefined => {
     if (!id) {
       return axes && axes.length > 0 ? axes[0] : undefined;
     }
     return axes?.find((axis) => axis.id === id);
   };
 
   const axisStats = React.useMemo(() => {
     const stats = new Map<string, { min: number; max: number; maxAbs: number }>();
     if (!data || data.length === 0 || !series || series.length === 0) return stats;
     for (const row of data) {
       for (const serie of series) {
         const axisId = serie.yAxisId || "default";
         const raw = row[serie.dataKey];
         const numericValue =
           typeof raw === "number"
             ? raw
             : raw === null || raw === undefined
             ? NaN
             : Number(raw);
         if (!Number.isFinite(numericValue)) continue;
         const abs = Math.abs(numericValue);
         const existing = stats.get(axisId);
         if (!existing) {
           stats.set(axisId, { min: numericValue, max: numericValue, maxAbs: abs });
         } else {
           if (numericValue < existing.min) existing.min = numericValue;
           if (numericValue > existing.max) existing.max = numericValue;
           if (abs > existing.maxAbs) existing.maxAbs = abs;
         }
       }
     }
     return stats;
   }, [data, series]);
 
   const consolidationByAxis = React.useMemo(() => {
     const map = new Map<string, { divisor: number; suffix: string }>();
     if (!consolidateDecimal) return map;
     axisStats.forEach((stats, axisId) => {
       const axisConfig =
         axes && axes.length > 0
           ? axes.find((axis) => axis.id === axisId) || axes[0]
           : undefined;
       if (!axisConfig) return;
       if (axisConfig.format !== "currency" && axisConfig.format !== "number") return;
       const abs = stats.maxAbs;
       if (!abs || abs < 1000) {
         map.set(axisId, { divisor: 1, suffix: "" });
         return;
       }
       let divisor = 1;
       let suffix = "";
       if (abs >= 1_000_000_000_000) {
         divisor = 1_000_000_000_000;
         suffix = "T";
       } else if (abs >= 1_000_000_000) {
         divisor = 1_000_000_000;
         suffix = "B";
       } else if (abs >= 1_000_000) {
         divisor = 1_000_000;
         suffix = "M";
       } else if (abs >= 1_000) {
         divisor = 1_000;
         suffix = "K";
       }
       map.set(axisId, { divisor, suffix });
     });
     return map;
   }, [axisStats, axes, consolidateDecimal]);
 
   const getAxisDomain = (axisId: string | undefined) => {
     if (!scale) return undefined;
     const key = axisId || "default";
     const stats = axisStats.get(key);
     if (!stats) return undefined;
     const { min, max, maxAbs } = stats;
     if (!Number.isNaN(min) && !Number.isNaN(max)) {
       if (min === max) {
         const baseRange = maxAbs || Math.abs(max) || 1;
         const padding = baseRange * (scaleMinPadding + scaleMaxPadding);
         return [min - padding, max + padding] as [number, number];
       }
       const range = max - min;
       const lower = min - range * scaleMinPadding;
       const upper = max + range * scaleMaxPadding;
       return [lower, upper] as [number, number];
     }
     return undefined;
   };
 
   const getTickFormatter = (axisId: string | undefined) => {
     const axisConfig = getAxis(axisId);
     const baseFormatter = buildTickFormatter(axisConfig, locale);
     if (!consolidateDecimal) return baseFormatter;
     const key = axisConfig?.id || "default";
     const consolidation = consolidationByAxis.get(key);
     if (!consolidation || consolidation.divisor === 1) return baseFormatter;
     return (value: number) => {
       const scaled = value / consolidation.divisor;
       const axisFormat = axisConfig?.format;
       if (axisFormat === "currency" && axisConfig) {
         const formatter = new Intl.NumberFormat(locale, {
           style: "currency",
           currency: axisConfig.currency ?? "BRL",
           minimumFractionDigits: 1,
           maximumFractionDigits: 1,
         });
         const formatted = formatter.format(scaled ?? 0);
         return consolidation.suffix ? `${formatted}\u00A0${consolidation.suffix}` : formatted;
       }
       if (axisFormat === "number") {
         const formatter = new Intl.NumberFormat(locale, {
           minimumFractionDigits: 1,
           maximumFractionDigits: 1,
         });
         const formatted = formatter.format(scaled ?? 0);
         return consolidation.suffix ? `${formatted}\u00A0${consolidation.suffix}` : formatted;
       }
       const formatted = baseFormatter ? baseFormatter(scaled) : String(scaled ?? 0);
       return consolidation.suffix ? `${formatted}\u00A0${consolidation.suffix}` : formatted;
     };
   };
 
   const tooltipFormatter = (value: any, name: any, tooltipProps: any): [string, string] => {
     const serie = series.find(
       (s) => s.dataKey === tooltipProps?.dataKey || s.name === name
     );
     const axisConfig = getAxis(serie?.yAxisId);
     const numeric =
       typeof value === "number"
         ? value
         : value === null || value === undefined
         ? NaN
         : Number(value);
     if (!Number.isFinite(numeric)) return ["", ""];
     if (axisConfig?.format === "currency") {
       const formatter = new Intl.NumberFormat(locale, {
         style: "currency",
         currency: axisConfig.currency ?? "BRL",
         minimumFractionDigits: 2,
         maximumFractionDigits: 2,
       });
       return [formatter.format(numeric), ""];
     }
     const formatter = getTickFormatter(serie?.yAxisId);
     if (!formatter) return [value, ""];
     return [formatter(numeric), ""];
   };
 
   const xTickFormatter =
     xDateFormat ||
     (xIsDate
       ? (value: any) => {
           const date = value instanceof Date ? value : new Date(value);
           if (Number.isNaN(date.getTime())) return value;
           return new Intl.DateTimeFormat("pt-BR", {
             year: "2-digit",
             month: "2-digit",
           }).format(date);
         }
       : undefined);
 
  return (
    <Box sx={{ p: 2, ...sx }}>
      {(title || showLegend) && (
        <Box
          sx={{
            display: "flex",
            alignItems: "flex-start",
            justifyContent: "space-between",
            mb: 2,
          }}
        >
          {title && (
            <Typography variant="h6" component="h3">
              {title}
            </Typography>
          )}
          {showLegend && (
            <Box
              sx={{
                display: "flex",
                flexDirection: "column",
                alignItems: "flex-end",
                gap: 0.5,
                ml: 2,
              }}
            >
              {series.map((serie, index) => {
                let color: string;
                if (typeof serie.color === "string" && serie.color) {
                  color = serie.color;
                } else {
                  const fallbackColor = theme.palette.primary.main as string;
                  const idx = palette.length > 0 ? index % palette.length : 0;
                  color =
                    palette.length > 0 ? (palette[idx] as string) : fallbackColor;
                }
                return (
                  <Box
                    key={serie.id}
                    sx={{ display: "flex", alignItems: "center", gap: 0.75 }}
                  >
                    <Box
                      sx={{
                        width: 10,
                        height: 10,
                        borderRadius: "50%",
                        bgcolor: color,
                      }}
                    />
                    <Typography variant="caption" sx={{ fontSize: 11, lineHeight: "16px" }}>
                      {serie.name}
                    </Typography>
                  </Box>
                );
              })}
            </Box>
          )}
        </Box>
      )}
       <Box sx={{ width, height }} data-testid="bar-chart-responsive-container">
         <ResponsiveContainer width="100%" height="100%">
          <RechartsBarChart
            data={data}
            barCategoryGap={barCategoryGap as any}
            barGap={barGap as any}
            margin={{
              top: margin?.top ?? 24,
              right: margin?.right ?? 24,
              left: margin?.left ?? 24,
              bottom: margin?.bottom ?? 20,
            }}
          >
             {showGrid && (
               <CartesianGrid
                 strokeDasharray="3 3"
                 stroke={theme.palette.divider}
                 vertical={false}
               />
             )}
             {showXAxis && (
               <XAxis
                 dataKey={xKey}
                 tickFormatter={xTickFormatter}
                 tick={{ fontSize: 11, fill: theme.palette.text.secondary }}
                 axisLine={{ stroke: theme.palette.divider }}
                 tickLine={{ stroke: theme.palette.divider }}
                 label={
                   xLabel && showXAxisTitle
                     ? {
                         value: xLabel,
                         position: "insideBottom",
                         fill: theme.palette.text.secondary,
                       }
                     : undefined
                 }
               />
             )}
             {showYAxis &&
               (axes && axes.length > 0 ? (
                 <>
                   {leftAxes.map((axis) => (
                     <YAxis
                       key={axis.id}
                       yAxisId={axis.id}
                       orientation="left"
                       padding={axisPadding}
                       axisLine={{ stroke: theme.palette.divider }}
                       tickLine={{ stroke: theme.palette.divider }}
                       label={
                         axis.label && showYAxisTitles
                           ? {
                               value: axis.label,
                               angle: -90,
                               position: "insideLeft",
                               fill: theme.palette.text.secondary,
                             }
                           : undefined
                       }
                       tick={{ fontSize: 11, fill: theme.palette.text.secondary }}
                       tickFormatter={getTickFormatter(axis.id)}
                       domain={getAxisDomain(axis.id)}
                     />
                   ))}
                   {rightAxes.map((axis) => (
                     <YAxis
                       key={axis.id}
                       yAxisId={axis.id}
                       orientation="right"
                       padding={axisPadding}
                       axisLine={{ stroke: theme.palette.divider }}
                       tickLine={{ stroke: theme.palette.divider }}
                       label={
                         axis.label && showYAxisTitles
                           ? {
                               value: axis.label,
                               angle: -90,
                               position: "insideRight",
                               fill: theme.palette.text.secondary,
                             }
                           : undefined
                       }
                       tick={{ fontSize: 11, fill: theme.palette.text.secondary }}
                       tickFormatter={getTickFormatter(axis.id)}
                       domain={getAxisDomain(axis.id)}
                     />
                   ))}
                 </>
               ) : (
                 <YAxis
                   padding={axisPadding}
                   axisLine={{ stroke: theme.palette.divider }}
                   tickLine={{ stroke: theme.palette.divider }}
                   tick={{ fontSize: 11, fill: theme.palette.text.secondary }}
                   domain={getAxisDomain(undefined)}
                 />
               ))}
             {referenceLines?.map((ref, index) => (
               <ReferenceLine
                 key={index}
                 y={ref.y}
                 x={ref.x}
                 stroke={ref.stroke || theme.palette.divider}
                 strokeDasharray={ref.strokeDasharray}
                 yAxisId={ref.yAxisId}
                 label={ref.label}
               />
             ))}
             {showTooltip && (
               <Tooltip
                 content={(props) => (
                   <CustomTooltip
                     {...props}
                     theme={theme}
                     palette={palette}
                     xTickFormatter={xTickFormatter}
                     tooltipFormatter={tooltipFormatter}
                   />
                 )}
               />
             )}
            {series.map((serie, index) => {
                let color: string;
                if (typeof serie.color === "string" && serie.color) {
                  color = serie.color;
                } else {
                  const fallbackColor = theme.palette.primary.main as string;
                  const idx = palette.length > 0 ? index % palette.length : 0;
                  color = palette.length > 0 ? (palette[idx] as string) : fallbackColor;
                }
                const yAxisKey = getAxis(serie.yAxisId)?.id;
               const total = data?.length ?? 0;
               const startA = Math.max(0, Math.min(1, gradientOpacityStart));
               const endA = Math.max(0, Math.min(1, gradientOpacityEnd));
               return (
                  <Bar
                    key={serie.id}
                    dataKey={serie.dataKey}
                    name={serie.name}
                    yAxisId={yAxisKey as any}
                    fill={color}
                    barSize={barSize}
                    stackId={serie.stackId}
                  >
                    {fadeOlderBars &&
                      total > 0 &&
                      data.map((_, i) => {
                        const t = total > 1 ? i / (total - 1) : 1;
                        const a = startA + t * (endA - startA);
                        return (
                          <Cell key={`${serie.id}-${i}`} fill={colorAlpha(color as string, a)} />
                        );
                      })}
                  </Bar>
               );
             })}
           </RechartsBarChart>
        </ResponsiveContainer>
      </Box>
     </Box>
   );
 };
 
 export default BarChart;
