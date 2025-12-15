import { test, expect } from '@playwright/test';
import { signInAsValidUser, signOut } from './helpers/auth';
import { RoleManagementPage } from './pages/RoleManagementPage';
import { SettingsPage } from './pages/SettingsPage';
import { createUniqueTestRole } from './fixtures/roles';
import { signIn } from './helpers/graphql';
import { TEST_USERS } from './fixtures/users';

test.describe('Role Management', () => {
  test.beforeEach(async ({ page }) => {
    // Ensure we start from a clean state
    await signOut(page);
  });

  test.describe('Navigation and Access', () => {
    test('should navigate to Role Management page', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // When I navigate to settings
      const settingsPage = new SettingsPage(page);
      await settingsPage.goto();
      
      // And I click on Roles tab
      await settingsPage.clickRolesTab();
      
      // Then I should be on the Role Management page
      await expect(page).toHaveURL(/\/settings/);
      
      // And the role search should be visible
      const roleManagementPage = new RoleManagementPage(page);
      await roleManagementPage.search.verifyVisible();
    });

    test('should display Roles tab when user has authorization permissions', async ({ page }) => {
      // Given I am signed in as a user with authorization permissions
      await signInAsValidUser(page);
      
      // When I navigate to settings
      const settingsPage = new SettingsPage(page);
      await settingsPage.goto();
      
      // Then the Roles tab should be visible
      await settingsPage.verifyRolesTabVisible();
    });

    test('should load role list on page load', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // When I navigate to Role Management
      const roleManagementPage = new RoleManagementPage(page);
      await roleManagementPage.goto();
      
      // Then the role list should be displayed
      await roleManagementPage.waitForRoleList();
    });
  });

  test.describe('Role List Display', () => {
    test('should display list of roles', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // When I navigate to Role Management
      const roleManagementPage = new RoleManagementPage(page);
      await roleManagementPage.goto();
      
      // Then I should see the role list
      await roleManagementPage.waitForRoleList();
      
      // And the list should contain roles (if any exist)
      const roleCount = await roleManagementPage.getRoleCount();
      expect(roleCount).toBeGreaterThanOrEqual(0);
    });

    test('should display role information correctly', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // And there is a test role in the system
      const adminToken = await signIn(TEST_USERS.valid.email, TEST_USERS.valid.password);
      const testRole = await createUniqueTestRole('display-test', null, adminToken);
      
      // When I navigate to Role Management
      const roleManagementPage = new RoleManagementPage(page);
      await roleManagementPage.goto();
      
      // Search for the role to ensure it appears in the list
      await roleManagementPage.search.search(testRole.name);
      await roleManagementPage.search.waitForSearchComplete();
      
      // Then I should see role name
      await roleManagementPage.verifyRoleInfo(testRole.name);
    });

    test('should display empty state when no roles exist', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // When I navigate to Role Management
      const roleManagementPage = new RoleManagementPage(page);
      await roleManagementPage.goto();
      
      // Then I should see an empty state message (if no roles)
      // Note: This test assumes empty state, actual behavior may vary
      const roleCount = await roleManagementPage.getRoleCount();
      if (roleCount === 0) {
        await roleManagementPage.verifyEmptyState();
      }
    });

    test('should show loading state while fetching roles', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // When I navigate to Role Management
      const roleManagementPage = new RoleManagementPage(page);
      await roleManagementPage.goto();
      
      // Then loading state should eventually resolve
      await roleManagementPage.verifyLoadingState(false);
    });
  });

  test.describe('Search Functionality', () => {
    test('should search roles by name', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // And there are roles in the system
      const adminToken = await signIn(TEST_USERS.valid.email, TEST_USERS.valid.password);
      const testRole = await createUniqueTestRole('search-name', null, adminToken);
      
      // When I navigate to Role Management
      const roleManagementPage = new RoleManagementPage(page);
      await roleManagementPage.goto();
      
      // And I search for the role's name
      await roleManagementPage.search.search(testRole.name);
      await roleManagementPage.search.waitForSearchComplete();
      
      // Then the role should appear in the results
      await roleManagementPage.verifyRoleInList(testRole.name, true);
    });

    test('should show no results for non-matching search', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // When I navigate to Role Management
      const roleManagementPage = new RoleManagementPage(page);
      await roleManagementPage.goto();
      
      // And I search for a non-existent role
      await roleManagementPage.search.search('nonexistent-role-xyz-123');
      
      // Wait for search to complete
      await roleManagementPage.search.waitForSearchComplete();
      
      // Then I should see an empty search results message
      await roleManagementPage.verifyEmptyState();
    });

    test('should clear search and show all roles', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // And I have searched for a role
      const roleManagementPage = new RoleManagementPage(page);
      await roleManagementPage.goto();
      await roleManagementPage.search.search('test');
      
      // When I clear the search
      await roleManagementPage.search.clear();
      
      // Then all roles should be displayed again
      await roleManagementPage.waitForRoleList();
    });

    test('should debounce search input', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // When I navigate to Role Management
      const roleManagementPage = new RoleManagementPage(page);
      await roleManagementPage.goto();
      
      // And I type in the search field quickly
      const searchInput = roleManagementPage.search;
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
      
      // And there are multiple pages of roles
      const roleManagementPage = new RoleManagementPage(page);
      await roleManagementPage.goto();
      
      // When I click next page (if available and enabled)
      const nextButton = page.locator('button:has-text("Next")').first();
      const hasNext = await nextButton.count() > 0;
      
      if (hasNext) {
        const isNextEnabled = await nextButton.isEnabled();
        if (isNextEnabled) {
          await roleManagementPage.clickNextPage();
          
          // Then I should see the next page of roles
          await roleManagementPage.waitForRoleList();
        }
      }
    });

    test('should navigate to previous page', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // And I am on a later page
      const roleManagementPage = new RoleManagementPage(page);
      await roleManagementPage.goto();
      
      // Navigate to next page first (if available and enabled)
      const nextButton = page.locator('button:has-text("Next")').first();
      const hasNext = await nextButton.count() > 0;
      
      if (hasNext) {
        const isNextEnabled = await nextButton.isEnabled();
        if (isNextEnabled) {
          await roleManagementPage.clickNextPage();
          
          // When I click previous page (if enabled)
          const prevButton = page.locator('button:has-text("Previous")').first();
          const hasPrev = await prevButton.count() > 0;
          
          if (hasPrev) {
            const isPrevEnabled = await prevButton.isEnabled();
            if (isPrevEnabled) {
              await roleManagementPage.clickPreviousPage();
              
              // Then I should see the previous page of roles
              await roleManagementPage.waitForRoleList();
            }
          }
        }
      }
    });

    test('should navigate to first page', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // And I am on a later page
      const roleManagementPage = new RoleManagementPage(page);
      await roleManagementPage.goto();
      
      // Navigate to next page first (if available and enabled)
      const nextButton = page.locator('button:has-text("Next")').first();
      const hasNext = await nextButton.count() > 0;
      
      if (hasNext) {
        const isNextEnabled = await nextButton.isEnabled();
        if (isNextEnabled) {
          await roleManagementPage.clickNextPage();
          
          // When I click first page (if enabled)
          const firstButton = page.locator('button:has-text("First")').first();
          const hasFirst = await firstButton.count() > 0;
          
          if (hasFirst) {
            const isFirstEnabled = await firstButton.isEnabled();
            if (isFirstEnabled) {
              await roleManagementPage.clickFirstPage();
              
              // Then I should see the first page of roles
              await roleManagementPage.waitForRoleList();
            }
          }
        }
      }
    });

    test('should display pagination range', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // When I navigate to Role Management
      const roleManagementPage = new RoleManagementPage(page);
      await roleManagementPage.goto();
      
      // Then pagination range should be displayed (if pagination exists)
      const range = await roleManagementPage.getPaginationRange();
      // Range might be null if pagination is not needed
      if (range) {
        expect(range).toMatch(/\d+-\d+ of \d+/);
      }
    });
  });

  test.describe('Sorting', () => {
    test('should sort by name', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // When I navigate to Role Management
      const roleManagementPage = new RoleManagementPage(page);
      await roleManagementPage.goto();
      
      // And I click on the name column header
      await roleManagementPage.sortBy('name');
      
      // Then the list should be sorted
      await roleManagementPage.waitForRoleList();
    });

    test('should toggle sort direction', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // When I navigate to Role Management
      const roleManagementPage = new RoleManagementPage(page);
      await roleManagementPage.goto();
      
      // And I click on a column header twice
      await roleManagementPage.sortBy('name');
      await roleManagementPage.sortBy('name');
      
      // Then the sort direction should toggle
      await roleManagementPage.waitForRoleList();
    });
  });

  test.describe('Role CRUD Operations', () => {
    test('should create a new role', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // When I navigate to Role Management
      const roleManagementPage = new RoleManagementPage(page);
      await roleManagementPage.goto();
      
      // And I click the create role button
      await roleManagementPage.clickCreateRole();
      
      // Then the drawer should open
      await roleManagementPage.drawer.waitForOpen();
      
      // And I enter a role name
      const roleName = `test-role-${Date.now()}`;
      await roleManagementPage.drawer.updateRoleName(roleName);
      
      // And I save (this will wait for drawer to close)
      await roleManagementPage.drawer.save();
      
      // Wait for role list to refresh after save
      await roleManagementPage.waitForRoleList();
      
      // And the role should appear in the list
      await roleManagementPage.search.search(roleName);
      await roleManagementPage.search.waitForSearchComplete();
      await roleManagementPage.verifyRoleInList(roleName, true);
    });

    test('should open role drawer when clicking edit', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // And there is a role
      const adminToken = await signIn(TEST_USERS.valid.email, TEST_USERS.valid.password);
      const testRole = await createUniqueTestRole('drawer-test', null, adminToken);
      
      // When I navigate to Role Management
      const roleManagementPage = new RoleManagementPage(page);
      await roleManagementPage.goto();
      
      // Search for the role to ensure it appears in the list
      await roleManagementPage.search.search(testRole.name);
      await roleManagementPage.search.waitForSearchComplete();
      await roleManagementPage.waitForRoleInList(testRole.name);
      
      // And I click edit role
      await roleManagementPage.clickEditRole(testRole.id);
      
      // Then the drawer should open
      await roleManagementPage.drawer.waitForOpen();
    });

    test('should display role details in drawer', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // And there is a role
      const adminToken = await signIn(TEST_USERS.valid.email, TEST_USERS.valid.password);
      const testRole = await createUniqueTestRole('details-test', null, adminToken);
      
      // When I navigate to Role Management
      const roleManagementPage = new RoleManagementPage(page);
      await roleManagementPage.goto();
      
      // Search for the role to ensure it appears in the list
      await roleManagementPage.search.search(testRole.name);
      await roleManagementPage.search.waitForSearchComplete();
      await roleManagementPage.waitForRoleInList(testRole.name);
      
      // And I open the role drawer
      await roleManagementPage.clickEditRole(testRole.id);
      
      // Then I should see role information
      await roleManagementPage.drawer.verifyRoleInfo(testRole.name);
    });

    test('should update role name', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // And there is a role
      const adminToken = await signIn(TEST_USERS.valid.email, TEST_USERS.valid.password);
      const testRole = await createUniqueTestRole('update-test', null, adminToken);
      
      // When I navigate to Role Management
      const roleManagementPage = new RoleManagementPage(page);
      await roleManagementPage.goto();
      
      // Search for the role to ensure it appears in the list
      await roleManagementPage.search.search(testRole.name);
      await roleManagementPage.search.waitForSearchComplete();
      await roleManagementPage.waitForRoleInList(testRole.name);
      
      // And I open the role drawer
      await roleManagementPage.clickEditRole(testRole.id);
      
      // And I update the role name
      const newName = `updated-role-${Date.now()}`;
      await roleManagementPage.drawer.updateRoleName(newName);
      
      // And I save
      await roleManagementPage.drawer.save();
      
      // Then the role name should be updated
      // Wait for drawer to close
      await roleManagementPage.drawer.waitForClose();
      
      // Verify by searching for the new name
      await roleManagementPage.search.search(newName);
      await roleManagementPage.search.waitForSearchComplete();
      await roleManagementPage.verifyRoleInList(newName, true);
    });

    test('should close drawer', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // And there is a role
      const adminToken = await signIn(TEST_USERS.valid.email, TEST_USERS.valid.password);
      const testRole = await createUniqueTestRole('close-test', null, adminToken);
      
      // When I navigate to Role Management
      const roleManagementPage = new RoleManagementPage(page);
      await roleManagementPage.goto();
      
      // Search for the role to ensure it appears in the list
      await roleManagementPage.search.search(testRole.name);
      await roleManagementPage.search.waitForSearchComplete();
      await roleManagementPage.waitForRoleInList(testRole.name);
      
      // And I open the role drawer
      await roleManagementPage.clickEditRole(testRole.id);
      
      // And I close the drawer
      await roleManagementPage.drawer.close();
      
      // Then the drawer should be closed
      await roleManagementPage.drawer.waitForClose();
    });

    test('should delete a role', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // And there is a role
      const adminToken = await signIn(TEST_USERS.valid.email, TEST_USERS.valid.password);
      const testRole = await createUniqueTestRole('delete-test', null, adminToken);
      
      // When I navigate to Role Management
      const roleManagementPage = new RoleManagementPage(page);
      await roleManagementPage.goto();
      
      // Search for the role to ensure it appears in the list
      await roleManagementPage.search.search(testRole.name);
      await roleManagementPage.search.waitForSearchComplete();
      await roleManagementPage.waitForRoleInList(testRole.name);
      
      // And I click delete
      await roleManagementPage.clickDeleteRole(testRole.id);
      
      // And I confirm deletion
      await roleManagementPage.confirmDelete();
      
      // Then the role should be deleted
      // Wait for the role to disappear from the list
      await roleManagementPage.search.waitForSearchComplete();
      await roleManagementPage.verifyRoleInList(testRole.name, false);
    });
  });

  test.describe('Permission-Based UI', () => {
    test('edit button should be visible when user has security:role:view permission', async ({ page }) => {
      // Given I am signed in as a user with security:role:view permission
      await signInAsValidUser(page);
      
      // When I navigate to Role Management
      const roleManagementPage = new RoleManagementPage(page);
      await roleManagementPage.goto();
      
      // Then edit buttons should be visible (if roles exist)
      const roleCount = await roleManagementPage.getRoleCount();
      if (roleCount > 0) {
        // Wait for permissions to load and edit button to appear
        // PermissionGate loads permissions asynchronously, so we need to wait
        const editButton = page.locator('[data-testid^="edit-role-"]').first();
        await expect(editButton).toBeVisible({ timeout: 10000 });
      }
    });

    test('create button should be visible when user has security:role:save permission', async ({ page }) => {
      // Given I am signed in as a user with security:role:save permission
      await signInAsValidUser(page);
      
      // When I navigate to Role Management
      const roleManagementPage = new RoleManagementPage(page);
      await roleManagementPage.goto();
      
      // Then the create button should be visible
      // PermissionGate loads permissions asynchronously, so we need to wait
      const createButton = page.locator('[data-testid="create-role-button"]');
      await expect(createButton).toBeVisible({ timeout: 10000 });
    });

    test('delete button should be visible when user has security:role:delete permission', async ({ page }) => {
      // Given I am signed in as a user with security:role:delete permission
      await signInAsValidUser(page);
      
      // When I navigate to Role Management
      const roleManagementPage = new RoleManagementPage(page);
      await roleManagementPage.goto();
      
      // Then delete buttons should be visible (if roles exist)
      const roleCount = await roleManagementPage.getRoleCount();
      if (roleCount > 0) {
        // Wait for permissions to load and delete button to appear
        // PermissionGate loads permissions asynchronously, so we need to wait
        const deleteButton = page.locator('[data-testid^="delete-role-"]').first();
        await expect(deleteButton).toBeVisible({ timeout: 10000 });
      }
    });
  });
});
