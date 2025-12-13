/**
 * Test user fixtures for E2E tests
 */

import { createUser, signIn } from '../helpers/graphql';
import { TEST_USER_CREDENTIALS, TEST_DATA_IDS } from '../config/constants';

export const TEST_USERS = {
  valid: {
    email: 'test@example.com',
    password: 'TestPassword123!',
    displayName: 'Test User',
  },
  invalid: {
    email: 'invalid@example.com',
    password: 'WrongPassword123!',
  },
  admin: TEST_USER_CREDENTIALS.admin,
  editor: TEST_USER_CREDENTIALS.editor,
  viewer: TEST_USER_CREDENTIALS.viewer,
} as const;

export interface TestUserData {
  id: string;
  email: string;
  password: string;
  displayName: string;
  token?: string;
}

/**
 * Create a test user in the database via GraphQL
 */
export async function createTestUser(
  user: {
    email: string;
    password: string;
    displayName: string;
  } = TEST_USERS.valid
): Promise<TestUserData> {
  try {
    const createdUser = await createUser(user.email, user.password, user.displayName);
    const token = await signIn(user.email, user.password);
    
    return {
      id: createdUser.id,
      email: createdUser.email,
      password: user.password,
      displayName: user.displayName,
      token,
    };
  } catch (error) {
    console.error('Failed to create test user:', error);
    throw error;
  }
}

/**
 * Create a test user with a unique email (for parallel tests)
 */
export async function createUniqueTestUser(
  prefix = 'test',
  displayName = 'Test User'
): Promise<TestUserData> {
  const timestamp = Date.now();
  const random = Math.random().toString(36).substring(7);
  const email = `${prefix}-${timestamp}-${random}@test.neotool.com`;
  
  return createTestUser({
    email,
    password: 'TestPassword123!',
    displayName: `${displayName} ${timestamp}`,
  });
}

/**
 * Delete a test user from the database
 * This should be called in test teardown
 * Note: Actual deletion may require admin permissions or direct database access
 */
export async function deleteTestUser(email: string): Promise<void> {
  // TODO: Implement user deletion via GraphQL mutation or database cleanup
  // For now, this is a placeholder
  console.log('Deleting test user:', email);
}

/**
 * Create multiple test users for testing
 */
export async function createTestUsers(count: number): Promise<TestUserData[]> {
  const users: TestUserData[] = [];
  
  for (let i = 0; i < count; i++) {
    const user = await createUniqueTestUser(`user-${i}`, `Test User ${i}`);
    users.push(user);
  }
  
  return users;
}

