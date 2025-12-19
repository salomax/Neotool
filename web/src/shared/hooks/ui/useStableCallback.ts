"use client";

import { useEffect, useRef } from "react";

/**
 * Options for useStableCallback hook
 */
export interface UseStableCallbackOptions {
  /**
   * Delay in milliseconds before calling the callback after the value stabilizes
   * @default 75
   */
  delay?: number;
  /**
   * Whether the hook is enabled. When false, the callback is never called.
   * @default true
   */
  enabled?: boolean;
}

/**
 * Hook that debounces a callback invocation until a value has stabilized.
 * 
 * The callback is only called if:
 * - The value hasn't changed during the delay period
 * - The value is different from the last committed value
 * - enabled is true
 * 
 * @param value - The value to watch for stability
 * @param callback - The callback to invoke when the value stabilizes
 * @param options - Configuration options
 * 
 * @example
 * ```tsx
 * useStableCallback(
 *   pageSize,
 *   (size) => setFirst(size),
 *   { delay: 75, enabled: !!onTableResize }
 * );
 * ```
 */
export function useStableCallback<T>(
  value: T,
  callback: (value: T) => void,
  options: UseStableCallbackOptions = {}
): void {
  const { delay = 75, enabled = true } = options;
  const stabilityTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const pendingValueRef = useRef<T>(value);
  const lastCommittedValueRef = useRef<T>(value);

  useEffect(() => {
    if (!enabled || value === lastCommittedValueRef.current) {
      return;
    }

    pendingValueRef.current = value;

    // Clear any pending timeout
    if (stabilityTimeoutRef.current) {
      clearTimeout(stabilityTimeoutRef.current);
    }

    const scheduledValue = value;

    // Debounce the callback to avoid excessive calls during rapid changes
    stabilityTimeoutRef.current = setTimeout(() => {
      stabilityTimeoutRef.current = null;
      // Only call callback if value hasn't changed during the delay (stable)
      if (
        pendingValueRef.current === scheduledValue &&
        scheduledValue !== lastCommittedValueRef.current
      ) {
        lastCommittedValueRef.current = scheduledValue;
        callback(scheduledValue);
      }
    }, delay);

    return () => {
      if (stabilityTimeoutRef.current) {
        clearTimeout(stabilityTimeoutRef.current);
        stabilityTimeoutRef.current = null;
      }
    };
  }, [value, callback, delay, enabled]);
}
