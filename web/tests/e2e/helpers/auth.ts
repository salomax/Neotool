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
    await this.page.goto('/signin');
    await this.page.waitForSelector('[data-testid="signin-screen"]');
  }

  async fillEmail(email: string) {
    await this.page.fill('[data-testid="textfield-email"]', email);
  }

  async fillPassword(password: string) {
    await this.page.fill('[data-testid="textfield-password"]', password);
  }

  async clickSignIn() {
    await this.page.click('[data-testid="button-signin"]');
  }

  async clickGoogleSignIn() {
    await this.page.click('[data-testid="button-google-signin"]');
  }

  async toggleRememberMe() {
    await this.page.click('[data-testid="checkbox-remember-me"]');
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
    await this.page.waitForSelector('[data-testid="signin-error"]');
  }

  async getErrorText() {
    const errorElement = await this.page.$('[data-testid="signin-error"]');
    return errorElement?.textContent() || null;
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
  // Wait for redirect to home
  await page.waitForURL('/', { timeout: 5000 });
}

/**
 * Helper to check if user is authenticated
 */
export async function isAuthenticated(page: Page): Promise<boolean> {
  // Check if auth token exists in storage
  const token = await page.evaluate(() => {
    return localStorage.getItem('auth_token') || sessionStorage.getItem('auth_token');
  });
  return !!token;
}

/**
 * Helper to sign out
 */
export async function signOut(page: Page) {
  // Clear auth tokens
  await page.evaluate(() => {
    localStorage.removeItem('auth_token');
    localStorage.removeItem('auth_refresh_token');
    localStorage.removeItem('auth_user');
    sessionStorage.removeItem('auth_token');
    sessionStorage.removeItem('auth_refresh_token');
    sessionStorage.removeItem('auth_user');
  });
  await page.goto('/signin');
}

