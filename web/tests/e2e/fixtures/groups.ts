/**
 * Test group fixtures for E2E tests
 */

import { createGroup } from '../helpers/graphql';
import { TEST_DATA_IDS } from '../config/constants';

export const TEST_GROUPS = {
  financeTeam: {
    name: 'finance-team',
    description: 'Finance team group',
  },
  salesTeam: {
    name: 'sales-team',
    description: 'Sales team group',
  },
  supportTeam: {
    name: 'support-team',
    description: 'Support team group',
  },
  managers: {
    name: 'managers',
    description: 'Managers group',
  },
} as const;

export interface TestGroupData {
  id: string;
  name: string;
  description: string | null;
}

/**
 * Create a test group via GraphQL
 */
export async function createTestGroup(
  name: string,
  description: string | null,
  adminToken: string
): Promise<TestGroupData> {
  try {
    const group = await createGroup(name, description, adminToken);
    return {
      id: group.id,
      name: group.name,
      description,
    };
  } catch (error) {
    console.error('Failed to create test group:', error);
    throw error;
  }
}

/**
 * Create a test group with a unique name (for parallel tests)
 */
export async function createUniqueTestGroup(
  prefix = 'test-group',
  description: string | null = null,
  adminToken: string
): Promise<TestGroupData> {
  const timestamp = Date.now();
  const random = Math.random().toString(36).substring(7);
  const name = `${prefix}-${timestamp}-${random}`;
  
  return createTestGroup(name, description, adminToken);
}

/**
 * Create standard test groups
 */
export async function createStandardTestGroups(
  adminToken: string
): Promise<Record<string, TestGroupData>> {
  const groups: Record<string, TestGroupData> = {};
  
  for (const [key, groupData] of Object.entries(TEST_GROUPS)) {
    const group = await createUniqueTestGroup(
      groupData.name,
      groupData.description,
      adminToken
    );
    groups[key] = group;
  }
  
  return groups;
}
