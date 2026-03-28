'use client';

import * as React from 'react';
import { Box, FormControlLabel, Stack, Switch, Typography } from '@mui/material';
import { LineChart } from '@/shared/components/ui/data-display';
import { EmptyState } from '@/shared/components/ui/feedback';

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
  isTaxExempt?: boolean;
  title?: string;
  emptyTitle: string;
  emptyDescription: string;
  principalLabel: string;
  grossLabel: string;
  netLabel: string;
  projectionToggleLabel: string;
}

function formatTimestamp(value: unknown, locale: string) {
  const parsed = new Date(value instanceof Date ? value : (value as any));
  if (Number.isNaN(parsed.getTime())) return String(value);
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
  isTaxExempt = false,
  title,
  emptyTitle,
  emptyDescription,
  grossLabel,
  netLabel,
  projectionToggleLabel,
}: HoldingPerformanceChartProps) {
  const hasProjectedPoints = React.useMemo(() => points.some((point) => point.isProjected), [points]);
  const [includeProjection, setIncludeProjection] = React.useState(false);

  React.useEffect(() => {
    if (!hasProjectedPoints && includeProjection) {
      setIncludeProjection(false);
    }
  }, [hasProjectedPoints, includeProjection]);

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
            <Typography variant="subtitle1" fontWeight={700}>{title}</Typography>
          ) : <span />}
          {hasProjectedPoints ? (
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
        </Box>

        <LineChart
          data={chartData}
          xKey="timestamp"
          xIsDate
          xContinuous
          xDateFormat={(value) => formatTimestamp(value, locale)}
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
        />
      </Stack>
    </Box>
  );
}

export default HoldingPerformanceChart;
