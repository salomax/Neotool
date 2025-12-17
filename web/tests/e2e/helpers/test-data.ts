/**
 * Test data management helpers for E2E tests
 */

import { signIn, createUser, createRole, createGroup, assignGroupToUser, assignRoleToGroup } from './graphql';
import { TEST_USER_CREDENTIALS, TEST_DATA_IDS } from '../config/constants';

export interface TestUser {
  id: string;
  email: string;
  password: string;
  displayName: string;
  token?: string;
}

export interface TestRole {
  id: string;
  name: string;
}

export interface TestGroup {
  id: string;
  name: string;
}

/**
 * Create a test user with authentication
 */
export async function createTestUserWithAuth(
  email: string,
  password: string,
  displayName: string
): Promise<TestUser> {
  const user = await createUser(email, password, displayName);
  const token = await signIn(email, password);
  
  return {
    id: user.id,
    email: user.email,
    password,
    displayName,
    token,
  };
}

/**
 * Create a test admin user
 */
export async function createTestAdmin(): Promise<TestUser> {
  const admin = TEST_USER_CREDENTIALS.admin;
  return createTestUserWithAuth(admin.email, admin.password, admin.displayName);
}

/**
 * Create a test role
 */
export async function createTestRole(
  name: string,
  description: string | null,
  adminToken: string
): Promise<TestRole> {
  const role = await createRole(name, description, adminToken);
  return {
    id: role.id,
    name: role.name,
  };
}

/**
 * Create a test group
 */
export async function createTestGroup(
  name: string,
  description: string | null,
  adminToken: string
): Promise<TestGroup> {
  const group = await createGroup(name, description, adminToken);
  return {
    id: group.id,
    name: group.name,
  };
}

/**
 * Setup a complete test scenario with user, role, and group
 */
export async function setupTestScenario(options: {
  adminToken: string;
  userName?: string;
  roleName?: string;
  groupName?: string;
}): Promise<{
  user: TestUser;
  role?: TestRole;
  group?: TestGroup;
}> {
  const { adminToken, userName, roleName, groupName } = options;
  
  const userEmail = userName
    ? `${TEST_DATA_IDS.user(userName)}@test.neotool.com`
    : `test-${Date.now()}@test.neotool.com`;
  
  const user = await createTestUserWithAuth(
    userEmail,
    'TestPassword123!',
    userName || 'Test User'
  );

  let role: TestRole | undefined;
  let group: TestGroup | undefined;

  if (roleName) {
    role = await createTestRole(
      TEST_DATA_IDS.role(roleName),
      `Test role: ${roleName}`,
      adminToken
    );
  }

  if (groupName) {
    group = await createTestGroup(
      TEST_DATA_IDS.group(groupName),
      `Test group: ${groupName}`,
      adminToken
    );

    if (role) {
      await assignRoleToGroup(group.id, role.id, adminToken);
    }

    if (group) {
      await assignGroupToUser(user.id, group.id, adminToken);
    }
  }

  return { user, role, group };
}

/**
 * Clean up test data (placeholder - implement based on your cleanup strategy)
 */
export async function cleanupTestData(ids: {
  userIds?: string[];
  roleIds?: string[];
  groupIds?: string[];
}): Promise<void> {
  // TODO: Implement cleanup via GraphQL mutations or direct database access
  // For now, this is a placeholder
  console.log('Cleaning up test data:', ids);
}
