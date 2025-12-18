/**
 * Environment-specific configurations for E2E tests
 */

export type TestEnvironment = 'development' | 'staging' | 'production' | 'local';

export interface EnvironmentConfig {
  baseURL: string;
  graphqlEndpoint: string;
  apiTimeout: number;
  retries: number;
  workers: number;
}

const getEnvironment = (): TestEnvironment => {
  const env = process.env.PLAYWRIGHT_TEST_ENV || process.env.NODE_ENV || 'development';
  
  if (env === 'production' || env === 'prod') return 'production';
  if (env === 'staging' || env === 'stage') return 'staging';
  if (env === 'test' || env === 'local') return 'local';
  return 'development';
};

export const ENVIRONMENTS: Record<TestEnvironment, EnvironmentConfig> = {
  local: {
    baseURL: process.env.PLAYWRIGHT_TEST_BASE_URL || 'http://localhost:3000',
    graphqlEndpoint: process.env.GRAPHQL_ENDPOINT || 'http://localhost:4000/graphql',
    apiTimeout: 30000,
    retries: 0,
    workers: process.env.PLAYWRIGHT_WORKERS ? parseInt(process.env.PLAYWRIGHT_WORKERS) : 1,
  },
  development: {
    baseURL: process.env.PLAYWRIGHT_TEST_BASE_URL || 'http://localhost:3000',
    graphqlEndpoint: process.env.GRAPHQL_ENDPOINT || 'http://localhost:4000/graphql',
    apiTimeout: 30000,
    retries: 1,
    workers: process.env.PLAYWRIGHT_WORKERS ? parseInt(process.env.PLAYWRIGHT_WORKERS) : 2,
  },
  staging: {
    baseURL: process.env.PLAYWRIGHT_TEST_BASE_URL || 'https://staging.neotool.com',
    graphqlEndpoint: process.env.GRAPHQL_ENDPOINT || 'https://staging.neotool.com/graphql',
    apiTimeout: 60000,
    retries: 2,
    workers: process.env.PLAYWRIGHT_WORKERS ? parseInt(process.env.PLAYWRIGHT_WORKERS) : 2,
  },
  production: {
    baseURL: process.env.PLAYWRIGHT_TEST_BASE_URL || 'https://neotool.com',
    graphqlEndpoint: process.env.GRAPHQL_ENDPOINT || 'https://neotool.com/graphql',
    apiTimeout: 60000,
    retries: 2,
    workers: process.env.PLAYWRIGHT_WORKERS ? parseInt(process.env.PLAYWRIGHT_WORKERS) : 2,
  },
};

export const currentEnvironment = getEnvironment();
export const currentConfig = ENVIRONMENTS[currentEnvironment];
