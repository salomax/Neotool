"use client";

import * as React from "react";
import { Box, Stack, Typography, Tooltip, IconButton, Skeleton } from "@mui/material";
import InfoOutlinedIcon from "@mui/icons-material/InfoOutlined";
import { MetricBox, MetricBoxProps } from "./MetricBox";
import { PercentageBox, PercentageBoxProps } from "./PercentageBox";
import type { ResponsiveValue } from "../layout/types";

export type MetricGridItem = 
  | ({ type: 'metric' } & Omit<MetricBoxProps, 'isLoading'>)
  | ({ type: 'percentage' } & Omit<PercentageBoxProps, 'isLoading'>);

export interface MetricGridHeaderProps {
  title: string;
  icon?: React.ReactNode;
  tooltip?: string;
}

export interface MetricGridProps {
  /**
   * Array of metric items to display
   */
  items: MetricGridItem[];
  /**
   * Header configuration
   */
  header?: MetricGridHeaderProps;
  /**
   * Loading state
   */
  loading?: boolean;
  /**
   * Number of columns or responsive column configuration
   * @default { xs: 1, sm: 2, md: 5 }
   */
  columns?: number | ResponsiveValue<number>;
  /**
   * Gap between grid items
   * @default 2
   */
  gap?: number;
  /**
   * Custom loading component (skeleton cards)
   */
  loadingComponent?: React.ReactNode;
  /**
   * Number of skeleton items to show while loading
   * @default items.length or 10
   */
  skeletonCount?: number;
  /**
   * Custom skeleton heights for different item types
   */
  skeletonHeights?: {
    metric?: number;
    percentage?: number;
  };
}

/**
 * MetricGrid component for displaying a grid of metric and percentage boxes
 * 
 * @example
 * ```tsx
 * <MetricGrid
 *   header={{
 *     title: "Last Balance Summary",
 *     icon: <TrendingUpIcon />,
 *     tooltip: "Latest available data"
 *   }}
 *   items={[
 *     { type: 'metric', label: "Total Assets", value: 1000000000 },
 *     { type: 'percentage', label: "Basel Index", value: 15.5, thresholds: {...} }
 *   ]}
 *   loading={loading}
 *   columns={{ xs: 1, sm: 2, md: 5 }}
 * />
 * ```
 */
export const MetricGrid: React.FC<MetricGridProps> = ({
  items,
  header,
  loading = false,
  columns = { xs: 1, sm: 2, md: 5 },
  gap = 2,
  loadingComponent,
  skeletonCount,
  skeletonHeights = { metric: 120, percentage: 140 }
}) => {
  const defaultSkeletonCount = skeletonCount ?? items.length ?? 10;

  // Convert columns to responsive format if needed
  const gridColumns = React.useMemo(() => {
    if (typeof columns === "number") {
      return { xs: 1, sm: 2, md: columns };
    }
    return columns;
  }, [columns]);

  const gridTemplateColumns = React.useMemo(() => {
    const cols: { [key: string]: string } = {};
    if (gridColumns.xs !== undefined) cols.xs = `repeat(${gridColumns.xs}, 1fr)`;
    if (gridColumns.sm !== undefined) cols.sm = `repeat(${gridColumns.sm}, 1fr)`;
    if (gridColumns.md !== undefined) cols.md = `repeat(${gridColumns.md}, 1fr)`;
    if (gridColumns.lg !== undefined) cols.lg = `repeat(${gridColumns.lg}, 1fr)`;
    if (gridColumns.xl !== undefined) cols.xl = `repeat(${gridColumns.xl}, 1fr)`;
    return cols;
  }, [gridColumns]);

  return (
    <Box>
      {/* Header */}
      {header && (
        <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 2 }}>
          {header.icon}
          <Typography variant="h6" component="h2" sx={{ fontWeight: 600 }}>
            {header.title}
          </Typography>
          <Box sx={{ flexGrow: 1 }} />
          {header.tooltip && (
            <Tooltip title={header.tooltip}>
              <IconButton size="small">
                <InfoOutlinedIcon fontSize="small" />
              </IconButton>
            </Tooltip>
          )}
        </Stack>
      )}

      {/* Grid Layout */}
      <Box
        sx={{
          display: 'grid',
          gridTemplateColumns: gridTemplateColumns,
          gap: gap,
        }}
      >
        {loading ? (
          loadingComponent || (
            // Default Loading Skeletons
            Array.from({ length: defaultSkeletonCount }).map((_, i) => {
              // Alternate between metric and percentage skeletons
              const isPercentage = i % 6 === 0 || i % 6 === 5;
              const height = isPercentage 
                ? skeletonHeights.percentage 
                : skeletonHeights.metric;
              
              return (
                <Skeleton 
                  key={i}
                  variant="rectangular" 
                  height={height} 
                  sx={{ borderRadius: 1 }} 
                />
              );
            })
          )
        ) : (
          items.map((item, index) => (
            <Box key={index}>
              {item.type === 'percentage' ? (
                <PercentageBox
                  {...item}
                  isLoading={loading}
                />
              ) : (
                <MetricBox 
                  {...item}
                  isLoading={loading}
                />
              )}
            </Box>
          ))
        )}
      </Box>
    </Box>
  );
};
