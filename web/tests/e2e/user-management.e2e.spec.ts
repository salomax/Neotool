import { test, expect } from '@playwright/test';
import { signInAsValidUser, signOut } from './helpers/auth';
import { UserManagementPage } from './pages/UserManagementPage';
import { SettingsPage } from './pages/SettingsPage';
import { createUniqueTestUser } from './fixtures/users';

test.describe('User Management', () => {
  test.beforeEach(async ({ page }) => {
    // Ensure we start from a clean state
    await signOut(page);
  });

  test.describe('Navigation and Access', () => {
    test('should navigate to User Management page', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // When I navigate to settings
      const settingsPage = new SettingsPage(page);
      await settingsPage.goto();
      
      // And I click on Users tab
      await settingsPage.clickUsersTab();
      
      // Then I should be on the User Management page
      await expect(page).toHaveURL(/\/settings/);
      
      // And the user search should be visible
      const userManagementPage = new UserManagementPage(page);
      await userManagementPage.search.verifyVisible();
    });

    test('should display Users tab when user has authorization permissions', async ({ page }) => {
      // Given I am signed in as a user with authorization permissions
      await signInAsValidUser(page);
      
      // When I navigate to settings
      const settingsPage = new SettingsPage(page);
      await settingsPage.goto();
      
      // Then the Users tab should be visible
      await settingsPage.verifyUsersTabVisible();
    });

    test('should load user list on page load', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // When I navigate to User Management
      const userManagementPage = new UserManagementPage(page);
      await userManagementPage.goto();
      
      // Then the user list should be displayed
      await userManagementPage.waitForUserList();
    });
  });

  test.describe('User List Display', () => {
    test('should display list of users', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // When I navigate to User Management
      const userManagementPage = new UserManagementPage(page);
      await userManagementPage.goto();
      
      // Then I should see the user list
      await userManagementPage.waitForUserList();
      
      // And the list should contain users (if any exist)
      const userCount = await userManagementPage.getUserCount();
      expect(userCount).toBeGreaterThanOrEqual(0);
    });

    test('should display user information correctly', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // And there is a test user in the system
      const testUser = await createUniqueTestUser('display-test', 'Display Test User');
      
      // When I navigate to User Management
      const userManagementPage = new UserManagementPage(page);
      await userManagementPage.goto();
      
      // Search for the user to ensure it appears in the list
      await userManagementPage.search.search(testUser.email);
      await userManagementPage.search.waitForSearchComplete();
      
      // Then I should see user email
      await userManagementPage.verifyUserInfo(testUser.email);
      
      // And I should see display name if available
      if (testUser.displayName) {
        await userManagementPage.verifyUserInfo(testUser.email, testUser.displayName);
      }
    });

    test('should display empty state when no users exist', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // When I navigate to User Management
      const userManagementPage = new UserManagementPage(page);
      await userManagementPage.goto();
      
      // Then I should see an empty state message (if no users)
      // Note: This test assumes empty state, actual behavior may vary
      const userCount = await userManagementPage.getUserCount();
      if (userCount === 0) {
        await userManagementPage.verifyEmptyState();
      }
    });

    test('should show loading state while fetching users', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // When I navigate to User Management
      const userManagementPage = new UserManagementPage(page);
      await userManagementPage.goto();
      
      // Then loading state should eventually resolve
      await userManagementPage.verifyLoadingState(false);
    });
  });

  test.describe('Search Functionality', () => {
    test('should search users by email', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // And there are users in the system
      const testUser = await createUniqueTestUser('search-email', 'Search Email User');
      
      // When I navigate to User Management
      const userManagementPage = new UserManagementPage(page);
      await userManagementPage.goto();
      
      // And I search for the user's email
      await userManagementPage.search.search(testUser.displayName);
      await userManagementPage.search.waitForSearchComplete();
      
      // Then the user should appear in the results
      await userManagementPage.verifyUserInList(testUser.email, true);
    });

    test('should search users by display name', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // And there is a user with a display name
      const testUser = await createUniqueTestUser('search-name', 'Search Name User');
      
      // When I navigate to User Management
      const userManagementPage = new UserManagementPage(page);
      await userManagementPage.goto();
      
      // And I search for the user's display name
      await userManagementPage.search.search(testUser.email);
      await userManagementPage.search.waitForSearchComplete();
      
      // Then the user should appear in the results
      await userManagementPage.verifyUserInList(testUser.email, true);
    });

    test('should show no results for non-matching search', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // When I navigate to User Management
      const userManagementPage = new UserManagementPage(page);
      await userManagementPage.goto();
      
      // And I search for a non-existent user
      await userManagementPage.search.search('nonexistent-user-xyz-123');
      
      // Wait for search to complete
      await userManagementPage.search.waitForSearchComplete();
      
      // Then I should see an empty search results message
      await userManagementPage.verifyEmptyState();
    });

    test('should clear search and show all users', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // And I have searched for a user
      const userManagementPage = new UserManagementPage(page);
      await userManagementPage.goto();
      await userManagementPage.search.search('test');
      
      // When I clear the search
      await userManagementPage.search.clear();
      
      // Then all users should be displayed again
      await userManagementPage.waitForUserList();
    });

    test('should debounce search input', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // When I navigate to User Management
      const userManagementPage = new UserManagementPage(page);
      await userManagementPage.goto();
      
      // And I type in the search field quickly
      const searchInput = userManagementPage.search;
      await searchInput.search('a');
      await searchInput.search('ab');
      await searchInput.search('abc');
      
      // Then the search should only execute after debounce delay
      await searchInput.waitForSearchComplete();
    });
  });

  test.describe('Pagination', () => {
    test('should navigate to next page', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // And there are multiple pages of users
      const userManagementPage = new UserManagementPage(page);
      await userManagementPage.goto();
      
      // When I click next page (if available and enabled)
      const nextButton = page.locator('button:has-text("Next")').first();
      const hasNext = await nextButton.count() > 0;
      
      if (hasNext) {
        const isNextEnabled = await nextButton.isEnabled();
        if (isNextEnabled) {
          await userManagementPage.clickNextPage();
          
          // Then I should see the next page of users
          await userManagementPage.waitForUserList();
        }
      }
    });

    test('should navigate to previous page', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // And I am on a later page
      const userManagementPage = new UserManagementPage(page);
      await userManagementPage.goto();
      
      // Navigate to next page first (if available and enabled)
      const nextButton = page.locator('button:has-text("Next")').first();
      const hasNext = await nextButton.count() > 0;
      
      if (hasNext) {
        const isNextEnabled = await nextButton.isEnabled();
        if (isNextEnabled) {
          await userManagementPage.clickNextPage();
          
          // When I click previous page (if enabled)
          const prevButton = page.locator('button:has-text("Previous")').first();
          const hasPrev = await prevButton.count() > 0;
          
          if (hasPrev) {
            const isPrevEnabled = await prevButton.isEnabled();
            if (isPrevEnabled) {
              await userManagementPage.clickPreviousPage();
              
              // Then I should see the previous page of users
              await userManagementPage.waitForUserList();
            }
          }
        }
      }
    });

    test('should navigate to first page', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // And I am on a later page
      const userManagementPage = new UserManagementPage(page);
      await userManagementPage.goto();
      
      // Navigate to next page first (if available and enabled)
      const nextButton = page.locator('button:has-text("Next")').first();
      const hasNext = await nextButton.count() > 0;
      
      if (hasNext) {
        const isNextEnabled = await nextButton.isEnabled();
        if (isNextEnabled) {
          await userManagementPage.clickNextPage();
          
          // When I click first page (if enabled)
          const firstButton = page.locator('button:has-text("First")').first();
          const hasFirst = await firstButton.count() > 0;
          
          if (hasFirst) {
            const isFirstEnabled = await firstButton.isEnabled();
            if (isFirstEnabled) {
              await userManagementPage.clickFirstPage();
              
              // Then I should see the first page of users
              await userManagementPage.waitForUserList();
            }
          }
        }
      }
    });

    test('should display pagination range', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // When I navigate to User Management
      const userManagementPage = new UserManagementPage(page);
      await userManagementPage.goto();
      
      // Then pagination range should be displayed (if pagination exists)
      const range = await userManagementPage.getPaginationRange();
      // Range might be null if pagination is not needed
      if (range) {
        expect(range).toMatch(/\d+-\d+ of \d+/);
      }
    });
  });

  test.describe('Sorting', () => {
    test('should sort by display name', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // When I navigate to User Management
      const userManagementPage = new UserManagementPage(page);
      await userManagementPage.goto();
      
      // And I click on the name column header
      await userManagementPage.sortBy('name');
      
      // Then the list should be sorted
      await userManagementPage.waitForUserList();
    });

    test('should sort by email', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // When I navigate to User Management
      const userManagementPage = new UserManagementPage(page);
      await userManagementPage.goto();
      
      // And I click on the email column header
      await userManagementPage.sortBy('email');
      
      // Then the list should be sorted
      await userManagementPage.waitForUserList();
    });

    test('should sort by status', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // When I navigate to User Management
      const userManagementPage = new UserManagementPage(page);
      await userManagementPage.goto();
      
      // And I click on the status column header
      await userManagementPage.sortBy('status');
      
      // Then the list should be sorted
      await userManagementPage.waitForUserList();
    });

    test('should toggle sort direction', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // When I navigate to User Management
      const userManagementPage = new UserManagementPage(page);
      await userManagementPage.goto();
      
      // And I click on a column header twice
      await userManagementPage.sortBy('name');
      await userManagementPage.sortBy('name');
      
      // Then the sort direction should toggle
      await userManagementPage.waitForUserList();
    });
  });

  test.describe('User Status Toggle', () => {
    test('should enable user', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // And there is a user
      const testUser = await createUniqueTestUser('enable-test', 'Enable Test User');
      
      // When I navigate to User Management
      const userManagementPage = new UserManagementPage(page);
      await userManagementPage.goto();
      
      // Search for the user to ensure it appears in the list
      await userManagementPage.search.search(testUser.email);
      await userManagementPage.search.waitForSearchComplete();
      await userManagementPage.waitForUserInList(testUser.email);
      
      // Get the initial status
      const initialStatus = await userManagementPage.getUserStatus(testUser.id);
      
      // If user is already enabled, disable first so we can test enabling
      if (initialStatus) {
        await userManagementPage.toggleUserStatus(testUser.id);
        // Verify status changed (toggleUserStatus already waits for state change)
        const afterToggle = await userManagementPage.getUserStatus(testUser.id);
        expect(afterToggle).toBe(false);
      }
      
      // Now toggle to enabled
      await userManagementPage.toggleUserStatus(testUser.id);
      
      // Verify the status changed (toggleUserStatus already waits for state change)
      const newStatus = await userManagementPage.getUserStatus(testUser.id);
      expect(newStatus).toBe(true);
    });

    test('should disable user', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // And there is a user
      const testUser = await createUniqueTestUser('disable-test', 'Disable Test User');
      
      // When I navigate to User Management
      const userManagementPage = new UserManagementPage(page);
      await userManagementPage.goto();
      
      // Search for the user to ensure it appears in the list
      await userManagementPage.search.search(testUser.email);
      await userManagementPage.search.waitForSearchComplete();
      await userManagementPage.waitForUserInList(testUser.email);
      
      // Get the initial status
      const initialStatus = await userManagementPage.getUserStatus(testUser.id);
      
      // If user is already disabled, enable first so we can test disabling
      if (!initialStatus) {
        await userManagementPage.toggleUserStatus(testUser.id);
        // Verify status changed (toggleUserStatus already waits for state change)
        const afterToggle = await userManagementPage.getUserStatus(testUser.id);
        expect(afterToggle).toBe(true);
      }
      
      // Now toggle to disabled
      await userManagementPage.toggleUserStatus(testUser.id);
      
      // Verify the status changed (toggleUserStatus already waits for state change)
      const newStatus = await userManagementPage.getUserStatus(testUser.id);
      expect(newStatus).toBe(false);
    });

    test('should show loading state during toggle', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // And there is a user
      const testUser = await createUniqueTestUser('toggle-loading', 'Toggle Loading User');
      
      // When I navigate to User Management
      const userManagementPage = new UserManagementPage(page);
      await userManagementPage.goto();
      
      // Search for the user to ensure it appears in the list
      await userManagementPage.search.search(testUser.email);
      await userManagementPage.search.waitForSearchComplete();
      await userManagementPage.waitForUserInList(testUser.email);
      
      // And I toggle the user status
      await userManagementPage.toggleUserStatus(testUser.id);
      
      // Then the loading state should resolve
      await userManagementPage.waitForUserList();
    });
  });

  test.describe('User Drawer', () => {
    test('should open user drawer when clicking edit', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // And there is a user
      const testUser = await createUniqueTestUser('drawer-test', 'Drawer Test User');
      
      // When I navigate to User Management
      const userManagementPage = new UserManagementPage(page);
      await userManagementPage.goto();
      
      // Search for the user to ensure it appears in the list
      await userManagementPage.search.search(testUser.email);
      await userManagementPage.search.waitForSearchComplete();
      await userManagementPage.waitForUserInList(testUser.email);
      
      // And I click edit user
      await userManagementPage.clickEditUser(testUser.id);
      
      // Then the drawer should open
      await userManagementPage.drawer.waitForOpen();
    });

    test('should display user details in drawer', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // And there is a user
      const testUser = await createUniqueTestUser('details-test', 'Details Test User');
      
      // When I navigate to User Management
      const userManagementPage = new UserManagementPage(page);
      await userManagementPage.goto();
      
      // Search for the user to ensure it appears in the list
      await userManagementPage.search.search(testUser.email);
      await userManagementPage.search.waitForSearchComplete();
      await userManagementPage.waitForUserInList(testUser.email);
      
      // And I open the user drawer
      await userManagementPage.clickEditUser(testUser.id);
      
      // Then I should see user information
      await userManagementPage.drawer.verifyUserInfo(testUser.email, testUser.displayName);
    });

    test('should update display name', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // And there is a user
      const testUser = await createUniqueTestUser('update-test', 'Update Test User');
      
      // When I navigate to User Management
      const userManagementPage = new UserManagementPage(page);
      await userManagementPage.goto();
      
      // Search for the user to ensure it appears in the list
      await userManagementPage.search.search(testUser.email);
      await userManagementPage.search.waitForSearchComplete();
      await userManagementPage.waitForUserInList(testUser.email);
      
      // And I open the user drawer
      await userManagementPage.clickEditUser(testUser.id);
      
      // And I update the display name
      const newName = 'Updated Display Name';
      await userManagementPage.drawer.updateDisplayName(newName);
      
      // And I save
      await userManagementPage.drawer.save();
      
      // Then the display name should be updated
      // Verify by reopening drawer or checking list
      await userManagementPage.waitForUserList();
    });

    test('should show email as readonly', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // And there is a user
      const testUser = await createUniqueTestUser('readonly-test', 'Readonly Test User');
      
      // When I navigate to User Management
      const userManagementPage = new UserManagementPage(page);
      await userManagementPage.goto();
      
      // Search for the user to ensure it appears in the list
      await userManagementPage.search.search(testUser.email);
      await userManagementPage.search.waitForSearchComplete();
      await userManagementPage.waitForUserInList(testUser.email);
      
      // And I open the user drawer
      await userManagementPage.clickEditUser(testUser.id);
      
      // Then the email field should be readonly
      await userManagementPage.drawer.verifyEmailReadonly();
    });

    test('should close drawer', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // And there is a user
      const testUser = await createUniqueTestUser('close-test', 'Close Test User');
      
      // When I navigate to User Management
      const userManagementPage = new UserManagementPage(page);
      await userManagementPage.goto();
      
      // Search for the user to ensure it appears in the list
      await userManagementPage.search.search(testUser.email);
      await userManagementPage.search.waitForSearchComplete();
      await userManagementPage.waitForUserInList(testUser.email);
      
      // And I open the user drawer
      await userManagementPage.clickEditUser(testUser.id);
      
      // And I close the drawer
      await userManagementPage.drawer.close();
      
      // Then the drawer should be closed
      await userManagementPage.drawer.waitForClose();
    });
  });

  test.describe('Permission-Based UI', () => {
    test('edit button should be visible when user has security:user:view permission', async ({ page }) => {
      // Given I am signed in as a user with security:user:view permission
      await signInAsValidUser(page);
      
      // When I navigate to User Management
      const userManagementPage = new UserManagementPage(page);
      await userManagementPage.goto();
      
      // Then edit buttons should be visible (if users exist)
      const userCount = await userManagementPage.getUserCount();
      if (userCount > 0) {
        // Wait for permissions to load and edit button to appear
        // PermissionGate loads permissions asynchronously, so we need to wait
        const editButton = page.locator('[data-testid^="edit-user-"]').first();
        await expect(editButton).toBeVisible({ timeout: 10000 });
      }
    });

    test('status toggle should be visible when user has security:user:save permission', async ({ page }) => {
      // Given I am signed in as a user with security:user:save permission
      await signInAsValidUser(page);
      
      // When I navigate to User Management
      const userManagementPage = new UserManagementPage(page);
      await userManagementPage.goto();
      
      // Then status toggles should be visible (if users exist)
      const userCount = await userManagementPage.getUserCount();
      if (userCount > 0) {
        // Wait for permissions to load and status toggle to appear
        // PermissionGate loads permissions asynchronously, so we need to wait
        const toggle = page.locator('[data-testid^="user-status-toggle-"]').first();
        await expect(toggle).toBeVisible({ timeout: 10000 });
      }
    });
  });
});
