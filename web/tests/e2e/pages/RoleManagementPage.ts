/**
 * Page Object Model for Role Management Page
 */

import { Page, expect } from '@playwright/test';
import { SELECTORS } from '../config/constants';
import { RoleSearch } from './RoleSearch';
import { RoleDrawer } from './RoleDrawer';

export class RoleManagementPage {
  public search: RoleSearch;
  public drawer: RoleDrawer;

  constructor(private page: Page) {
    this.search = new RoleSearch(page);
    this.drawer = new RoleDrawer(page);
  }

  /**
   * Navigate to Role Management page
   */
  async goto(): Promise<void> {
    await this.page.goto('/settings');
    // Wait for page to load and settings tab to be visible
    await this.page.waitForLoadState('domcontentloaded');
    await this.page.locator(SELECTORS.rolesTab).waitFor({ state: 'visible', timeout: 10000 });
    await this.clickRolesTab();
    await this.waitForRoleList();
  }

  /**
   * Click on Roles tab
   */
  async clickRolesTab(): Promise<void> {
    const rolesTab = this.page.locator(SELECTORS.rolesTab);
    await rolesTab.waitFor({ state: 'visible', timeout: 10000 });
    await expect(rolesTab).toBeEnabled({ timeout: 5000 });
    await rolesTab.click();
    await this.waitForRoleList();
  }

  /**
   * Wait for role list to load
   */
  async waitForRoleList(): Promise<void> {
    // Wait for the page to be in a stable state
    try {
      await this.page.waitForLoadState('domcontentloaded');
      
      // Wait for search input to be visible (indicates page structure is ready)
      await this.page.waitForSelector(SELECTORS.roleSearch, { timeout: 10000 });
      
      // Wait for the table to appear
      await this.page.waitForSelector(SELECTORS.roleList, { timeout: 10000 });
      
      // Wait for skeleton rows to disappear - the table should show either:
      // 1. Real data rows (with actual role data)
      // 2. Empty state row (data-testid="table-empty-state-row")
      // But NOT skeleton rows
      const tableBody = this.page.locator(`${SELECTORS.roleList} tbody`);
      await tableBody.waitFor({ state: 'visible', timeout: 10000 });
      
      // Wait for skeleton loaders to disappear (MUI Skeleton components)
      // Skeleton rows contain Skeleton components, so we wait for them to not be visible
      const skeletonLoader = tableBody.locator('[class*="MuiSkeleton"]').first();
      const skeletonCount = await skeletonLoader.count();
      if (skeletonCount > 0) {
        await expect(skeletonLoader).not.toBeVisible({ timeout: 10000 });
      }
      
      // Additional check: ensure table is in a stable state by waiting for either
      // data rows or empty state to be present (not skeleton rows)
      // This ensures the table has finished rendering
      await this.page.waitForFunction(
        (selector) => {
          const table = document.querySelector(selector);
          if (!table) return false;
          const tbody = table.querySelector('tbody');
          if (!tbody) return false;
          
          // Check if there are skeleton rows (rows with Skeleton components)
          const hasSkeletons = tbody.querySelector('[class*="MuiSkeleton"]');
          if (hasSkeletons) return false;
          
          // Table is ready if it has either data rows or empty state
          const hasDataRows = tbody.querySelector('tr:not([data-testid="table-empty-state-row"])');
          const hasEmptyState = tbody.querySelector('[data-testid="table-empty-state-row"]');
          
          return hasDataRows !== null || hasEmptyState !== null;
        },
        SELECTORS.roleList,
        { timeout: 10000 }
      );
    } catch (error) {
      // If page was closed, re-throw with better message
      if (error instanceof Error && error.message.includes('Target page, context or browser has been closed')) {
        throw new Error('Page was closed during test execution. This may indicate a navigation or error issue.');
      }
      throw error;
    }
  }

  /**
   * Get role row by role ID
   */
  getUserRow(roleId: string) {
    return this.page.locator(`tr:has([data-testid="edit-role-${roleId}"])`);
  }

  /**
   * Get role row by name
   */
  getRoleRowByName(name: string) {
    return this.page.locator(`tr:has-text("${name}")`);
  }

