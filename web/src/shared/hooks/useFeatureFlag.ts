"use client";

import { useFlag, useFlags } from "@unleash/proxy-client-react";
import { useFeatureFlagsContext } from "@/shared/providers/FeatureFlagsProvider";

/**
 * Hook to check if a specific feature flag is enabled
 * @param flagName - The name of the feature flag
 * @returns boolean indicating if the flag is enabled
 */
export function useFeatureFlag(flagName: string): boolean {
  const { isReady } = useFeatureFlagsContext();
  const enabled = useFlag(flagName);

  // Return false if flags are not ready yet (prevents flicker)
  if (!isReady) {
    return false;
  }

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

  return flags;
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
): boolean {
  // Client-side proxy doesn't support context in the same way
  // For context-based targeting, use server-side evaluation
  return useFeatureFlag(flagName);
}
