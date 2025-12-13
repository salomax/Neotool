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
    await this.page.waitForLoadState('networkidle');
    await this.clickUsersTab();
    await this.waitForUserList();
  }

  /**
   * Click on Users tab
   */
  async clickUsersTab(): Promise<void> {
    await this.page.click(SELECTORS.usersTab);
    await this.waitForUserList();
  }

  /**
   * Wait for user list to load
   */
  async waitForUserList(): Promise<void> {
    // Wait for the table to appear (might be empty)
    await this.page.waitForSelector(SELECTORS.userList, { timeout: 10000 }).catch(() => {
      // Table might not exist if empty, so just wait for search to be visible
    });
    // At minimum, wait for search to be visible
    await this.page.waitForSelector(SELECTORS.userSearch, { timeout: 10000 });
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
    await toggle.click();
    // Wait for the mutation to complete
    await this.page.waitForLoadState('networkidle');
  }

  /**
   * Get user status (enabled/disabled)
   */
  async getUserStatus(userId: string): Promise<boolean> {
    const toggle = this.page.locator(SELECTORS.userStatusToggle(userId));
    return await toggle.isChecked();
  }

  /**
   * Get number of users displayed
   */
  async getUserCount(): Promise<number> {
    const rows = this.page.locator(`${SELECTORS.userList} tbody tr`);
    return await rows.count();
  }

  /**
   * Verify user is in list
   */
  async verifyUserInList(email: string, shouldBeVisible = true): Promise<void> {
    const row = this.getUserRowByEmail(email);
    
    if (shouldBeVisible) {
      await expect(row).toBeVisible({ timeout: 5000 });
    } else {
      await expect(row).not.toBeVisible({ timeout: 5000 });
    }
  }

  /**
   * Verify user information in list
   */
  async verifyUserInfo(email: string, displayName?: string): Promise<void> {
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
    await nextButton.click();
    await this.page.waitForLoadState('networkidle');
    await this.waitForUserList();
  }

  /**
   * Click previous page button
   */
  async clickPreviousPage(): Promise<void> {
    const prevButton = this.page.locator('button:has-text("Previous"), button[aria-label*="previous"], button[aria-label*="Previous"]').first();
    await prevButton.click();
    await this.page.waitForLoadState('networkidle');
    await this.waitForUserList();
  }

  /**
   * Click first page button
   */
  async clickFirstPage(): Promise<void> {
    const firstButton = this.page.locator('button:has-text("First"), button[aria-label*="first"], button[aria-label*="First"]').first();
    await firstButton.click();
    await this.page.waitForLoadState('networkidle');
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
    await header.click();
    await this.page.waitForLoadState('networkidle');
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
      await expect(this.page.locator(`text=${message}`)).toBeVisible();
    } else {
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
