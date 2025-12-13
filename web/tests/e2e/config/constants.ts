/**
 * Test constants for E2E tests
 */

export const TEST_TIMEOUTS = {
  navigation: 10000,
  api: 30000,
  element: 5000,
  test: 60000,
} as const;

export const TEST_RETRIES = {
  flaky: 3,
  ci: 2,
  local: 0,
} as const;

export const SELECTORS = {
  // User Management
  userSearch: '[data-testid="user-search"]',
  userList: '[data-testid="user-list-table"]',
  editUser: (userId: string) => `[data-testid="edit-user-${userId}"]`,
  userStatusToggle: (userId: string) => `[data-testid="user-status-toggle-${userId}"]`,
  
  // Drawer
  drawer: '[data-testid="drawer"]',
  drawerHeader: '[data-testid="drawer-header"]',
  drawerBody: '[data-testid="drawer-body"]',
  drawerFooter: '[data-testid="drawer-footer"]',
  
  // Settings
  settingsPage: '[data-testid="settings-page"]',
  usersTab: 'button:has-text("Users")',
  groupsTab: 'button:has-text("Groups")',
  rolesTab: 'button:has-text("Roles")',
} as const;

export const TEST_USER_CREDENTIALS = {
  admin: {
    email: 'admin@test.neotool.com',
    password: 'AdminTest123!',
    displayName: 'Admin Test User',
  },
  editor: {
    email: 'editor@test.neotool.com',
    password: 'EditorTest123!',
    displayName: 'Editor Test User',
  },
  viewer: {
    email: 'viewer@test.neotool.com',
    password: 'ViewerTest123!',
    displayName: 'Viewer Test User',
  },
} as const;

export const TEST_DATA_IDS = {
  prefix: 'e2e-test-',
  user: (name: string) => `e2e-test-user-${name}`,
  role: (name: string) => `e2e-test-role-${name}`,
  group: (name: string) => `e2e-test-group-${name}`,
} as const;
