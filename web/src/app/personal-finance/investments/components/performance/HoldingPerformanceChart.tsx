'use client';

import * as React from 'react';
import { Box, FormControlLabel, Stack, Switch } from '@mui/material';
import TimelineIcon from '@mui/icons-material/Timeline';
import { HoldingPerformanceResolution } from "@/lib/graphql/types/__generated__/graphql";
import { LineChart } from '@/shared/components/ui/data-display';
import { EmptyState } from '@/shared/components/ui/feedback';
import { Label } from '@/shared/components/ui/primitives/Label';

export interface HoldingPerformanceChartPoint {
  timestamp: string;
  isProjected: boolean;
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
  principalLabel: string;
  grossLabel: string;
  netLabel: string;
  projectionToggleLabel: string;
}

function formatTimestamp(
  value: unknown,
  locale: string,
  resolution: HoldingPerformanceResolution | null | undefined,
) {
  const parsed = new Date(value instanceof Date ? value : (value as any));
  if (Number.isNaN(parsed.getTime())) return String(value);
  if (resolution === HoldingPerformanceResolution.Day) {
    return new Intl.DateTimeFormat(locale, {
      day: "2-digit",
      month: "short",
    })
      .format(parsed)
      .replace(/\.$/, "");
  }
  const month = new Intl.DateTimeFormat(locale, { month: "short" })
    .format(parsed)
    .replace(/\.$/, "");
  const year = new Intl.DateTimeFormat(locale, { year: "2-digit" }).format(parsed);
  return `${month} ${year}`;
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

    const lastActual = filtered[firstProjectedIdx - 1];
    if (!lastActual) return filtered;
    const lastActualDate = new Date(lastActual.timestamp);
    const endOfMonth = new Date(lastActualDate.getFullYear(), lastActualDate.getMonth() + 1, 0);

    if (lastActualDate.getDate() === endOfMonth.getDate()) return filtered;

    const bridge: HoldingPerformanceChartPoint = {
      ...lastActual,
      timestamp: endOfMonth.toISOString(),
      isProjected: true,
    };

    return [
      ...filtered.slice(0, firstProjectedIdx),
      bridge,
      ...filtered.slice(firstProjectedIdx),
    ];
  }, [includeProjection, points]);

  const series = React.useMemo(() => {
    const netValueSeries = {
      id: "netValue",
      name: netLabel,
      dataKey: "netValue",
      yAxisId: "value",
      color: "#059669",
      kind: "area" as const,
      areaOpacity: 1,
    };

    if (isTaxExempt) return [netValueSeries];

    return [
      {
        id: "grossValue",
        name: grossLabel,
        dataKey: "grossValue",
        yAxisId: "value",
        color: "#1d4ed8",
        kind: "area" as const,
        areaOpacity: 0.2,
      },
      netValueSeries,
    ];
  }, [grossLabel, isTaxExempt, netLabel]);

  if (chartData.length === 0) {
    return (
      <EmptyState title={emptyTitle} description={emptyDescription} />
    );
  }

  return (
    <Box>
      <Stack spacing={2}>
        <Box sx={{ display: 'flex', flexDirection: { xs: 'column', md: 'row' }, alignItems: { xs: 'flex-start', md: 'center' }, justifyContent: 'space-between', gap: 2 }}>
          {title ? (
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <TimelineIcon sx={{ fontSize: 16, color: 'primary.main' }} />
              <Label sx={{ fontSize: '0.8rem', fontWeight: 500, mb: 0 }}>{title}</Label>
            </Box>
          ) : <span />}
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
          xDateFormat={(value) => formatTimestamp(value, locale, resolution)}
          projectionKey="isProjected"
          projectedStyle={{ lineDash: "6 4", areaOpacity: 0.1 }}
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
