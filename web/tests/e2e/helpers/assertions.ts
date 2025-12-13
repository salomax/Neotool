/**
 * Custom assertions for E2E tests
 */

import { Page, expect } from '@playwright/test';

/**
 * Assert that a user is visible in the user list
 */
export async function assertUserInList(
  page: Page,
  userEmail: string,
  shouldBeVisible = true
): Promise<void> {
  const userRow = page.locator(`tr:has-text("${userEmail}")`);
  
  if (shouldBeVisible) {
    await expect(userRow).toBeVisible({ timeout: 5000 });
  } else {
    await expect(userRow).not.toBeVisible({ timeout: 5000 });
  }
}

/**
 * Assert that a user has a specific status (enabled/disabled)
 */
export async function assertUserStatus(
  page: Page,
  userId: string,
  enabled: boolean
): Promise<void> {
  const toggle = page.locator(`[data-testid="user-status-toggle-${userId}"]`);
  await expect(toggle).toBeVisible();
  
  // Check if toggle is checked (enabled) or not (disabled)
  const isChecked = await toggle.isChecked();
  expect(isChecked).toBe(enabled);
}

/**
 * Assert that an element is visible based on permissions
 */
export async function assertElementVisibleByPermission(
  page: Page,
  selector: string,
  shouldBeVisible: boolean
): Promise<void> {
  const element = page.locator(selector);
  
  if (shouldBeVisible) {
    await expect(element).toBeVisible({ timeout: 5000 });
  } else {
    await expect(element).not.toBeVisible({ timeout: 5000 });
  }
}

/**
 * Assert that the user list is displayed
 */
export async function assertUserListDisplayed(page: Page): Promise<void> {
  const userList = page.locator('[data-testid="user-list-table"]');
  await expect(userList).toBeVisible({ timeout: 10000 });
}

/**
 * Assert that the user drawer is open
 */
export async function assertUserDrawerOpen(page: Page, isOpen = true): Promise<void> {
  const drawer = page.locator('[data-testid="drawer"]');
  
  if (isOpen) {
    await expect(drawer).toBeVisible({ timeout: 5000 });
  } else {
    await expect(drawer).not.toBeVisible({ timeout: 5000 });
  }
}

/**
 * Assert that search results contain expected users
 */
export async function assertSearchResults(
  page: Page,
  expectedEmails: string[]
): Promise<void> {
  for (const email of expectedEmails) {
    await assertUserInList(page, email, true);
  }
}

/**
 * Assert pagination controls are visible
 */
export async function assertPaginationVisible(page: Page, shouldBeVisible = true): Promise<void> {
  // Look for pagination controls (this selector may need adjustment based on actual implementation)
  const pagination = page.locator('[data-testid*="pagination"]').first();
  
  if (shouldBeVisible) {
    await expect(pagination).toBeVisible({ timeout: 5000 });
  } else {
    // If pagination shouldn't be visible, it might not exist or be hidden
    const count = await pagination.count();
    if (count > 0) {
      await expect(pagination).not.toBeVisible({ timeout: 5000 });
    }
  }
}
