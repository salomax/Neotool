"use client";

import React from "react";
import {
  ComposedChart as RechartsComposedChart,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
  Line,
  Area,
  ReferenceLine,
  useXAxisScale,
  usePlotArea,
} from "recharts";
import { Box, Typography, useTheme } from "@mui/material";
import { Label } from "@/shared/components/ui/primitives/Label";
import { Amount } from "./Amount";

// --- Projection line rendered as a child of ComposedChart (recharts 3.8+ API) ---

interface ProjectionLineProps {
  xValue: number;
  label?: string;
  stroke?: string;
  labelFill?: string;
  labelBackgroundFill?: string;
}

function ProjectionLine({
  xValue,
  label,
  stroke = "#888",
  labelFill,
  labelBackgroundFill,
}: ProjectionLineProps) {
  const xScale = useXAxisScale(0);
  const plotArea = usePlotArea();
  const textRef = React.useRef<SVGTextElement | null>(null);
  const [textBox, setTextBox] = React.useState<DOMRect | null>(null);

  React.useEffect(() => {
    if (!label) return;
    if (!textRef.current) return;
    const next = textRef.current.getBBox();
    setTextBox(next);
  }, [label]);

  if (!xScale || !plotArea) return null;

  const scaleAny = xScale as any;
  const xBase = xScale(xValue);
  if (xBase == null) return null;
  const bandOffset = typeof scaleAny.bandwidth === "function" ? scaleAny.bandwidth() / 2 : 0;
  const xPixel = xBase + bandOffset;

  const { x: _x, y, width: _w, height } = plotArea;
  const labelY = y + 6;
  const backgroundFill = labelBackgroundFill ?? stroke;
  const foregroundFill = labelFill ?? "#fff";
  const paddingX = 6;
  const paddingY = 3;

  return (
    <g>
      <line
        x1={xPixel} y1={y}
        x2={xPixel} y2={y + height}
        stroke={stroke}
        strokeWidth={1.5}
        strokeDasharray="4 3"
      />
      {label && (
        <g transform={`translate(${xPixel}, ${labelY})`}>
          {textBox && (
            <rect
              x={textBox.x - paddingX}
              y={textBox.y - paddingY}
              width={textBox.width + paddingX * 2}
              height={textBox.height + paddingY * 2}
              fill={backgroundFill}
              rx={4}
              ry={4}
            />
          )}
          <text
            ref={textRef}
            x={0}
            y={0}
            fontSize={11}
            fill={foregroundFill}
            textAnchor="middle"
            dominantBaseline="hanging"
          >
            {label}
          </text>
        </g>
      )}
    </g>
  );
}

const MemoProjectionLine = React.memo(ProjectionLine);

function formatFullShortDate(value: unknown): string | null {
  const date =
    value instanceof Date
      ? value
      : typeof value === "number"
        ? new Date(value)
        : new Date(String(value));

  if (Number.isNaN(date.getTime())) return null;

  const dd = String(date.getDate()).padStart(2, "0");
  const mm = String(date.getMonth() + 1).padStart(2, "0");
  const yyyy = String(date.getFullYear());
  return `${dd}/${mm}/${yyyy}`;
}

// --- Types ---

type AxisFormat = "number" | "currency" | "percent";

export interface LineChartSeries {
  id: string;
  name: string;
  dataKey: string;
  color?: string;
  yAxisId?: string;
  kind?: "line" | "area";
  strokeDasharray?: string;
  areaOpacity?: number;
}

export interface LineChartReferenceLine {
  y?: number;
  x?: string | number;
  stroke?: string;
  strokeDasharray?: string;
  label?: string;
  yAxisId?: string;
}

export interface LineChartEventMarkerItem {
  id: string;
  primary: string;
  secondary?: string | null;
}

export interface LineChartEventMarker {
  id: string;
  xValue: number | string;
  label: string;
  items: readonly LineChartEventMarkerItem[];
  onClick?: () => void;
}

export interface LineChartAxisConfig {
  id: string;
  side: "left" | "right";
  label?: string;
  type?: "number" | "category";
  format?: AxisFormat;
  currency?: string;
  tickFormatter?: (value: number) => string;
  ticksInside?: boolean;
  min?: number;
  max?: number;
  marginPercent?: number;
}

export interface LineChartProps {
  data: Array<Record<string, any>>;
  title?: string;
  width?: number | string;
  height?: number | string;
  xKey?: string;
  xIsDate?: boolean;
  xDateFormat?: (value: any) => string;
  xLabel?: string;
  xContinuous?: boolean;
  xHideTicks?: boolean;
  xTickCount?: number;
  projectionKey?: string;
  projectedStyle?: {
    lineDash?: string;
    areaOpacity?: number;
  };
  /** When set, the segment ending at a row with this key (from the previous point) uses `partialStyle` (e.g. current partial month). */
  partialKey?: string;
  partialStyle?: {
    lineDash?: string;
    areaOpacity?: number;
  };
  projectionMarker?: boolean;
  projectionMarkerSeriesId?: string;
  projectionMarkerLabel?: string;
  axes?: LineChartAxisConfig[];
  series: LineChartSeries[];
  showXAxis?: boolean;
  showYAxis?: boolean;
  showXAxisTitle?: boolean;
  showYAxisTitles?: boolean;
  showGrid?: boolean;
  showDots?: boolean;
  showValueLabels?: boolean;
  showTooltip?: boolean;
  showLegend?: boolean;
  strokeWidth?: number;
  showArea?: boolean;
  scale?: boolean;
  consolidateDecimal?: boolean;
  colors?: string[];
  margin?: {
    top?: number;
    right?: number;
    bottom?: number;
    left?: number;
  };
  referenceLines?: LineChartReferenceLine[];
  eventMarkers?: readonly LineChartEventMarker[];
  sx?: any;
  locale?: string;
  /** Recharts Line/Area interpolation. Default `monotone` for smooth curves. */
  curveType?: React.ComponentProps<typeof Line>["type"];
  isAnimationActive?: boolean;
}

