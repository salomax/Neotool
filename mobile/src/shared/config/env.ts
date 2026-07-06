/**
 * Environment configuration
 * Access environment variables through this module
 */
import { Platform } from 'react-native';

export type AppEnv = 'development' | 'staging' | 'production';

const IOS_LOCAL_GRAPHQL_ENDPOINT = 'http://localhost:4000/graphql';
const ANDROID_EMULATOR_GRAPHQL_ENDPOINT = 'http://10.0.2.2:4000/graphql';

function getDevelopmentGraphqlEndpoint() {
  return Platform.OS === 'android' ? ANDROID_EMULATOR_GRAPHQL_ENDPOINT : IOS_LOCAL_GRAPHQL_ENDPOINT;
}

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
    return getDevelopmentGraphqlEndpoint();
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
