---
title: E2E Testing Pattern
type: pattern
category: frontend
status: current
version: 1.0.0
tags: [testing, e2e, playwright, frontend, end-to-end]
ai_optimized: true
search_keywords: [testing, e2e, playwright, end-to-end, browser-testing, integration-testing]
related:
  - 04-patterns/frontend-patterns/testing-pattern.md
  - 05-standards/testing-standards/unit-test-standards.md
  - 06-workflows/testing-workflow.md
---

# E2E Testing Pattern

> **Purpose**: Standard patterns and practices for end-to-end (E2E) testing with Playwright in the NeoTool web application.

## Overview

E2E tests verify that the application works correctly from a user's perspective by testing complete user flows and interactions. These tests run in real browsers and interact with the application as a real user would.

## Setup

### Prerequisites

Before running E2E tests, you must install Playwright browsers and their dependencies:

```bash
pnpm exec playwright install --with-deps
```

This command:
- Downloads browser binaries (Chromium, Firefox, WebKit) required for testing
- Installs system dependencies needed for browser execution
- Is required for both local development and CI environments

**Important**: Without installing Playwright browsers, tests will fail with errors like "browser not found" or "executable doesn't exist".

**Note**: This is typically done automatically in CI (see `.github/workflows/ci.yml`), but must be run manually when setting up a new development environment or after cloning the repository.

### First-Time Setup

1. Install project dependencies:
   ```bash
   pnpm install
   ```

2. Install Playwright browsers:
   ```bash
   pnpm exec playwright install --with-deps
   ```

