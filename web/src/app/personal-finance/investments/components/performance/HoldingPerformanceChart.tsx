'use client';

import * as React from 'react';
import { Box, FormControlLabel, Stack, Switch, Typography, useTheme } from '@mui/material';
import { alpha } from '@mui/material/styles';
import TimelineIcon from '@mui/icons-material/Timeline';
import { HoldingPerformanceResolution } from "@/lib/graphql/types/__generated__/graphql";
import { LineChart } from '@/shared/components/ui/data-display';
import { EmptyState } from '@/shared/components/ui/feedback';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from "../../../i18n";
import { FINANCE_TABLE_BODY_FONT_SIZE } from "@/shared/components/ui/data-display/Table";
import { InvestmentSectionLabel } from "../InvestmentSectionLabel";

const CHART_PROJECTED_STYLE = { lineDash: "6 4", areaOpacity: 0.1 } as const;
const CHART_PARTIAL_STYLE = { lineDash: "6 5" } as const;

/** Gross series stroke: primary with this alpha so it reads behind the net series. */
const GROSS_SERIES_ALPHA = 0.62;

/** Prefix for the partial-month caption (bullet + space). */
const PARTIAL_MONTH_NOTICE_PREFIX = "\u2022 ";

export interface HoldingPerformanceChartPoint {
  timestamp: string;
  isProjected: boolean;
  /** Last non-projected point is in the current month (incomplete month in the series). */
  isPartialMonth?: boolean;
  investedPrincipal: number | null;
  grossValue: number | null;
  netValue: number | null;
}

export interface HoldingPerformanceChartEvent {
  id: string;
  timestamp: string;
  title: string;
  subtitle?: string | null;
}

interface HoldingPerformanceChartProps {
  points: HoldingPerformanceChartPoint[];
  currencyCode: string;
  locale: string;
  resolution?: HoldingPerformanceResolution | null;
  /** When false, projection UI is hidden and only historical points apply. */
  supportsProjection?: boolean;
  isTaxExempt?: boolean;
  title?: string;
  headerAction?: React.ReactNode;
  emptyTitle: string;
  emptyDescription: string;
  grossLabel: string;
  netLabel: string;
  projectionToggleLabel: string;
  events?: readonly HoldingPerformanceChartEvent[];
  onEventDayClick?: (dayKey: string) => void;
}

function capitalizeFirstLetter(text: string, locale: string): string {
  if (!text) return text;
  const head = text.charAt(0).toLocaleUpperCase(locale);
  return head + text.slice(1);
}

/** Reuses Intl formatters for axis ticks (can run often during chart layout). */
function useAxisDateFormatter(
  locale: string,
  resolution: HoldingPerformanceResolution | null | undefined,
): (value: unknown) => string {
  return React.useMemo(() => {
    const isDay = resolution === HoldingPerformanceResolution.Day;
    const dayFormatter = new Intl.DateTimeFormat(locale, {
      day: "2-digit",
      month: "short",
    });
    const monthYearFormatter = new Intl.DateTimeFormat(locale, {
      month: "long",
      year: "2-digit",
    });

    return (value: unknown): string => {
      const parsed =
        value instanceof Date ? value : new Date(value as string | number);
      if (Number.isNaN(parsed.getTime())) {
        return String(value);
      }
      const raw = isDay
        ? dayFormatter.format(parsed).replace(/\.$/, "")
        : monthYearFormatter.format(parsed).replace(/\.$/, "");
      return capitalizeFirstLetter(raw, locale);
    };
  }, [locale, resolution]);
}

function useThroughDateFormatter(locale: string): Intl.DateTimeFormat {
  return React.useMemo(
    () =>
      new Intl.DateTimeFormat(locale, {
        day: "2-digit",
        month: "2-digit",
        year: "2-digit",
      }),
    [locale],
  );
}

function findLastPartialActualPoint(
  data: readonly HoldingPerformanceChartPoint[],
): HoldingPerformanceChartPoint | undefined {
  for (let i = data.length - 1; i >= 0; i--) {
    const p = data[i];
    if (p?.isPartialMonth && !p.isProjected) {
      return p;
    }
  }
  return undefined;
}

