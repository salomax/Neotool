const { defineConfig, globalIgnores } = require('eslint/config');
const expoConfig = require('eslint-config-expo/flat');
const eslintPluginPrettierRecommended = require('eslint-plugin-prettier/recommended');
const tsPlugin = require('@typescript-eslint/eslint-plugin');
const globals = require('globals');

module.exports = defineConfig([
  globalIgnores([
    'node_modules/**',
    '.expo/**',
    'dist/**',
    'build/**',
    'src/generated/**',
    'coverage/**',
  ]),
  expoConfig,
  eslintPluginPrettierRecommended,
  {
    plugins: {
      '@typescript-eslint': tsPlugin,
    },
    rules: {
      'prettier/prettier': 'error',
      '@typescript-eslint/no-unused-vars': [
        'error',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_' },
      ],
      'no-console': ['warn', { allow: ['warn', 'error'] }],
    },
  },
  {
    files: ['jest.setup.js', 'jest.config.js', 'babel.config.js', 'metro.config.js'],
    languageOptions: {
      globals: {
        ...globals.node,
        ...globals.jest,
      },
    },
  },
  {
    files: ['**/__tests__/**/*.{ts,tsx}', '**/*.test.{ts,tsx}'],
    languageOptions: {
      globals: {
        ...globals.jest,
      },
    },
    rules: {
      '@typescript-eslint/no-require-imports': 'off',
      'react/display-name': 'off',
    },
  },
  {
    files: [
      'src/shared/config/i18n.ts',
      'src/shared/i18n/hooks/useTranslation.ts',
      'src/shared/providers/ThemeProvider.tsx',
    ],
    rules: {
      'import/no-named-as-default-member': 'off',
      'react-hooks/exhaustive-deps': 'off',
    },
  },
]);
