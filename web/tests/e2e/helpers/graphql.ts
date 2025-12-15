/**
 * GraphQL operation helpers for E2E tests
 */

import { currentConfig } from '../config/environments';

export interface GraphQLResponse<T = any> {
  data?: T;
  errors?: Array<{ message: string; path?: string[] }>;
}

/**
 * Execute a GraphQL query or mutation
 */
export async function executeGraphQL<T = any>(
  query: string,
  variables?: Record<string, any>,
  token?: string
): Promise<GraphQLResponse<T>> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  };

  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await fetch(currentConfig.graphqlEndpoint, {
    method: 'POST',
    headers,
    body: JSON.stringify({
      query,
      variables: variables || {},
    }),
  });

  if (!response.ok) {
    throw new Error(`GraphQL request failed: ${response.status} ${response.statusText}`);
  }

  const result = await response.json();
  
  if (result.errors) {
    console.error('GraphQL errors:', result.errors);
  }

  return result;
}

/**
 * Sign in and get authentication token
 */
export async function signIn(email: string, password: string): Promise<string> {
  const mutation = `
    mutation SignIn($input: SignInInput!) {
      signIn(input: $input) {
        token
        user {
          id
          email
        }
      }
    }
  `;

  const result = await executeGraphQL<{
    signIn: { token: string; user: { id: string; email: string } };
  }>(mutation, {
    input: {
      email,
      password,
      rememberMe: false,
    },
  });

  if (result.errors || !result.data?.signIn?.token) {
    throw new Error(`Sign in failed: ${result.errors?.[0]?.message || 'Unknown error'}`);
  }

  return result.data.signIn.token;
}

/**
 * Create a test user via sign up
 */
export async function createUser(
  email: string,
  password: string,
  name: string
): Promise<{ id: string; email: string }> {
  const mutation = `
    mutation SignUp($input: SignUpInput!) {
      signUp(input: $input) {
        user {
          id
          email
          displayName
        }
      }
    }
  `;

  const result = await executeGraphQL<{
    signUp: { user: { id: string; email: string; displayName: string | null } };
  }>(mutation, {
    input: {
      email,
      password,
      name,
    },
  });

  if (result.errors || !result.data?.signUp?.user) {
    throw new Error(`User creation failed: ${result.errors?.[0]?.message || 'Unknown error'}`);
  }

  return {
    id: result.data.signUp.user.id,
    email: result.data.signUp.user.email,
  };
}

/**
 * Enable a user
 */
export async function enableUser(userId: string, token: string): Promise<void> {
  const mutation = `
    mutation EnableUser($userId: ID!) {
      enableUser(userId: $userId) {
        id
        enabled
      }
    }
  `;

  const result = await executeGraphQL(mutation, { userId }, token);

  if (result.errors) {
    throw new Error(`Enable user failed: ${result.errors[0].message}`);
  }
}

/**
 * Disable a user
 */
export async function disableUser(userId: string, token: string): Promise<void> {
  const mutation = `
    mutation DisableUser($userId: ID!) {
      disableUser(userId: $userId) {
        id
        enabled
      }
    }
  `;

  const result = await executeGraphQL(mutation, { userId }, token);

  if (result.errors) {
    throw new Error(`Disable user failed: ${result.errors[0].message}`);
  }
}

/**
 * Create a role
 */
export async function createRole(
  name: string,
  description: string | null,
  token: string
): Promise<{ id: string; name: string }> {
  const mutation = `
    mutation CreateRole($input: CreateRoleInput!) {
      createRole(input: $input) {
        id
        name
      }
    }
  `;

  const result = await executeGraphQL<{
    createRole: { id: string; name: string };
  }>(
    mutation,
    {
      input: {
        name
      },
    },
    token
  );

  if (result.errors || !result.data?.createRole) {
    throw new Error(`Role creation failed: ${result.errors?.[0]?.message || 'Unknown error'}`);
  }

  return result.data.createRole;
}

/**
 * Create a group
 */
export async function createGroup(
  name: string,
  description: string | null,
  token: string
): Promise<{ id: string; name: string }> {
  const mutation = `
    mutation CreateGroup($input: CreateGroupInput!) {
      createGroup(input: $input) {
        id
        name
      }
    }
  `;

  const result = await executeGraphQL<{
    createGroup: { id: string; name: string };
  }>(
    mutation,
    {
      input: {
        name,
        description,
      },
    },
    token
  );

  if (result.errors || !result.data?.createGroup) {
    throw new Error(`Group creation failed: ${result.errors?.[0]?.message || 'Unknown error'}`);
  }

  return result.data.createGroup;
}

/**
 * Assign group to user
 */
export async function assignGroupToUser(
  userId: string,
  groupId: string,
  token: string
): Promise<void> {
  const mutation = `
    mutation AssignGroupToUser($userId: ID!, $groupId: ID!) {
      assignGroupToUser(userId: $userId, groupId: $groupId) {
        id
      }
    }
  `;

  const result = await executeGraphQL(mutation, { userId, groupId }, token);

  if (result.errors) {
    throw new Error(`Assign group to user failed: ${result.errors[0].message}`);
  }
}

/**
 * Assign role to group
 */
export async function assignRoleToGroup(
  groupId: string,
  roleId: string,
  token: string
): Promise<void> {
  const mutation = `
    mutation AssignRoleToGroup($groupId: ID!, $roleId: ID!) {
      assignRoleToGroup(groupId: $groupId, roleId: $roleId) {
        id
      }
    }
  `;

  const result = await executeGraphQL(mutation, { groupId, roleId }, token);

  if (result.errors) {
    throw new Error(`Assign role to group failed: ${result.errors[0].message}`);
  }
}

/**
 * Get user by ID
 */
export async function getUser(userId: string, token: string): Promise<any> {
  const query = `
    query GetUser($id: ID!) {
      user(id: $id) {
        id
        email
        displayName
        enabled
        groups {
          id
          name
        }
        roles {
          id
          name
        }
      }
    }
  `;

  const result = await executeGraphQL<{
    user: {
      id: string;
      email: string;
      displayName: string | null;
      enabled: boolean;
      groups: Array<{ id: string; name: string }>;
      roles: Array<{ id: string; name: string }>;
    };
  }>(query, { id: userId }, token);

  if (result.errors || !result.data?.user) {
    throw new Error(`Get user failed: ${result.errors?.[0]?.message || 'Unknown error'}`);
  }

  return result.data.user;
}
