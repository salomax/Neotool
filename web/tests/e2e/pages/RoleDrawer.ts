/**
 * Page Object Model for Role Drawer
 */

import { Page, expect } from '@playwright/test';
import { SELECTORS } from '../config/constants';

export class RoleDrawer {
  constructor(private page: Page) {}

  /**
   * Get drawer element
   */
  private getDrawer() {
    return this.page.locator(SELECTORS.roleDrawer);
  }

  /**
   * Check if drawer is open
   */
  async isOpen(): Promise<boolean> {
    const drawer = this.getDrawer();
    const count = await drawer.count();
    if (count === 0) return false;
    return await drawer.isVisible();
  }

  /**
   * Wait for drawer to open
   */
  async waitForOpen(): Promise<void> {
    // Wait for drawer to be visible with a longer timeout
    await expect(this.getDrawer()).toBeVisible({ timeout: 10000 });
  }

  /**
   * Wait for drawer to close
   */
  async waitForClose(): Promise<void> {
    // Wait for drawer to not be visible with a longer timeout
    // The drawer may take time to close due to animations and async operations
    await expect(this.getDrawer()).not.toBeVisible({ timeout: 10000 });
  }

  /**
   * Close drawer
   */
  async close(): Promise<void> {
    // Use data-testid for the close button
    const closeButton = this.page.locator('[data-testid="drawer-close-button"]');
    await closeButton.waitFor({ state: 'visible', timeout: 10000 });
    await expect(closeButton).toBeEnabled({ timeout: 5000 });
    await closeButton.click();
    await this.waitForClose();
  }

  /**
   * Get role name input
   */
  private getRoleNameInput() {
    // TextField renders data-testid on the wrapper, but we need to target the actual input element
    const roleNameInputWrapper = this.page.locator('[data-testid="role-form-name"]');
    return roleNameInputWrapper.locator('input');
  }

  /**
   * Update role name
   */
  async updateRoleName(name: string): Promise<void> {
    const input = this.getRoleNameInput();
    await input.waitFor({ state: 'visible', timeout: 10000 });
    await input.clear();
    await input.fill(name);
  }

  /**
   * Get current role name
   */
  async getRoleName(): Promise<string> {
    const input = this.getRoleNameInput();
    await input.waitFor({ state: 'visible', timeout: 10000 });
    return await input.inputValue();
  }

  /**
   * Click save button
   */
  async save(): Promise<void> {
    const saveButton = this.page.locator('[data-testid="role-form-submit"]');
    await saveButton.waitFor({ state: 'visible', timeout: 10000 });
    await expect(saveButton).toBeEnabled({ timeout: 5000 });
    
    // Click the save button
    await saveButton.click();
    
    // Wait for save operation to complete
    // The button is disabled and shows "Saving..." text while saving
    // Wait for the button to be enabled again, which indicates save completed
    await expect(saveButton).toBeEnabled({ timeout: 15000 });
    
    // Wait for drawer to close after save (drawer closes automatically after successful save)
    // Use a longer timeout to account for network requests and UI updates
    await this.waitForClose();
    
    // Wait a moment for any UI updates, network requests, or animations to complete
    await this.page.waitForTimeout(500);
  }

  /**
   * Click cancel button
   */
  async cancel(): Promise<void> {
    const cancelButton = this.page.locator('[data-testid="drawer-close-button"]');
    await cancelButton.waitFor({ state: 'visible', timeout: 10000 });
    await expect(cancelButton).toBeEnabled({ timeout: 5000 });
    await cancelButton.click();
    await this.waitForClose();
  }

  /**
   * Verify drawer title
   */
  async verifyTitle(expectedTitle: string): Promise<void> {
    const title = this.getDrawer().locator('h2, h3, h4, h5, h6').first();
    await expect(title).toContainText(expectedTitle);
  }

  /**
   * Verify role information is displayed
   */
  async verifyRoleInfo(roleName: string): Promise<void> {
    const drawer = this.getDrawer();
    // Wait for loading to complete - check that all loading indicators are gone
    // There may be multiple loading indicators: one for main drawer, one for permissions, one for users, one for groups
    const loadingIndicators = drawer.locator('[role="progressbar"], [data-testid*="loading"]');
    const loadingCount = await loadingIndicators.count();
    
    // Wait for each loading indicator individually to disappear
    // This handles cases where permissions/users/groups fields may still be loading
    for (let i = 0; i < loadingCount; i++) {
      const indicator = loadingIndicators.nth(i);
      // Check if this indicator is visible before waiting for it to disappear
      const isVisible = await indicator.isVisible().catch(() => false);
      if (isVisible) {
        await expect(indicator).not.toBeVisible({ timeout: 15000 });
      }
    }
    
    const roleNameInput = this.getRoleNameInput();
    await roleNameInput.waitFor({ state: 'visible', timeout: 10000 });
    
    // Wait for the input value to be populated with the expected role name
    // Use toHaveValue which will automatically retry until the value matches or timeout
    // This is more reliable than manual polling and handles async data loading
    await expect(roleNameInput).toHaveValue(roleName, { timeout: 15000 });
  }

  /**
   * Get assigned permissions
   */
  async getAssignedPermissions(): Promise<string[]> {
    // Look for permission chips or list items
    const permissions = this.getDrawer().locator('[data-testid*="permission"], [data-testid*="Permission"]');
    const count = await permissions.count();
    const permissionNames: string[] = [];
    
    for (let i = 0; i < count; i++) {
      const text = await permissions.nth(i).textContent();
      if (text) permissionNames.push(text.trim());
    }
    
    return permissionNames;
  }

  /**
   * Get assigned users
   */
  async getAssignedUsers(): Promise<string[]> {
    // Look for user chips or list items
    const users = this.getDrawer().locator('[data-testid*="user"], [data-testid*="User"]');
    const count = await users.count();
    const userNames: string[] = [];
    
    for (let i = 0; i < count; i++) {
      const text = await users.nth(i).textContent();
      if (text) userNames.push(text.trim());
    }
    
    return userNames;
  }

  /**
   * Get assigned groups
   */
  async getAssignedGroups(): Promise<string[]> {
    // Look for group chips or list items
    const groups = this.getDrawer().locator('[data-testid*="group"], [data-testid*="Group"]');
    const count = await groups.count();
    const groupNames: string[] = [];
    
    for (let i = 0; i < count; i++) {
      const text = await groups.nth(i).textContent();
      if (text) groupNames.push(text.trim());
    }
    
    return groupNames;
  }
}
