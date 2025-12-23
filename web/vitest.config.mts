import { defineConfig } from "vitest/config";
import tsconfigPaths from "vite-tsconfig-paths";
import { fileURLToPath } from "node:url";
import os from "node:os";

// Determine if we're in CI environment
const isCI = Boolean(process.env.CI);
const isSharding = Boolean(process.env.VITEST_SHARD);

// Calculate optimal thread count based on CPU cores
// Best practice: Use CPU cores - 1 to leave one core for the main process
// For CI, use a conservative number (4-8) to prevent resource exhaustion
// For local, use CPU-based calculation for optimal performance
const getOptimalThreadCount = (): number => {
  if (isCI) {
    // CI environments: Use conservative thread count to prevent resource exhaustion
    // Most CI runners have 2-4 cores, so we cap at 4 for safety
    return Math.min(4, os.cpus().length - 1 || 1);
  }
  // Local development: Use CPU cores - 1 for optimal performance
  // Leave one core for the main process and system tasks
  return Math.max(1, os.cpus().length - 1);
};

// Configure reporters based on environment
// - CI with sharding: blob + junit (for merging shard results + CI integration)
// - CI without sharding: default + junit (for CI integration)
// - Local: default (verbose output for development)
const getReporters = () => {
  if (isCI && isSharding) {
    return ['blob', 'junit', 'default'];
  }
  if (isCI) {
    return ['junit', 'default'];
  }
  return ['default'];
};

