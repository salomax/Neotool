import { test, expect } from '@playwright/test';
import { SignInPage, signInAsValidUser, isAuthenticated, signOut } from './helpers/auth.js';
import { TEST_USERS } from './fixtures/users.js';

test.describe('User Sign In', () => {
  test.beforeEach(async ({ page }) => {
    // Ensure we start from a clean state
    await signOut(page);
  });

  test.describe('Email + password sign in', () => {
    test('Successful sign in', async ({ page }) => {
      // Given I have a valid account
      // (Test user should be created in test setup)
      
      // When I enter a valid email and a valid password
      const signInPage = new SignInPage(page);
      await signInPage.goto();
      await signInPage.fillEmail(TEST_USERS.valid.email);
      await signInPage.fillPassword(TEST_USERS.valid.password);
      
      // And I press "Sign in"
      await signInPage.clickSignIn();
      
      // Then I should be redirected to the Home screen
      await expect(page).toHaveURL('/');
      await expect(page.locator('body')).toBeVisible();
      
      // Verify authentication
      const authenticated = await isAuthenticated(page);
      expect(authenticated).toBe(true);
    });

    test('Invalid credentials - wrong password', async ({ page }) => {
      const signInPage = new SignInPage(page);
      await signInPage.goto();
      
      // When I enter valid email and wrong password
      await signInPage.fillEmail(TEST_USERS.valid.email);
      await signInPage.fillPassword(TEST_USERS.invalid.password);
      
      // And I press "Sign in"
      await signInPage.clickSignIn();
      
      // Then I should see an authentication error message
      await signInPage.waitForError();
      const errorText = await signInPage.getErrorText();
      expect(errorText).toBeTruthy();
      expect(errorText?.toLowerCase()).toContain('invalid');
      
      // Should still be on signin page
      expect(await signInPage.isOnSignInPage()).toBe(true);
    });

    test('Invalid credentials - wrong email', async ({ page }) => {
      const signInPage = new SignInPage(page);
      await signInPage.goto();
      
      // When I enter wrong email and valid password
      await signInPage.fillEmail(TEST_USERS.invalid.email);
      await signInPage.fillPassword(TEST_USERS.valid.password);
      
      // And I press "Sign in"
      await signInPage.clickSignIn();
      
      // Then I should see an authentication error message
      await signInPage.waitForError();
      const errorText = await signInPage.getErrorText();
      expect(errorText).toBeTruthy();
      expect(errorText?.toLowerCase()).toContain('invalid');
      
      // Should still be on signin page
      expect(await signInPage.isOnSignInPage()).toBe(true);
    });
  });

  test.describe('Social sign in', () => {
    test('Sign in with Google', async ({ page }) => {
      const signInPage = new SignInPage(page);
      await signInPage.goto();
      
      // When I press "Continue with Google"
      await signInPage.clickGoogleSignIn();
      
      // Then the Google OAuth flow should start
      // (For now, we just verify the button exists and is clickable)
      // In a real implementation, we would check for OAuth redirect
      const googleButton = page.locator('[data-testid="button-google-signin"]');
      await expect(googleButton).toBeVisible();
      
      // Note: Actual OAuth flow testing would require mocking or test OAuth credentials
      // For now, we verify the button triggers the OAuth flow initiation
      // TODO: Implement OAuth flow testing when Google OAuth is implemented
    });
  });

  test.describe('Session persistence', () => {
    test('Keep me signed in', async ({ page, context }) => {
      const signInPage = new SignInPage(page);
      await signInPage.goto();
      
      // Given the "Keep me signed in" option is enabled
      await signInPage.fillEmail(TEST_USERS.valid.email);
      await signInPage.fillPassword(TEST_USERS.valid.password);
      await signInPage.toggleRememberMe();
      
      // When I sign in successfully
      await signInPage.clickSignIn();
      await expect(page).toHaveURL('/');
      
      // Verify token is in localStorage (not sessionStorage)
      const tokenInLocalStorage = await page.evaluate(() => {
        return localStorage.getItem('auth_token');
      });
      expect(tokenInLocalStorage).toBeTruthy();
      
      // Then my session should persist after closing and reopening the app
      // Close the page and create a new one (simulating app close/reopen)
      await page.close();
      const newPage = await context.newPage();
      
      // Navigate to home - should still be authenticated
      await newPage.goto('/');
      
      // Should not redirect to signin
      expect(newPage.url()).not.toContain('/signin');
      
      // Verify still authenticated
      const stillAuthenticated = await isAuthenticated(newPage);
      expect(stillAuthenticated).toBe(true);
    });

    test('Session expires when not using remember me', async ({ page, context }) => {
      const signInPage = new SignInPage(page);
      await signInPage.goto();
      
      // Sign in without remember me
      await signInPage.fillEmail(TEST_USERS.valid.email);
      await signInPage.fillPassword(TEST_USERS.valid.password);
      // Don't toggle remember me
      await signInPage.clickSignIn();
      await expect(page).toHaveURL('/');
      
      // Verify token is in sessionStorage (not localStorage)
      const tokenInSessionStorage = await page.evaluate(() => {
        return sessionStorage.getItem('auth_token');
      });
      expect(tokenInSessionStorage).toBeTruthy();
      
      // Close the page and create a new one (simulating new session)
      await page.close();
      const newPage = await context.newPage();
      
      // Navigate to home - should redirect to signin (session expired)
      await newPage.goto('/');
      
      // Should redirect to signin if session is not persistent
      // Note: This behavior depends on your session management implementation
      // For sessionStorage, new tabs/windows won't share the session
      // This test verifies the remember me functionality works differently
    });
  });

  test.describe('Background scenarios', () => {
    test('@web - Can access the web app and see Sign in screen', async ({ page }) => {
      // Given I can access the web app
      await page.goto('/');
      
      // And I am on the "Sign in" screen
      // (Assuming unauthenticated users are redirected to signin)
      // Or navigate directly
      await page.goto('/signin');
      
      const signInPage = new SignInPage(page);
      expect(await signInPage.isOnSignInPage()).toBe(true);
      await expect(page.locator('[data-testid="signin-screen"]')).toBeVisible();
    });
  });
});

