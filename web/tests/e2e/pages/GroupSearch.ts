/**
 * Page Object Model for Group Search
 */

import { Page, expect } from '@playwright/test';
import { SELECTORS } from '../config/constants';

export class GroupSearch {
  constructor(private page: Page) {}

  /**
   * Get the search input element
   */
  private getSearchInput() {
    return this.page.locator(SELECTORS.groupSearch).locator('input');
  }

  /**
   * Enter search query
   */
  async search(query: string): Promise<void> {
    const input = this.getSearchInput();
    await input.fill(query);
    // Wait for debounce (typically 300ms)
    await this.page.waitForTimeout(400);
    // Wait for search results to load - wait for loading indicator to disappear
    const loadingIndicator = this.page.locator('[role="progressbar"], [data-testid*="loading"]').first();
    const loadingCount = await loadingIndicator.count();
    if (loadingCount > 0) {
      await expect(loadingIndicator).not.toBeVisible({ timeout: 10000 });
    }
    // Also wait for group list to be visible (might be empty, but should be present)
    await this.page.waitForSelector(SELECTORS.groupList, { timeout: 10000 }).catch(() => {
      // Table might not exist if empty, so just wait for search to be visible
    });
  }

  /**
   * Clear search
   */
  async clear(): Promise<void> {
    const input = this.getSearchInput();
    await input.clear();
    await this.page.waitForTimeout(400);
    // Wait for search to clear - wait for loading indicator to disappear
    const loadingIndicator = this.page.locator('[role="progressbar"], [data-testid*="loading"]').first();
    const loadingCount = await loadingIndicator.count();
    if (loadingCount > 0) {
      await expect(loadingIndicator).not.toBeVisible({ timeout: 10000 });
    }
    // Also wait for group list to be visible (reset state)
    await this.page.waitForSelector(SELECTORS.groupList, { timeout: 10000 }).catch(() => {
      // Table might not exist if empty, so just wait for search to be visible
    });
  }

  /**
   * Get current search value
   */
  async getValue(): Promise<string> {
    const input = this.getSearchInput();
    return await input.inputValue();
  }

  /**
   * Verify search input is visible
   */
  async verifyVisible(): Promise<void> {
    await expect(this.page.locator(SELECTORS.groupSearch)).toBeVisible();
  }

  /**
   * Verify search input has placeholder
   */
  async verifyPlaceholder(expectedPlaceholder: string): Promise<void> {
    const input = this.getSearchInput();
    const placeholder = await input.getAttribute('placeholder');
    expect(placeholder).toContain(expectedPlaceholder);
  }

  /**
   * Wait for search to complete
   */
  async waitForSearchComplete(): Promise<void> {
    // Wait for loading indicator to disappear
    const loadingIndicator = this.page.locator('[role="progressbar"], [data-testid*="loading"]').first();
    const loadingCount = await loadingIndicator.count();
    if (loadingCount > 0) {
      await expect(loadingIndicator).not.toBeVisible({ timeout: 10000 });
    }
    // Also wait for group list to be visible
    await this.page.waitForSelector(SELECTORS.groupList, { timeout: 10000 }).catch(() => {
      // Table might not exist if empty, so just wait for search to be visible
    });
    // Additional wait for list to stabilize after search (especially important on mobile browsers)
    await this.page.waitForTimeout(300);
  }
}


