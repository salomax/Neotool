/**
 * Server-only Unleash client for Next.js SSR and route handlers
 * This file should only be imported in server components and API routes
 */

import { Unleash } from 'unleash-client';
import type { UnleashEvaluationContext } from '@/shared/types/unleash';

let unleashClient: Unleash | null = null;
let isInitialized = false;

/**
 * Get or create the Unleash client instance (server-side only)
 * Uses singleton pattern to avoid creating multiple clients
 */
export function getUnleashClient(): Unleash {
  if (typeof window !== 'undefined') {
    throw new Error('getUnleashClient should only be called on the server');
  }

  if (unleashClient) {
    return unleashClient;
  }

  const unleashUrl = process.env.UNLEASH_SERVER_URL;
  const unleashApiToken = process.env.UNLEASH_SERVER_API_TOKEN;

  if (!unleashUrl) {
    throw new Error('UNLEASH_SERVER_URL environment variable is required');
  }

  if (!unleashApiToken) {
    throw new Error('UNLEASH_SERVER_API_TOKEN environment variable is required');
  }

  unleashClient = new Unleash({
    url: unleashUrl,
    appName: 'neotool-web',
    instanceId: `web-${process.env.NEXT_PUBLIC_ENV || 'production'}-${Date.now()}`,
    customHeaders: {
      Authorization: unleashApiToken,
    },
    refreshInterval: 15, // Refresh every 15 seconds
    disableMetrics: false,
    disableAutoStart: false,
  });

  // Start the client
  if (!isInitialized) {
    unleashClient.start();
    isInitialized = true;
  }

  return unleashClient;
}

/**
 * Wait for Unleash client to be ready with timeout
 * @param timeoutMs - Maximum time to wait in milliseconds (default: 5000ms)
 * @returns true if ready, false if timed out
 */
async function waitForReady(timeoutMs: number = 5000): Promise<boolean> {
  const client = getUnleashClient();

  return Promise.race([
    new Promise<boolean>((resolve) => {
      // Listen for ready event - in v6, this means local cache is ready
      // For server sync, we'd listen to 'synchronized' event, but for bootstrap
      // 'ready' is sufficient as we have bootstrap data
      client.once('ready', () => {
        resolve(true);
      });
    }),
    new Promise<boolean>((resolve) =>
      setTimeout(() => {
        console.warn(`[Unleash] Client not ready after ${timeoutMs}ms, falling back to defaults`);
        resolve(false);
      }, timeoutMs)
    ),
  ]);
}

/**
 * Check if a feature flag is enabled (server-side)
 * @param flagName - The name of the feature flag
 * @param context - Optional context for targeting (userId, tenantId, etc.)
 * @param defaultValue - Default value to return if client is not ready (default: false)
 */
export async function isFeatureEnabled(
  flagName: string,
  context?: UnleashEvaluationContext,
  defaultValue: boolean = false
): Promise<boolean> {
  try {
    const client = getUnleashClient();
    const ready = await waitForReady();

    // If not ready, return default value
    if (!ready) {
      return defaultValue;
    }

    const unleashContext = context
      ? {
          userId: context.userId,
          properties: {
            ...(context.tenantId && { tenantId: context.tenantId }),
            ...(context.role && { role: context.role }),
            ...(context.plan && { plan: context.plan }),
            ...(context.region && { region: context.region }),
            ...(context.environment && { environment: context.environment }),
          },
        }
      : undefined;

    return client.isEnabled(flagName, unleashContext, defaultValue);
  } catch (error) {
    console.error(`[Unleash] Error evaluating flag "${flagName}":`, error);
    return defaultValue;
  }
}

/**
 * Get all feature flags with their enabled state (server-side)
 * Useful for bootstrap data
 */
export async function getFeatureFlags(
  context?: UnleashEvaluationContext
): Promise<Record<string, boolean>> {
  try {
    const client = getUnleashClient();
    const ready = await waitForReady();

    // If not ready, return empty object
    if (!ready) {
      return {};
    }

    const unleashContext = context
      ? {
          userId: context.userId,
          properties: {
            ...(context.tenantId && { tenantId: context.tenantId }),
            ...(context.role && { role: context.role }),
            ...(context.plan && { plan: context.plan }),
            ...(context.region && { region: context.region }),
            ...(context.environment && { environment: context.environment }),
          },
        }
      : undefined;

    // Get all toggles
    const toggles = client.getFeatureToggleDefinitions();
    const flags: Record<string, boolean> = {};

    for (const toggle of toggles) {
      flags[toggle.name] = client.isEnabled(toggle.name, unleashContext);
    }

    return flags;
  } catch (error) {
    console.error('[Unleash] Error fetching all flags:', error);
    return {};
  }
}
