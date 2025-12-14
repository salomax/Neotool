/**
 * Page Object Model for Settings Page
 */

import { Page, expect } from '@playwright/test';
import { SELECTORS } from '../config/constants';

export class SettingsPage {
  constructor(private page: Page) {}

  /**
   * Navigate to settings page
   */
  async goto(): Promise<void> {
    await this.page.goto('/settings');
    // Wait for page to load and settings tab to be visible
    await this.page.waitForLoadState('domcontentloaded');
    await this.page.locator(SELECTORS.usersTab).waitFor({ state: 'visible', timeout: 10000 });
    await this.waitForPageLoad();
  }

  /**
   * Wait for page to load
   */
  async waitForPageLoad(): Promise<void> {
    await this.page.waitForSelector(SELECTORS.settingsPage, { timeout: 10000 }).catch(() => {
      // Settings page might not have a specific test ID, so just wait for URL
    });
    await expect(this.page).toHaveURL(/\/settings/);
  }

  /**
   * Click on Users tab
   */
  async clickUsersTab(): Promise<void> {
    await this.page.click(SELECTORS.usersTab);
    // Wait for user management to load
    await this.page.waitForSelector('[data-testid="user-search"]', { timeout: 10000 }).catch(() => {
      // Search might not be visible if empty
    });
  }

  /**
   * Click on Groups tab
   */
  async clickGroupsTab(): Promise<void> {
    await this.page.click(SELECTORS.groupsTab);
  }

  /**
   * Click on Roles tab
   */
  async clickRolesTab(): Promise<void> {
    await this.page.click(SELECTORS.rolesTab);
  }

  /**
   * Verify Users tab is visible
   */
  async verifyUsersTabVisible(): Promise<void> {
    await expect(this.page.locator(SELECTORS.usersTab)).toBeVisible();
  }

  /**
   * Verify Groups tab is visible
   */
  async verifyGroupsTabVisible(): Promise<void> {
    await expect(this.page.locator(SELECTORS.groupsTab)).toBeVisible();
  }

  /**
   * Verify Roles tab is visible
   */
  async verifyRolesTabVisible(): Promise<void> {
    await expect(this.page.locator(SELECTORS.rolesTab)).toBeVisible();
  }

  /**
   * Verify tab is not visible (for permission-based hiding)
   */
  async verifyTabNotVisible(tabName: 'Users' | 'Groups' | 'Roles'): Promise<void> {
    const selector = tabName === 'Users' 
      ? SELECTORS.usersTab 
      : tabName === 'Groups' 
      ? SELECTORS.groupsTab 
      : SELECTORS.rolesTab;
    
    const count = await this.page.locator(selector).count();
    if (count > 0) {
      await expect(this.page.locator(selector)).not.toBeVisible();
    }
  }
}
