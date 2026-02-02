/**
 * Server-side utility to get feature flags for bootstrap
 * This should be called in Server Components or Route Handlers
 */

import { getFeatureFlags } from './server';
import { getAuthToken, getAuthUser } from '@/shared/utils/auth';

/**
 * Get feature flags with user context for bootstrap
 * This ensures the first client render matches SSR
 */
export async function getBootstrapFlags(): Promise<Record<string, boolean>> {
  try {
    // Get user context if available
    const user = getAuthUser();
    const token = getAuthToken();

    // Extract user context for targeting
    const context = user
      ? {
          userId: user.id,
          tenantId: user.tenantId,
          role: user.role || (user.roles && user.roles.length > 0 ? user.roles[0].name : undefined),
          plan: user.plan,
          environment: process.env.NEXT_PUBLIC_ENV || 'production',
        }
      : {
          environment: process.env.NEXT_PUBLIC_ENV || 'production',
        };

    const flags = await getFeatureFlags(context);
    return flags;
  } catch (error) {
    console.error('Failed to get bootstrap flags:', error);
    // Return empty object on error - flags will be fetched client-side
    return {};
  }
}
