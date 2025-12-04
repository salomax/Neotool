"use client";

import React, { useRef, useEffect } from "react";
import { Box, type BoxProps } from "./Box";
import { useDynamicPageSize, type UseDynamicPageSizeOptions } from "@/shared/hooks/ui";

export interface DynamicTableBoxProps extends BoxProps {
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
 * DynamicTableBox component - A Box that automatically calculates and reports optimal page size
 * 
 * This component wraps a Box and uses ResizeObserver to monitor its size, calculating
 * how many table rows can fit within it. It calls onTableResize whenever the calculated
 * page size changes.
 * 
 * @example
 * ```tsx
 * <DynamicTableBox
 *   sx={{ flex: 1, overflow: "auto", minHeight: 0 }}
 *   pageSizeOptions={{
 *     minRows: 5,
 *     maxRows: 50,
 *     rowHeight: 53,
 *     autoDetectHeaderHeight: true,
 *     autoDetectRowHeight: true,
 *   }}
 *   recalculationKey={`${users.length}-${loading ? "loading" : "ready"}`}
 *   onTableResize={(pageSize) => setFirst(pageSize)}
 * >
 *   <Table>...</Table>
 * </DynamicTableBox>
 * ```
 */
export const DynamicTableBox = React.forwardRef<HTMLDivElement, DynamicTableBoxProps>(
  function DynamicTableBox(
    {
      pageSizeOptions = {},
      onTableResize,
      recalculationKey,
      ...boxProps
    }: DynamicTableBoxProps,
    ref
  ) {
    const internalRef = useRef<HTMLDivElement>(null);

    // Merge recalculationKey into pageSizeOptions
    const optionsWithKey: UseDynamicPageSizeOptions = {
      ...pageSizeOptions,
      recalculationKey,
    };

    const dynamicPageSize = useDynamicPageSize(internalRef, optionsWithKey);

    // Call onTableResize when page size changes
    useEffect(() => {
      if (onTableResize && dynamicPageSize > 0) {
        onTableResize(dynamicPageSize);
      }
    }, [dynamicPageSize, onTableResize]);

    // Combine refs: forward the external ref and use internal ref for measurements
    const combinedRef = React.useCallback(
      (node: HTMLDivElement | null) => {
        internalRef.current = node;
        if (typeof ref === "function") {
          ref(node);
        } else if (ref && "current" in ref) {
          (ref as React.MutableRefObject<HTMLDivElement | null>).current = node;
        }
      },
      [ref]
    );

    return <Box ref={combinedRef} {...boxProps} />;
  }
);

export default DynamicTableBox;

