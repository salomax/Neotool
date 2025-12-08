/**
 * GraphQL Code Generator configuration for generating TypeScript types and React hooks.
 * 
 * This file configures how GraphQL operations are converted into type-safe TypeScript code.
 * 
 * Apollo Client 4 Compatibility Notes:
 * - The @graphql-codegen/typescript-react-apollo plugin (v4.3.3) has some compatibility
 *   issues with Apollo Client 4 that are fixed in the post-processing script:
 *   - BaseMutationOptions types are generated but don't exist in Apollo Client 4
 *   - useSuspenseQuery has type mismatches when queries have required variables
 * 
 * These issues are handled by scripts/fix-generated-types.mjs which runs after codegen.
 * 
 * See scripts/fix-generated-types.mjs for detailed documentation of the fixes.
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
  '!src/lib/graphql/fragments/common.graphql',
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
    'src/lib/graphql/fragments/common.generated.ts': {
      documents: ['src/lib/graphql/fragments/common.graphql'],
      plugins: ['typescript', 'typescript-operations'],
      config: {
        ...sharedTypeConfig,
        importOperationTypesFrom: 'Types',
      },
    },
    'src/': {
      preset: 'near-operation-file',
      presetConfig: {
        baseTypesPath: 'lib/graphql/types/__generated__/graphql.ts',
        extension: '.generated.ts',
      },
      plugins: ['typescript-operations', 'typescript-react-apollo'],
      config: {
        ...sharedTypeConfig,
        withHooks: true,
        reactApolloVersion: 4, // Apollo Client 4 compatibility
        apolloReactHooksImportFrom: '@apollo/client/react',
        apolloReactCommonImportFrom: '@apollo/client/react',
        withMutationFn: false,
        withComponent: false,
        addDocBlocks: true,
        // Note: skipExportingMutationOptions could be set to true to prevent
        // BaseMutationOptions generation, but we handle it in post-processing
        // for better visibility and control. See scripts/fix-generated-types.mjs
      },
    },
  },
};

export default config;
