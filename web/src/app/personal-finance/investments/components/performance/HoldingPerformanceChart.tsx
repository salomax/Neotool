'use client';

import * as React from 'react';
import { Box, FormControlLabel, Stack, Switch, Typography, useTheme } from '@mui/material';
import { alpha } from '@mui/material/styles';
import TimelineIcon from '@mui/icons-material/Timeline';
import { HoldingPerformanceResolution } from "@/lib/graphql/types/__generated__/graphql";
import { LineChart } from '@/shared/components/ui/data-display';
import { EmptyState } from '@/shared/components/ui/feedback';
import { Label } from '@/shared/components/ui/primitives/Label';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from "../../../i18n";
import { FINANCE_TABLE_BODY_FONT_SIZE } from "@/shared/components/ui/data-display/Table";

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
    const netColor = theme.palette.success.main;
    const netValueSeries = {
      id: "netValue",
      name: netLabel,
      dataKey: "netValue",
      yAxisId: "value",
      color: netColor,
      kind: "area" as const,
      areaOpacity: 1,
    };

    if (isTaxExempt) return [netValueSeries];

    const grossColor = alpha(theme.palette.primary.main, GROSS_SERIES_ALPHA);
    return [
      {
        id: "grossValue",
        name: grossLabel,
        dataKey: "grossValue",
        yAxisId: "value",
        color: grossColor,
        kind: "area" as const,
        areaOpacity: 0.2,
      },
      netValueSeries,
    ];
  }, [grossLabel, isTaxExempt, netLabel, theme.palette.primary.main, theme.palette.success.main]);

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
                <>
                  <TimelineIcon sx={{ fontSize: 16, color: 'primary.main' }} />
                  <Label sx={{ fontSize: '0.8rem', fontWeight: 500, mb: 0 }}>{title}</Label>
                </>
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
          showLegend
          height={320}
          locale={locale}
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
