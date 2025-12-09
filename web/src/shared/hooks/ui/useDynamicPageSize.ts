"use client";

import { useState, useEffect, useCallback, RefObject, startTransition } from "react";

export interface UseDynamicPageSizeOptions {
  /**
   * Minimum number of rows to show (default: 5)
   */
  minRows?: number;
  /**
   * Maximum number of rows to show (default: 50)
   */
  maxRows?: number;
  /**
   * Estimated row height in pixels (default: 53 for MUI TableRow)
   */
  rowHeight?: number;
  /**
   * Additional space to reserve (e.g., for header, padding) in pixels (default: 0)
   */
  reservedHeight?: number;
  /**
   * When true, automatically subtracts the measured table header height from the available space.
   */
  autoDetectHeaderHeight?: boolean;
  /**
   * CSS selector used to locate the header element when autoDetectHeaderHeight is enabled.
   * Defaults to "thead".
   */
  headerSelector?: string;
  /**
   * When true, attempts to measure the height of the first matching row (tbody tr) instead of using a fixed rowHeight.
   */
  autoDetectRowHeight?: boolean;
  /**
   * CSS selector used to find rows when autoDetectRowHeight is enabled. Defaults to "tbody tr".
   */
  rowSelector?: string;
  /**
   * Optional key that, when changed, forces the hook to recalculate even if the container size remains the same.
   * Useful to re-evaluate measurements when the table content changes (e.g., loading state resolved).
   */
  recalculationKey?: string | number | boolean;
}

/**
 * Hook to calculate dynamic page size based on container height
 * 
 * This hook monitors a container element's height and calculates how many rows
 * can fit within it, accounting for row height and reserved space.
 * 
 * @param containerRef - Ref to the container element to measure
 * @param options - Configuration options
 * @returns Calculated page size (number of rows that fit)
 * 
 * @example
 * ```tsx
 * const containerRef = useRef<HTMLDivElement>(null);
 * const pageSize = useDynamicPageSize(containerRef, {
 *   minRows: 5,
 *   maxRows: 50,
 *   rowHeight: 53,
 *   reservedHeight: 80,
 * });
 * ```
 */
export function useDynamicPageSize(
  containerRef: RefObject<HTMLElement>,
  options: UseDynamicPageSizeOptions = {}
): number {
  const {
    minRows = 5,
    maxRows = 50,
    rowHeight = 53, // MUI TableRow default height
    reservedHeight = 0,
    autoDetectHeaderHeight = false,
    headerSelector = "thead",
    autoDetectRowHeight = false,
    rowSelector = "tbody tr",
    recalculationKey,
  } = options;

  const [pageSize, setPageSize] = useState(minRows);

  const calculatePageSize = useCallback(() => {
    if (!containerRef.current) {
      startTransition(() => {
        setPageSize(minRows);
      });
      return;
    }

    const containerHeight = containerRef.current.clientHeight;
    let effectiveReservedHeight = reservedHeight;

    if (autoDetectHeaderHeight) {
      const headerElement = containerRef.current.querySelector(headerSelector) as HTMLElement | null;
      if (headerElement) {
        const headerHeight = headerElement.getBoundingClientRect().height;
        if (headerHeight > 0) {
          effectiveReservedHeight += headerHeight;
        }
      }
    }

    let effectiveRowHeight = rowHeight;
    if (autoDetectRowHeight) {
      const rowElement = containerRef.current.querySelector(rowSelector) as HTMLElement | null;
      if (rowElement) {
        const measuredRowHeight = rowElement.getBoundingClientRect().height;
        if (measuredRowHeight > 0) {
          effectiveRowHeight = measuredRowHeight;
        }
      }
    }

    const availableHeight = containerHeight - effectiveReservedHeight;
    
    if (availableHeight <= 0 || effectiveRowHeight <= 0) {
      startTransition(() => {
        setPageSize(minRows);
      });
      return;
    }

    // Calculate how many rows fit
    const calculatedRows = Math.floor(availableHeight / effectiveRowHeight);
    
    // Clamp between min and max
    const clampedRows = Math.max(minRows, Math.min(maxRows, calculatedRows));
    
    startTransition(() => {
      setPageSize(clampedRows);
    });
  }, [
    containerRef,
    minRows,
    maxRows,
    rowHeight,
    reservedHeight,
    autoDetectHeaderHeight,
    headerSelector,
    autoDetectRowHeight,
    rowSelector,
  ]);

  useEffect(() => {
    // Calculate initial page size
    calculatePageSize();

    if (!containerRef.current) {
      return;
    }

    // Use ResizeObserver to watch for container size changes
    const resizeObserver = new ResizeObserver(() => {
      calculatePageSize();
    });

    resizeObserver.observe(containerRef.current);

    return () => {
      resizeObserver.disconnect();
    };
  }, [calculatePageSize, containerRef]);

  useEffect(() => {
    if (typeof recalculationKey === "undefined") {
      return;
    }
    calculatePageSize();
  }, [recalculationKey, calculatePageSize]);

  return pageSize;
}
