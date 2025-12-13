/**
 * Page Object Model for User Search
 */

import { Page, expect } from '@playwright/test';
import { SELECTORS } from '../config/constants';

export class UserSearch {
  constructor(private page: Page) {}

  /**
   * Get the search input element
   */
  private getSearchInput() {
    return this.page.locator(SELECTORS.userSearch).locator('input');
  }

  /**
   * Enter search query
   */
  async search(query: string): Promise<void> {
    const input = this.getSearchInput();
    await input.fill(query);
    // Wait for debounce (typically 300ms)
    await this.page.waitForTimeout(400);
    // Wait for search results to load
    await this.page.waitForLoadState('networkidle');
  }

  /**
   * Clear search
   */
  async clear(): Promise<void> {
    const input = this.getSearchInput();
    await input.clear();
    await this.page.waitForTimeout(400);
    await this.page.waitForLoadState('networkidle');
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
    await expect(this.page.locator(SELECTORS.userSearch)).toBeVisible();
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
    // Wait for loading to finish
    await this.page.waitForLoadState('networkidle');
    // Additional wait for debounce
    await this.page.waitForTimeout(500);
  }
}
