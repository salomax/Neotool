/**
 * Page Object Model for Group Management Page
 */

import { Page, expect } from '@playwright/test';
import { SELECTORS } from '../config/constants';
import { GroupSearch } from './GroupSearch';
import { GroupDrawer } from './GroupDrawer';

export class GroupManagementPage {
  public search: GroupSearch;
  public drawer: GroupDrawer;

  constructor(private page: Page) {
    this.search = new GroupSearch(page);
    this.drawer = new GroupDrawer(page);
  }

  /**
   * Navigate to Group Management page
   */
  async goto(): Promise<void> {
    await this.page.goto('/settings');
    // Wait for page to load and settings tab to be visible
    await this.page.waitForLoadState('domcontentloaded');
    await this.page.locator(SELECTORS.groupsTab).waitFor({ state: 'visible', timeout: 10000 });
    await this.clickGroupsTab();
    await this.waitForGroupList();
  }

  /**
   * Click on Groups tab
   */
  async clickGroupsTab(): Promise<void> {
    const groupsTab = this.page.locator(SELECTORS.groupsTab);
    await groupsTab.waitFor({ state: 'visible', timeout: 10000 });
    await expect(groupsTab).toBeEnabled({ timeout: 5000 });
    await groupsTab.click();
    await this.waitForGroupList();
  }

  /**
   * Wait for group list to load
   */
  async waitForGroupList(): Promise<void> {
    // Wait for the page to be in a stable state
    try {
      await this.page.waitForLoadState('domcontentloaded');
      // Wait for the table to appear (might be empty)
      await this.page.waitForSelector(SELECTORS.groupList, { timeout: 10000 }).catch(() => {
        // Table might not exist if empty, so just wait for search to be visible
      });
      // At minimum, wait for search to be visible
      await this.page.waitForSelector(SELECTORS.groupSearch, { timeout: 10000 });
    } catch (error) {
      // If page was closed, re-throw with better message
      if (error instanceof Error && error.message.includes('Target page, context or browser has been closed')) {
        throw new Error('Page was closed during test execution. This may indicate a navigation or error issue.');
      }
      throw error;
    }
  }

  /**
   * Get group row by group ID
   */
  getGroupRow(groupId: string) {
    return this.page.locator(`tr:has([data-testid="edit-group-${groupId}"])`);
  }

  /**
   * Get group row by name
   */
  getGroupRowByName(name: string) {
    return this.page.locator(`tr:has-text("${name}")`);
  }

  /**
   * Click edit button for a group
   */
  async clickEditGroup(groupId: string): Promise<void> {
    const editButton = this.page.locator(SELECTORS.editGroup(groupId));
    await editButton.waitFor({ state: 'visible', timeout: 10000 });
    await expect(editButton).toBeEnabled({ timeout: 5000 });
    await editButton.click();
    // Wait for drawer to open and be ready
    await this.drawer.waitForOpen();
    // Wait a moment for the drawer content to load
    await this.page.waitForTimeout(300);
  }

  /**
   * Click edit button for a group by name
   */
  async clickEditGroupByName(name: string): Promise<void> {
    const row = this.getGroupRowByName(name);
    const editButton = row.locator('button[data-testid^="edit-group-"]').first();
    await editButton.click();
    await this.drawer.waitForOpen();
  }

  /**
   * Click create group button
   */
  async clickCreateGroup(): Promise<void> {
    const createButton = this.page.locator('[data-testid="create-group-button"]');
    await createButton.waitFor({ state: 'visible', timeout: 10000 });
    await expect(createButton).toBeEnabled({ timeout: 5000 });
    await createButton.click();
    await this.drawer.waitForOpen();
  }

  /**
   * Click delete button for a group
   */
  async clickDeleteGroup(groupId: string): Promise<void> {
    const deleteButton = this.page.locator(SELECTORS.deleteGroup(groupId));
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
   * Get number of groups displayed
   */
  async getGroupCount(): Promise<number> {
    const rows = this.page.locator(`${SELECTORS.groupList} tbody tr:not([data-testid="table-empty-state-row"])`);
    return await rows.count();
  }

  /**
   * Verify group is in list
   */
  async verifyGroupInList(name: string, shouldBeVisible = true): Promise<void> {
    const row = this.getGroupRowByName(name);
    
    if (shouldBeVisible) {
      // Wait for group to appear (with longer timeout for newly created/updated groups)
      await this.waitForGroupInList(name, 15000);
    } else {
      // Wait for group to disappear (with longer timeout for deleted groups)
      await expect(row).not.toBeVisible({ timeout: 10000 });
    }
  }

  /**
   * Wait for group to appear in list (with retries for newly created groups)
   */
  async waitForGroupInList(name: string, timeout = 15000): Promise<void> {
    const row = this.getGroupRowByName(name);
    // Wait for the row to be visible, with a longer timeout for newly created/updated groups
    await expect(row).toBeVisible({ timeout });
  }

  /**
   * Verify group information in list
   */
  async verifyGroupInfo(name: string, description?: string): Promise<void> {
    // First wait for the group to appear in the list
    await this.waitForGroupInList(name);
    
    const row = this.getGroupRowByName(name);
    await expect(row).toContainText(name);
    
    if (description) {
      await expect(row).toContainText(description);
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
    // waitForGroupList() already waits for elements, no need for networkidle
    await this.waitForGroupList();
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
    // waitForGroupList() already waits for elements, no need for networkidle
    await this.waitForGroupList();
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
    // waitForGroupList() already waits for elements, no need for networkidle
    await this.waitForGroupList();
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
    // waitForGroupList() already waits for elements, no need for networkidle
    await this.waitForGroupList();
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
      await this.page.waitForSelector(SELECTORS.groupSearch, { timeout: 10000 });
      // Wait for search to complete and list to update
      await this.page.waitForTimeout(1000);
      
      // Check for empty state row first (more reliable than counting rows)
      const emptyStateRow = this.page.locator('[data-testid="table-empty-state-row"]');
      const emptyStateCount = await emptyStateRow.count();
      
      if (emptyStateCount > 0) {
        // Empty state row exists, verify it's visible
        await expect(emptyStateRow).toBeVisible({ timeout: 10000 });
      } else {
        // No empty state row, verify that the table is empty
        // Wait a bit more and check again - sometimes the empty state takes time to appear
        await this.page.waitForTimeout(500);
        const finalEmptyStateCount = await emptyStateRow.count();
        if (finalEmptyStateCount > 0) {
          await expect(emptyStateRow).toBeVisible({ timeout: 5000 });
        } else {
          // Verify that there are no group rows (excluding empty state)
          const rows = await this.getGroupCount();
          expect(rows).toBe(0);
        }
      }
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





