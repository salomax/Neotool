"use client";

import React, { useRef, useEffect } from "react";
import { Box, type BoxProps } from "./Box";
import { useDynamicPageSize, type UseDynamicPageSizeOptions } from "@/shared/hooks/ui";
import {
  MANAGEMENT_TABLE_ROW_HEIGHT,
  TABLE_STABILITY_DELAY,
  PAGINATION_FOOTER_MIN_HEIGHT,
  TABLE_HEADER_FALLBACK_HEIGHT,
  LOADING_BAR_HEIGHT,
  TABLE_PAGINATION_MARGIN,
} from "./DynamicTableBox";

export interface DynamicTableContainerProps extends BoxProps {
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

// Default page size options for container (measures outer Box including pagination)
const defaultPageSizeOptions: UseDynamicPageSizeOptions = {
  minRows: 5,
  maxRows: 50,
  rowHeight: MANAGEMENT_TABLE_ROW_HEIGHT, // Fixed row height to keep calculations predictable across screens
  reservedHeight: LOADING_BAR_HEIGHT + TABLE_PAGINATION_MARGIN, // Account for fixed elements
  autoDetectHeaderHeight: true,
  fallbackHeaderHeight: TABLE_HEADER_FALLBACK_HEIGHT, // Use fallback if header measurement fails
  autoDetectRowHeight: false,
  autoDetectFooterHeight: true, // Automatically detect and subtract pagination footer
  footerSelector: "[data-pagination-footer]",
  fallbackFooterHeight: PAGINATION_FOOTER_MIN_HEIGHT, // Use fallback if footer doesn't exist yet
};

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
      pageSizeOptions,
      onTableResize,
      recalculationKey,
      sx,
      ...boxProps
    }: DynamicTableContainerProps,
    ref: React.ForwardedRef<HTMLDivElement>
  ) {
    const internalRef = useRef<HTMLDivElement | null>(null);

    // Merge default pageSizeOptions with user-provided options
    const mergedPageSizeOptions: UseDynamicPageSizeOptions = {
      ...defaultPageSizeOptions,
      ...pageSizeOptions,
      recalculationKey,
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
      }, TABLE_STABILITY_DELAY);

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
