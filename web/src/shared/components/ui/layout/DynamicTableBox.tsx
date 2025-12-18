"use client";

import React, { useRef, useEffect } from "react";
import { Box, type BoxProps } from "./Box";
import { useDynamicPageSize, type UseDynamicPageSizeOptions } from "@/shared/hooks/ui";
import type { SxProps, Theme } from "@mui/material/styles";

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

// Default sx styles for DynamicTableBox
const defaultSx: SxProps<Theme> = {
  flex: 1,
  overflow: "auto",
  minHeight: 0,
  // Hide scrollbar while maintaining scroll functionality
  scrollbarWidth: 'none', // Firefox
  '&::-webkit-scrollbar': {
    display: 'none', // Chrome, Safari, Edge
  },
};

export const MANAGEMENT_TABLE_ROW_HEIGHT = 66;
export const TABLE_STABILITY_DELAY = 75;

// Default page size options
const defaultPageSizeOptions: UseDynamicPageSizeOptions = {
  minRows: 5,
  maxRows: 50,
  rowHeight: MANAGEMENT_TABLE_ROW_HEIGHT, // Fixed row height to keep calculations predictable across screens
  reservedHeight: 0,
  autoDetectHeaderHeight: true,
  autoDetectRowHeight: false,
};

/**
 * DynamicTableBox component - A Box that automatically calculates and reports optimal page size
 * 
 * This component wraps a Box and uses ResizeObserver to monitor its size, calculating
 * how many table rows can fit within it. It calls onTableResize whenever the calculated
 * page size changes.
 * 
 * @example
 * ```tsx
 * // Basic usage with defaults (sx and pageSizeOptions are applied automatically)
 * <DynamicTableBox
 *   recalculationKey={`${users.length}-${loading ? "loading" : "ready"}`}
 *   onTableResize={(pageSize) => setFirst(pageSize)}
 * >
 *   <Table>...</Table>
 * </DynamicTableBox>
 * ```
 * 
 * @example
 * ```tsx
 * // Override defaults if needed
 * <DynamicTableBox
 *   sx={{ flex: 1, overflow: "auto", minHeight: 0, customStyle: "value" }}
 *   pageSizeOptions={{ minRows: 10, maxRows: 100 }}
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
      pageSizeOptions,
      onTableResize,
      recalculationKey,
      sx,
      ...boxProps
    }: DynamicTableBoxProps,
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

      if (stabilityTimeoutRef.current) {
        clearTimeout(stabilityTimeoutRef.current);
      }

      const scheduledSize = dynamicPageSize;

      stabilityTimeoutRef.current = setTimeout(() => {
        stabilityTimeoutRef.current = null;
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

    // Merge default sx with user-provided sx
    // MUI's sx prop supports arrays, so we can simply combine them
    const mergedSx: SxProps<Theme> = React.useMemo(() => {
      if (!sx) return defaultSx;
      return [defaultSx, sx] as SxProps<Theme>;
    }, [sx]);

    // TypeScript has difficulty inferring the ref callback type correctly when combining refs
    // The logic is correct - this is a known TypeScript limitation with ref forwarding
    // @ts-expect-error - Ref callback type inference issue with combined refs
    return <Box ref={combinedRef} sx={mergedSx} {...boxProps} />;
  }
);

export default DynamicTableBox;
