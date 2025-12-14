"use client";

import { useState, useEffect, useRef } from "react";

/**
 * Hook to debounce a value
 * 
 * Returns a debounced version of the input value that only updates
 * after the specified delay has passed since the last change.
 * 
 * @param value - The value to debounce
 * @param delay - Delay in milliseconds (default: 300)
 * @returns Debounced value
 * 
 * @example
 * ```tsx
 * const [input, setInput] = useState('');
 * const debouncedInput = useDebounce(input, 300);
 * 
 * // input updates immediately
 * // debouncedInput updates 300ms after input stops changing
 * ```
 */
export function useDebounce<T>(value: T, delay: number = 300): T {
  const [debouncedValue, setDebouncedValue] = useState<T>(value);
  const timeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    // Clear existing timeout
    if (timeoutRef.current) {
      clearTimeout(timeoutRef.current);
    }

    // Set new timeout
    timeoutRef.current = setTimeout(() => {
      setDebouncedValue(value);
    }, delay);

    // Cleanup on unmount or when value/delay changes
    return () => {
      if (timeoutRef.current) {
        clearTimeout(timeoutRef.current);
      }
    };
  }, [value, delay]);

  return debouncedValue;
}

