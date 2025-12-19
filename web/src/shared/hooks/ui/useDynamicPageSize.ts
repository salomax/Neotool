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
   * Fallback header height to use when autoDetectHeaderHeight is enabled but measurement fails or returns 0.
   * Defaults to 56px (typical MUI TableHead height).
   */
  fallbackHeaderHeight?: number;
  /**
   * When true, automatically subtracts the measured footer height (e.g., pagination) from the available space.
   */
  autoDetectFooterHeight?: boolean;
  /**
   * CSS selector used to locate the footer element when autoDetectFooterHeight is enabled.
   * Defaults to "[data-pagination-footer]".
   */
  footerSelector?: string;
  /**
   * Fallback footer height to use when autoDetectFooterHeight is enabled but element doesn't exist or measurement returns 0.
   * Defaults to 60px (PAGINATION_FOOTER_MIN_HEIGHT).
   */
  fallbackFooterHeight?: number;
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
    fallbackHeaderHeight = 56, // Typical MUI TableHead height
    autoDetectFooterHeight = false,
    footerSelector = "[data-pagination-footer]",
    fallbackFooterHeight = 60, // PAGINATION_FOOTER_MIN_HEIGHT
    recalculationKey,
  } = options;

  const [pageSize, setPageSize] = useState(minRows);

  /**
   * Safely measures an element's height, returning 0 if measurement fails.
   * Uses fallback value if element doesn't exist or measurement returns 0.
   */
  const measureElementHeight = useCallback(
    (selector: string, fallback: number): number => {
      if (!containerRef.current) {
        return fallback;
      }

      try {
        const element = containerRef.current.querySelector(selector) as HTMLElement | null;
        if (!element) {
          return fallback;
        }

        const height = element.getBoundingClientRect().height;
        return height > 0 ? height : fallback;
      } catch (error) {
        // Silently fall back if measurement fails (e.g., element not in DOM)
        return fallback;
      }
    },
    [containerRef]
  );

  const calculatePageSize = useCallback(() => {
    if (!containerRef.current) {
      startTransition(() => {
        setPageSize(minRows);
      });
      return;
    }

    const containerHeight = containerRef.current.clientHeight;
    if (containerHeight <= 0) {
      startTransition(() => {
        setPageSize(minRows);
      });
      return;
    }

    let effectiveReservedHeight = reservedHeight;

    // Measure or use fallback for header
    if (autoDetectHeaderHeight) {
      effectiveReservedHeight += measureElementHeight(headerSelector, fallbackHeaderHeight);
    }

    // Measure or use fallback for footer
    if (autoDetectFooterHeight) {
      effectiveReservedHeight += measureElementHeight(footerSelector, fallbackFooterHeight);
    }

    const effectiveRowHeight = rowHeight;
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
    fallbackHeaderHeight,
    autoDetectFooterHeight,
    footerSelector,
    fallbackFooterHeight,
    measureElementHeight,
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
