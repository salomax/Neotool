"use client";

import { useState, useEffect, useRef } from "react";

/**
 * Options for useOptimisticUpdate hook
 */
export interface UseOptimisticUpdateOptions<T> {
  /**
   * Current value from server/props
   */
  value: T;
  /**
   * Whether to sync optimistic value with prop value when prop changes
   * @default true
   */
  syncOnPropChange?: boolean;
}

/**
 * Return type for useOptimisticUpdate hook
 */
export interface UseOptimisticUpdateReturn<T> {
  /**
   * Optimistic value - immediately reflects user actions
   */
  optimisticValue: T;
  /**
   * Set the optimistic value (typically called immediately on user action)
   */
  setOptimisticValue: (value: T) => void;
  /**
   * Whether an update operation is currently in progress
   */
  isUpdating: boolean;
  /**
   * Set the updating state
   */
  setIsUpdating: (updating: boolean) => void;
  /**
   * Execute an async update with optimistic UI updates
   * @param newValue - The new value to optimistically set
   * @param updateFn - Async function to execute the actual update
   * @returns Promise that resolves when update completes
   */
  executeUpdate: (newValue: T, updateFn: () => Promise<void>) => Promise<void>;
}

/**
 * Hook for managing optimistic UI updates
 * 
 * Provides a reusable pattern for optimistic updates that:
 * - Immediately updates UI when user performs an action
 * - Tracks update state to prevent duplicate operations
 * - Automatically syncs with server state when available
 * - Reverts to previous value on error
 * 
 * @param options - Configuration options
 * @returns Object with optimistic value, update state, and executeUpdate function
 * 
 * @example
 * ```tsx
 * function StatusToggle({ enabled, onToggle }) {
 *   const {
 *     optimisticValue,
 *     isUpdating,
 *     executeUpdate,
 *   } = useOptimisticUpdate({ value: enabled });
 * 
 *   const handleToggle = async (checked: boolean) => {
 *     await executeUpdate(checked, () => onToggle(checked));
 *   };
 * 
 *   return (
 *     <Switch
 *       checked={optimisticValue}
 *       onChange={handleToggle}
 *       disabled={isUpdating}
 *     />
 *   );
 * }
 * ```
 */
export function useOptimisticUpdate<T>(
  options: UseOptimisticUpdateOptions<T>
): UseOptimisticUpdateReturn<T> {
  const { value, syncOnPropChange = true } = options;
  
  const [optimisticValue, setOptimisticValue] = useState(value);
  const [isUpdating, setIsUpdating] = useState(false);
  const previousValueRef = useRef(value);
  const isUpdatingRef = useRef(false);

  // Sync optimistic value with prop when:
  // 1. Prop changes and we're not currently updating (server confirmed the change)
  // 2. Initial mount
  useEffect(() => {
    if (syncOnPropChange && !isUpdatingRef.current && value !== optimisticValue) {
      setOptimisticValue(value);
      previousValueRef.current = value;
    }
  }, [value, optimisticValue, syncOnPropChange]);

  const executeUpdate = async (newValue: T, updateFn: () => Promise<void>) => {
    if (isUpdating) return;

    // Store previous value for potential rollback
    const previousValue = optimisticValue;
    previousValueRef.current = previousValue;

    // Optimistic update - immediately update UI
    setOptimisticValue(newValue);
    setIsUpdating(true);
    isUpdatingRef.current = true;

    try {
      await updateFn();
      // Success - optimistic value will sync with server response via useEffect
    } catch (error) {
      // Error - revert to previous value
      setOptimisticValue(previousValue);
      previousValueRef.current = previousValue;
      throw error; // Re-throw so caller can handle it
    } finally {
      setIsUpdating(false);
      // Use setTimeout to ensure state updates are processed before clearing the flag
      setTimeout(() => {
        isUpdatingRef.current = false;
      }, 0);
    }
  };

  return {
    optimisticValue,
    setOptimisticValue,
    isUpdating,
    setIsUpdating,
    executeUpdate,
  };
}