  /**
   * Click edit button for a role
   */
  async clickEditRole(roleId: string): Promise<void> {
    const editButton = this.page.locator(SELECTORS.editRole(roleId));
    await editButton.waitFor({ state: 'visible', timeout: 10000 });
    await expect(editButton).toBeEnabled({ timeout: 5000 });
    await editButton.click();
    // Wait for drawer to open and be ready
    await this.drawer.waitForOpen();
    // Wait a moment for the drawer content to load
    await this.page.waitForTimeout(300);
  }

  /**
   * Click edit button for a role by name
   */
  async clickEditRoleByName(name: string): Promise<void> {
    const row = this.getRoleRowByName(name);
    const editButton = row.locator('button[data-testid^="edit-role-"]').first();
    await editButton.click();
    await this.drawer.waitForOpen();
  }

  /**
   * Click create role button
   */
  async clickCreateRole(): Promise<void> {
    const createButton = this.page.locator('[data-testid="create-role-button"]');
    await createButton.waitFor({ state: 'visible', timeout: 10000 });
    await expect(createButton).toBeEnabled({ timeout: 5000 });
    await createButton.click();
    await this.drawer.waitForOpen();
  }

  /**
   * Click delete button for a role
   */
  async clickDeleteRole(roleId: string): Promise<void> {
    const deleteButton = this.page.locator(SELECTORS.deleteRole(roleId));
    await deleteButton.waitFor({ state: 'visible', timeout: 10000 });
    await expect(deleteButton).toBeEnabled({ timeout: 5000 });
    await deleteButton.click();
  }

  /**
   * Confirm delete in dialog
   */
  async confirmDelete(): Promise<void> {
    const confirmButton = this.page.locator('button:has-text("Delete"), button[aria-label*="delete"], button[aria-label*="Delete"]').first();
    await confirmButton.waitFor({ state: 'visible', timeout: 10000 });
    await expect(confirmButton).toBeEnabled({ timeout: 5000 });
    await confirmButton.click();
    // Wait for dialog to close
    await this.page.waitForTimeout(500);
  }

  /**
   * Get number of roles displayed
   */
  async getRoleCount(): Promise<number> {
    const rows = this.page.locator(`${SELECTORS.roleList} tbody tr:not([data-testid="table-empty-state-row"])`);
    return await rows.count();
  }

  /**
   * Verify role is in list
   */
  async verifyRoleInList(name: string, shouldBeVisible = true): Promise<void> {
    const row = this.getRoleRowByName(name);
    
    if (shouldBeVisible) {
      // Wait for role to appear (with longer timeout for newly created/updated roles)
      await this.waitForRoleInList(name, 15000);
    } else {
      // Wait for role to disappear (with longer timeout for deleted roles)
      await expect(row).not.toBeVisible({ timeout: 10000 });
    }
  }

  /**
   * Wait for role to appear in list (with retries for newly created roles)
   */
  async waitForRoleInList(name: string, timeout = 15000): Promise<void> {
    const row = this.getRoleRowByName(name);
    // Wait for the row to be visible, with a longer timeout for newly created/updated roles
    await expect(row).toBeVisible({ timeout });
  }

  /**
   * Verify role information in list
   */
  async verifyRoleInfo(name: string): Promise<void> {
    // First wait for the role to appear in the list
    await this.waitForRoleInList(name);
    
    const row = this.getRoleRowByName(name);
    await expect(row).toContainText(name);
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
    // waitForRoleList() already waits for elements, no need for networkidle
    await this.waitForRoleList();
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
    // waitForRoleList() already waits for elements, no need for networkidle
    await this.waitForRoleList();
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
    // waitForRoleList() already waits for elements, no need for networkidle
    await this.waitForRoleList();
  }

  /**
   * Sort by column
   */
  async sortBy(field: 'name'): Promise<void> {
    // Map field to column header
    const columnMap = {
      name: 'Name',
    };
    
    const header = this.page.locator(`th:has-text("${columnMap[field]}")`).first();
    await header.waitFor({ state: 'visible', timeout: 10000 });
    await expect(header).toBeEnabled({ timeout: 5000 });
    await header.click();
    // waitForRoleList() already waits for elements, no need for networkidle
    await this.waitForRoleList();
  }

  /**
   * Verify sort indicator
   */
  async verifySortIndicator(field: 'name', direction: 'asc' | 'desc'): Promise<void> {
    const columnMap = {
      name: 'Name',
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
      await this.page.waitForSelector(SELECTORS.roleSearch, { timeout: 10000 });
      // Just verify that the table is empty or shows empty message
      const rows = await this.getRoleCount();
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






