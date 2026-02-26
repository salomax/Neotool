/**
 * Server-side utility to get feature flags for bootstrap
 * This should be called in Server Components or Route Handlers
 */

import { getFeatureFlags } from './server';
import { buildUnleashEvaluationContext } from '@/shared/utils/auth-session/shared';
import { getAuthSessionContextFromCookies } from '@/shared/utils/auth-session/server';
import type { AuthSessionContext } from '@/shared/utils/auth-session/shared';
import type { UnleashEvaluationContext } from '@/shared/types/unleash';

export type FeatureFlagsBootstrapData = {
  flags: Record<string, boolean>;
  context: UnleashEvaluationContext;
  sessionContext: AuthSessionContext | null;
};

/**
 * Get feature flags with user context for bootstrap
 * This ensures the first client render matches SSR
 */
export async function getBootstrapFlags(): Promise<Record<string, boolean>> {
  const { flags } = await getBootstrapFeatureFlagsData();
  return flags;
}

export async function getBootstrapFeatureFlagsData(): Promise<FeatureFlagsBootstrapData> {
  try {
    const sessionContext = await getAuthSessionContextFromCookies();
    const context = buildUnleashEvaluationContext(sessionContext);

    const flags = await getFeatureFlags(context);
    return { flags, context, sessionContext };
  } catch (error) {
    console.error('Failed to get bootstrap flags:', error);
    return {
      flags: {},
      context: buildUnleashEvaluationContext(null),
      sessionContext: null,
    };
  }
}
