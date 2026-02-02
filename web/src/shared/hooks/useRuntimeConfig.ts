"use client";

import { useMemo } from 'react';
import { getRuntimeConfig, type RuntimeConfig } from '@/shared/config/runtime-config';

/**
 * Hook to access runtime configuration
 * Memoizes the config to avoid unnecessary re-renders
 */
export function useRuntimeConfig(): RuntimeConfig {
  return useMemo(() => getRuntimeConfig(), []);
}
