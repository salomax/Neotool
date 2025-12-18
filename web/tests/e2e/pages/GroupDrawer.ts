/**
 * Page Object Model for Group Drawer
 */

import { Page, expect } from '@playwright/test';
import { SELECTORS } from '../config/constants';

export class GroupDrawer {
  constructor(private page: Page) {}

  /**
   * Get drawer element
   */
  private getDrawer() {
    return this.page.locator(SELECTORS.groupDrawer);
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
    // Use a longer timeout (20 seconds) to account for network requests and UI updates
    await expect(this.getDrawer()).not.toBeVisible({ timeout: 20000 });
  }

  /**
   * Close drawer
   */
  async close(): Promise<void> {
    // The close button is an IconButton with aria-label containing "Close"
    // It's in the drawer header - use aria-label to find it
    const closeButton = this.getDrawer().locator('button[aria-label*="Close"]').first();
    await closeButton.waitFor({ state: 'visible', timeout: 10000 });
    await expect(closeButton).toBeEnabled({ timeout: 5000 });
    await closeButton.click();
    await this.waitForClose();
  }

  /**
   * Get group name input
   */
  private getGroupNameInput() {
    // TextField renders data-testid on the wrapper, but we need to target the actual input element
    const groupNameInputWrapper = this.page.locator('[data-testid="group-form-name"]');
    return groupNameInputWrapper.locator('input');
  }

  /**
   * Get description input
   */
  private getDescriptionInput() {
    // TextField renders data-testid on the wrapper, but we need to target the actual input element
    // MUI TextField may render multiple textareas (one visible, one hidden for measurement)
    // Use first() to get the visible, editable one
    const descriptionInputWrapper = this.page.locator('[data-testid="group-form-description"]');
    return descriptionInputWrapper.locator('textarea:not([readonly]), input:not([readonly])').first();
  }

  /**
   * Update group name
   */
  async updateGroupName(name: string): Promise<void> {
    const input = this.getGroupNameInput();
    await input.waitFor({ state: 'visible', timeout: 10000 });
    await input.clear();
    await input.fill(name);
  }

  /**
   * Update description
   */
  async updateDescription(description: string): Promise<void> {
    const input = this.getDescriptionInput();
    await input.waitFor({ state: 'visible', timeout: 10000 });
    await input.clear();
    await input.fill(description);
  }

  /**
   * Get current group name
   */
  async getGroupName(): Promise<string> {
    const input = this.getGroupNameInput();
    await input.waitFor({ state: 'visible', timeout: 10000 });
    return await input.inputValue();
  }

  /**
   * Get current description
   */
  async getDescription(): Promise<string> {
    const input = this.getDescriptionInput();
    await input.waitFor({ state: 'visible', timeout: 10000 });
    return await input.inputValue();
  }

  /**
   * Click save button
   */
  async save(): Promise<void> {
    // The save button text changes based on mode (Create or Save)
    // Find button in drawer that contains "Create" or "Save" or "Saving"
    const saveButton = this.getDrawer()
      .locator('button:has-text("Create"), button:has-text("Save"), button:has-text("Saving")')
      .first();
    
    await saveButton.waitFor({ state: 'visible', timeout: 10000 });
    await expect(saveButton).toBeEnabled({ timeout: 5000 });
    
    // Click the save button
    await saveButton.click();
    
    // Wait for the saving state to complete
    // The button is disabled and shows "Saving..." while saving
    // Wait for the button to no longer show "Saving..." text, which indicates save completed
    // Use a longer timeout to account for network requests
    const savingButton = this.getDrawer().locator('button:has-text("Saving")').first();
    const savingCount = await savingButton.count();
    if (savingCount > 0) {
      // Wait for "Saving" button to disappear (save operation completed)
      await expect(savingButton).not.toBeVisible({ timeout: 20000 });
    }
    
    // Wait for drawer to close after save (drawer closes automatically after successful save)
    // The drawer closing is the primary indicator that save completed successfully
    // Use a longer timeout to account for network requests, UI updates, and animations
    await this.waitForClose();
    
    // Wait a moment for any UI updates, network requests, or animations to complete
    await this.page.waitForTimeout(500);
  }

  /**
   * Click cancel button
   */
  async cancel(): Promise<void> {
    const cancelButton = this.getDrawer()
      .locator('button:has-text("Cancel")')
      .first();
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
   * Verify group information is displayed
   */
  async verifyGroupInfo(name: string, description?: string): Promise<void> {
    const drawer = this.getDrawer();
    // Wait for loading to complete - check that all loading indicators are gone
    // There may be multiple loading indicators: one for main drawer, one for users, one for roles
    const loadingIndicators = drawer.locator('[role="progressbar"], [data-testid*="loading"]');
    const loadingCount = await loadingIndicators.count();
    
    // Wait for each loading indicator individually to disappear
    // This handles cases where users/roles fields may still be loading
    for (let i = 0; i < loadingCount; i++) {
      const indicator = loadingIndicators.nth(i);
      // Check if this indicator is visible before waiting for it to disappear
      const isVisible = await indicator.isVisible().catch(() => false);
      if (isVisible) {
        await expect(indicator).not.toBeVisible({ timeout: 15000 });
      }
    }
    
    const groupNameInput = this.getGroupNameInput();
    await groupNameInput.waitFor({ state: 'visible', timeout: 10000 });
    
    // Wait for the input value to be populated - retry a few times
    // The value might not be set immediately after drawer opens
    let groupNameValue = '';
    for (let i = 0; i < 5; i++) {
      groupNameValue = await groupNameInput.inputValue();
      if (groupNameValue && groupNameValue.trim() !== '') {
        break;
      }
      await this.page.waitForTimeout(300);
    }
    
    // Verify group name is in the input value
    if (groupNameValue && groupNameValue.trim() !== '') {
      expect(groupNameValue).toBe(name);
    } else {
      // Fallback: check drawer text
      await expect(drawer).toContainText(name, { timeout: 5000 });
    }
    
    if (description !== undefined) {
      const descriptionInput = this.getDescriptionInput();
      await descriptionInput.waitFor({ state: 'visible', timeout: 10000 });
      
      // Wait for the input value to be populated
      await this.page.waitForTimeout(300);
      const descriptionValue = await descriptionInput.inputValue();
      
      if (descriptionValue !== null && descriptionValue !== undefined) {
        expect(descriptionValue).toBe(description || '');
      } else {
        // Fallback: check drawer text
        if (description) {
          await expect(drawer).toContainText(description, { timeout: 5000 });
        }
      }
    }
  }

  /**
   * Get assigned roles
   */
  async getAssignedRoles(): Promise<string[]> {
    // Look for role chips or list items
    const roles = this.getDrawer().locator('[data-testid*="role"], [data-testid*="Role"]');
    const count = await roles.count();
    const roleNames: string[] = [];
    
    for (let i = 0; i < count; i++) {
      const text = await roles.nth(i).textContent();
      if (text) roleNames.push(text.trim());
    }
    
    return roleNames;
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
}
