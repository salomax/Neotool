/**
 * Environment configuration
 * Access environment variables through this module
 */

export type AppEnv = 'development' | 'staging' | 'production';

const LOCAL_GRAPHQL_ENDPOINT = 'http://localhost:4000/graphql';

function normalizeAppEnv(raw: string | undefined): AppEnv {
  const normalized = raw?.trim().toLowerCase();

  if (normalized === 'production' || normalized === 'prod') {
    return 'production';
  }

  if (normalized === 'staging' || normalized === 'stage') {
    return 'staging';
  }

  return 'development';
}

function readEnvVariable(value: string | undefined): string | undefined {
  const trimmed = value?.trim();
  return trimmed ? trimmed : undefined;
}

export function resolveAppEnv(
  rawAppEnv: string | undefined = process.env.EXPO_PUBLIC_APP_ENV
): AppEnv {
  return normalizeAppEnv(rawAppEnv);
}

export function resolveGraphqlEndpoint(
  appEnv: AppEnv,
  envSource: NodeJS.ProcessEnv = process.env
): string {
  const explicitEndpoint = readEnvVariable(envSource.EXPO_PUBLIC_GRAPHQL_ENDPOINT);
  if (explicitEndpoint) {
    return explicitEndpoint;
  }

  const envSpecificEndpoint =
    appEnv === 'production'
      ? readEnvVariable(envSource.EXPO_PUBLIC_GRAPHQL_ENDPOINT_PRODUCTION)
      : appEnv === 'staging'
        ? readEnvVariable(envSource.EXPO_PUBLIC_GRAPHQL_ENDPOINT_STAGING)
        : readEnvVariable(envSource.EXPO_PUBLIC_GRAPHQL_ENDPOINT_DEVELOPMENT);

  if (envSpecificEndpoint) {
    return envSpecificEndpoint;
  }

  if (appEnv === 'development') {
    return LOCAL_GRAPHQL_ENDPOINT;
  }

  throw new Error(
    `Missing GraphQL endpoint for ${appEnv}. Configure EXPO_PUBLIC_GRAPHQL_ENDPOINT or EXPO_PUBLIC_GRAPHQL_ENDPOINT_${appEnv.toUpperCase()}.`
  );
}

const appEnv = resolveAppEnv();

export const env = {
  appEnv,
  graphqlEndpoint: resolveGraphqlEndpoint(appEnv),
  projectId: process.env.EXPO_PUBLIC_PROJECT_ID || '',
  isDev: __DEV__,
  isStaging: appEnv === 'staging',
  isProduction: appEnv === 'production',
} as const;
