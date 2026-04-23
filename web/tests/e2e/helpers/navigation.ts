/**
 * Navigation helpers for E2E tests
 */

import { Page } from '@playwright/test';
import { currentConfig } from '../config/environments';
import { SELECTORS } from '../config/constants';

/**
 * Navigates to the app shell and waits for auth-aware UI to render.
 * Authentication is injected by storageState fixtures.
 */
export async function signIntoPage(page: Page, _token?: string): Promise<void> {
  await page.goto('/');
  await page.waitForLoadState('domcontentloaded');
  await page.waitForURL((url) => !url.pathname.includes('/signin'), { timeout: 30_000 });

  // Desktop renders an explicit "account menu" button, while mobile layouts can hide it.
  // Consider auth-ready when either the desktop account menu or a core app shell marker appears.
  await Promise.race([
    page
      .getByRole('button', { name: /account menu/i })
      .waitFor({ state: 'visible', timeout: 30_000 }),
    page.getByTestId('app-layout').waitFor({ state: 'visible', timeout: 30_000 }),
    page.getByTestId('main-layout').waitFor({ state: 'visible', timeout: 30_000 }),
  ]).catch(async () => {
    // Last-resort fallback for mobile where only the URL transition is reliable.
    await page.waitForLoadState('networkidle', { timeout: 10_000 }).catch(() => {});
  });
}

/**
 * Navigate to the settings page
 */
export async function navigateToSettings(page: Page): Promise<void> {
  await page.goto('/settings');
  // Wait for page to load and settings tab to be visible
  await page.waitForLoadState('domcontentloaded');
  await page.locator(SELECTORS.usersTab).waitFor({ state: 'visible', timeout: 10000 });
}

/**
 * Navigate to User Management tab
 */
export async function navigateToUserManagement(page: Page): Promise<void> {
  await navigateToSettings(page);
  // Click on Users tab
  await page.click('button:has-text("Users")');
  // Wait for user list to load
  await page.waitForSelector('[data-testid="user-list-table"]', { timeout: 10000 }).catch(() => {
    // Table might not be visible if empty, so just wait for the page
  });
}

/**
 * Navigate to Group Management tab
 */
export async function navigateToGroupManagement(page: Page): Promise<void> {
  await navigateToSettings(page);
  await page.click('button:has-text("Groups")');
}

/**
 * Navigate to Role Management tab
 */
export async function navigateToRoleManagement(page: Page): Promise<void> {
  await navigateToSettings(page);
  await page.click('button:has-text("Roles")');
}

/**
 * Wait for page to be fully loaded
 */
export async function waitForPageLoad(page: Page): Promise<void> {
  // Use domcontentloaded instead of networkidle to avoid timeout issues
  await page.waitForLoadState('domcontentloaded');
}

/**
 * Wait for authentication redirect (if needed)
 */
export async function waitForAuthRedirect(page: Page): Promise<void> {
  const url = page.url();
  if (url.includes('/signin')) {
    // Wait for redirect after sign in
    await page.waitForURL((url) => !url.includes('/signin'), { timeout: 10000 });
  }
}
