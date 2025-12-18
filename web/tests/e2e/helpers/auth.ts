/**
 * Authentication helpers for E2E tests
 */

import { Page } from '@playwright/test';
import { TEST_USERS } from '../fixtures/users';

/**
 * Page Object Model for Sign In page
 */
export class SignInPage {
  constructor(private page: Page) {}

  async goto() {
    await this.page.goto('/signin', { waitUntil: 'domcontentloaded', timeout: 30000 });
    
    // Wait for the signin screen - use a generous timeout for cross-browser compatibility
    // This ensures the page component has mounted
    await this.page.waitForSelector('[data-testid="signin-screen"]', { 
      timeout: 20000,
      state: 'visible'
    });
    
    // Wait for the form to be ready - this ensures the form component has fully rendered
    // This is more reliable than just waiting for the screen container
    await this.page.waitForSelector('[data-testid="signin-form"]', {
      timeout: 15000,
      state: 'visible'
    });
    
    // Wait for key form elements to be visible to ensure form is fully rendered
    // This provides better cross-browser reliability than waiting for a single field
    const emailField = this.page.locator('[data-testid="textfield-email"]');
    const passwordField = this.page.locator('[data-testid="textfield-password"]');
    const signInButton = this.page.locator('[data-testid="button-signin"]');
    
    // Wait for all key elements to be visible (parallel wait for efficiency)
    await Promise.all([
      emailField.waitFor({ state: 'visible', timeout: 10000 }),
      passwordField.waitFor({ state: 'visible', timeout: 10000 }),
      signInButton.waitFor({ state: 'visible', timeout: 10000 }),
    ]);
  }

  async fillEmail(email: string) {
    // MUI TextField renders the data-testid on the wrapper, but we need to target the actual input element
    const emailField = this.page.locator('[data-testid="textfield-email"] input');
    await emailField.waitFor({ state: 'visible' });
    await emailField.fill(email);
  }

  async fillPassword(password: string) {
    // MUI TextField renders the data-testid on the wrapper, but we need to target the actual input element
    const passwordField = this.page.locator('[data-testid="textfield-password"] input');
    await passwordField.waitFor({ state: 'visible' });
    await passwordField.fill(password);
  }

  async clickSignIn() {
    const signInButton = this.page.locator('[data-testid="button-signin"]');
    await signInButton.waitFor({ state: 'visible' });
    await signInButton.click();
  }

  async clickGoogleSignIn() {
    const googleButton = this.page.locator('[data-testid="button-google-signin"]');
    await googleButton.waitFor({ state: 'visible' });
    await googleButton.click();
  }

  async toggleRememberMe() {
    const checkbox = this.page.locator('[data-testid="checkbox-remember-me"]');
    await checkbox.waitFor({ state: 'visible' });
    await checkbox.click();
  }

  async signIn(email: string, password: string, rememberMe = false) {
    await this.fillEmail(email);
    await this.fillPassword(password);
    if (rememberMe) {
      await this.toggleRememberMe();
    }
    await this.clickSignIn();
  }

  async waitForError() {
    await this.page.waitForSelector('[data-testid="signin-error"]', { timeout: 10000 });
  }

  async getErrorText() {
    const errorElement = await this.page.locator('[data-testid="signin-error"]');
    const count = await errorElement.count();
    if (count === 0) {
      return null;
    }
    return await errorElement.textContent();
  }

  async isOnSignInPage() {
    return this.page.url().includes('/signin');
  }
}

/**
 * Helper to sign in with valid credentials
 */
export async function signInAsValidUser(page: Page, rememberMe = false) {
  const signInPage = new SignInPage(page);
  await signInPage.goto();
  await signInPage.signIn(
    TEST_USERS.valid.email,
    TEST_USERS.valid.password,
    rememberMe
  );
  // Wait for redirect to home (with longer timeout and error handling)
  try {
    await page.waitForURL('/', { timeout: 15000 });
  } catch (error) {
    // Check if we're already on the home page or if there was a different navigation
    const currentUrl = page.url();
    if (!currentUrl.includes('/signin')) {
      // We're not on sign-in page, assume we're logged in
      return;
    }
    throw error;
  }
}

/**
 * Helper to check if user is authenticated
 */
export async function isAuthenticated(page: Page): Promise<boolean> {
  // Check if auth token exists in storage
  // Ensure we're on a real page before accessing storage
  const url = page.url();
  if (url === 'about:blank' || !url.startsWith('http')) {
    return false;
  }
  
  const token = await page.evaluate(() => {
    try {
      return localStorage.getItem('auth_token') || sessionStorage.getItem('auth_token');
    } catch (e) {
      return null;
    }
  });
  return !!token;
}

/**
 * Helper to sign out
 */
export async function signOut(page: Page) {
  // Check if we're already on a valid page before navigating
  const currentUrl = page.url();
  const isOnValidPage = currentUrl.startsWith('http') && currentUrl !== 'about:blank';
  
  // Navigate to signin page first (localStorage is only available on actual pages, not about:blank)
  // Use 'domcontentloaded' for faster execution, 'load' if we need full page load
  if (!isOnValidPage || !currentUrl.includes('/signin')) {
    await page.goto('/signin', { waitUntil: 'domcontentloaded', timeout: 30000 });
  }
  
  // Clear auth tokens (now safe since we're on a real page)
  await page.evaluate(() => {
    try {
      localStorage.removeItem('auth_token');
      localStorage.removeItem('auth_refresh_token');
      localStorage.removeItem('auth_user');
      sessionStorage.removeItem('auth_token');
      sessionStorage.removeItem('auth_refresh_token');
      sessionStorage.removeItem('auth_user');
    } catch (e) {
      // Ignore errors if storage is not available (shouldn't happen after navigation)
    }
  });
}

