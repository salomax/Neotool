/**
 * Page Object Model for User Management Page
 */

import { Page, expect } from '@playwright/test';
import { SELECTORS } from '../config/constants';
import { UserSearch } from './UserSearch';
import { UserDrawer } from './UserDrawer';

export class UserManagementPage {
  public search: UserSearch;
  public drawer: UserDrawer;

  constructor(private page: Page) {
    this.search = new UserSearch(page);
    this.drawer = new UserDrawer(page);
  }

  /**
   * Navigate to User Management page
   */
  async goto(): Promise<void> {
    await this.page.goto('/settings');
    // Wait for page to load and settings tab to be visible
    await this.page.waitForLoadState('domcontentloaded');
    await this.page.locator(SELECTORS.usersTab).waitFor({ state: 'visible', timeout: 10000 });
    await this.clickUsersTab();
    await this.waitForUserList();
  }

  /**
   * Click on Users tab
   */
  async clickUsersTab(): Promise<void> {
    const usersTab = this.page.locator(SELECTORS.usersTab);
    await usersTab.waitFor({ state: 'visible', timeout: 10000 });
    await expect(usersTab).toBeEnabled({ timeout: 5000 });
    await usersTab.click();
    await this.waitForUserList();
  }

  /**
   * Wait for user list to load
   */
  async waitForUserList(): Promise<void> {
    // Wait for the page to be in a stable state
    try {
      await this.page.waitForLoadState('domcontentloaded');
      // Wait for the table to appear (might be empty)
      await this.page.waitForSelector(SELECTORS.userList, { timeout: 10000 }).catch(() => {
        // Table might not exist if empty, so just wait for search to be visible
      });
      // At minimum, wait for search to be visible
      await this.page.waitForSelector(SELECTORS.userSearch, { timeout: 10000 });
    } catch (error) {
      // If page was closed, re-throw with better message
      if (error instanceof Error && error.message.includes('Target page, context or browser has been closed')) {
        throw new Error('Page was closed during test execution. This may indicate a navigation or error issue.');
      }
      throw error;
    }
  }

  /**
   * Get user row by user ID
   */
  getUserRow(userId: string) {
    return this.page.locator(`tr:has([data-testid="edit-user-${userId}"])`);
  }

  /**
   * Get user row by email
   */
  getUserRowByEmail(email: string) {
    return this.page.locator(`tr:has-text("${email}")`);
  }

  /**
   * Click edit button for a user
   */
  async clickEditUser(userId: string): Promise<void> {
    const editButton = this.page.locator(SELECTORS.editUser(userId));
    await editButton.waitFor({ state: 'visible', timeout: 10000 });
    await expect(editButton).toBeEnabled({ timeout: 5000 });
    await editButton.click();
    await this.drawer.waitForOpen();
  }

  /**
   * Click edit button for a user by email
   */
  async clickEditUserByEmail(email: string): Promise<void> {
    const row = this.getUserRowByEmail(email);
    const editButton = row.locator('button[data-testid^="edit-user-"]').first();
    await editButton.click();
    await this.drawer.waitForOpen();
  }

  /**
   * Toggle user status (enable/disable)
   */
  async toggleUserStatus(userId: string): Promise<void> {
    const toggle = this.page.locator(SELECTORS.userStatusToggle(userId));
    await toggle.waitFor({ state: 'visible', timeout: 10000 });
    await expect(toggle).toBeEnabled({ timeout: 5000 });
    
    // Get initial state
    const input = toggle.locator('input[type="checkbox"]');
    const initialChecked = await input.isChecked();
    
    await toggle.click();
    
    // Wait for the toggle state to change (mutation complete)
    // This is more reliable than networkidle which can timeout
    await expect(input).toBeChecked({ checked: !initialChecked, timeout: 5000 });
  }

  /**
   * Get user status (enabled/disabled)
   */
  async getUserStatus(userId: string): Promise<boolean> {
    const toggle = this.page.locator(SELECTORS.userStatusToggle(userId));
    await toggle.waitFor({ state: 'visible', timeout: 5000 });
    
    // Find the input element inside the Switch (following TextField pattern)
    const input = toggle.locator('input[type="checkbox"]');
    return await input.isChecked();
  }

  /**
   * Get number of users displayed
   */
  async getUserCount(): Promise<number> {
    const rows = this.page.locator(`${SELECTORS.userList} tbody tr:not([data-testid="table-empty-state-row"])`);
    return await rows.count();
  }

  /**
   * Verify user is in list
   */
  async verifyUserInList(email: string, shouldBeVisible = true): Promise<void> {
    const row = this.getUserRowByEmail(email);
    
    if (shouldBeVisible) {
      // Wait for user to appear (with longer timeout for newly created users)
      await this.waitForUserInList(email, 10000);
    } else {
      await expect(row).not.toBeVisible({ timeout: 5000 });
    }
  }

  /**
   * Wait for user to appear in list (with retries for newly created users)
   */
  async waitForUserInList(email: string, timeout = 10000): Promise<void> {
    const row = this.getUserRowByEmail(email);
    await expect(row).toBeVisible({ timeout });
  }

