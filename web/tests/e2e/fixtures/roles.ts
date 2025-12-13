/**
 * Test role fixtures for E2E tests
 */

import { createRole } from '../helpers/graphql';
import { TEST_DATA_IDS } from '../config/constants';

export const TEST_ROLES = {
  admin: {
    name: 'admin',
    description: 'Administrator role with full permissions',
  },
  editor: {
    name: 'editor',
    description: 'Editor role with write permissions',
  },
  viewer: {
    name: 'viewer',
    description: 'Viewer role with read-only permissions',
  },
} as const;

export interface TestRoleData {
  id: string;
  name: string;
  description: string | null;
}

/**
 * Create a test role via GraphQL
 */
export async function createTestRole(
  name: string,
  description: string | null,
  adminToken: string
): Promise<TestRoleData> {
  try {
    const role = await createRole(name, description, adminToken);
    return {
      id: role.id,
      name: role.name,
      description,
    };
  } catch (error) {
    console.error('Failed to create test role:', error);
    throw error;
  }
}

/**
 * Create a test role with a unique name (for parallel tests)
 */
export async function createUniqueTestRole(
  prefix = 'test-role',
  description: string | null = null,
  adminToken: string
): Promise<TestRoleData> {
  const timestamp = Date.now();
  const random = Math.random().toString(36).substring(7);
  const name = `${prefix}-${timestamp}-${random}`;
  
  return createTestRole(name, description, adminToken);
}

/**
 * Create standard test roles (admin, editor, viewer)
 */
export async function createStandardTestRoles(
  adminToken: string
): Promise<Record<string, TestRoleData>> {
  const roles: Record<string, TestRoleData> = {};
  
  for (const [key, roleData] of Object.entries(TEST_ROLES)) {
    const role = await createUniqueTestRole(
      roleData.name,
      roleData.description,
      adminToken
    );
    roles[key] = role;
  }
  
  return roles;
}
