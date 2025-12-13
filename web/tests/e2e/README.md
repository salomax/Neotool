# E2E Testing with Playwright

This directory contains end-to-end (E2E) tests for the NeoTool web application using Playwright.

> **📖 Full Documentation**: See the [E2E Testing Pattern](../../docs/04-patterns/frontend-patterns/e2e-testing-pattern.md) in the specification for complete documentation, patterns, and best practices.

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

3. Ensure the application is running (or let Playwright start it automatically via `webServer` config)

4. Run tests:
   ```bash
   pnpm test:e2e
   ```

## Structure

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
└── *.e2e.spec.mjs      # Test specification files
```

## Running Tests

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
pnpm exec playwright test tests/e2e/user-management.e2e.spec.mjs
```

### Run tests in a specific browser

```bash
pnpm exec playwright test --project=chromium
```

### Run tests in headed mode (see browser)

```bash
pnpm exec playwright test --headed
```

## Writing Tests

### Test Structure

Tests follow the Page Object Model (POM) pattern for maintainability and reusability:

1. **Page Objects** (`pages/`): Encapsulate page interactions
2. **Test Helpers** (`helpers/`): Reusable utility functions
3. **Fixtures** (`fixtures/`): Test data and setup
4. **Test Specs** (`*.e2e.spec.mjs`): Test scenarios

### Example Test

```javascript
import { test, expect } from '@playwright/test';
import { signInAsValidUser } from './helpers/auth.js';
import { UserManagementPage } from './pages/UserManagementPage.js';

test.describe('User Management', () => {
  test('should display user list', async ({ page }) => {
    // Given I am signed in
    await signInAsValidUser(page);
    
    // When I navigate to User Management
    const userManagementPage = new UserManagementPage(page);
    await userManagementPage.goto();
    
    // Then I should see the user list
    await userManagementPage.waitForUserList();
  });
});
```

### Using Page Objects

Page Objects encapsulate page-specific logic:

```javascript
const userManagementPage = new UserManagementPage(page);
await userManagementPage.goto();
await userManagementPage.search.search('test@example.com');
await userManagementPage.clickEditUser(userId);
await userManagementPage.drawer.verifyUserInfo(email, displayName);
```

### Using Test Fixtures

Test fixtures provide reusable test data:

```javascript
import { createUniqueTestUser } from './fixtures/users.js';

test('should create and display user', async ({ page }) => {
  const testUser = await createUniqueTestUser('test-user', 'Test User');
  // Use testUser in your test...
});
```

### Using Helpers

Helpers provide common operations:

```javascript
import { navigateToUserManagement } from './helpers/navigation.js';
import { assertUserInList } from './helpers/assertions.js';

await navigateToUserManagement(page);
await assertUserInList(page, 'test@example.com', true);
```

## Test Data Management

### Creating Test Data

Use GraphQL helpers to create test data:

```javascript
import { createUser, signIn } from './helpers/graphql.js';

const token = await signIn('admin@test.com', 'password');
const user = await createUser('test@example.com', 'password', 'Test User');
```

### Test Data Cleanup

Test data should be cleaned up after tests. Use the cleanup helpers:

```javascript
import { cleanupTestData } from './helpers/test-data.js';

test.afterEach(async () => {
  await cleanupTestData({ userIds: [testUser.id] });
});
```

## Best Practices

### 1. Use Page Object Model

Always use Page Objects instead of directly interacting with the page:

```javascript
// ✅ Good
await userManagementPage.search.search('query');

// ❌ Bad
await page.fill('[data-testid="user-search"] input', 'query');
```

### 2. Use data-testid Attributes

Always use `data-testid` for element selection:

```javascript
// ✅ Good
await page.click('[data-testid="edit-user-123"]');

// ❌ Bad
await page.click('text=Edit');
```

### 3. Wait for Elements

Always wait for elements to be ready:

```javascript
// ✅ Good
await userManagementPage.waitForUserList();

// ❌ Bad
await page.click('button'); // Might click before element is ready
```

### 4. Use Descriptive Test Names

Test names should clearly describe what is being tested:

```javascript
// ✅ Good
test('should display user list when navigating to User Management');

// ❌ Bad
test('user list test');
```

### 5. Follow Given-When-Then Pattern

Structure tests using Given-When-Then:

```javascript
test('should enable user', async ({ page }) => {
  // Given I am signed in
  await signInAsValidUser(page);
  
  // When I toggle user status
  await userManagementPage.toggleUserStatus(userId);
  
  // Then the user should be enabled
  const status = await userManagementPage.getUserStatus(userId);
  expect(status).toBe(true);
});
```

### 6. Isolate Test Data

Each test should use isolated test data to avoid conflicts:

```javascript
// ✅ Good
const testUser = await createUniqueTestUser('test-user');

// ❌ Bad
// Using hardcoded test user that might conflict with other tests
```

### 7. Clean Up After Tests

Always clean up test data after tests:

```javascript
test.afterEach(async () => {
  await cleanupTestData({ userIds: [testUser.id] });
});
```

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

- **[E2E Testing Pattern](../../docs/04-patterns/frontend-patterns/e2e-testing-pattern.md)** - Complete specification with patterns, best practices, and troubleshooting
- [Frontend Testing Pattern](../../docs/04-patterns/frontend-patterns/testing-pattern.md) - Unit and component testing patterns
- [Testing Standards](../../docs/05-standards/testing-standards/unit-test-standards.md) - Testing standards and rules
- [Testing Workflow](../../docs/06-workflows/testing-workflow.md) - Testing workflow and process
- [Playwright Documentation](https://playwright.dev) - Official Playwright documentation

## Contributing

When adding new tests:

1. Follow the Page Object Model pattern
2. Add `data-testid` attributes to new components
3. Use test fixtures for test data
4. Write descriptive test names
5. Clean up test data after tests
6. Update the [E2E Testing Pattern](../../docs/04-patterns/frontend-patterns/e2e-testing-pattern.md) specification if adding new patterns or conventions
