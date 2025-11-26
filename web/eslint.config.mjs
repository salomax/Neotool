import js from "@eslint/js";
import tseslint from "typescript-eslint";
import testingLibrary from "eslint-plugin-testing-library";
import nextPlugin from "eslint-config-next/core-web-vitals";
import globals from "globals";

const config = [
  { ignores: ["node_modules/**", ".next/**", "dist/**", "coverage/**", "playwright-report/**", "test-results/**", "src/lib/graphql/types/__generated__/**", "**/*.generated.ts"] },

  // Base JS rules
  js.configs.recommended,

  // Next.js config (includes TypeScript ESLint)
  ...nextPlugin,

  {
    files: ['**/*.js', '**/*.cjs', '**/*.mjs'],
    languageOptions: {
      parserOptions: { project: null, sourceType: 'module', ecmaVersion: 'latest' },
    },
    rules: {
      // Disable TypeScript-specific rules for JS files
      '@typescript-eslint/no-var-requires': 'off',
      '@typescript-eslint/await-thenable': 'off',
      '@typescript-eslint/no-array-delete': 'off',
      '@typescript-eslint/no-unused-vars': 'off',
      '@typescript-eslint/no-base-to-string': 'off',
      '@typescript-eslint/no-duplicate-type-constituents': 'off',
      '@typescript-eslint/no-floating-promises': 'off',
      '@typescript-eslint/no-implied-eval': 'off',
      '@typescript-eslint/no-misused-promises': 'off',
      '@typescript-eslint/no-redundant-type-constituents': 'off',
    },
  },

  {
    files: ["**/__tests__/**/*.{ts,tsx}", "**/*.test.{ts,tsx}"],
    languageOptions: {
      globals: {
        ...globals.vitest,
      },
    },
    plugins: { "testing-library": testingLibrary },
    rules: {
      "testing-library/no-node-access": "error",
      // allow getByTestId; we intentionally avoid getByText to prevent i18n flakiness
    },
  },

  {
    files: ["**/*.stories.{ts,tsx}"],
    rules: {
      // Storybook stories commonly use hooks in render functions, which is acceptable
      "react-hooks/rules-of-hooks": "off",
    },
  },

  {
    rules: {
      "no-unused-vars": "off",
      // Note: @typescript-eslint/no-unused-vars is configured by nextPlugin
    },
  },
];

export default config;