/** Parse API timestamp to a local calendar date (YYYY-MM-DD avoids UTC day shifts). */
function parseChartPointCalendarDate(raw: string): Date | null {
  const s = String(raw).trim();
  const cal = /^(\d{4})-(\d{2})-(\d{2})/.exec(s);
  if (cal) {
    const y = Number(cal[1]);
    const mo = Number(cal[2]);
    const d = Number(cal[3]);
    const dt = new Date(y, mo - 1, d);
    return Number.isNaN(dt.getTime()) ? null : dt;
  }
  const ms = Date.parse(s);
  if (Number.isNaN(ms)) return null;
  const dt = new Date(ms);
  return Number.isNaN(dt.getTime()) ? null : dt;
}

function startOfLocalCalendarDay(d: Date): number {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
}

function calendarDayKey(raw: string): string | null {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(String(raw).trim());
  return m ? `${m[1]}-${m[2]}-${m[3]}` : null;
}

function endOfLocalMonth(d: Date): Date {
  return new Date(d.getFullYear(), d.getMonth() + 1, 0);
}

/** Stable chart X for a calendar day (noon UTC of local Y/M/D). */
function chartTimestampForLocalCalendarDate(d: Date): string {
  return new Date(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate(), 12, 0, 0)).toISOString();
}

function interpolateNullable(a: number | null, b: number | null, ratio: number): number | null {
  if (a == null && b == null) return null;
  if (a == null) return b;
  if (b == null) return a;
  return a + ratio * (b - a);
}

/**
 * When the last actual day is mid-month and the first projected point is later, optionally insert
 * a projected point at **local** month-end **between** them with values linearly interpolated in time.
 * Skips when the backend already provides month-end (or any point on/before month-end) as the first
 * projected anchor — avoids duplicate month-end and flat “copy today” artifacts.
 *
 * Exported for unit tests.
 */
export function withProjectionMonthBridge(
  filtered: HoldingPerformanceChartPoint[],
  firstProjectedIdx: number,
): HoldingPerformanceChartPoint[] {
  const lastActual = filtered[firstProjectedIdx - 1];
  const firstProjected = filtered[firstProjectedIdx];
  if (!lastActual || !firstProjected) return filtered;

  const lastActualDay = parseChartPointCalendarDate(lastActual.timestamp);
  const firstProjectedDay = parseChartPointCalendarDate(firstProjected.timestamp);
  if (!lastActualDay || !firstProjectedDay) return filtered;

  const monthEnd = endOfLocalMonth(lastActualDay);
  const tLast = startOfLocalCalendarDay(lastActualDay);
  const tFirst = startOfLocalCalendarDay(firstProjectedDay);
  const tBridge = startOfLocalCalendarDay(monthEnd);

  if (!(tLast < tBridge && tBridge < tFirst)) {
    return filtered;
  }

  const span = tFirst - tLast;
  if (span <= 0) return filtered;

  const ratio = (tBridge - tLast) / span;

  const bridge: HoldingPerformanceChartPoint = {
    ...lastActual,
    timestamp: chartTimestampForLocalCalendarDate(monthEnd),
    isProjected: true,
    isPartialMonth: false,
    grossValue: interpolateNullable(lastActual.grossValue, firstProjected.grossValue, ratio),
    netValue: interpolateNullable(lastActual.netValue, firstProjected.netValue, ratio),
    investedPrincipal: interpolateNullable(
      lastActual.investedPrincipal,
      firstProjected.investedPrincipal,
      ratio,
    ),
  };

  return [...filtered.slice(0, firstProjectedIdx), bridge, ...filtered.slice(firstProjectedIdx)];
}

