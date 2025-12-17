/**
 * Playwright test fixtures for E2E tests
 */

import { test as base, Page } from '@playwright/test';
import { signInAsValidUser } from '../helpers/auth';
import { createTestUserWithAuth, TestUser } from '../helpers/test-data';
import { TEST_USER_CREDENTIALS } from '../config/constants';

export interface TestFixtures {
  authenticatedPage: Page;
  adminUser: TestUser;
  testUser: TestUser;
}

/**
 * Extended test with custom fixtures
 */
export const test = base.extend<TestFixtures>({
  /**
   * Authenticated page fixture - provides a page with a signed-in user
   */
  authenticatedPage: async ({ page }, use) => {
    await signInAsValidUser(page);
    await use(page);
  },

  /**
   * Admin user fixture - provides an admin user with token
   */
  adminUser: async ({}, use) => {
    const admin = await createTestUserWithAuth(
      TEST_USER_CREDENTIALS.admin.email,
      TEST_USER_CREDENTIALS.admin.password,
      TEST_USER_CREDENTIALS.admin.displayName
    );
    await use(admin);
  },

  /**
   * Test user fixture - provides a regular test user
   */
  testUser: async ({}, use) => {
    const user = await createTestUserWithAuth(
      `test-${Date.now()}@test.neotool.com`,
      'TestPassword123!',
      'Test User'
    );
    await use(user);
  },
});

export { expect } from '@playwright/test';
