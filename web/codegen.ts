// GraphQL Code Generator configuration for generating TypeScript types and React hooks
// This file configures how GraphQL operations are converted into type-safe TypeScript code
import type { CodegenConfig } from '@graphql-codegen/cli';

// GraphQL endpoint configuration with fallback chain
const GRAPHQL_ENDPOINT =
  process.env.NEXT_PUBLIC_GRAPHQL_URL ||
  process.env.VITE_GRAPHQL_HTTP ||
  'http://localhost:4000/graphql'; // Apollo Router (supergraph)

// Use supergraph schema file if available, otherwise fall back to endpoint
const SCHEMA_SOURCE = process.env.GRAPHQL_SCHEMA_PATH || 
  '../contracts/graphql/supergraph/supergraph.local.graphql';

const config: CodegenConfig = {
  // Overwrite existing generated files instead of merging
  overwrite: true,
  
  // GraphQL schema source - can be URL, file path, or introspection result
  schema: SCHEMA_SOURCE,
  
  // Glob patterns to find GraphQL operations (queries, mutations, subscriptions)
  // Scans all .ts and .tsx files in src/ directory for gql`` template literals
  // Exclude generated files to avoid recursive generation (file.generated.ts, file.generated.generated.ts, etc.)
  // Exclude any file containing '.generated' in its name to prevent recursive generation
  documents: [
    'src/**/*.{ts,tsx}',
    '!src/**/*.generated.ts',
    '!src/**/*.generated.tsx',
    '!src/**/*.generated*.ts',
    '!src/**/*.generated*.tsx',
    '!src/lib/graphql/types/__generated__/**',
  ],
  
  // Don't fail if no GraphQL documents are found (useful for incremental builds)
  ignoreNoDocuments: true,
  
  // Output generation configuration - defines where and how files are generated
  generates: {
    // Base types generated once - shared across all operations
    // Contains core GraphQL types, scalars, and schema definitions
    'src/lib/graphql/types/__generated__/graphql.ts': {
      // Generate TypeScript types from GraphQL schema
      plugins: ['typescript'],
      config: {
        // Use 'unknown' for scalar types instead of 'any' for better type safety
        defaultScalarType: 'unknown',
        // Nullable fields use 'T | null' instead of 'T | null | undefined'
        maybeValue: 'T | null',
        // Always include __typename field in generated types
        nonOptionalTypename: true,
        // Don't include __typename in root query/mutation types
        skipTypeNameForRoot: true,
        // Make fields optional only when necessary, not inputs
        avoidOptionals: { field: true, inputValue: false },
      },
    },
    
    // Near-operation-file preset - generates files next to GraphQL operations
    // Each .ts/.tsx file with GraphQL gets a corresponding .generated.ts file
    'src/': {
      // Preset that generates files adjacent to source files
      preset: 'near-operation-file',
      presetConfig: {
        // Path to base types file (relative to generated files)
        baseTypesPath: 'lib/graphql/types/__generated__/graphql.ts',
        // File extension for generated files
        extension: '.generated.ts',
      },
      plugins: [
        // Generate TypeScript types for operations (queries, mutations, etc.)
        'typescript-operations',
        // Generate React Apollo hooks (useQuery, useMutation, etc.)
        'typescript-react-apollo',
      ],
      config: {
        // Generate React hooks for all operations
        withHooks: true,
        
        // Use Apollo Client v4 hooks API
        reactApolloVersion: 4,
        
        // Import React hooks from the correct module (@apollo/client/react)
        // This fixes "useQuery is not a function" errors by ensuring hooks
        // are imported from the module that actually exports them
        apolloReactHooksImportFrom: '@apollo/client/react',
        
        // Import common types from @apollo/client/react (v4 types are in react package)
        // Note: This should import MutationResult and QueryResult from @apollo/client/react
        apolloReactCommonImportFrom: '@apollo/client/react',
        
        // Don't generate MutationFunction - it doesn't exist in Apollo Client v4
        withMutationFn: false,
        
        // Don't generate component wrappers
        withComponent: false,
        
        // Add documentation blocks
        addDocBlocks: true,
        
        // Type generation settings (same as base types for consistency)
        defaultScalarType: 'unknown',
        nonOptionalTypename: true,
        skipTypeNameForRoot: true,
        avoidOptionals: { field: true, inputValue: false },
      },
    },
  },
};

export default config;
