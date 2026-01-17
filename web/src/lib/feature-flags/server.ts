/**
 * Server-only Unleash client for Next.js SSR and route handlers
 * This file should only be imported in server components and API routes
 */

import { Unleash } from 'unleash-client';

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
 * Wait for Unleash client to be ready
 */
async function waitForReady(): Promise<void> {
  const client = getUnleashClient();
  
  return new Promise((resolve) => {
    if (client.isReady()) {
      resolve();
      return;
    }
    
    client.once('ready', () => {
      resolve();
    });
  });
}

/**
 * Check if a feature flag is enabled (server-side)
 * @param flagName - The name of the feature flag
 * @param context - Optional context for targeting (userId, tenantId, etc.)
 */
export async function isFeatureEnabled(
  flagName: string,
  context?: {
    userId?: string;
    tenantId?: string;
    role?: string;
    plan?: string;
    region?: string;
    environment?: string;
  }
): Promise<boolean> {
  const client = getUnleashClient();
  await waitForReady();
  
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

  return client.isEnabled(flagName, unleashContext);
}

/**
 * Get all feature flags with their enabled state (server-side)
 * Useful for bootstrap data
 */
export async function getFeatureFlags(context?: {
  userId?: string;
  tenantId?: string;
  role?: string;
  plan?: string;
  region?: string;
  environment?: string;
}): Promise<Record<string, boolean>> {
  const client = getUnleashClient();
  await waitForReady();
  
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
}