  /**
   * Verify user information in list
   */
  async verifyUserInfo(email: string, displayName?: string): Promise<void> {
    // First wait for the user to appear in the list
    await this.waitForUserInList(email);
    
    const row = this.getUserRowByEmail(email);
    await expect(row).toContainText(email);
    
    if (displayName) {
      await expect(row).toContainText(displayName);
    }
  }

  /**
   * Click next page button
   */
  async clickNextPage(): Promise<void> {
    const nextButton = this.page.locator('button:has-text("Next"), button[aria-label*="next"], button[aria-label*="Next"]').first();
    await nextButton.waitFor({ state: 'visible', timeout: 5000 });
    await nextButton.waitFor({ state: 'attached' });
    // Wait for button to be enabled
    await expect(nextButton).toBeEnabled({ timeout: 5000 });
    await nextButton.click();
    // waitForUserList() already waits for elements, no need for networkidle
    await this.waitForUserList();
  }

  /**
   * Click previous page button
   */
  async clickPreviousPage(): Promise<void> {
    const prevButton = this.page.locator('button:has-text("Previous"), button[aria-label*="previous"], button[aria-label*="Previous"]').first();
    await prevButton.waitFor({ state: 'visible', timeout: 5000 });
    await prevButton.waitFor({ state: 'attached' });
    // Wait for button to be enabled
    await expect(prevButton).toBeEnabled({ timeout: 5000 });
    await prevButton.click();
    // waitForUserList() already waits for elements, no need for networkidle
    await this.waitForUserList();
  }

  /**
   * Click first page button
   */
  async clickFirstPage(): Promise<void> {
    const firstButton = this.page.locator('button:has-text("First"), button[aria-label*="first"], button[aria-label*="First"]').first();
    await firstButton.waitFor({ state: 'visible', timeout: 5000 });
    await firstButton.waitFor({ state: 'attached' });
    // Wait for button to be enabled
    await expect(firstButton).toBeEnabled({ timeout: 5000 });
    await firstButton.click();
    // waitForUserList() already waits for elements, no need for networkidle
    await this.waitForUserList();
  }

  /**
   * Sort by column
   */
  async sortBy(field: 'name' | 'email' | 'status'): Promise<void> {
    // Map field to column header
    const columnMap = {
      name: 'Name',
      email: 'Email',
      status: 'Status',
    };
    
    const header = this.page.locator(`th:has-text("${columnMap[field]}")`).first();
    await header.waitFor({ state: 'visible', timeout: 10000 });
    await expect(header).toBeEnabled({ timeout: 5000 });
    await header.click();
    // waitForUserList() already waits for elements, no need for networkidle
    await this.waitForUserList();
  }

  /**
   * Verify sort indicator
   */
  async verifySortIndicator(field: 'name' | 'email' | 'status', direction: 'asc' | 'desc'): Promise<void> {
    const columnMap = {
      name: 'Name',
      email: 'Email',
      status: 'Status',
    };
    
    const header = this.page.locator(`th:has-text("${columnMap[field]}")`).first();
    // Check for sort indicator (arrow icon or aria-sort attribute)
    const ariaSort = await header.getAttribute('aria-sort');
    
    if (direction === 'asc') {
      expect(ariaSort).toBe('ascending');
    } else {
      expect(ariaSort).toBe('descending');
    }
  }

  /**
   * Verify empty state message
   */
  async verifyEmptyState(message?: string): Promise<void> {
    if (message) {
      await expect(this.page.locator(`text=${message}`)).toBeVisible({ timeout: 10000 });
    } else {
      // Wait for search element to be visible (indicates page is ready)
      // This is more reliable than networkidle which can timeout
      await this.page.waitForSelector(SELECTORS.userSearch, { timeout: 10000 });
      // Just verify that the table is empty or shows empty message
      const rows = await this.getUserCount();
      expect(rows).toBe(0);
    }
  }

  /**
   * Verify loading state
   */
  async verifyLoadingState(isLoading: boolean): Promise<void> {
    // Look for loading indicators
    const loadingIndicator = this.page.locator('[data-testid*="loading"], [role="progressbar"]').first();
    
    if (isLoading) {
      await expect(loadingIndicator).toBeVisible({ timeout: 5000 });
    } else {
      await expect(loadingIndicator).not.toBeVisible({ timeout: 5000 });
    }
  }

  /**
   * Verify pagination controls
   */
  async verifyPaginationVisible(shouldBeVisible: boolean): Promise<void> {
    const pagination = this.page.locator('button:has-text("Next"), button:has-text("Previous")').first();
    const count = await pagination.count();
    
    if (shouldBeVisible) {
      expect(count).toBeGreaterThan(0);
    }
  }

  /**
   * Get pagination range text (e.g., "1-10 of 100")
   */
  async getPaginationRange(): Promise<string | null> {
    // Look for pagination text
    const paginationText = this.page.locator('text=/\\d+-\\d+ of \\d+/').first();
    const count = await paginationText.count();
    
    if (count > 0) {
      return await paginationText.textContent();
    }
    
    return null;
  }
}