export function HoldingPerformanceChart({
  points,
  currencyCode,
  locale,
  resolution,
  supportsProjection = false,
  isTaxExempt = false,
  title,
  headerAction,
  emptyTitle,
  emptyDescription,
  grossLabel,
  netLabel,
  projectionToggleLabel,
  events,
  onEventDayClick,
}: HoldingPerformanceChartProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();
  const formatAxisDate = useAxisDateFormatter(locale, resolution);
  const throughDateFormatter = useThroughDateFormatter(locale);

  const hasProjectedPoints = React.useMemo(() => points.some((point) => point.isProjected), [points]);
  const [includeProjection, setIncludeProjection] = React.useState(false);
  const showProjectionToggle = supportsProjection && hasProjectedPoints;

  React.useEffect(() => {
    if ((!supportsProjection || !hasProjectedPoints) && includeProjection) {
      setIncludeProjection(false);
    }
  }, [supportsProjection, hasProjectedPoints, includeProjection]);

  const chartData = React.useMemo(() => {
    const filtered = points.filter((point) => includeProjection || !point.isProjected);
    if (!includeProjection) return filtered;

    // Inject a bridge point at end-of-current-month (isProjected: true) so the
    // projected segment has a natural month-boundary point between "today" and
    // the first end-of-next-month projection, avoiding a large visual gap.
    const firstProjectedIdx = filtered.findIndex((p) => p.isProjected);
    if (firstProjectedIdx <= 0) return filtered;

    return withProjectionMonthBridge(filtered, firstProjectedIdx);
  }, [includeProjection, points]);

  const partialMonthNotice = React.useMemo(() => {
    if (!findLastPartialActualPoint(chartData)) return null;
    const throughDate = throughDateFormatter.format(new Date());
    return `${PARTIAL_MONTH_NOTICE_PREFIX}${t("investments.detailDrawer.chart.partialMonthChip", { throughDate })}`;
  }, [chartData, t, throughDateFormatter]);

  const series = React.useMemo(() => {
    const lastVisiblePoint = (() => {
      for (let i = chartData.length - 1; i >= 0; i--) {
        const p = chartData[i];
        if (p && !p.isProjected) return p;
      }
      return chartData[chartData.length - 1] ?? null;
    })();

    const netValueSeries = {
      id: "netValue",
      name: netLabel,
      dataKey: "netValue",
      yAxisId: "value",
      color: theme.palette.success.main,
      kind: "area" as const,
      areaOpacity: 1,
    };

    if (isTaxExempt) return [netValueSeries];

    const hasGross = chartData.some((p) => typeof p.grossValue === "number" && Number.isFinite(p.grossValue));
    const hasNet = chartData.some((p) => typeof p.netValue === "number" && Number.isFinite(p.netValue));
    let netEqualsGross = false;
    if (hasGross && hasNet) {
      const EPS = 0.005;
      let anyComparablePair = false;
      for (const p of chartData) {
        if (typeof p.grossValue !== "number" || typeof p.netValue !== "number") continue;
        if (!Number.isFinite(p.grossValue) || !Number.isFinite(p.netValue)) continue;
        anyComparablePair = true;
        if (Math.abs(p.grossValue - p.netValue) > EPS) {
          anyComparablePair = false;
          break;
        }
      }
      netEqualsGross = anyComparablePair;
    }

    const shouldShowNet = hasNet && !netEqualsGross;
    const shouldShowGross = hasGross || !shouldShowNet;
    if (!shouldShowNet && hasNet && !hasGross) return [netValueSeries];

    const valueForGain = (() => {
      if (!lastVisiblePoint) return null;
      const value =
        shouldShowNet && typeof lastVisiblePoint.netValue === "number"
          ? lastVisiblePoint.netValue
          : typeof lastVisiblePoint.grossValue === "number"
            ? lastVisiblePoint.grossValue
            : null;
      return value != null && Number.isFinite(value) ? value : null;
    })();

    const principalForGain =
      lastVisiblePoint && typeof lastVisiblePoint.investedPrincipal === "number" && Number.isFinite(lastVisiblePoint.investedPrincipal)
        ? lastVisiblePoint.investedPrincipal
        : null;

    const percentageGain =
      principalForGain != null && principalForGain > 0 && valueForGain != null ? (valueForGain - principalForGain) / principalForGain : null;

    const toneColor =
      percentageGain != null && Math.abs(percentageGain) > 0.0005
        ? percentageGain > 0
          ? theme.palette.success.main
          : theme.palette.error.main
        : null;

    const effectiveNetColor = toneColor ?? theme.palette.success.main;
    const effectiveGrossBase = toneColor ?? theme.palette.primary.main;
    const grossColor = shouldShowNet ? alpha(effectiveGrossBase, GROSS_SERIES_ALPHA) : effectiveGrossBase;
    const netSeries = { ...netValueSeries, color: effectiveNetColor };
    const grossValueSeries = {
      id: "grossValue",
      name: grossLabel,
      dataKey: "grossValue",
      yAxisId: "value",
      color: grossColor,
      kind: "area" as const,
      areaOpacity: shouldShowNet ? 0.2 : 0.25,
    };

    if (shouldShowGross && !shouldShowNet) return [grossValueSeries];
    if (!shouldShowGross && shouldShowNet) return [netSeries];
    return [grossValueSeries, netSeries];
  }, [
    chartData,
    grossLabel,
    isTaxExempt,
    netLabel,
    theme.palette.error.main,
    theme.palette.primary.main,
    theme.palette.success.main,
  ]);

  const eventMarkers = React.useMemo(() => {
    if (!events || events.length === 0) return [];
    const labelFormatter = new Intl.DateTimeFormat(locale, { day: "2-digit", month: "short", year: "numeric" });
    const byDay = new Map<string, HoldingPerformanceChartEvent[]>();
    for (const ev of events) {
      const key = calendarDayKey(ev.timestamp);
      if (!key) continue;
      const arr = byDay.get(key);
      if (arr) arr.push(ev);
      else byDay.set(key, [ev]);
    }
    const markers = Array.from(byDay.entries()).map(([dayKey, evs]) => {
      const day = parseChartPointCalendarDate(dayKey);
      if (!day) return null;
      const xTs = Date.UTC(day.getFullYear(), day.getMonth(), day.getDate(), 12, 0, 0);
      return {
        id: dayKey,
        xValue: xTs,
        label: capitalizeFirstLetter(labelFormatter.format(day).replace(/\.$/, ""), locale),
        items: evs.map((e) => ({ id: e.id, primary: e.title, secondary: e.subtitle })),
        onClick: onEventDayClick ? () => onEventDayClick(dayKey) : undefined,
      };
    });
    return markers.filter((m): m is NonNullable<typeof m> => Boolean(m));
  }, [events, locale, onEventDayClick]);

  if (chartData.length === 0) {
    return (
      <EmptyState title={emptyTitle} description={emptyDescription} />
    );
  }

  const showHeaderBlock = Boolean(title || partialMonthNotice);

  return (
    <Box>
      <Stack spacing={2}>
        <Box sx={{ display: 'flex', flexDirection: { xs: 'column', md: 'row' }, alignItems: { xs: 'flex-start', md: 'center' }, justifyContent: 'space-between', gap: 2 }}>
          {showHeaderBlock ? (
            <Box
              sx={{
                display: 'flex',
                flexWrap: 'wrap',
                alignItems: 'center',
                columnGap: 1,
                rowGap: 0.75,
                minWidth: 0,
              }}
            >
              {title ? (
                <InvestmentSectionLabel
                  title={title}
                  icon={<TimelineIcon sx={{ fontSize: 16 }} />}
                />
              ) : null}
              {partialMonthNotice ? (
                <Typography
                  variant="body2"
                  component="span"
                  sx={{
                    fontSize: FINANCE_TABLE_BODY_FONT_SIZE,
                    fontWeight: 400,
                    color: "text.disabled",
                    maxWidth: "100%",
                  }}
                >
                  {partialMonthNotice}
                </Typography>
              ) : null}
            </Box>
          ) : (
            <span />
          )}
          <Stack direction={{ xs: "column", md: "row" }} spacing={1.5} alignItems={{ xs: "stretch", md: "center" }}>
            {headerAction}
            {showProjectionToggle ? (
              <FormControlLabel
                sx={{ m: 0 }}
                label={projectionToggleLabel}
                labelPlacement="start"
                control={
                  <Switch
                    size="small"
                    checked={includeProjection}
                    onChange={(_, checked) => setIncludeProjection(checked)}
                  />
                }
              />
            ) : null}
          </Stack>
        </Box>

        <LineChart
          data={chartData}
          xKey="timestamp"
          xIsDate
          xContinuous
          curveType="linear"
          isAnimationActive={false}
          xDateFormat={formatAxisDate}
          projectionKey="isProjected"
          projectedStyle={CHART_PROJECTED_STYLE}
          partialKey="isPartialMonth"
          partialStyle={CHART_PARTIAL_STYLE}
          showDots={false}
          showLegend={series.length > 1}
          height={320}
          locale={locale}
          eventMarkers={eventMarkers}
          axes={[
            {
              id: "value",
              side: "left",
              format: "currency",
              currency: currencyCode,
              ticksInside: true,
              marginPercent: 0.1,
            },
          ]}
          series={series}
          sx={{ px: 0 }}
        />
      </Stack>
    </Box>
  );
}

export default HoldingPerformanceChart;
