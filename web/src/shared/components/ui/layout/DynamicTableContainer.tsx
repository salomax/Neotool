"use client";

import React, { useRef, useEffect, useMemo } from "react";
import { Box, type BoxProps } from "./Box";
import { useDynamicPageSize, type UseDynamicPageSizeOptions } from "@/shared/hooks/ui";
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
      autoDetectRowHeight: false,
      autoDetectFooterHeight: true,
      footerSelector: "[data-pagination-footer]",
      fallbackFooterHeight: sizeConfig.footerHeight,
      ...pageSizeOptions,
      recalculationKey: combinedRecalculationKey,
    };

    const dynamicPageSize = useDynamicPageSize(internalRef, mergedPageSizeOptions);
    const stabilityTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
    const pendingSizeRef = useRef<number>(0);
    const lastCommittedSizeRef = useRef<number>(0);

    useEffect(() => {
      if (!onTableResize || dynamicPageSize <= 0) {
        return;
      }

      pendingSizeRef.current = dynamicPageSize;

      // Clear any pending timeout
      if (stabilityTimeoutRef.current) {
        clearTimeout(stabilityTimeoutRef.current);
      }

      const scheduledSize = dynamicPageSize;

      // Debounce the resize callback to avoid excessive calls during rapid recalculations
      stabilityTimeoutRef.current = setTimeout(() => {
        stabilityTimeoutRef.current = null;
        // Only call callback if size hasn't changed during the delay (stable)
        if (
          pendingSizeRef.current === scheduledSize &&
          scheduledSize !== lastCommittedSizeRef.current
        ) {
          lastCommittedSizeRef.current = scheduledSize;
          onTableResize(scheduledSize);
        }
      }, TABLE_CONSTANTS.STABILITY_DELAY);

      return () => {
        if (stabilityTimeoutRef.current) {
          clearTimeout(stabilityTimeoutRef.current);
          stabilityTimeoutRef.current = null;
        }
      };
    }, [dynamicPageSize, onTableResize]);

    // Combine refs: forward the external ref and use internal ref for measurements
    const combinedRef = React.useMemo(
      () => {
        const callback: React.RefCallback<HTMLDivElement> = (node: HTMLDivElement | null) => {
          (internalRef as React.MutableRefObject<HTMLDivElement | null>).current = node;
          if (typeof ref === "function") {
            ref(node);
          } else if (ref && typeof ref === "object" && "current" in ref) {
            (ref as React.MutableRefObject<HTMLDivElement | null>).current = node;
          }
        };
        return callback;
      },
      [ref]
    );

    // Container should fill available space and use flex column layout
    const containerSx = React.useMemo(() => {
      return {
        flex: 1,
        display: "flex",
        flexDirection: "column",
        minHeight: 0,
        height: "100%",
        overflow: "hidden",
        ...sx,
      };
    }, [sx]);

    return <Box ref={combinedRef} sx={containerSx} {...boxProps} />;
  }
);

export default DynamicTableContainer;
