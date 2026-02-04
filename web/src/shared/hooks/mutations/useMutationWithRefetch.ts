"use client";

import { useRef, useCallback } from "react";
import type { DocumentNode } from "@apollo/client";
import { extractErrorMessage } from "@/shared/utils/error";

/**
 * Options for useMutationWithRefetch hook
 */
export interface UseMutationWithRefetchOptions<TVariables> {
  /**
   * Query document to refetch after successful mutation
   */
  refetchQuery?: DocumentNode;
  /**
   * Variables for the refetch query
   */
  refetchVariables?: TVariables;
  /**
   * Callback to refetch (alternative to refetchQuery)
   */
  onRefetch?: () => void;
  /**
   * Error message prefix for error handling
   */
  errorMessage?: string;
}

/**
 * Return type for useMutationWithRefetch hook
 */
export interface UseMutationWithRefetchReturn<TData, TVariables> {
  /**
   * Execute mutation with race condition prevention and automatic refetch
   */
  executeMutation: <TMutationVariables, TMutationData = TData>(
    mutationFn: (options: { variables: TMutationVariables; refetchQueries?: any[] }) => Promise<{ data?: TMutationData }>,
    variables: TMutationVariables,
    mutationKey: string
  ) => Promise<{ data?: TMutationData }>;
  /**
   * Check if a mutation is currently in flight for the given key
   */
  isMutationInFlight: (mutationKey: string) => boolean;
}

/**
 * Hook for executing mutations with race condition prevention and automatic refetch.
 * 
 * Features:
 * - Prevents duplicate mutations (race condition prevention)
 * - Automatic refetch after successful mutation
 * - Error handling with user-friendly messages
 * 
 * @param options - Configuration options
 * @returns Object with executeMutation function and isMutationInFlight checker
 * 
 * @example
 * ```tsx
 * function useUserManagement() {
 *   const { executeMutation } = useMutationWithRefetch({
 *     refetchQuery: GetUsersDocument,
 *     refetchVariables: { first, after, query, orderBy },
 *     errorMessage: 'Failed to update user',
 *   });
 * 
 *   const [enableUserMutation] = useEnableUserMutation();
 * 
 *   const enableUser = useCallback(async (userId: string) => {
 *     await executeMutation(
 *       enableUserMutation,
 *       { userId },
 *       userId // mutation key for race condition prevention
 *     );
 *   }, [executeMutation, enableUserMutation]);
 * }
 * ```
 */
export function useMutationWithRefetch<TData, TVariables>(
  options: UseMutationWithRefetchOptions<TVariables> = {}
): UseMutationWithRefetchReturn<TData, TVariables> {
  const {
    refetchQuery,
    refetchVariables,
    onRefetch,
    errorMessage = "Failed to execute mutation",
  } = options;

  // Ref to track in-flight mutations to prevent race conditions
  const mutationInFlightRef = useRef<Set<string>>(new Set());

  const executeMutation = useCallback(
    async <TMutationVariables, TMutationData = TData>(
      mutationFn: (options: { variables: TMutationVariables; refetchQueries?: any[] }) => Promise<{ data?: TMutationData }>,
      variables: TMutationVariables,
      mutationKey: string
    ) => {
      // Prevent race conditions: if a mutation is already in flight for this key, skip
      if (mutationInFlightRef.current.has(mutationKey)) {
        return { data: undefined } as { data?: TMutationData };
      }

      mutationInFlightRef.current.add(mutationKey);
      try {
        const result = await mutationFn({
          variables,
          ...(refetchQuery && refetchVariables
            ? {
              refetchQueries: [
                {
                  query: refetchQuery,
                  variables: refetchVariables,
                },
              ],
            }
            : {}),
        });

        // Only refetch if mutation was successful
        if (result.data) {
          onRefetch?.();
        }

        return result;
      } catch (err) {
        const errorMessageText = extractErrorMessage(err, errorMessage);
        throw new Error(errorMessageText);
      } finally {
        mutationInFlightRef.current.delete(mutationKey);
      }
    },
    [refetchQuery, refetchVariables, onRefetch, errorMessage]
  );

  const isMutationInFlight = useCallback(
    (mutationKey: string) => {
      return mutationInFlightRef.current.has(mutationKey);
    },
    []
  );

  return {
    executeMutation,
    isMutationInFlight,
  };
}