3. Build and start the application (required for optimal performance):
   ```bash
   pnpm build && pnpm start
   ```
   
   **Important**: Always use the compiled version (`pnpm build && pnpm start`) for E2E tests to ensure better performance. See [Performance: Use Compiled Version](#performance-use-compiled-version) section for details.

4. Run tests:
   ```bash
   pnpm test:e2e
   ```

## Test Structure

Tests follow the Page Object Model (POM) pattern for maintainability and reusability:

```
tests/e2e/
├── config/              # Test configuration files
│   ├── constants.ts     # Test constants (timeouts, selectors, etc.)
│   └── environments.ts # Environment-specific configurations
├── fixtures/            # Test data fixtures
│   ├── users.ts        # User fixtures and helpers
│   ├── roles.ts        # Role fixtures and helpers
│   ├── groups.ts       # Group fixtures and helpers
│   └── test-context.ts # Playwright test fixtures
├── helpers/            # Test helper functions
│   ├── auth.ts         # Authentication helpers
│   ├── graphql.ts      # GraphQL operation helpers
│   ├── navigation.ts   # Navigation helpers
│   ├── test-data.ts    # Test data management
│   └── assertions.ts   # Custom assertions
├── pages/              # Page Object Models
│   ├── SettingsPage.ts
│   ├── UserManagementPage.ts
│   ├── UserDrawer.ts
│   └── UserSearch.ts
├── global-setup.ts      # Global test setup
├── global-teardown.ts  # Global test teardown
└── *.e2e.spec.ts       # Test specification files
```

## Running Tests

### Performance: Use Compiled Version

**IMPORTANT**: For better performance, E2E tests must run against a compiled version of the application. Always build and start the application before running tests:

```bash
pnpm build && pnpm start
```

**Rationale**:
- Compiled production builds are optimized and faster
- Development mode includes additional overhead (hot reload, source maps, etc.)
- E2E tests should run against production-like builds for accurate performance testing
- Reduces test execution time and improves reliability

**Note**: If using Playwright's `webServer` configuration, ensure it uses the compiled build, not the development server.

### Run all E2E tests

```bash
pnpm test:e2e
```

### Run tests in UI mode (interactive)

```bash
pnpm test:e2e:ui
```

### Run tests in debug mode

```bash
pnpm test:e2e:debug
```

### Run specific test file

```bash
pnpm exec playwright test tests/e2e/signin.e2e.spec.ts
```

### Run tests in a specific browser

```bash
pnpm exec playwright test --project=chromium
```

### Run tests in headed mode (see browser)

```bash
pnpm exec playwright test --headed
```

### Run tests matching a pattern

```bash
pnpm exec playwright test --grep "Can access the web app"
```

## Writing Tests

### Pattern: Page Object Model (POM)

**Rule**: Always use Page Objects to encapsulate page-specific logic and interactions.

**Rationale**:
- Reduces code duplication
- Makes tests more maintainable
- Provides a clear abstraction layer
- Easier to update when UI changes

**Example**:

```typescript
// Page Object
export class SignInPage {
  constructor(private page: Page) {}

  async goto() {
    await this.page.goto('/signin', { waitUntil: 'domcontentloaded' });
    await this.page.waitForSelector('[data-testid="signin-screen"]', { 
      timeout: 20000,
      state: 'visible'
    });
  }

  async fillEmail(email: string) {
    const emailField = this.page.locator('[data-testid="textfield-email"]');
    await emailField.waitFor({ state: 'visible' });
    await emailField.fill(email);
  }

  async fillPassword(password: string) {
    const passwordField = this.page.locator('[data-testid="textfield-password"]');
    await passwordField.waitFor({ state: 'visible' });
    await passwordField.fill(password);
  }

  async clickSignIn() {
    const signInButton = this.page.locator('[data-testid="button-signin"]');
    await signInButton.waitFor({ state: 'visible' });
    await signInButton.click();
  }
}

// Test
test('should sign in successfully', async ({ page }) => {
  const signInPage = new SignInPage(page);
  await signInPage.goto();
  await signInPage.fillEmail('test@example.com');
  await signInPage.fillPassword('password123');
  await signInPage.clickSignIn();
  
  await expect(page).toHaveURL('/');
});
```

### Pattern: Test Helpers

**Rule**: Create reusable helper functions for common operations like authentication, navigation, and assertions.

**Example**:

```typescript
// helpers/auth.ts
export async function signInAsValidUser(page: Page, rememberMe = false) {
  const signInPage = new SignInPage(page);
  await signInPage.goto();
  await signInPage.signIn(
    TEST_USERS.valid.email,
    TEST_USERS.valid.password,
    rememberMe
  );
  await page.waitForURL('/', { timeout: 5000 });
}

// Test
test('should display user list', async ({ page }) => {
  await signInAsValidUser(page);
  const userManagementPage = new UserManagementPage(page);
  await userManagementPage.goto();
  await userManagementPage.waitForUserList();
});
```

### Pattern: Test Fixtures

**Rule**: Use test fixtures for reusable test data and setup.

**Example**:

```typescript
// fixtures/users.ts
export const TEST_USERS = {
  valid: {
    email: 'test@example.com',
    password: 'TestPassword123!',
  },
  invalid: {
    email: 'invalid@example.com',
    password: 'WrongPassword',
  },
};

export async function createUniqueTestUser(prefix: string, displayName: string) {
  // Create test user via GraphQL
  // ...
}

// Test
test('should create and display user', async ({ page }) => {
  const testUser = await createUniqueTestUser('test-user', 'Test User');
  // Use testUser in your test...
});
```

### Pattern: Given-When-Then Structure

**Rule**: Structure tests using the Given-When-Then pattern for clarity.

**Example**:

```typescript
test('should enable user', async ({ page }) => {
  // Given I am signed in
  await signInAsValidUser(page);
  
  // When I toggle user status
  const userManagementPage = new UserManagementPage(page);
  await userManagementPage.goto();
  await userManagementPage.toggleUserStatus(userId);
  
  // Then the user should be enabled
  const status = await userManagementPage.getUserStatus(userId);
  expect(status).toBe(true);
});
```

## Best Practices

### 1. Use Page Object Model

Always use Page Objects instead of directly interacting with the page:

```typescript
// ✅ Good
await userManagementPage.search.search('query');

// ❌ Bad
await page.fill('[data-testid="user-search"] input', 'query');
```

### 2. Use data-testid Attributes

Always use `data-testid` for element selection:

```typescript
// ✅ Good
await page.click('[data-testid="edit-user-123"]');

// ❌ Bad
await page.click('text=Edit');
```

**Rationale**:
- Text content can change (i18n, design updates)
- CSS selectors are brittle and tied to implementation
- Test IDs provide stable, semantic identifiers

### 3. Wait for Elements

Always wait for elements to be ready before interacting:

```typescript
// ✅ Good
await userManagementPage.waitForUserList();
await emailField.waitFor({ state: 'visible' });

// ❌ Bad
await page.click('button'); // Might click before element is ready
```

### 4. Use Descriptive Test Names

Test names should clearly describe what is being tested:

```typescript
// ✅ Good
test('should display user list when navigating to User Management');

// ❌ Bad
test('user list test');
```

### 5. Isolate Test Data

Each test should use isolated test data to avoid conflicts:

```typescript
// ✅ Good
const testUser = await createUniqueTestUser('test-user');

// ❌ Bad
// Using hardcoded test user that might conflict with other tests
```

### 6. Clean Up After Tests

Always clean up test data after tests:

```typescript
test.afterEach(async () => {
  await cleanupTestData({ userIds: [testUser.id] });
});
```

### 7. Handle Async Operations Properly

Wait for async operations to complete:

```typescript
// ✅ Good
await page.waitForURL('/', { timeout: 10000 });
await page.waitForLoadState('networkidle');

// ❌ Bad
await page.goto('/');
// Immediately checking something that might not be ready
```

## Component-Specific Interaction Patterns

These patterns were discovered through practical E2E testing and should be followed to ensure reliable test interactions.

### TextField Inputs

**Rule**: Always target the `input` element inside TextField components using `data-testid`.

**Rationale**: TextField components wrap the actual input element. Targeting the input directly ensures reliable interaction.

```typescript
// ✅ Good
const emailInput = page.locator('[data-testid="textfield-email"] input');
await emailInput.fill('test@example.com');

// ❌ Bad
const emailField = page.locator('[data-testid="textfield-email"]');
await emailField.fill('test@example.com'); // May not work reliably
```

### Switch Components

**Rule**: Check the `input[type="checkbox"]` element inside Switch components, not the Switch wrapper.

**Rationale**: Switch components render a checkbox input internally. Checking the wrapper may not accurately reflect the actual state.

```typescript
// ✅ Good
const switchInput = page.locator('[data-testid="switch-enabled"] input[type="checkbox"]');
await expect(switchInput).toBeChecked();

// ❌ Bad
const switchComponent = page.locator('[data-testid="switch-enabled"]');
await expect(switchComponent).toBeChecked(); // May not work correctly
```

### Empty States

**Rule**: Add unique `data-testid` attributes to empty state elements and exclude them from counts.

**Rationale**: Empty states are part of the UI but should not be counted as data items. Unique test IDs help distinguish them.

```typescript
// ✅ Good
// Empty state has data-testid="empty-state-users"
const emptyState = page.locator('[data-testid="empty-state-users"]');
await expect(emptyState).toBeVisible();

// Count only actual data items, excluding empty state
const userRows = page.locator('[data-testid^="user-row-"]');
const count = await userRows.count();
expect(count).toBe(0);

// ❌ Bad
// Counting all elements including empty state
const allElements = page.locator('[data-testid^="user-"]');
const count = await allElements.count(); // May include empty state
```

### Data Creation Verification

**Rule**: After creating data, search or wait for it to appear before asserting.

**Rationale**: Data creation is asynchronous. The UI may not immediately reflect new data. Waiting or searching ensures the data is visible.

```typescript
// ✅ Good
await createUser('test@example.com', 'Test User');
// Wait for the user to appear in the list
await page.waitForSelector('[data-testid="user-row-test@example.com"]', { 
  state: 'visible',
  timeout: 5000 
});

// Or search for the user
await userManagementPage.search.search('test@example.com');
await expect(page.locator('[data-testid="user-row-test@example.com"]')).toBeVisible();

// ❌ Bad
await createUser('test@example.com', 'Test User');
// Immediately checking - may fail if data hasn't loaded yet
await expect(page.locator('[data-testid="user-row-test@example.com"]')).toBeVisible();
```

### Button Interactions

**Rule**: Check if a button is enabled before clicking it.

**Rationale**: Buttons may be disabled during loading states or invalid form states. Checking `isEnabled()` prevents clicking disabled buttons and makes test failures more informative.

```typescript
// ✅ Good
const submitButton = page.locator('[data-testid="button-submit"]');
await expect(submitButton).toBeEnabled();
await submitButton.click();

// Or with explicit check
if (await submitButton.isEnabled()) {
  await submitButton.click();
} else {
  throw new Error('Submit button is disabled');
}

// ❌ Bad
const submitButton = page.locator('[data-testid="button-submit"]');
await submitButton.click(); // May fail silently or click disabled button
```

### Drawer Elements

**Rule**: Use `page.locator()` directly for drawer elements, not `body.locator()`.

**Rationale**: Drawers are rendered in the page context. Using `body.locator()` may not reliably find drawer elements, especially when drawers are portaled.

```typescript
// ✅ Good
const drawer = page.locator('[data-testid="user-drawer"]');
const drawerInput = drawer.locator('[data-testid="textfield-email"] input');
await drawerInput.fill('test@example.com');

// ❌ Bad
const drawer = page.locator('body').locator('[data-testid="user-drawer"]');
// May not find drawer elements reliably
```

### State Verification

**Rule**: Get initial state, perform action, then verify the change.

**Rationale**: Verifying state changes requires knowing the initial state. This pattern ensures tests are deterministic and can handle both initial states.

```typescript
// ✅ Good
// Get initial state
const switchInput = page.locator('[data-testid="switch-enabled"] input[type="checkbox"]');
const initialChecked = await switchInput.isChecked();

// Perform action
await switchInput.click();

// Verify change
const newChecked = await switchInput.isChecked();
expect(newChecked).toBe(!initialChecked);

// ❌ Bad
// Assuming initial state
await switchInput.click();
await expect(switchInput).toBeChecked(); // May fail if already checked
```

## Test Data Management

### Creating Test Data

Use GraphQL helpers to create test data:

```typescript
import { createUser, signIn } from './helpers/graphql';

const token = await signIn('admin@test.com', 'password');
const user = await createUser('test@example.com', 'password', 'Test User');
```

### Test Data Cleanup

Test data should be cleaned up after tests:

```typescript
import { cleanupTestData } from './helpers/test-data';

test.afterEach(async () => {
  await cleanupTestData({ userIds: [testUser.id] });
});
```

## Environment Configuration

Tests can run in different environments:

- `local`: Local development (default)
- `development`: Development environment
- `staging`: Staging environment
- `production`: Production environment

Set the environment:

```bash
PLAYWRIGHT_TEST_ENV=staging pnpm test:e2e
```

Configuration is managed in `tests/e2e/config/environments.ts`.

## Debugging Tests

### Run in Debug Mode

```bash
pnpm test:e2e:debug
```

This opens Playwright Inspector where you can:
- Step through tests
- Inspect page state
- View console logs
- See network requests

### View Test Reports

After running tests, view the HTML report:

```bash
pnpm exec playwright show-report
```

### Screenshots and Videos

Screenshots are automatically captured on test failure. Videos are recorded when tests fail in CI.

### Trace Viewer

Traces are captured on test retries. View them:

```bash
pnpm exec playwright show-trace test-results/trace.zip
```

## CI/CD Integration

E2E tests run automatically in CI on:
- Push to main/master branches
- Pull requests
- Manual workflow dispatch

Test artifacts (reports, screenshots, videos, traces) are uploaded as GitHub Actions artifacts and retained for:
- Reports: 30 days
- Screenshots: 7 days
- Videos: 7 days
- Traces: 7 days

The CI workflow automatically installs Playwright browsers:
```yaml
- name: Install Playwright browsers
  run: pnpm exec playwright install --with-deps
```

## Troubleshooting

### Browser not found / Executable doesn't exist

**Error**: `Executable doesn't exist` or `Browser not found`

**Solution**: Install Playwright browsers:
```bash
pnpm exec playwright install --with-deps
```

This is the most common issue when setting up E2E tests for the first time. The `--with-deps` flag ensures all system dependencies are installed.

### Tests are flaky

1. Increase timeouts in `config/constants.ts`
2. Add explicit waits for async operations
3. Use `waitForLoadState('networkidle')` after navigation
4. Check for race conditions in test setup

### Tests fail in CI but pass locally

1. Check CI logs for specific errors
2. Verify environment variables are set correctly
3. Ensure test data is properly isolated
4. Check for timing issues (add waits)
5. Verify Playwright browsers are installed in CI (should be in workflow)

### Element not found

1. Verify `data-testid` attribute exists in component
2. Check if element is conditionally rendered
3. Wait for element to be visible before interacting
4. Verify element is not hidden by CSS

### Authentication issues

1. Verify test user credentials in `fixtures/users.ts`
2. Check authentication token expiration
3. Ensure sign-in helper completes before proceeding

## Related Documentation

- [Frontend Testing Pattern](./testing-pattern.md) - Unit and component testing patterns
- [Testing Standards](../../05-standards/testing-standards/unit-test-standards.md) - Testing standards and rules
- [Testing Workflow](../../06-workflows/testing-workflow.md) - Testing workflow and process
- [Playwright Documentation](https://playwright.dev) - Official Playwright documentation

## Contributing

When adding new E2E tests:

1. Follow the Page Object Model pattern
2. Add `data-testid` attributes to new components
3. Use test fixtures for test data
4. Write descriptive test names
5. Clean up test data after tests
6. Update this document if adding new patterns or conventions
