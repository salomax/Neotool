/**
 * GraphQL Code Generator configuration for generating TypeScript types and React hooks.
 * 
 * This file configures how GraphQL operations are converted into type-safe TypeScript code.
 * 
 * Apollo Client 4 Compatibility Notes:
 * - Apollo Client 4 recommends using its own hooks with TypedDocumentNode instead of
 *   generating hooks via @graphql-codegen/typescript-react-apollo.
 */
import type { CodegenConfig } from '@graphql-codegen/cli';

const GRAPHQL_ENDPOINT =
  process.env.NEXT_PUBLIC_GRAPHQL_URL ||
  process.env.VITE_GRAPHQL_HTTP ||
  'http://localhost:4000/graphql';

const SCHEMA_SOURCE =
  process.env.GRAPHQL_SCHEMA_PATH ||
  '../contracts/graphql/supergraph/supergraph.local.graphql';

const sharedTypeConfig = {
  defaultScalarType: 'unknown',
  nonOptionalTypename: true,
  skipTypeNameForRoot: true,
  avoidOptionals: { field: true, inputValue: false },
} as const;

const baseDocuments = [
  'src/**/*.{ts,tsx,graphql}',
  '!src/**/*.generated.ts',
  '!src/**/*.generated.tsx',
  '!src/**/*.generated*.ts',
  '!src/**/*.generated*.tsx',
  '!src/lib/graphql/types/__generated__/**',
  '!src/lib/graphql/fragments/**', // Exclude all fragment files - fragments are not used
];

const config: CodegenConfig = {
  overwrite: true,
  schema: SCHEMA_SOURCE || GRAPHQL_ENDPOINT,
  documents: baseDocuments,
  ignoreNoDocuments: true,
  generates: {
    'src/lib/graphql/types/__generated__/graphql.ts': {
      plugins: ['typescript'],
      config: {
        ...sharedTypeConfig,
        maybeValue: 'T | null',
      },
    },
    // Fragment generation removed - fragments are not used in this project
    // Each query/mutation must specify only the fields it needs inline
    'src/': {
      preset: 'near-operation-file',
      presetConfig: {
        baseTypesPath: 'lib/graphql/types/__generated__/graphql.ts',
        extension: '.generated.ts',
      },
      plugins: ['typescript-operations', 'typed-document-node'],
      config: {
        ...sharedTypeConfig,
        addDocBlocks: true,
      },
    },
  },
};

export default config;
