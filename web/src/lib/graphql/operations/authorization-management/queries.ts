import { gql } from '@apollo/client';
import { UserFieldsFragmentDoc, GroupFieldsFragmentDoc, RoleFieldsFragmentDoc, PermissionFieldsFragmentDoc } from '../../fragments/common.generated';

// Get users with pagination
export const GET_USERS = gql`
  query GetUsers($first: Int, $after: String, $query: String, $orderBy: [UserOrderByInput!]) {
    users(first: $first, after: $after, query: $query, orderBy: $orderBy) {
      edges {
        node {
          ...UserFields
        }
        cursor
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
  ${UserFieldsFragmentDoc}
`;

// Get groups with pagination
export const GET_GROUPS = gql`
  query GetGroups($first: Int, $after: String, $query: String, $orderBy: [GroupOrderByInput!]) {
    groups(first: $first, after: $after, query: $query, orderBy: $orderBy) {
      edges {
        node {
          ...GroupFields
        }
        cursor
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
  ${UserFieldsFragmentDoc}
  ${GroupFieldsFragmentDoc}
`;

// Get roles with pagination
export const GET_ROLES = gql`
  query GetRoles($first: Int, $after: String, $query: String, $orderBy: [RoleOrderByInput!]) {
    roles(first: $first, after: $after, query: $query, orderBy: $orderBy) {
      edges {
        node {
          ...RoleFields
        }
        cursor
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
  ${RoleFieldsFragmentDoc}
`;

// Get permissions with pagination
export const GET_PERMISSIONS = gql`
  query GetPermissions($first: Int, $after: String, $query: String) {
    permissions(first: $first, after: $after, query: $query) {
      edges {
        node {
          ...PermissionFields
        }
        cursor
      }
      pageInfo {
        hasNextPage
        hasPreviousPage
        startCursor
        endCursor
      }
    }
  }
  ${PermissionFieldsFragmentDoc}
`;

// Get user with relationships (groups, roles, permissions)
export const GET_USER_WITH_RELATIONSHIPS = gql`
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
  ${UserFieldsFragmentDoc}
  ${GroupFieldsFragmentDoc}
  ${RoleFieldsFragmentDoc}
  ${PermissionFieldsFragmentDoc}
`;

// Get group with relationships (members, roles)
export const GET_GROUP_WITH_RELATIONSHIPS = gql`
  query GetGroupWithRelationships($id: ID!) {
    group(id: $id) {
      ...GroupFields
      roles {
        ...RoleFields
      }
    }
  }
  ${GroupFieldsFragmentDoc}
  ${RoleFieldsFragmentDoc}
`;

// Get roles with permissions
// Note: Since there's no role(id: ID!) query in the schema, we query roles
// and filter client-side by the provided roleId
export const GET_ROLES_WITH_PERMISSIONS = gql`
  query GetRolesWithPermissions {
    roles(first: 100) {
      edges {
        node {
          ...RoleFields
          permissions {
            ...PermissionFields
          }
        }
      }
    }
  }
  ${RoleFieldsFragmentDoc}
  ${PermissionFieldsFragmentDoc}
`;

// Get users and groups with their roles
// Note: Since there's no role(id: ID!) query in the schema, we query users and groups
// with their roles and filter client-side to find which users/groups have the specific role
export const GET_ROLE_WITH_USERS_AND_GROUPS = gql`
  query GetRoleWithUsersAndGroups {
    users(first: 1000) {
      edges {
        node {
          ...UserFields
          roles {
            ...RoleFields
          }
        }
      }
    }
    groups(first: 1000) {
      edges {
        node {
          ...GroupFields
          roles {
            ...RoleFields
          }
        }
      }
    }
  }
  ${UserFieldsFragmentDoc}
  ${GroupFieldsFragmentDoc}
  ${RoleFieldsFragmentDoc}
`;
