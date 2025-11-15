import { defineConfig } from "vitest/config";
import tsconfigPaths from "vite-tsconfig-paths";
import { fileURLToPath } from "node:url";

export default defineConfig({
  plugins: [tsconfigPaths({ projects: ["./tsconfig.vitest.json"] })],

  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },

  test: {
    include: [
      "src/**/__tests__/**/*.{test,spec}.?(c|m)[jt]s?(x)",
      "src/**/*.{test,spec}.?(c|m)[jt]s?(x)",
    ],
    exclude: [
      "tests/**",
      "e2e/**",
      "**/*.e2e.*",
      "node_modules",
      "dist",
      ".next",
      "playwright-report",
      "test-results",
    ],
    environment: "jsdom",
    setupFiles: ["./src/__tests__/setup.ts"],
    globals: true,
    css: true,
    restoreMocks: true,
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'json'],
      reportsDirectory: './coverage',
      include: ['src/**/*.{ts,tsx}'],
      exclude: [
        'src/**/*.d.ts',
        'src/**/*.test.{ts,tsx}',
        'src/**/*.spec.{ts,tsx}',
        'src/**/__tests__/**',
        'src/**/__mocks__/**',
        'src/**/stories/**',
        'src/**/*.stories.{ts,tsx}',
      ],
      thresholds: {
        global: {
          branches: 80,
          functions: 80,
          lines: 80,
          statements: 80,
        },
      },
    },
  },
});
