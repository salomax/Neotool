const path = require("path");

// ADR-0014 follow-up: lint/typecheck resolve tooling from mobile/node_modules today.
// Give this lib own devDependencies or hoist shared tooling to the workspace root
// before web also consumes @invistus/shared-graphql.
const mobileNodeModules = path.resolve(__dirname, "../../mobile/node_modules");

function requireFromMobile(moduleName) {
  return require(require.resolve(moduleName, { paths: [mobileNodeModules] }));
}

const { defineConfig, globalIgnores } = requireFromMobile("eslint/config");
const tsParser = requireFromMobile("@typescript-eslint/parser");
const tsPlugin = requireFromMobile("@typescript-eslint/eslint-plugin");
const eslintPluginPrettierRecommended = requireFromMobile(
  "eslint-plugin-prettier/recommended",
);

module.exports = defineConfig([
  globalIgnores(["src/generated/**"]),
  eslintPluginPrettierRecommended,
  {
    files: ["**/*.ts", "**/*.tsx"],
    languageOptions: {
      parser: tsParser,
      parserOptions: {
        ecmaVersion: "latest",
        sourceType: "module",
      },
    },
    plugins: {
      "@typescript-eslint": tsPlugin,
    },
    rules: {
      ...tsPlugin.configs.recommended.rules,
      "prettier/prettier": "error",
      "@typescript-eslint/no-unused-vars": [
        "error",
        { argsIgnorePattern: "^_", varsIgnorePattern: "^_" },
      ],
      "no-console": ["warn", { allow: ["warn", "error"] }],
    },
  },
]);
