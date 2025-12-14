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
  usersTab: '[data-testid="users-tab"]',
  groupsTab: '[data-testid="groups-tab"]',
  rolesTab: '[data-testid="roles-tab"]',
} as const;

export const TEST_USER_CREDENTIALS = {
  admin: {
    email: 'admin@example.com',
    password: 'admin',
    displayName: 'Admin Test User',
  },
  editor: {
    email: 'admin@example.com',
    password: 'admin',
    displayName: 'Admin Test User',
  },
  viewer: {
    email: 'admin@example.com',
    password: 'admin',
    displayName: 'Admin Test User',
  },
  // Test user with NO group assigned - for negative testing (access control)
  testuser: {
    email: 'testuser@example.com',
    password: 'test',
    displayName: 'Test User',
  },
} as const;

export const TEST_DATA_IDS = {
  prefix: 'e2e-test-',
  user: (name: string) => `e2e-test-user-${name}`,
  role: (name: string) => `e2e-test-role-${name}`,
  group: (name: string) => `e2e-test-group-${name}`,
} as const;
