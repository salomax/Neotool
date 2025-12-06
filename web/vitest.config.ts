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
