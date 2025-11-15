// eslint.config.mjs - flat config
// Requires: @eslint/js, typescript-eslint, eslint-plugin-react-hooks, eslint-plugin-testing-library, eslint-plugin-unused-imports
import js from "@eslint/js";
import tseslint from "typescript-eslint";
import testingLibrary from "eslint-plugin-testing-library";

export default [
  { ignores: ["node_modules/**", ".next/**", "dist/**", "coverage/**", "playwright-report/**", "test-results/**"] },

  // Base JS rules
  js.configs.recommended,

  // TypeScript (type-checked)
  ...tseslint.configs.recommendedTypeChecked.map((cfg) => ({
    ...cfg,
    languageOptions: {
      ...cfg.languageOptions,
      parserOptions: {
        ...cfg.languageOptions?.parserOptions,
        project: ["./tsconfig.json"],
        tsconfigRootDir: import.meta.dirname,
      },
    },
  })),

  {
    files: ['**/*.js', '**/*.cjs', '**/*.mjs'],
    languageOptions: {
      parserOptions: { project: null, sourceType: 'module', ecmaVersion: 'latest' },
    },
    rules: {
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
    plugins: { "testing-library": testingLibrary },
    rules: {
      "testing-library/no-node-access": "error",
      // allow getByTestId; we intentionally avoid getByText to prevent i18n flakiness
    },
  },

  {
    rules: {
      "no-unused-vars": "off",
      "@typescript-eslint/no-unused-vars": ["warn", { "argsIgnorePattern": "^_", "varsIgnorePattern": "^_" }],
    },
  },
];
