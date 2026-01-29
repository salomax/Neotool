"use client";

import * as React from "react";
import { Box, Skeleton } from "@mui/material";
import { MetricCard, MetricCardProps } from "./MetricCard";
import type { ResponsiveValue } from "../layout/types";

export type MetricGridItem = MetricCardProps & { type?: 'metric' };

export interface MetricGridProps {
  /**
   * Array of metric items to display
   */
  items: MetricGridItem[];
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
   * Explicit gridTemplateColumns definition. When provided, overrides columns.
   */
  templateColumns?: ResponsiveValue<string>;
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
  loading = false,
  columns = { xs: 1, sm: 2, md: 5 },
  templateColumns,
  gap = 2,
  loadingComponent,
  skeletonCount,
  skeletonHeights = { metric: 120, percentage: 140 }
}) => {
  const defaultSkeletonCount = skeletonCount ?? items.length ?? 10;

  const resolvedSkeletonHeights = React.useMemo(
    () => ({
      metric: skeletonHeights.metric ?? 120,
      percentage: skeletonHeights.percentage ?? 140,
    }),
    [skeletonHeights]
  );

  // Convert columns to responsive format if needed
  const gridColumns = React.useMemo(() => {
    if (typeof columns === "number") {
      return { xs: 1, sm: 2, md: columns };
    }
    return columns;
  }, [columns]);

  const gridTemplateColumns = React.useMemo(() => {
    if (templateColumns) {
      return templateColumns;
    }
    const cols: { [key: string]: string } = {};
    if (gridColumns.xs !== undefined) cols.xs = `repeat(${gridColumns.xs}, 1fr)`;
    if (gridColumns.sm !== undefined) cols.sm = `repeat(${gridColumns.sm}, 1fr)`;
    if (gridColumns.md !== undefined) cols.md = `repeat(${gridColumns.md}, 1fr)`;
    if (gridColumns.lg !== undefined) cols.lg = `repeat(${gridColumns.lg}, 1fr)`;
    if (gridColumns.xl !== undefined) cols.xl = `repeat(${gridColumns.xl}, 1fr)`;
    return cols;
  }, [gridColumns, templateColumns]);

  return (
    <Box>
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
              return (
                <Skeleton 
                  key={i}
                  variant="rectangular" 
                  height={resolvedSkeletonHeights.metric} 
                  sx={{ borderRadius: 1 }} 
                />
              );
            })
          )
        ) : (
          items.map((item, index) => (
            <Box
              key={index}
              sx={{
                height: '100%',
                minHeight: resolvedSkeletonHeights.metric,
              }}
            >
              <MetricCard
                {...item}
                sx={{
                  ...(item.sx || {}),
                  height: '100%',
                }}
              />
            </Box>
          )) 
        )}
      </Box>
    </Box>
  );
};
