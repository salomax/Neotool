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
  flex: '0 1 auto', // Don't grow unnecessarily, but can shrink if needed
  overflow: "auto",
  maxHeight: '100%', // Prevent overflow of parent container
  mb: 2, // Add spacing between table and pagination
  // Hide scrollbar while maintaining scroll functionality
  scrollbarWidth: 'none', // Firefox
  '&::-webkit-scrollbar': {
    display: 'none', // Chrome, Safari, Edge
  },
};

/**
 * Table size options matching MUI Table size prop
 */
export type TableSize = "small" | "medium";

/**
 * Table size configuration with heights for rows, header, and pagination footer
 */
export interface TableSizeConfig {
  rowHeight: number;
  headerHeight: number;
  footerHeight: number;
}

/**
 * Size presets for table dimensions
 * Based on MUI Table size prop behavior:
 * - small: Compact rows (53px), smaller header (48px), compact footer (52px)
 * - medium: Standard rows (66px), standard header (56px), standard footer (60px)
 */
export const TABLE_SIZE_CONFIGS: Record<TableSize, TableSizeConfig> = {
  small: {
    rowHeight: 46, // MUI TableRow small size
    headerHeight: 28, // Compact header
    footerHeight: 52, // Compact pagination footer
  },
  medium: {
    rowHeight: 66, // Standard row height
    headerHeight: 56, // Standard header height
    footerHeight: 60, // Standard pagination footer height
  },
};

/**
 * Gets the table size configuration for a given size.
 * Defaults to "medium" if size is not provided.
 */
export function getTableSizeConfig(size?: TableSize): TableSizeConfig {
  return TABLE_SIZE_CONFIGS[size ?? "medium"];
}

// Table constants grouped for cleaner imports
export const TABLE_CONSTANTS = {
  // Size configurations
  SIZE_CONFIGS: TABLE_SIZE_CONFIGS,
  
  // Timing
  STABILITY_DELAY: 75, // Debounce delay for page size calculations (ms)
  
  // Fixed heights (medium size defaults for backward compatibility)
  ROW_HEIGHT: TABLE_SIZE_CONFIGS.medium.rowHeight,
  HEADER_HEIGHT: TABLE_SIZE_CONFIGS.medium.headerHeight,
  FOOTER_HEIGHT: TABLE_SIZE_CONFIGS.medium.footerHeight,
  LOADING_BAR_HEIGHT: 4, // Fixed height for LinearProgress container
  PAGINATION_MARGIN: 16, // mb: 2 = 16px margin between table and pagination
} as const;

// Individual exports for backward compatibility
export const MANAGEMENT_TABLE_ROW_HEIGHT = TABLE_CONSTANTS.ROW_HEIGHT;
export const TABLE_STABILITY_DELAY = TABLE_CONSTANTS.STABILITY_DELAY;
export const PAGINATION_FOOTER_MIN_HEIGHT = TABLE_CONSTANTS.FOOTER_HEIGHT;
export const TABLE_HEADER_FALLBACK_HEIGHT = TABLE_CONSTANTS.HEADER_HEIGHT;
export const LOADING_BAR_HEIGHT = TABLE_CONSTANTS.LOADING_BAR_HEIGHT;
export const TABLE_PAGINATION_MARGIN = TABLE_CONSTANTS.PAGINATION_MARGIN;

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

    // Only set up dynamic page size calculation if onTableResize is provided
    const mergedPageSizeOptions: UseDynamicPageSizeOptions | undefined = onTableResize
      ? {
          ...defaultPageSizeOptions,
          ...pageSizeOptions,
          recalculationKey,
        }
      : undefined;

    const dynamicPageSize = useDynamicPageSize(
      internalRef, 
      mergedPageSizeOptions ?? { minRows: 0, maxRows: 0, rowHeight: 0 }
    );
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
    return <Box id="dynamic-table-box" ref={combinedRef} sx={mergedSx} {...boxProps} />;
  }
);

export default DynamicTableBox;
