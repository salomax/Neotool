import type { CodegenConfig } from '@graphql-codegen/cli';

const config: CodegenConfig = {
  schema: '../contracts/graphql/supergraph/supergraph.local.graphql',
  documents: ['../libs/shared-graphql/src/**/*.{ts,tsx}'],
  generates: {
    '../libs/shared-graphql/src/generated/': {
      preset: 'client',
      plugins: [],
      presetConfig: {
        gqlTagName: 'graphql',
      },
    },
  },
  ignoreNoDocuments: true,
};

export default config;
