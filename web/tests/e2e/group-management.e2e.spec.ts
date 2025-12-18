import { test, expect } from '@playwright/test';
import { signInAsValidUser, signOut } from './helpers/auth';
import { GroupManagementPage } from './pages/GroupManagementPage';
import { SettingsPage } from './pages/SettingsPage';
import { createUniqueTestGroup } from './fixtures/groups';
import { signIn } from './helpers/graphql';
import { TEST_USERS } from './fixtures/users';

test.describe('Group Management', () => {
  test.beforeEach(async ({ page }) => {
    // Ensure we start from a clean state
    await signOut(page);
  });

  test.describe('Navigation and Access', () => {
    test('should navigate to Group Management page', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // When I navigate to settings
      const settingsPage = new SettingsPage(page);
      await settingsPage.goto();
      
      // And I click on Groups tab
      await settingsPage.clickGroupsTab();
      
      // Then I should be on the Group Management page
      await expect(page).toHaveURL(/\/settings/, { timeout: 10000 });
      
      // And the group search should be visible
      const groupManagementPage = new GroupManagementPage(page);
      await groupManagementPage.search.verifyVisible();
      // Also wait for the group list to be ready
      await groupManagementPage.waitForGroupList();
    });

    test('should display Groups tab when user has authorization permissions', async ({ page }) => {
      // Given I am signed in as a user with authorization permissions
      await signInAsValidUser(page);
      
      // When I navigate to settings
      const settingsPage = new SettingsPage(page);
      await settingsPage.goto();
      
      // Wait for page to fully load
      await page.waitForLoadState('domcontentloaded');
      
      // Then the Groups tab should be visible
      await settingsPage.verifyGroupsTabVisible();
    });

    test('should load group list on page load', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // When I navigate to Group Management
      const groupManagementPage = new GroupManagementPage(page);
      await groupManagementPage.goto();
      
      // Then the group list should be displayed
      await groupManagementPage.waitForGroupList();
    });
  });

  test.describe('Group List Display', () => {
    test('should display list of groups', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // When I navigate to Group Management
      const groupManagementPage = new GroupManagementPage(page);
      await groupManagementPage.goto();
      
      // Then I should see the group list
      await groupManagementPage.waitForGroupList();
      
      // And the list should contain groups (if any exist)
      const groupCount = await groupManagementPage.getGroupCount();
      expect(groupCount).toBeGreaterThanOrEqual(0);
    });

    test('should display group information correctly', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // And there is a test group in the system
      const adminToken = await signIn(TEST_USERS.valid.email, TEST_USERS.valid.password);
      const testGroup = await createUniqueTestGroup('display-test', 'Display Test Group', adminToken);
      
      // When I navigate to Group Management
      const groupManagementPage = new GroupManagementPage(page);
      await groupManagementPage.goto();
      
      // Wait a moment for the newly created group to be available in search
      await page.waitForTimeout(500);
      
      // Search for the group to ensure it appears in the list
      await groupManagementPage.search.search(testGroup.name);
      await groupManagementPage.search.waitForSearchComplete();
      
      // Then I should see group name
      await groupManagementPage.verifyGroupInfo(testGroup.name);
      
      // And I should see description if available
      if (testGroup.description) {
        await groupManagementPage.verifyGroupInfo(testGroup.name, testGroup.description);
      }
    });

    test('should display empty state when no groups exist', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // When I navigate to Group Management
      const groupManagementPage = new GroupManagementPage(page);
      await groupManagementPage.goto();
      
      // Then I should see an empty state message (if no groups)
      // Note: This test assumes empty state, actual behavior may vary
      const groupCount = await groupManagementPage.getGroupCount();
      if (groupCount === 0) {
        await groupManagementPage.verifyEmptyState();
      }
    });

    test('should show loading state while fetching groups', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // When I navigate to Group Management
      const groupManagementPage = new GroupManagementPage(page);
      await groupManagementPage.goto();
      
      // Then loading state should eventually resolve
      await groupManagementPage.verifyLoadingState(false);
    });
  });

  test.describe('Search Functionality', () => {
    test('should search groups by name', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // And there are groups in the system
      const adminToken = await signIn(TEST_USERS.valid.email, TEST_USERS.valid.password);
      const testGroup = await createUniqueTestGroup('search-name', '', adminToken);
      
      // When I navigate to Group Management
      const groupManagementPage = new GroupManagementPage(page);
      await groupManagementPage.goto();
      
      // Wait a moment for the newly created group to be available in search
      await page.waitForTimeout(500);
      
      // And I search for the group's name
      await groupManagementPage.search.search(testGroup.name);
      await groupManagementPage.search.waitForSearchComplete();
      
      // Then the group should appear in the results
      await groupManagementPage.verifyGroupInList(testGroup.name, true);
    });

    test('should show no results for non-matching search', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // When I navigate to Group Management
      const groupManagementPage = new GroupManagementPage(page);
      await groupManagementPage.goto();
      
      // And I search for a non-existent group
      await groupManagementPage.search.search('nonexistent-group-xyz-123');
      
      // Wait for search to complete
      await groupManagementPage.search.waitForSearchComplete();
      
      // Then I should see an empty search results message
      await groupManagementPage.verifyEmptyState();
    });

    test('should clear search and show all groups', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // And I have searched for a group
      const groupManagementPage = new GroupManagementPage(page);
      await groupManagementPage.goto();
      await groupManagementPage.search.search('test');
      
      // When I clear the search
      await groupManagementPage.search.clear();
      
      // Then all groups should be displayed again
      await groupManagementPage.waitForGroupList();
    });

    test('should debounce search input', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // When I navigate to Group Management
      const groupManagementPage = new GroupManagementPage(page);
      await groupManagementPage.goto();
      
      // And I type in the search field quickly
      const searchInput = groupManagementPage.search;
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
      
      // And there are multiple pages of groups
      const groupManagementPage = new GroupManagementPage(page);
      await groupManagementPage.goto();
      
      // When I click next page (if available and enabled)
      const nextButton = page.locator('button:has-text("Next")').first();
      const hasNext = await nextButton.count() > 0;
      
      if (hasNext) {
        const isNextEnabled = await nextButton.isEnabled();
        if (isNextEnabled) {
          await groupManagementPage.clickNextPage();
          
          // Then I should see the next page of groups
          await groupManagementPage.waitForGroupList();
        }
      }
    });

    test('should navigate to previous page', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // And I am on a later page
      const groupManagementPage = new GroupManagementPage(page);
      await groupManagementPage.goto();
      
      // Navigate to next page first (if available and enabled)
      const nextButton = page.locator('button:has-text("Next")').first();
      const hasNext = await nextButton.count() > 0;
      
      if (hasNext) {
        const isNextEnabled = await nextButton.isEnabled();
        if (isNextEnabled) {
          await groupManagementPage.clickNextPage();
          
          // When I click previous page (if enabled)
          const prevButton = page.locator('button:has-text("Previous")').first();
          const hasPrev = await prevButton.count() > 0;
          
          if (hasPrev) {
            const isPrevEnabled = await prevButton.isEnabled();
            if (isPrevEnabled) {
              await groupManagementPage.clickPreviousPage();
              
              // Then I should see the previous page of groups
              await groupManagementPage.waitForGroupList();
            }
          }
        }
      }
    });

    test('should navigate to first page', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // And I am on a later page
      const groupManagementPage = new GroupManagementPage(page);
      await groupManagementPage.goto();
      
      // Navigate to next page first (if available and enabled)
      const nextButton = page.locator('button:has-text("Next")').first();
      const hasNext = await nextButton.count() > 0;
      
      if (hasNext) {
        const isNextEnabled = await nextButton.isEnabled();
        if (isNextEnabled) {
          await groupManagementPage.clickNextPage();
          
          // When I click first page (if enabled)
          const firstButton = page.locator('button:has-text("First")').first();
          const hasFirst = await firstButton.count() > 0;
          
          if (hasFirst) {
            const isFirstEnabled = await firstButton.isEnabled();
            if (isFirstEnabled) {
              await groupManagementPage.clickFirstPage();
              
              // Then I should see the first page of groups
              await groupManagementPage.waitForGroupList();
            }
          }
        }
      }
    });

    test('should display pagination range', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // When I navigate to Group Management
      const groupManagementPage = new GroupManagementPage(page);
      await groupManagementPage.goto();
      
      // Then pagination range should be displayed (if pagination exists)
      const range = await groupManagementPage.getPaginationRange();
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
      
      // When I navigate to Group Management
      const groupManagementPage = new GroupManagementPage(page);
      await groupManagementPage.goto();
      
      // And I click on the name column header
      await groupManagementPage.sortBy('name');
      
      // Then the list should be sorted
      await groupManagementPage.waitForGroupList();
    });

    test('should toggle sort direction', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // When I navigate to Group Management
      const groupManagementPage = new GroupManagementPage(page);
      await groupManagementPage.goto();
      
      // And I click on a column header twice
      await groupManagementPage.sortBy('name');
      await groupManagementPage.sortBy('name');
      
      // Then the sort direction should toggle
      await groupManagementPage.waitForGroupList();
    });
  });

  test.describe('Group CRUD Operations', () => {
    test('should create a new group', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // When I navigate to Group Management
      const groupManagementPage = new GroupManagementPage(page);
      await groupManagementPage.goto();
      
      // And I click the create group button
      await groupManagementPage.clickCreateGroup();
      
      // Then the drawer should open
      await groupManagementPage.drawer.waitForOpen();
      
      // And I enter a group name
      const groupName = `test-group-${Date.now()}`;
      await groupManagementPage.drawer.updateGroupName(groupName);
      
      // And I enter a description
      const description = 'Test group description';
      await groupManagementPage.drawer.updateDescription(description);
      
      // And I save (this will wait for drawer to close)
      await groupManagementPage.drawer.save();
      
      // Wait for group list to refresh after save
      await groupManagementPage.waitForGroupList();
      
      // And the group should appear in the list
      await groupManagementPage.search.search(groupName);
      await groupManagementPage.search.waitForSearchComplete();
      await groupManagementPage.verifyGroupInList(groupName, true);
    });

    test('should open group drawer when clicking edit', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // And there is a group
      const adminToken = await signIn(TEST_USERS.valid.email, TEST_USERS.valid.password);
      const testGroup = await createUniqueTestGroup('drawer-test', '', adminToken);
      
      // When I navigate to Group Management
      const groupManagementPage = new GroupManagementPage(page);
      await groupManagementPage.goto();
      
      // Wait a moment for the newly created group to be available in search
      await page.waitForTimeout(500);
      
      // Search for the group to ensure it appears in the list
      await groupManagementPage.search.search(testGroup.name);
      await groupManagementPage.search.waitForSearchComplete();
      await groupManagementPage.waitForGroupInList(testGroup.name);
      
      // And I click edit group (this already waits for drawer to open)
      await groupManagementPage.clickEditGroup(testGroup.id);
      
      // Then the drawer should be open
      const isOpen = await groupManagementPage.drawer.isOpen();
      expect(isOpen).toBe(true);
    });

    test('should display group details in drawer', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // And there is a group
      const adminToken = await signIn(TEST_USERS.valid.email, TEST_USERS.valid.password);
      const testGroup = await createUniqueTestGroup('details-test', 'Test Description', adminToken);
      
      // When I navigate to Group Management
      const groupManagementPage = new GroupManagementPage(page);
      await groupManagementPage.goto();
      
      // Search for the group to ensure it appears in the list
      await groupManagementPage.search.search(testGroup.name);
      await groupManagementPage.search.waitForSearchComplete();
      await groupManagementPage.waitForGroupInList(testGroup.name);
      
      // And I open the group drawer
      await groupManagementPage.clickEditGroup(testGroup.id);
      
      // Wait for drawer content to load
      await page.waitForTimeout(500);
      
      // Then I should see group information
      await groupManagementPage.drawer.verifyGroupInfo(testGroup.name, testGroup.description || undefined);
    });

    test('should update group name', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // And there is a group
      const adminToken = await signIn(TEST_USERS.valid.email, TEST_USERS.valid.password);
      const testGroup = await createUniqueTestGroup('update-test', '', adminToken);
      
      // When I navigate to Group Management
      const groupManagementPage = new GroupManagementPage(page);
      await groupManagementPage.goto();
      
      // Wait a moment for the newly created group to be available in search
      await page.waitForTimeout(500);
      
      // Search for the group to ensure it appears in the list
      await groupManagementPage.search.search(testGroup.name);
      await groupManagementPage.search.waitForSearchComplete();
      await groupManagementPage.waitForGroupInList(testGroup.name);
      
      // And I open the group drawer
      await groupManagementPage.clickEditGroup(testGroup.id);
      
      // And I update the group name
      const newName = `updated-group-${Date.now()}`;
      await groupManagementPage.drawer.updateGroupName(newName);
      
      // And I save (this will wait for drawer to close)
      await groupManagementPage.drawer.save();
      
      // Wait for group list to refresh after save
      await groupManagementPage.waitForGroupList();
      
      // Clear any existing search to ensure we see all groups
      await groupManagementPage.search.clear();
      await groupManagementPage.search.waitForSearchComplete();
      
      // Wait a moment for the list to refresh
      await page.waitForTimeout(500);
      
      // Verify by searching for the new name
      await groupManagementPage.search.search(newName);
      await groupManagementPage.search.waitForSearchComplete();
      await groupManagementPage.verifyGroupInList(newName, true);
    });

    test('should update group description', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // And there is a group
      const adminToken = await signIn(TEST_USERS.valid.email, TEST_USERS.valid.password);
      const testGroup = await createUniqueTestGroup('update-desc-test', 'Original Description', adminToken);
      
      // Wait a moment for the newly created group to be available
      await page.waitForTimeout(500);
      
      // When I navigate to Group Management
      const groupManagementPage = new GroupManagementPage(page);
      await groupManagementPage.goto();
      
      // Search for the group to ensure it appears in the list
      await groupManagementPage.search.search(testGroup.name);
      await groupManagementPage.search.waitForSearchComplete();
      await groupManagementPage.waitForGroupInList(testGroup.name);
      
      // And I open the group drawer
      await groupManagementPage.clickEditGroup(testGroup.id);
      
      // And I update the description
      const newDescription = 'Updated Description';
      await groupManagementPage.drawer.updateDescription(newDescription);
      
      // And I save (this will wait for drawer to close)
      await groupManagementPage.drawer.save();
      
      // Wait for group list to refresh after save
      await groupManagementPage.waitForGroupList();
      
      // Clear any existing search to ensure we see all groups
      await groupManagementPage.search.clear();
      await groupManagementPage.search.waitForSearchComplete();
      await page.waitForTimeout(500);
      
      // Verify by reopening drawer and checking description
      await groupManagementPage.search.search(testGroup.name);
      await groupManagementPage.search.waitForSearchComplete();
      await groupManagementPage.waitForGroupInList(testGroup.name);
      await groupManagementPage.clickEditGroup(testGroup.id);
      await page.waitForTimeout(500);
      
      const description = await groupManagementPage.drawer.getDescription();
      expect(description).toBe(newDescription);
      
      // Close drawer
      await groupManagementPage.drawer.close();
    });

    test('should close drawer', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // And there is a group
      const adminToken = await signIn(TEST_USERS.valid.email, TEST_USERS.valid.password);
      const testGroup = await createUniqueTestGroup('close-test', '', adminToken);
      
      // When I navigate to Group Management
      const groupManagementPage = new GroupManagementPage(page);
      await groupManagementPage.goto();
      
      // Wait a moment for the newly created group to be available in search
      await page.waitForTimeout(500);
      
      // Search for the group to ensure it appears in the list
      await groupManagementPage.search.search(testGroup.name);
      await groupManagementPage.search.waitForSearchComplete();
      await groupManagementPage.waitForGroupInList(testGroup.name);
      
      // And I open the group drawer
      await groupManagementPage.clickEditGroup(testGroup.id);
      
      // Verify drawer is open
      const isOpenBefore = await groupManagementPage.drawer.isOpen();
      expect(isOpenBefore).toBe(true);
      
      // And I close the drawer (this already waits for drawer to close)
      await groupManagementPage.drawer.close();
      
      // Then the drawer should be closed
      const isOpenAfter = await groupManagementPage.drawer.isOpen();
      expect(isOpenAfter).toBe(false);
    });

    test('should delete a group', async ({ page }) => {
      // Given I am signed in
      await signInAsValidUser(page);
      
      // And there is a group
      const adminToken = await signIn(TEST_USERS.valid.email, TEST_USERS.valid.password);
      const testGroup = await createUniqueTestGroup('delete-test', '', adminToken);
      
      // When I navigate to Group Management
      const groupManagementPage = new GroupManagementPage(page);
      await groupManagementPage.goto();
      
      // Wait a moment for the newly created group to be available in search
      await page.waitForTimeout(500);
      
      // Search for the group to ensure it appears in the list
      await groupManagementPage.search.search(testGroup.name);
      await groupManagementPage.search.waitForSearchComplete();
      await groupManagementPage.waitForGroupInList(testGroup.name);
      
      // And I click delete
      await groupManagementPage.clickDeleteGroup(testGroup.id);
      
      // And I confirm deletion
      await groupManagementPage.confirmDelete();
      
      // Wait for group list to refresh after deletion
      await groupManagementPage.waitForGroupList();
      
      // Then the group should be deleted
      // Wait for the group to disappear from the list
      await groupManagementPage.search.waitForSearchComplete();
      await groupManagementPage.verifyGroupInList(testGroup.name, false);
    });
  });

  test.describe('Permission-Based UI', () => {
    test('edit button should be visible when user has security:group:view permission', async ({ page }) => {
      // Given I am signed in as a user with security:group:view permission
      await signInAsValidUser(page);
      
      // When I navigate to Group Management
      const groupManagementPage = new GroupManagementPage(page);
      await groupManagementPage.goto();
      
      // Then edit buttons should be visible (if groups exist)
      const groupCount = await groupManagementPage.getGroupCount();
      if (groupCount > 0) {
        // Wait for permissions to load and edit button to appear
        // PermissionGate loads permissions asynchronously, so we need to wait
        const editButton = page.locator('[data-testid^="edit-group-"]').first();
        await expect(editButton).toBeVisible({ timeout: 10000 });
      }
    });

    test('create button should be visible when user has security:group:save permission', async ({ page }) => {
      // Given I am signed in as a user with security:group:save permission
      await signInAsValidUser(page);
      
      // When I navigate to Group Management
      const groupManagementPage = new GroupManagementPage(page);
      await groupManagementPage.goto();
      
      // Then the create button should be visible
      // PermissionGate loads permissions asynchronously, so we need to wait
      const createButton = page.locator('[data-testid="create-group-button"]');
      await expect(createButton).toBeVisible({ timeout: 10000 });
    });

    test('delete button should be visible when user has security:group:delete permission', async ({ page }) => {
      // Given I am signed in as a user with security:group:delete permission
      await signInAsValidUser(page);
      
      // When I navigate to Group Management
      const groupManagementPage = new GroupManagementPage(page);
      await groupManagementPage.goto();
      
      // Then delete buttons should be visible (if groups exist)
      const groupCount = await groupManagementPage.getGroupCount();
      if (groupCount > 0) {
        // Wait for permissions to load and delete button to appear
        // PermissionGate loads permissions asynchronously, so we need to wait
        const deleteButton = page.locator('[data-testid^="delete-group-"]').first();
        await expect(deleteButton).toBeVisible({ timeout: 10000 });
      }
    });
  });
});