export default defineConfig({
  plugins: [tsconfigPaths({ projects: ["./tsconfig.vitest.json"] })],

  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },

  test: {
    // Test file discovery
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

    // Environment configuration
    environment: "jsdom",
    setupFiles: ["./src/__tests__/setup.ts"],
    globals: true,
    css: true,

    // Mock and isolation configuration
    restoreMocks: true,
    clearMocks: true,
    mockReset: true,

    // Performance optimizations
    // Use thread pool for parallel test execution
    // Thread count is dynamically calculated based on CPU cores and environment
    // - Local: CPU cores - 1 (optimal performance)
    // - CI: Conservative limit (4 threads max) to prevent resource exhaustion
    // Isolation is enabled to prevent test interference and memory leaks
    pool: "threads",
    poolOptions: {
      threads: {
        // Minimum threads: always have at least 1 thread available
        minThreads: 1,
        // Maximum threads: dynamically calculated based on environment and CPU cores
        // This balances performance with memory usage
        // With proper cleanup in setup.ts, multiple threads are safe
        maxThreads: getOptimalThreadCount(),
        // Enable isolation to prevent test interference
        // Each test file runs in its own isolated context
        isolate: true,
        // Use single worker for better memory isolation when memory is constrained
        // Can be overridden via VITEST_SINGLE_THREAD environment variable
        singleThread: Boolean(process.env.VITEST_SINGLE_THREAD),
      },
    },

    // Timeout configuration
    testTimeout: 30000,
    hookTimeout: 30000,
    teardownTimeout: 10000,

    // Parallelism and sharding
    // Sharding support: enable file parallelism when not sharding
    // When sharding, Vitest handles file distribution automatically
    // When not sharding, enable file parallelism for better performance
    fileParallelism: !isSharding,
    // Limit concurrent tests per thread to prevent memory exhaustion
    // With multiple threads, this controls how many tests run concurrently in each thread
    // Lower value = less memory per thread, higher value = faster execution
    // Adjusted based on thread count: more threads = lower concurrency per thread
    maxConcurrency: isCI ? 5 : 10,

    // CI/CD optimizations
    // Bail on first failure in CI for faster feedback
    // bail: isCI ? 1 : 0,
    // Fail if no tests are found (prevents silent failures)
    passWithNoTests: false,

    // Reporter configuration
    // Blob reporter generates reports that can be merged across shards
    // JUnit reporter provides XML output for CI integration
    reporters: getReporters(),

    // JUnit reporter configuration for CI integration
    outputFile: isCI
      ? {
          junit: "./test-results/junit.xml",
        }
      : undefined,

    // Test execution configuration
    // Retry flaky tests (only in CI to catch intermittent failures)
    retry: isCI ? 2 : 0,
    // Run tests in a deterministic order for better reproducibility
    sequence: {
      shuffle: false,
      concurrent: true,
    },

    // Output configuration
    // Show heap usage in verbose mode (useful for memory debugging)
    logHeapUsage: false,
    // Silent mode for CI (reduce noise)
    silent: false,

    // Watch mode configuration
    watchExclude: [
      "**/node_modules/**",
      "**/dist/**",
      "**/.next/**",
      "**/coverage/**",
    ],

    // Force rerun triggers (invalidate cache when these change)
    forceRerunTriggers: [
      "**/package.json",
      "**/vitest.config.*",
      "**/tsconfig*.json",
    ],
    // Coverage configuration
    coverage: {
      provider: 'v8',
      // Coverage reporters: text for console, html for detailed view, json for CI, lcov for coverage services
      reporter: isCI
        ? ['text', 'json', 'lcov', 'html']
        : ['text', 'html', 'json'],
      reportsDirectory: './coverage',
      // Clean coverage directory before running (prevents stale data)
      clean: true,
      // Clean coverage on rerun in watch mode
      cleanOnRerun: true,
      // Include all files by default, then exclude specific patterns
      all: true,
      // Skip full coverage report if below threshold (faster execution)
      skipFull: false,
      // Files to include in coverage
      include: ['src/**/*.{ts,tsx}'],
      // Files to exclude from coverage
      exclude: [
        'src/**/*.d.ts',
        'src/**/*.test.{ts,tsx}',
        'src/**/*.spec.{ts,tsx}',
        'src/**/__tests__/**',
        'src/**/__mocks__/**',
        'src/**/stories/**',
        'src/**/*.stories.{ts,tsx}',
        // Exclude examples and documentation
        'src/app/(neotool)/**',
        // Exclude GraphQL schema and instrumentation components
        'src/lib/graphql/**',
        // Exclude barrel/index files (pure exports)
        'src/**/index.ts',
        // Exclude Next.js boilerplate (better tested via E2E)
        'src/middleware.ts',
        'src/app/layout.tsx',
        'src/app/page.tsx',
        'src/app/not-found.tsx',
        'src/app/providers.tsx',
        'src/app/**/page.tsx',
        'src/app/**/route.ts',
        // Exclude configuration files
        'src/shared/config/**',
        // Exclude SEO metadata configuration (Next.js boilerplate)
        'src/shared/seo/metadata.ts',
        // Exclude Storybook and development files
        'src/shared/i18n/storybook.ts',
        'src/shared/i18n/client.ts',
        // Exclude simple provider wrappers
        'src/lib/api/AppQueryProvider.tsx',
        'src/shared/providers/ToastProvider.tsx',
        // Exclude theme provider wrappers (thin wrappers around MUI)
        'src/styles/themes/ThemeRegistry.tsx',
        // Exclude Sentry initialization (thin wrapper around well-tested library)
        'src/shared/sentry/sentry.tsx',
        // Exclude demo/example stores (minimal logic, not used in production)
        'src/shared/store/counter.ts',
        // Exclude thin wrapper components (well-tested libraries)
        'src/shared/components/ErrorBoundary.tsx',
        'src/shared/components/LazyWrapper.tsx',
        // Exclude data-display components (thin wrappers around well-tested libraries)
        'src/shared/components/ui/data-display/Chart.tsx',
        'src/shared/components/ui/data-display/DataTable.tsx',
        'src/shared/components/ui/data-display/DataTableSkeleton.tsx',
        // Exclude data-table utility functions (thin wrappers around browser APIs)
        'src/shared/components/ui/data-table/components/enterprise/presets.ts',
        // Exclude feedback components (thin wrappers and pure presentational components)
        'src/shared/components/ui/feedback/dialogs/ConfirmDialog.tsx',
        'src/shared/components/ui/feedback/dialogs/ConfirmationDialog.tsx',
        'src/shared/components/ui/feedback/states/EmptyErrorState.tsx',
        'src/shared/components/ui/feedback/tooltip/Tooltip.tsx',
        'src/shared/components/ui/feedback/toast/ToastProvider.tsx',
        // Exclude Chat components (integration components better tested via E2E)
        'src/shared/components/ui/feedback/Chat/Chat.tsx',
        'src/shared/components/ui/feedback/Chat/ChatDrawer.tsx',
        'src/shared/components/ui/feedback/Chat/ChatMessage.tsx',
        // Exclude form layout components (pure presentational)
        'src/shared/components/ui/forms/FormSkeleton.tsx',
        'src/shared/components/ui/forms/components/FormActions.tsx',
        'src/shared/components/ui/forms/components/FormErrorBanner.tsx',
        'src/shared/components/ui/forms/components/FormLayout.tsx',
        'src/shared/components/ui/forms/components/FormRow.tsx',
        'src/shared/components/ui/forms/components/FormSection.tsx',
        'src/shared/components/ui/forms/SearchFilters.tsx',
        // Exclude controlled field components (thin wrappers around react-hook-form and MUI)
        'src/shared/components/ui/forms/components/fields/ControlledAsyncValidateField.tsx',
        'src/shared/components/ui/forms/components/fields/ControlledAutocomplete.tsx',
        'src/shared/components/ui/forms/components/fields/ControlledCheckbox.tsx',
        'src/shared/components/ui/forms/components/fields/ControlledCurrencyField.tsx',
        'src/shared/components/ui/forms/components/fields/ControlledDatePicker.tsx',
        'src/shared/components/ui/forms/components/fields/ControlledFileUpload.tsx',
        'src/shared/components/ui/forms/components/fields/ControlledMaskedTextField.tsx',
        'src/shared/components/ui/forms/components/fields/ControlledNumberField.tsx',
        'src/shared/components/ui/forms/components/fields/ControlledPercentField.tsx',
        'src/shared/components/ui/forms/components/fields/ControlledRadioGroup.tsx',
        'src/shared/components/ui/forms/components/fields/ControlledSelect.tsx',
        'src/shared/components/ui/forms/components/fields/ControlledSpecificMaskedFields.tsx',
        'src/shared/components/ui/forms/components/fields/ControlledSwitch.tsx',
        'src/shared/components/ui/forms/components/fields/ControlledTextField.tsx',
        // Exclude complex form components (better tested via E2E or thin wrappers with minimal logic)
        'src/shared/components/ui/forms/RichTextEditor.tsx', // Complex DOM manipulation, better for E2E
        'src/shared/components/ui/forms/SearchField.tsx', // Thin wrapper around MUI with simple debouncing
        // Exclude form field components (thin wrappers around react-hook-form and MUI with integration logic)
        'src/shared/components/ui/forms/form/AsyncAutocomplete.tsx', // Thin wrapper with standard async/debouncing patterns
        'src/shared/components/ui/forms/form/AutocompleteField.tsx', // Thin wrapper around MUI Autocomplete
        // Exclude thin wrapper form field components (react-hook-form Controller wrappers with minimal logic)
        'src/shared/components/ui/forms/form/DatePickers.tsx', // Thin wrapper around MUI DatePicker with dayjs conversion
        'src/shared/components/ui/forms/form/Form.tsx', // Pure wrapper around useForm/FormProvider
        'src/shared/components/ui/forms/form/FormField.tsx', // Thin wrapper around Controller + TextField
        'src/shared/components/ui/forms/form/FormMessage.tsx', // Pure presentational Alert component
        'src/shared/components/ui/forms/form/MaskedField.tsx', // Thin wrapper around InputMask + Controller
        'src/shared/components/ui/forms/form/RadioGroupField.tsx', // Thin wrapper around RadioGroup + Controller
        'src/shared/components/ui/forms/form/SelectField.tsx', // Thin wrapper around TextField select + Controller
        'src/shared/components/ui/forms/form/ToggleField.tsx', // Thin wrapper around Switch + Controller
        // Exclude layout components (pure presentational components with minimal logic)
        'src/shared/components/ui/layout/Cluster.tsx', // Pure presentational flexbox layout component
        'src/shared/components/ui/layout/Cover.tsx', // Pure presentational flexbox layout component
        'src/shared/components/ui/layout/Drawer.tsx', // Thin wrapper around MUI Drawer with minimal mobile detection
        'src/shared/components/ui/layout/Frame.tsx', // Pure presentational layout component
        'src/shared/components/ui/layout/Grid.tsx', // Pure presentational grid layout component
        'src/shared/components/ui/layout/Inline.tsx', // Pure presentational flexbox layout component
        'src/shared/components/ui/layout/PageHeader.tsx', // Pure presentational component
        'src/shared/components/ui/layout/PageLayout.tsx', // Mostly presentational with minimal conditional rendering
        'src/shared/components/ui/layout/Paper.tsx', // Thin wrapper around MUI Paper with theme padding
        'src/shared/components/ui/layout/Reel.tsx', // Pure presentational layout component
        'src/shared/components/ui/layout/Stack.tsx', // Pure presentational flexbox layout component
        'src/shared/components/ui/layout/utils.ts', // Simple style transformation utilities (tested indirectly)
        // Exclude brand components (pure presentational components)
        'src/shared/ui/brand/LogoMark.tsx', // Pure presentational component, thin wrapper around Next.js Image
        // Exclude card components (pure presentational components)
        'src/shared/ui/cards/StatCard.tsx', // Pure presentational component, thin wrapper around MUI Card
        // Exclude shell components (pure presentational layout components)
        'src/shared/ui/shell/AppShell.tsx', // Pure presentational layout component, just composition
        // Exclude primitive components (thin wrappers and pure presentational components)
        'src/shared/components/ui/primitives/Badge.tsx', // Thin wrapper around MUI Chip with color mapping
        'src/shared/components/ui/primitives/Chip.tsx', // Thin wrapper around MUI Chip with size mapping
        'src/shared/components/ui/primitives/IconButton.tsx', // Thin wrapper around MUI IconButton with size styling
        'src/shared/components/ui/primitives/LoadingSpinner.tsx', // Pure presentational loading component
        'src/shared/components/ui/primitives/PageSkeleton.tsx', // Pure presentational skeleton loader
        'src/shared/components/ui/primitives/Select.tsx', // Thin wrapper around MUI Select with minimal normalization
        'src/shared/components/ui/primitives/Skeleton.tsx', // Mostly presentational skeleton variants
        'src/shared/components/ui/primitives/Switcher.tsx', // Pure layout component with CSS grid logic
        // Exclude complex primitive components (better tested via E2E due to complex DOM interactions)
        'src/shared/components/ui/primitives/ColorPicker.tsx', // Complex color conversion logic and DOM manipulation
        'src/shared/components/ui/primitives/DateRangePicker.tsx', // Complex date range logic and calendar interactions
        'src/shared/components/ui/primitives/DateTimePicker.tsx', // Thin wrapper with format logic, better for E2E
        'src/shared/components/ui/primitives/ImageUpload.tsx', // Complex file validation, compression, and drag-and-drop
      ],
      // Coverage thresholds
      // Enforce minimum coverage requirements
      thresholds: {
        global: {
          branches: 80,
          functions: 80,
          lines: 80,
          statements: 80,
        },
        // Per-file thresholds (stricter for critical paths)
        // Can be extended with specific file patterns if needed
        // Example:
        // 'src/shared/hooks/**': {
        //   branches: 90,
        //   functions: 90,
        //   lines: 90,
        //   statements: 90,
        // },
      },
    },
  },
});
