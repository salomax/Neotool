/**
 * Runtime Configuration System
 *
 * This module provides runtime configuration that is injected into the HTML
 * by the server and accessed via window.__RUNTIME_CONFIG__ on the client.
 *
 * Benefits:
 * - No rebuild required to change configs
 * - Configs can be stored in Vault and injected via Kubernetes
 * - Single source of truth for environment-specific settings
 */

/**
 * Runtime configuration schema
 * Add new public configuration keys here
 */
export interface RuntimeConfig {
  /** Environment name (development, staging, production) */
  env: string;
  /** Unleash Edge/Proxy URL for feature flags */
  unleashProxyUrl: string;
  /** Unleash client token (safe to expose - read-only) */
  unleashClientToken: string;
  /** GraphQL API endpoint */
  graphqlEndpoint?: string;
  /** Google OAuth client ID */
  googleClientId?: string;
}

/** Window augmentation for TypeScript */
declare global {
  interface Window {
    __RUNTIME_CONFIG__?: RuntimeConfig;
  }
}

/**
 * Get runtime configuration from window or environment
 * Works on both server and client side
 */
export function getRuntimeConfig(): RuntimeConfig {
  // Client-side: use injected window config
  if (typeof window !== 'undefined' && window.__RUNTIME_CONFIG__) {
    return window.__RUNTIME_CONFIG__;
  }

  // Server-side: read from environment variables
  return {
    env: process.env.RUNTIME_ENV || process.env.NODE_ENV || 'development',
    unleashProxyUrl: process.env.UNLEASH_PROXY_URL || '',
    unleashClientToken: process.env.UNLEASH_CLIENT_TOKEN || '',
    graphqlEndpoint: process.env.GRAPHQL_ENDPOINT || '',
    googleClientId: process.env.GOOGLE_CLIENT_ID || '',
  };
}

/**
 * Generate the script tag content for injecting config into HTML
 * Used by the server to inject config into the initial HTML
 */
export function generateConfigScript(): string {
  const config = getRuntimeConfig();
  return `window.__RUNTIME_CONFIG__ = ${JSON.stringify(config)};`;
}

/**
 * Validate that required config values are present
 */
export function validateConfig(config: RuntimeConfig): { valid: boolean; errors: string[] } {
  const errors: string[] = [];

  if (!config.unleashProxyUrl) {
    errors.push('unleashProxyUrl is required');
  }

  if (!config.unleashClientToken) {
    errors.push('unleashClientToken is required');
  }

  return {
    valid: errors.length === 0,
    errors,
  };
}
