"use client";

import { useFlag, useFlags, type IToggle } from "@unleash/proxy-client-react";
import { useFeatureFlagsContext } from "@/shared/providers/FeatureFlagsProvider";
import type { FeatureFlagEvaluationResult } from "@/shared/types/unleash";

/**
 * Hook to check if a specific feature flag is enabled with loading state
 * @param flagName - The name of the feature flag
 * @returns Object with enabled state, loading state, and error
 */
export function useFeatureFlag(flagName: string): FeatureFlagEvaluationResult {
  const { isReady, flagsError } = useFeatureFlagsContext();
  const enabled = useFlag(flagName);

  return {
    enabled: isReady ? enabled : false,
    loading: !isReady,
    error: flagsError,
  };
}

/**
 * Simple hook that returns only the boolean enabled state
 * Use this when you don't need loading/error states
 * @param flagName - The name of the feature flag
 * @returns boolean indicating if the flag is enabled
 */
export function useFeatureFlagEnabled(flagName: string): boolean {
  const { enabled } = useFeatureFlag(flagName);
  return enabled;
}

/**
 * Hook to get all feature flags
 * @returns Object with all feature flags and their enabled state
 */
export function useFeatureFlags(): Record<string, boolean> {
  const { isReady } = useFeatureFlagsContext();
  const flags = useFlags();

  // Return empty object if flags are not ready yet
  if (!isReady) {
    return {};
  }

  // Convert IToggle[] to Record<string, boolean>
  // Convert IToggle[] to Record<string, boolean>
  const flagsRecord: Record<string, boolean> = {};

  // The unleash proxy client typically returns an array of IToggle objects
  if (Array.isArray(flags)) {
    flags.forEach((toggle) => {
      if (toggle && typeof toggle === 'object' && 'name' in toggle) {
        flagsRecord[toggle.name] = toggle.enabled;
      }
    });
    return flagsRecord;
  }

  // Handle case where flags might already be a record (unlikely with this library but good fallback)
  if (flags && typeof flags === 'object') {
    return flags as Record<string, boolean>;
  }

  return flagsRecord;
}

/**
 * Hook to check if a feature flag is enabled with context
 * Note: Context-based targeting is typically handled server-side
 * This is a client-side check without context
 */
export function useFeatureFlagWithContext(
  flagName: string,
  context?: {
    userId?: string;
    tenantId?: string;
    role?: string;
    plan?: string;
    region?: string;
  }
): FeatureFlagEvaluationResult {
  // Client-side proxy doesn't support context in the same way
  // For context-based targeting, use server-side evaluation
  return useFeatureFlag(flagName);
}
