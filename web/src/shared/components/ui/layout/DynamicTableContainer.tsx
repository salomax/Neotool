"use client";

import React, { useRef, useMemo } from "react";
import { Box, type BoxProps } from "./Box";
import { useDynamicPageSize, type UseDynamicPageSizeOptions } from "@/shared/hooks/ui";
import { useStableCallback } from "@/shared/hooks/ui/useStableCallback";
import { useCombinedRef } from "@/shared/hooks/ui/useCombinedRef";
import type { SxProps, Theme } from "@mui/material/styles";
import {
  TABLE_STABILITY_DELAY,
  TABLE_CONSTANTS,
  type TableSize,
  getTableSizeConfig,
} from "./DynamicTableBox";

export interface DynamicTableContainerProps extends BoxProps {
  /**
   * Table size affecting row height, header height, and pagination footer height.
   * @default "medium"
   */
  size?: TableSize;
  /**
   * Configuration options for dynamic page size calculation
   */
  pageSizeOptions?: UseDynamicPageSizeOptions;
  /**
   * Callback invoked when the calculated page size changes.
   * The new page size (number of rows) is passed as the argument.
   */
  onTableResize?: (pageSize: number) => void;
  /**
   * Optional key that, when changed, forces recalculation even if container size remains the same.
   * Useful to re-evaluate measurements when table content changes (e.g., loading state resolved).
   */
  recalculationKey?: string | number | boolean;
}

/**
 * DynamicTableContainer component - A container that automatically calculates and reports optimal page size
 * 
 * This component wraps the entire table structure (including pagination) and measures the outer
 * container Box to calculate how many table rows can fit, accounting for header and footer heights.
 * It calls onTableResize whenever the calculated page size changes.
 * 
 * Unlike DynamicTableBox which measures only the table container, this component measures the
 * outer container and automatically subtracts pagination footer height when present.
 * 
 * @example
 * ```tsx
 * // Basic usage with defaults
 * <DynamicTableContainer
 *   recalculationKey={`${users.length}-${loading ? "loading" : "ready"}`}
 *   onTableResize={(pageSize) => setFirst(pageSize)}
 * >
 *   <Box fullHeight>
 *     <DynamicTableBox>
 *       <Table>...</Table>
 *     </DynamicTableBox>
 *     <Box data-pagination-footer>
 *       <RelayPagination />
 *     </Box>
 *   </Box>
 * </DynamicTableContainer>
 * ```
 */
export const DynamicTableContainer = React.forwardRef<HTMLDivElement, DynamicTableContainerProps>(
  function DynamicTableContainer(
    {
      size = "medium",
      pageSizeOptions,
      onTableResize,
      recalculationKey,
      sx,
      ...boxProps
    }: DynamicTableContainerProps,
    ref: React.ForwardedRef<HTMLDivElement>
  ) {
    const internalRef = useRef<HTMLDivElement | null>(null);
    const sizeConfig = getTableSizeConfig(size);

    // Combine size with recalculationKey to trigger recalculation when size changes
    const combinedRecalculationKey = useMemo(
      () => (recalculationKey ? `${size}-${recalculationKey}` : size),
      [size, recalculationKey]
    );

    // Merge default pageSizeOptions with user-provided options and size-based heights
    const mergedPageSizeOptions: UseDynamicPageSizeOptions = {
      minRows: 5,
      maxRows: 50,
      rowHeight: sizeConfig.rowHeight,
      reservedHeight: TABLE_CONSTANTS.LOADING_BAR_HEIGHT + TABLE_CONSTANTS.PAGINATION_MARGIN,
      autoDetectHeaderHeight: true,
      fallbackHeaderHeight: sizeConfig.headerHeight,
      autoDetectFooterHeight: true,
      footerSelector: "[data-pagination-footer]",
      fallbackFooterHeight: sizeConfig.footerHeight,
      ...pageSizeOptions,
      recalculationKey: combinedRecalculationKey,
    };

    const dynamicPageSize = useDynamicPageSize(internalRef, mergedPageSizeOptions);

    // Use stable callback to debounce resize notifications
    useStableCallback(
      dynamicPageSize,
      (size) => {
        if (onTableResize && size > 0) {
          onTableResize(size);
        }
      },
      {
        delay: TABLE_CONSTANTS.STABILITY_DELAY,
        enabled: !!onTableResize && dynamicPageSize > 0,
      }
    );

    // Combine refs: forward the external ref and use internal ref for measurements
    const combinedRef = useCombinedRef(ref, internalRef);

    // Container should fill available space and use flex column layout
    const containerSx: SxProps<Theme> = React.useMemo(() => {
      const baseSx = {
        flex: 1,
        display: "flex",
        flexDirection: "column",
        minHeight: 0,
        height: "100%",
        overflow: "hidden",
      };
      if (!sx) return baseSx;
      return [baseSx, sx] as SxProps<Theme>;
    }, [sx]);

    // TypeScript has difficulty inferring the ref callback type correctly when combining refs
    // The logic is correct - this is a known TypeScript limitation with ref forwarding
    // @ts-expect-error - Ref callback type inference issue with combined refs
    return <Box ref={combinedRef} sx={containerSx} {...boxProps} />;
  }
);

export default DynamicTableContainer;