// --- Helpers ---

const buildTickFormatter = (axis: LineChartAxisConfig | undefined, locale: string) => {
  if (!axis) return undefined;
  if (axis.tickFormatter) return axis.tickFormatter;

  if (axis.format === "currency") {
    const formatter = new Intl.NumberFormat(locale, {
      style: "currency",
      currency: axis.currency ?? "BRL",
      maximumFractionDigits: 0,
    });
    return (value: number) => formatter.format(value ?? 0);
  }
  if (axis.format === "percent") {
    const formatter = new Intl.NumberFormat(locale, {
      style: "percent",
      maximumFractionDigits: 1,
    });
    return (value: number) => formatter.format((value ?? 0) / 100);
  }
  if (axis.format === "number") {
    const formatter = new Intl.NumberFormat(locale, { maximumFractionDigits: 2 });
    return (value: number) => formatter.format(value ?? 0);
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

// --- Custom XAxis Tick (aligns first/last labels to edges) ---

interface XAxisEdgeTickProps {
  x?: number;
  y?: number;
  payload?: { value: any };
  index?: number;
  visibleTicksCount?: number;
  formatter?: (value: any) => string;
}

function XAxisEdgeTick({ x = 0, y = 0, payload, index = 0, visibleTicksCount = 0, formatter }: XAxisEdgeTickProps) {
  if (!payload) return null;
  const label = formatter ? formatter(payload.value) : String(payload.value);
  const isFirst = index === 0;
  const isLast = index === visibleTicksCount - 1;
  const textAlign = isFirst ? "left" : isLast ? "right" : "center";
  const width = 96;
  const height = 18;
  const xOffset = isFirst ? 0 : isLast ? -width : -width / 2;
  return (
    <foreignObject
      x={x + xOffset}
      y={y + 2}
      width={width}
      height={height}
      style={{ overflow: "visible", pointerEvents: "none" }}
    >
      <div style={{ width: "100%", height: "100%" }}>
        <Label
          component="div"
          sx={{
            mb: 0,
            lineHeight: 1.2,
            whiteSpace: "nowrap",
            textAlign,
          }}
        >
          {label}
        </Label>
      </div>
    </foreignObject>
  );
}

const MemoXAxisEdgeTick = React.memo(XAxisEdgeTick);

// --- Custom Tooltip ---

interface CustomTooltipProps {
  active?: boolean;
  payload?: readonly any[];
  label?: any;
  theme: any;
  palette: string[];
  series: LineChartSeries[];
  getAxis: (id: string | undefined) => LineChartAxisConfig | undefined;
  locale: string;
  xTickFormatter?: (value: any) => string;
  tooltipFormatter: (value: any, name: any, tooltipProps: any) => [string, string];
}

const CustomTooltip: React.FC<CustomTooltipProps> = ({
  active,
  payload,
  label,
  theme,
  palette,
  series,
  getAxis,
  locale,
  xTickFormatter,
  tooltipFormatter,
}) => {
  const { byId, byDataKey, byName } = React.useMemo(() => {
    const byIdMap = new Map<string, LineChartSeries>();
    const byDataKeyMap = new Map<string, LineChartSeries>();
    const byNameMap = new Map<string, LineChartSeries>();
    for (const s of series) {
      byIdMap.set(s.id, s);
      byDataKeyMap.set(s.dataKey, s);
      byNameMap.set(s.name, s);
    }
    return { byId: byIdMap, byDataKey: byDataKeyMap, byName: byNameMap };
  }, [series]);

  const safePayload = React.useMemo(() => (payload ?? []) as any[], [payload]);

  const visiblePayload = React.useMemo(() => {
    const rawPayload = safePayload;
    /** Higher wins: one tooltip row per logical series (avoid duplicate net/gross from __partial + __proj). */
    const bestBySeriesId = new Map<string, { entry: any; priority: number }>();

    const resolveSeriesId = (dataKey: string): string => {
      if (dataKey.startsWith("__actual_")) return dataKey.slice("__actual_".length);
      if (dataKey.startsWith("__partial_")) return dataKey.slice("__partial_".length);
      if (dataKey.startsWith("__proj_")) return dataKey.slice("__proj_".length);
      const serie = series.find((s) => s.dataKey === dataKey);
      return serie?.id ?? dataKey;
    };

    const prefixPriority = (dataKey: string): number => {
      if (dataKey.startsWith("__actual_")) return 3;
      if (dataKey.startsWith("__partial_")) return 2;
      if (dataKey.startsWith("__proj_")) return 1;
      return 3;
    };

    for (const entry of rawPayload) {
      const key = entry?.dataKey;
      if (typeof key !== "string") continue;
      const v = entry.value;
      if (v == null && typeof v !== "number") continue;

      const seriesId = resolveSeriesId(key);
      const priority = prefixPriority(key);
      const prev = bestBySeriesId.get(seriesId);
      if (!prev || priority > prev.priority) {
        bestBySeriesId.set(seriesId, { entry, priority });
      }
    }

    const order = new Map(series.map((s, i) => [s.id, i]));
    return Array.from(bestBySeriesId.entries())
      .sort((a, b) => (order.get(a[0]) ?? 999) - (order.get(b[0]) ?? 999))
      .map(([, { entry }]) => entry);
  }, [safePayload, series]);

  if (!active || safePayload.length === 0) return null;

  if (visiblePayload.length === 0) return null;

  const formattedLabel = xTickFormatter ? xTickFormatter(label) : label;
  const borderColor = (theme as any).custom?.palette?.inputBorder ?? theme.palette.divider;
  const radius = (theme as any).custom?.radius?.md ?? theme.shape.borderRadius;

  const resolveSerie = (entry: any) => {
    const key = entry?.dataKey;
    if (typeof key === "string") {
      if (key.startsWith("__actual_")) {
        const id = key.slice("__actual_".length);
        return byId.get(id);
      }
      if (key.startsWith("__proj_")) {
        const id = key.slice("__proj_".length);
        return byId.get(id);
      }
      if (key.startsWith("__partial_")) {
        const id = key.slice("__partial_".length);
        return byId.get(id);
      }
      const matchByDataKey = byDataKey.get(key);
      if (matchByDataKey) return matchByDataKey;
    }
    const entryName = entry?.name;
    if (entryName) {
      const matchByName = byName.get(entryName);
      if (matchByName) return matchByName;
    }
    return undefined;
  };

  return (
    <Box
      sx={{
        bgcolor: theme.palette.background.paper,
        border: `1px solid ${borderColor}`,
        borderRadius: `${radius}px`,
        boxShadow: theme.shadows[2],
        p: 1.5,
      }}
    >
      <Label sx={{ mb: 0 }}>{String(formattedLabel)}</Label>
      {visiblePayload.map((entry: any, index: number) => {
        const [formattedValue] = tooltipFormatter(entry.value, entry.name, entry);
        const color = entry.color || palette[index % palette.length];
        const serie = resolveSerie(entry);
        const axisConfig = getAxis(serie?.yAxisId);
        const numeric = typeof entry.value === "number" ? entry.value : entry.value == null ? NaN : Number(entry.value);
        const isCurrency = axisConfig?.format === "currency";
        return (
          <Box key={entry.dataKey || index} sx={{ display: "flex", alignItems: "flex-start", gap: 1, mt: index === 0 ? 0.75 : 0.5 }}>
            <Box sx={{ width: 8, height: 8, borderRadius: "50%", bgcolor: color, mr: 1 }} />
            <Box sx={{ display: "flex", flexDirection: "column", minWidth: 0 }}>
              {entry.name ? (
                <Label component="div" sx={{ mb: 0, whiteSpace: "nowrap" }}>
                  {String(entry.name)}
                </Label>
              ) : null}
              {isCurrency ? (
                <Amount
                  value={Number.isFinite(numeric) ? numeric : null}
                  currency={axisConfig?.currency ?? "BRL"}
                  locale={locale}
                  tone="neutral"
                  variant="body2"
                  sx={{ mt: 0.25 }}
                />
              ) : (
                <Typography variant="body2" component="span" sx={{ whiteSpace: "nowrap", mt: 0.25 }}>
                  {formattedValue}
                </Typography>
              )}
            </Box>
          </Box>
        );
      })}

    </Box>
  );
};

const MemoCustomTooltip = React.memo(CustomTooltip);

interface CustomLegendProps {
  payload?: ReadonlyArray<{ value?: unknown; color?: string }>;
}

const CustomLegend: React.FC<CustomLegendProps> = ({ payload }) => {
  if (!payload || payload.length === 0) return null;

  const items: Array<{ value?: unknown; color?: string }> = [];
  const seen = new Set<string>();

  for (const item of payload) {
    const key = item?.value == null ? "" : String(item.value).trim();
    if (!key) continue;
    if (seen.has(key)) continue;
    seen.add(key);
    items.push(item);
  }

  if (items.length === 0) return null;

  return (
    <Box sx={{ display: "flex", flexWrap: "wrap", gap: 1.5, mt: 1, width: "100%", justifyContent: "center" }}>
      {items.map((item, index) => (
          <Box key={`${String(item.value)}-${index}`} sx={{ display: "flex", alignItems: "center", gap: 0.75 }}>
            <Box
              sx={{
                width: 8,
                height: 8,
                borderRadius: "50%",
                bgcolor: item.color,
                flex: "0 0 auto",
              }}
            />
            <Label component="span" sx={{ mb: 0 }}>
              {String(item.value)}
            </Label>
          </Box>
        ))}
    </Box>
  );
};

const MemoCustomLegend = React.memo(CustomLegend);

interface EventMarkersLayerProps {
  markers: readonly LineChartEventMarker[];
  plotBottomOffset?: number;
  onHoverChange: (next: { marker: LineChartEventMarker; x: number; y: number } | null) => void;
}

function EventMarkersLayer({ markers, plotBottomOffset = 14, onHoverChange }: EventMarkersLayerProps) {
  const theme = useTheme();
  const xScale = useXAxisScale(0);
  const plotArea = usePlotArea();
  const clearHover = React.useCallback(() => onHoverChange(null), [onHoverChange]);

  if (!xScale || !plotArea) return null;
  if (!markers || markers.length === 0) return null;

  const scaleAny = xScale as any;
  const bandOffset = typeof scaleAny.bandwidth === "function" ? scaleAny.bandwidth() / 2 : 0;

  const { x: plotX, y: plotY, width: plotW, height: plotH } = plotArea;
  const yPixel = plotY + plotH - plotBottomOffset;

  const stroke = theme.palette.info.main;
  const fill = theme.palette.background.paper;
  const textFill = theme.palette.info.main;

  return (
    <g>
      {markers.map((m) => {
        const xBase = (xScale as any)(m.xValue as any);
        if (xBase == null) return null;
        const xPixel = xBase + bandOffset;
        if (xPixel < plotX - 2 || xPixel > plotX + plotW + 2) return null;

        return (
          <g
            key={m.id}
            onMouseEnter={(evt) => {
              evt.stopPropagation();
              onHoverChange({ marker: m, x: xPixel, y: yPixel });
            }}
            onMouseMove={(evt) => {
              evt.stopPropagation();
              onHoverChange({ marker: m, x: xPixel, y: yPixel });
            }}
            onMouseLeave={(evt) => {
              evt.stopPropagation();
              clearHover();
            }}
            onClick={(evt) => {
              evt.stopPropagation();
              m.onClick?.();
            }}
            style={{ cursor: m.onClick ? "pointer" : "default" }}
          >
            <circle cx={xPixel} cy={yPixel} r={8} fill={fill} stroke={stroke} strokeWidth={1.5} />
            <text
              x={xPixel}
              y={yPixel + 0.5}
              textAnchor="middle"
              dominantBaseline="middle"
              fontSize={11}
              fontWeight={700}
              fill={textFill}
              pointerEvents="none"
            >
              i
            </text>
          </g>
        );
      })}
    </g>
  );
}

const MemoEventMarkersLayer = React.memo(EventMarkersLayer);

// --- Main Component ---

export const LineChart: React.FC<LineChartProps> = ({
  data,
  title,
  width = "100%",
  height = 300,
  xKey = "name",
  xIsDate = false,
  xContinuous = false,
  xHideTicks = false,
  xTickCount = 6,
  xDateFormat,
  xLabel,
  axes,
  series,
  projectionKey,
  projectedStyle,
  partialKey,
  partialStyle,
  projectionMarker = true,
  projectionMarkerLabel,
  showXAxis = true,
  showYAxis = true,
  showXAxisTitle = false,
  showYAxisTitles = false,
  showGrid = true,
  showDots = true,
  showValueLabels = false,
  showTooltip = true,
  showLegend = true,
  strokeWidth = 2,
  showArea = true,
  scale = false,
  consolidateDecimal = true,
  colors,
  margin = { top: 0, right: 0, left: 0, bottom: 24 },
  referenceLines,
  eventMarkers,
  sx,
  locale: localeProp,
  curveType = "monotone",
  isAnimationActive = true,
}) => {
  const theme = useTheme();
  const [eventMarkerHover, setEventMarkerHover] = React.useState<{
    marker: LineChartEventMarker;
    x: number;
    y: number;
  } | null>(null);

  const locale = React.useMemo(() => {
    if (localeProp) return localeProp;
    if (typeof window !== "undefined" && window.navigator?.language) return window.navigator.language;
    return "pt-BR";
  }, [localeProp]);

  const palette = React.useMemo(
    () => (colors && colors.length > 0 ? colors : defaultPalette(theme)),
    [colors, theme]
  );

  const axisById = React.useMemo(() => {
    const map = new Map<string, LineChartAxisConfig>();
    for (const axis of axes || []) map.set(axis.id, axis);
    return map;
  }, [axes]);

  const defaultAxis = axes && axes.length > 0 ? axes[0] : undefined;
  const defaultAxisId = defaultAxis?.id ?? "default";

  const leftAxes = React.useMemo(() => (axes || []).filter((a) => a.side === "left"), [axes]);
  const rightAxes = React.useMemo(() => (axes || []).filter((a) => a.side === "right"), [axes]);

  const getAxis = React.useCallback(
    (id: string | undefined): LineChartAxisConfig | undefined => {
      if (!id) return defaultAxis;
      return axisById.get(id);
    },
    [axisById, defaultAxis]
  );

  const seriesByDataKey = React.useMemo(() => {
    const map = new Map<string, LineChartSeries>();
    for (const s of series) map.set(s.dataKey, s);
    return map;
  }, [series]);

  const seriesByName = React.useMemo(() => {
    const map = new Map<string, LineChartSeries>();
    for (const s of series) map.set(s.name, s);
    return map;
  }, [series]);

  const needsAxisStats = React.useMemo(() => {
    if (scale || consolidateDecimal) return true;
    return (axes || []).some((a) => a.marginPercent != null || a.min != null || a.max != null);
  }, [axes, consolidateDecimal, scale]);

  const axisStats = React.useMemo(() => {
    const stats = new Map<string, { min: number; max: number; maxAbs: number }>();
    if (!needsAxisStats) return stats;
    if (!data || data.length === 0 || !series || series.length === 0) return stats;

    for (const row of data) {
      for (const serie of series) {
        const axisId = serie.yAxisId ?? defaultAxisId;
        const raw = row[serie.dataKey];
        const numericValue =
          typeof raw === "number" ? raw : raw == null ? NaN : Number(raw);

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
  }, [data, series, defaultAxisId, needsAxisStats]);

  const consolidationByAxis = React.useMemo(() => {
    const map = new Map<string, { divisor: number; suffix: string }>();
    if (!consolidateDecimal) return map;

    axisStats.forEach((stats, axisId) => {
      const axisConfig = axisById.get(axisId) ?? defaultAxis;
      if (!axisConfig || (axisConfig.format !== "currency" && axisConfig.format !== "number")) return;

      const abs = stats.maxAbs;
      if (!abs || abs < 1000) {
        map.set(axisId, { divisor: 1, suffix: "" });
        return;
      }

      const divisor =
        abs >= 1_000_000_000_000 ? 1_000_000_000_000 :
        abs >= 1_000_000_000 ? 1_000_000_000 :
        abs >= 1_000_000 ? 1_000_000 : 1_000;
      const suffix =
        divisor === 1_000_000_000_000 ? "T" :
        divisor === 1_000_000_000 ? "B" :
        divisor === 1_000_000 ? "M" : "K";

      map.set(axisId, { divisor, suffix });
    });
    return map;
  }, [axisById, axisStats, consolidateDecimal, defaultAxis]);

  const getAxisDomain = React.useCallback((axisId: string | undefined) => {
    const axisConfig = getAxis(axisId);
    const key = axisId ?? defaultAxisId;
    const stats = axisStats.get(key);
    const hasExplicitBounds = axisConfig?.min != null || axisConfig?.max != null;

    if (!hasExplicitBounds && !axisConfig?.marginPercent && !scale) return undefined;

    const baseMin = axisConfig?.min != null ? axisConfig.min : stats?.min;
    const baseMax = axisConfig?.max != null ? axisConfig.max : stats?.max;
    if (baseMin == null || baseMax == null) return undefined;

    const min = Number(baseMin);
    const max = Number(baseMax);
    if (!Number.isFinite(min) || !Number.isFinite(max)) return undefined;

    if (hasExplicitBounds && axisConfig?.marginPercent == null) return [min, max] as [number, number];

    const percent = axisConfig?.marginPercent ?? (hasExplicitBounds ? 0 : 0.1);

    if (min === max) {
      const padding = (stats?.maxAbs || Math.abs(max) || 1) * percent;
      return [min - padding, max + padding] as [number, number];
    }

    const padding = (max - min) * percent;
    return [min - padding, max + padding] as [number, number];
  }, [axisStats, defaultAxisId, getAxis, scale]);

  const getTickFormatter = React.useCallback((axisId: string | undefined) => {
    const axisConfig = getAxis(axisId);
    const baseFormatter = buildTickFormatter(axisConfig, locale);
    if (!consolidateDecimal) return baseFormatter;

    const key = axisId ?? defaultAxisId;
    const consolidation = consolidationByAxis.get(key);
    if (!consolidation || consolidation.divisor === 1) return baseFormatter;

    const currencyFormatter =
      axisConfig?.format === "currency"
        ? new Intl.NumberFormat(locale, {
            style: "currency",
            currency: axisConfig.currency ?? "BRL",
            minimumFractionDigits: 1,
            maximumFractionDigits: 1,
          })
        : null;

    const numberFormatter =
      axisConfig?.format === "number"
        ? new Intl.NumberFormat(locale, {
            minimumFractionDigits: 1,
            maximumFractionDigits: 1,
          })
        : null;

    return (value: number) => {
      const scaled = value / consolidation.divisor;
      if (axisConfig?.format === "currency") {
        const formatted = (currencyFormatter ?? new Intl.NumberFormat(locale)).format(scaled ?? 0);
        return consolidation.suffix ? `${formatted}\u00A0${consolidation.suffix}` : formatted;
      }
      if (axisConfig?.format === "number") {
        const formatted = (numberFormatter ?? new Intl.NumberFormat(locale)).format(scaled ?? 0);
        return consolidation.suffix ? `${formatted}\u00A0${consolidation.suffix}` : formatted;
      }
      const formatted = baseFormatter ? baseFormatter(scaled) : String(scaled ?? 0);
      return consolidation.suffix ? `${formatted}\u00A0${consolidation.suffix}` : formatted;
    };
  }, [consolidateDecimal, consolidationByAxis, defaultAxisId, getAxis, locale]);

  const tooltipFormatter = React.useCallback(
    (value: any, name: any, tooltipProps: any): [string, string] => {
      const serie =
        (typeof tooltipProps?.dataKey === "string" ? seriesByDataKey.get(tooltipProps.dataKey) : undefined) ??
        (name ? seriesByName.get(String(name)) : undefined);
      const axisConfig = getAxis(serie?.yAxisId);
      const numeric = typeof value === "number" ? value : value == null ? NaN : Number(value);

      if (!Number.isFinite(numeric)) return ["", ""];

      if (axisConfig?.format === "currency") {
        return [
          new Intl.NumberFormat(locale, {
            style: "currency",
            currency: axisConfig.currency ?? "BRL",
            minimumFractionDigits: 2,
            maximumFractionDigits: 2,
          }).format(numeric),
          "",
        ];
      }

      const formatter = getTickFormatter(serie?.yAxisId);
      return [formatter ? formatter(numeric) : String(value), ""];
    },
    [getAxis, getTickFormatter, locale, seriesByDataKey, seriesByName]
  );

  const xTickFormatter = React.useMemo(() => {
    if (xDateFormat) return xDateFormat;
    if (!xIsDate) return undefined;
    const dateFormatter = new Intl.DateTimeFormat(locale, { year: "2-digit", month: "2-digit" });
    return (value: any) => {
      const date = value instanceof Date ? value : new Date(value);
      if (Number.isNaN(date.getTime())) return String(value);
      return dateFormatter.format(date);
    };
  }, [locale, xDateFormat, xIsDate]);

  const INTERNAL_X_KEY = "__x_ts";

  const chartData = React.useMemo(() => {
    if (!xContinuous || !xIsDate) return data;
    return (data || []).map((row) => {
      const raw = row[xKey as string];
      const ts =
        raw instanceof Date ? raw.getTime() :
        typeof raw === "number" ? raw :
        new Date(String(raw)).getTime();
      return { ...row, [INTERNAL_X_KEY]: Number.isFinite(ts) ? ts : null };
    });
  }, [data, xContinuous, xIsDate, xKey]);

  const xDataKey = xContinuous && xIsDate ? INTERNAL_X_KEY : xKey;

  const xTicks = React.useMemo(() => {
    if (!xContinuous || !xIsDate) return undefined;
    let min = Infinity;
    let max = -Infinity;
    for (const row of chartData || []) {
      const v = (row as any)[INTERNAL_X_KEY];
      if (typeof v !== "number" || !Number.isFinite(v)) continue;
      if (v < min) min = v;
      if (v > max) max = v;
    }
    if (!Number.isFinite(min) || !Number.isFinite(max)) return undefined;
    if (!Number.isFinite(min) || !Number.isFinite(max) || min === max) return [min];
    const count = Math.max(2, Math.floor(xTickCount));
    const step = (max - min) / (count - 1);
    return Array.from({ length: count }, (_, i) => Math.round(min + i * step));
  }, [chartData, xContinuous, xIsDate, xTickCount]);

  const transformedData = React.useMemo(() => {
    const splitSeries = Boolean(projectionKey) || Boolean(partialKey);
    if (!splitSeries) return chartData;
    const arr = chartData || [];
    return arr.map((row, index) => {
      const isProj = projectionKey ? Boolean((row as any)[projectionKey]) : false;
      const isPartialTail = partialKey ? Boolean((row as any)[partialKey]) : false;
      const nextRow = index + 1 < arr.length ? (arr[index + 1] as any) : null;
      const isLastActual =
        projectionKey &&
        !isProj &&
        nextRow != null &&
        Boolean(nextRow[projectionKey]);
      const isPartialBridge =
        partialKey &&
        !isProj &&
        !isPartialTail &&
        nextRow != null &&
        !(projectionKey && Boolean(nextRow[projectionKey])) &&
        Boolean(nextRow[partialKey]);
      const next = { ...row };
      for (const s of series) {
        const v = (row as any)[s.dataKey];
        (next as any)[`__actual_${s.id}`] = isProj ? null : isPartialTail ? null : v;
        if (projectionKey) {
          // Include the last actual point in the projected series so the continuous
          // and dashed lines share the same origin — no visual gap at the boundary.
          (next as any)[`__proj_${s.id}`] = isProj || isLastActual ? v : null;
        }
        if (partialKey) {
          (next as any)[`__partial_${s.id}`] = isPartialTail || isPartialBridge ? v : null;
        }
      }
      return next;
    });
  }, [chartData, partialKey, projectionKey, series]);

  const projectionSplit = React.useMemo(() => {
    if (!projectionKey || !projectionMarker) return null;

    const firstProjectedIndex = (chartData || []).findIndex((row) =>
      Boolean((row as any)[projectionKey])
    );
    if (firstProjectedIndex <= 0) return null;

    const lastActualRow = (chartData || [])[firstProjectedIndex - 1] as any;
    const xValue = lastActualRow?.[xDataKey as any];
    if (xValue == null) return null;

    const label =
      projectionMarkerLabel ??
      formatFullShortDate(xValue) ??
      (xTickFormatter ? String(xTickFormatter(xValue)) : String(xValue));

    return { x: xValue as number, label };
  }, [chartData, projectionKey, projectionMarker, projectionMarkerLabel, xDataKey, xTickFormatter]);

  const axisPadding = { top: 16, bottom: 12 };
  const projectionStroke = theme.palette.text.disabled;
  const projectionLabelFill = theme.palette.getContrastText(projectionStroke);
  const borderColor = (theme as any).custom?.palette?.inputBorder ?? theme.palette.divider;
  const radius = (theme as any).custom?.radius?.md ?? theme.shape.borderRadius;

  return (
    <Box sx={{ p: 2, ...sx }}>
      {title && (
        <Typography variant="h6" component="h3" gutterBottom>
          {title}
        </Typography>
      )}
      <Box sx={{ width, height, display: "flex", flexDirection: "column" }} data-testid="line-chart-responsive-container">
        <Box
          sx={{
            minHeight: 0,
            flex: 1,
            height: xLabel && showXAxisTitle ? "calc(100% - 20px)" : "100%",
            position: "relative",
          }}
        >
          {eventMarkerHover ? (
            <Box
              sx={{
                position: "absolute",
                left: eventMarkerHover.x,
                top: eventMarkerHover.y,
                transform: "translate(-50%, calc(-100% - 10px))",
                zIndex: 10,
                pointerEvents: "none",
              }}
            >
              <Box
                sx={{
                  bgcolor: theme.palette.background.paper,
                  border: `1px solid ${borderColor}`,
                  borderRadius: `${radius}px`,
                  boxShadow: theme.shadows[2],
                  p: 1.5,
                  maxWidth: 320,
                }}
              >
                <Label sx={{ mb: 0 }}>{eventMarkerHover.marker.label}</Label>
                {eventMarkerHover.marker.items.map((it) => (
                  <Box key={it.id} sx={{ mt: 0.75, minWidth: 0 }}>
                    <Typography variant="body2" sx={{ fontWeight: 600, lineHeight: 1.2 }}>
                      {it.primary}
                    </Typography>
                    {it.secondary ? (
                      <Typography variant="caption" color="text.secondary" sx={{ display: "block", lineHeight: 1.2 }}>
                        {it.secondary}
                      </Typography>
                    ) : null}
                  </Box>
                ))}
              </Box>
            </Box>
          ) : null}
          <ResponsiveContainer width="100%" height="100%">
            <RechartsComposedChart
              data={transformedData}
              margin={{
                top: margin?.top ?? 5,
                right:
                  margin?.right ??
                  (xHideTicks
                    ? 0
                    : (axes || []).some((a) => a.side === "right" && a.ticksInside) && !showYAxisTitles
                    ? 0
                    : 40),
                left:
                  margin?.left ??
                  (xHideTicks
                    ? 0
                    : (axes || []).some((a) => a.side === "left" && a.ticksInside) && !showYAxisTitles
                    ? 0
                    : 40),
                bottom: margin?.bottom ?? 20,
              }}
            >
            {(series?.some((s) => s.kind === "area") || showArea) && (
              <defs>
                {series.map((serie, index) => {
                  const color = serie.color || palette[index % palette.length];
                  return (
                    <linearGradient key={serie.id} id={`linechart-gradient-${serie.id}`} x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor={color} stopOpacity={0.16} />
                      <stop offset="95%" stopColor={color} stopOpacity={0} />
                    </linearGradient>
                  );
                })}
              </defs>
            )}

            {showGrid && (
              <CartesianGrid
                xAxisId={0 as any}
                yAxisId={((axes?.[0]?.id ?? 0) as any)}
                strokeDasharray="4 4"
                stroke={theme.palette.divider}
                strokeOpacity={0.6}
                horizontal
                syncWithTicks
                vertical={false}
              />
            )}

            {showXAxis && (
              <XAxis
                xAxisId={0}
                dataKey={xDataKey}
                type={xContinuous && xIsDate ? "number" : undefined}
                domain={xContinuous && xIsDate ? (["dataMin", "dataMax"] as any) : undefined}
                scale={xContinuous && xIsDate ? "time" : undefined}
                ticks={xContinuous && xIsDate ? (xTicks as any) : undefined}
                tickFormatter={xHideTicks ? undefined : xTickFormatter}
                tick={xHideTicks ? false : <MemoXAxisEdgeTick formatter={xTickFormatter} />}
                axisLine={{ stroke: theme.palette.divider }}
                tickLine={xHideTicks ? { stroke: "transparent" } : { stroke: theme.palette.divider }}
                interval={xContinuous && xIsDate ? 0 : "preserveStartEnd"}
                padding={{ left: 0, right: 0 }}
                minTickGap={xHideTicks ? 0 : 12}
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
                      mirror={axis.ticksInside === true}
                      tickMargin={axis.ticksInside ? 6 : undefined}
                      axisLine={{ stroke: theme.palette.divider }}
                      tickLine={{ stroke: theme.palette.divider }}
                      label={
                        axis.label && showYAxisTitles
                          ? { value: axis.label, angle: -90, position: "insideLeft", fill: theme.palette.text.secondary }
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
                      mirror={axis.ticksInside === true}
                      tickMargin={axis.ticksInside ? 6 : undefined}
                      axisLine={{ stroke: theme.palette.divider }}
                      tickLine={{ stroke: theme.palette.divider }}
                      label={
                        axis.label && showYAxisTitles
                          ? { value: axis.label, angle: -90, position: "insideRight", fill: theme.palette.text.secondary }
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
                  mirror={false}
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
                content={
                  <MemoCustomTooltip
                    theme={theme}
                    palette={palette}
                    series={series}
                    getAxis={getAxis}
                    locale={locale}
                    xTickFormatter={xTickFormatter}
                    tooltipFormatter={tooltipFormatter}
                  />
                }
              />
            )}

            {showLegend && <Legend content={<MemoCustomLegend />} />}

            {series.map((serie, index) => {
              const color = serie.color || palette[index % palette.length];
              const yAxisKey = getAxis(serie.yAxisId)?.id;
              const splitSeriesKeys = Boolean(projectionKey) || Boolean(partialKey);
              const actualKey = splitSeriesKeys ? `__actual_${serie.id}` : serie.dataKey;
              const projKey = projectionKey ? `__proj_${serie.id}` : undefined;
              const partialDataKey = partialKey ? `__partial_${serie.id}` : undefined;

              return (
                <React.Fragment key={serie.id}>
                  {serie.kind === "area" ? (
                    <Area
                      type={curveType}
                      dataKey={actualKey}
                      name={serie.name}
                      stroke={color}
                      strokeWidth={strokeWidth}
                      fill={`url(#linechart-gradient-${serie.id})`}
                      fillOpacity={serie.areaOpacity ?? 0.2}
                      yAxisId={yAxisKey}
                      connectNulls
                      isAnimationActive={isAnimationActive}
                    />
                  ) : (
                    <Line
                      type={curveType}
                      dataKey={actualKey}
                      name={serie.name}
                      stroke={color}
                      strokeWidth={strokeWidth}
                      yAxisId={yAxisKey}
                      dot={showDots}
                      connectNulls
                      label={showValueLabels}
                      strokeDasharray={serie.strokeDasharray}
                      isAnimationActive={isAnimationActive}
                    />
                  )}
                  {partialDataKey && (
                    serie.kind === "area" ? (
                      <Area
                        type={curveType}
                        dataKey={partialDataKey}
                        name={serie.name}
                        stroke={color}
                        strokeWidth={strokeWidth}
                        strokeDasharray={partialStyle?.lineDash || "6 5"}
                        fill={`url(#linechart-gradient-${serie.id})`}
                        fillOpacity={
                          partialStyle?.areaOpacity !== undefined
                            ? partialStyle.areaOpacity
                            : (serie.areaOpacity ?? 0.2)
                        }
                        yAxisId={yAxisKey}
                        connectNulls
                        legendType="none"
                        isAnimationActive={isAnimationActive}
                      />
                    ) : (
                      <Line
                        type={curveType}
                        dataKey={partialDataKey}
                        name={serie.name}
                        stroke={color}
                        strokeWidth={strokeWidth}
                        yAxisId={yAxisKey}
                        dot={showDots}
                        connectNulls
                        label={showValueLabels}
                        strokeDasharray={partialStyle?.lineDash || "6 5"}
                        legendType="none"
                        isAnimationActive={isAnimationActive}
                      />
                    )
                  )}
                  {projKey && (
                    serie.kind === "area" ? (
                      <Area
                        type={curveType}
                        dataKey={projKey}
                        name={serie.name}
                        stroke={color}
                        strokeWidth={strokeWidth}
                        strokeDasharray={projectedStyle?.lineDash || "5 4"}
                        fill={`url(#linechart-gradient-${serie.id})`}
                        fillOpacity={projectedStyle?.areaOpacity ?? Math.max(0.06, (serie.areaOpacity ?? 0.2) * 0.6)}
                        yAxisId={yAxisKey}
                        connectNulls
                        legendType="none"
                        isAnimationActive={isAnimationActive}
                      />
                    ) : (
                      <Line
                        type={curveType}
                        dataKey={projKey}
                        name={serie.name}
                        stroke={color}
                        strokeWidth={strokeWidth}
                        yAxisId={yAxisKey}
                        dot={showDots}
                        connectNulls
                        label={showValueLabels}
                        strokeDasharray={projectedStyle?.lineDash || "5 4"}
                        legendType="none"
                        isAnimationActive={isAnimationActive}
                      />
                    )
                  )}
                </React.Fragment>
              );
            })}

            {projectionSplit && (
              <MemoProjectionLine
                xValue={projectionSplit.x}
                label={projectionSplit.label}
                stroke={projectionStroke}
                labelFill={projectionLabelFill}
                labelBackgroundFill={projectionStroke}
              />
            )}
            {eventMarkers && eventMarkers.length > 0 ? (
              <MemoEventMarkersLayer markers={eventMarkers} onHoverChange={setEventMarkerHover} />
            ) : null}
            </RechartsComposedChart>
          </ResponsiveContainer>
        </Box>

        {xLabel && showXAxisTitle ? (
          <Box sx={{ pt: 0.5, display: "flex", justifyContent: "center" }}>
            <Label sx={{ mb: 0 }}>{xLabel}</Label>
          </Box>
        ) : null}
      </Box>
    </Box>
  );
};

export default LineChart;
