/**
 * Page Object Model for User Drawer
 */

import { Page, expect } from '@playwright/test';
import { SELECTORS } from '../config/constants';

export class UserDrawer {
  constructor(private page: Page) {}

  /**
   * Get drawer element
   */
  private getDrawer() {
    return this.page.locator(SELECTORS.drawer);
  }

  /**
   * Get drawer header
   */
  private getHeader() {
    return this.page.locator(SELECTORS.drawerHeader);
  }

  /**
   * Get drawer body
   */
  private getBody() {
    return this.page.locator(SELECTORS.drawerBody);
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
    await expect(this.getDrawer()).toBeVisible({ timeout: 5000 });
  }

  /**
   * Wait for drawer to close
   */
  async waitForClose(): Promise<void> {
    await expect(this.getDrawer()).not.toBeVisible({ timeout: 5000 });
  }

  /**
   * Close drawer
   */
  async close(): Promise<void> {
    // Look for close button (X icon)
    const closeButton = this.page.locator('button[aria-label*="Close"], button[aria-label*="close"]').first();
    const count = await closeButton.count();
    if (count > 0) {
      await closeButton.click();
    } else {
      // Fallback: click outside or press Escape
      await this.page.keyboard.press('Escape');
    }
    await this.waitForClose();
  }

  /**
   * Get display name input
   */
  private getDisplayNameInput() {
    return this.getBody().locator('input[type="text"]').first();
  }

  /**
   * Update display name
   */
  async updateDisplayName(name: string): Promise<void> {
    const input = this.getDisplayNameInput();
    await input.clear();
    await input.fill(name);
  }

  /**
   * Get current display name
   */
  async getDisplayName(): Promise<string> {
    const input = this.getDisplayNameInput();
    return await input.inputValue();
  }

  /**
   * Verify email field is readonly
   */
  async verifyEmailReadonly(): Promise<void> {
    // Email field should be readonly
    const emailInput = this.getBody().locator('input[type="email"]').first();
    const isReadonly = await emailInput.getAttribute('readonly');
    expect(isReadonly).not.toBeNull();
  }

  /**
   * Get assigned groups
   */
  async getAssignedGroups(): Promise<string[]> {
    // Look for group chips or list items
    const groups = this.getBody().locator('[data-testid*="group"], [data-testid*="Group"]');
    const count = await groups.count();
    const groupNames: string[] = [];
    
    for (let i = 0; i < count; i++) {
      const text = await groups.nth(i).textContent();
      if (text) groupNames.push(text.trim());
    }
    
    return groupNames;
  }

  /**
   * Get assigned roles (readonly)
   */
  async getAssignedRoles(): Promise<string[]> {
    // Look for role chips or list items
    const roles = this.getBody().locator('[data-testid*="role"], [data-testid*="Role"]');
    const count = await roles.count();
    const roleNames: string[] = [];
    
    for (let i = 0; i < count; i++) {
      const text = await roles.nth(i).textContent();
      if (text) roleNames.push(text.trim());
    }
    
    return roleNames;
  }

  /**
   * Click save button
   */
  async save(): Promise<void> {
    const saveButton = this.getBody()
      .locator('button')
      .filter({ hasText: /save|Save|Save Changes/i })
      .first();
    
    await saveButton.click();
    // Wait for save to complete
    await this.page.waitForLoadState('networkidle');
  }

  /**
   * Click cancel button
   */
  async cancel(): Promise<void> {
    const cancelButton = this.getBody()
      .locator('button')
      .filter({ hasText: /cancel|Cancel/i })
      .first();
    
    await cancelButton.click();
    await this.waitForClose();
  }

  /**
   * Verify drawer title
   */
  async verifyTitle(expectedTitle: string): Promise<void> {
    const title = this.getHeader().locator('h2, h3, h4, h5, h6').first();
    await expect(title).toContainText(expectedTitle);
  }

  /**
   * Verify user information is displayed
   */
  async verifyUserInfo(email: string, displayName?: string): Promise<void> {
    const body = this.getBody();
    await expect(body).toContainText(email);
    
    if (displayName) {
      await expect(body).toContainText(displayName);
    }
  }

  /**
   * Assign group (if group assignment component is available)
   */
  async assignGroup(groupName: string): Promise<void> {
    // This will depend on the actual implementation of UserGroupAssignment
    // For now, this is a placeholder
    const groupAssignment = this.getBody().locator('[data-testid*="group-assignment"]');
    // Implementation would depend on the actual component structure
    console.log('Assigning group:', groupName);
  }

  /**
   * Remove group
   */
  async removeGroup(groupName: string): Promise<void> {
    // Look for remove button on group chip
    const groupChip = this.getBody()
      .locator(`[data-testid*="group"]:has-text("${groupName}")`)
      .first();
    
    const removeButton = groupChip.locator('button[aria-label*="remove"], button[aria-label*="Remove"]').first();
    const count = await removeButton.count();
    
    if (count > 0) {
      await removeButton.click();
      await this.page.waitForLoadState('networkidle');
    }
  }
}
