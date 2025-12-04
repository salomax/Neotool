import { gql } from '@apollo/client';
import { USER_FIELDS, GROUP_FIELDS, ROLE_FIELDS, PERMISSION_FIELDS } from '../../fragments/common';

// Get users with pagination
export const GET_USERS = gql`
  ${USER_FIELDS}
  query GetUsers($first: Int, $after: String, $query: String, $orderBy: [UserOrderByInput!]) {
    users(first: $first, after: $after, query: $query, orderBy: $orderBy) {
      edges {
        node {
          ...UserFields
        }
        cursor
      }
      nodes {
        ...UserFields
      }
      pageInfo {
        hasNextPage
        hasPreviousPage
        startCursor
        endCursor
      }
      totalCount
    }
  }
`;

// Get groups with pagination
export const GET_GROUPS = gql`
  ${GROUP_FIELDS}
  query GetGroups($first: Int, $after: String, $query: String) {
    groups(first: $first, after: $after, query: $query) {
      edges {
        node {
          ...GroupFields
        }
        cursor
      }
      nodes {
        ...GroupFields
      }
      pageInfo {
        hasNextPage
        hasPreviousPage
        startCursor
        endCursor
      }
      totalCount
    }
  }
`;

// Get roles with pagination
export const GET_ROLES = gql`
  ${ROLE_FIELDS}
  query GetRoles($first: Int, $after: String, $query: String) {
    roles(first: $first, after: $after, query: $query) {
      edges {
        node {
          ...RoleFields
        }
        cursor
      }
      nodes {
        ...RoleFields
      }
      pageInfo {
        hasNextPage
        hasPreviousPage
        startCursor
        endCursor
      }
      totalCount
    }
  }
`;

// Get permissions with pagination
export const GET_PERMISSIONS = gql`
  ${PERMISSION_FIELDS}
  query GetPermissions($first: Int, $after: String, $query: String) {
    permissions(first: $first, after: $after, query: $query) {
      edges {
        node {
          ...PermissionFields
        }
        cursor
      }
      nodes {
        ...PermissionFields
      }
      pageInfo {
        hasNextPage
        hasPreviousPage
        startCursor
        endCursor
      }
    }
  }
`;

// Get user with relationships (groups, roles, permissions)
export const GET_USER_WITH_RELATIONSHIPS = gql`
  ${USER_FIELDS}
  ${GROUP_FIELDS}
  ${ROLE_FIELDS}
  ${PERMISSION_FIELDS}
  query GetUserWithRelationships($id: ID!) {
    user(id: $id) {
      ...UserFields
      groups {
        ...GroupFields
      }
      roles {
        ...RoleFields
      }
      permissions {
        ...PermissionFields
      }
    }
  }
`;

// Get roles with permissions
// Note: Since there's no role(id: ID!) query in the schema, we query roles
// and filter client-side by the provided roleId
export const GET_ROLES_WITH_PERMISSIONS = gql`
  ${ROLE_FIELDS}
  ${PERMISSION_FIELDS}
  query GetRolesWithPermissions {
    roles(first: 1000) {
      nodes {
        ...RoleFields
        permissions {
          ...PermissionFields
        }
      }
    }
  }
`;

// Get users and groups with their roles
// Note: Since there's no role(id: ID!) query in the schema, we query users and groups
// with their roles and filter client-side to find which users/groups have the specific role
export const GET_ROLE_WITH_USERS_AND_GROUPS = gql`
  ${USER_FIELDS}
  ${GROUP_FIELDS}
  ${ROLE_FIELDS}
  query GetRoleWithUsersAndGroups {
    users(first: 1000) {
      nodes {
        ...UserFields
        roles {
          ...RoleFields
        }
      }
    }
    groups(first: 1000) {
      nodes {
        ...GroupFields
        roles {
          ...RoleFields
        }
      }
    }
  }
`;

