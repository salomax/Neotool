/**
 * Environment configuration
 * Access environment variables through this module
 */

export const env = {
  graphqlEndpoint: process.env.EXPO_PUBLIC_GRAPHQL_ENDPOINT || 'http://localhost:4000/graphql',
  projectId: process.env.EXPO_PUBLIC_PROJECT_ID || '',
  isDev: __DEV__,
} as const;
