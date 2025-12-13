import { gql } from '@apollo/client';

// Get users with pagination
export const GET_USERS = gql`
  query GetUsers($first: Int, $after: String, $query: String, $orderBy: [UserOrderByInput!]) {
    users(first: $first, after: $after, query: $query, orderBy: $orderBy) {
      edges {
        node {
          id
          email
          displayName
          enabled
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
`;

// Get groups with pagination
export const GET_GROUPS = gql`
  query GetGroups($first: Int, $after: String, $query: String, $orderBy: [GroupOrderByInput!]) {
    groups(first: $first, after: $after, query: $query, orderBy: $orderBy) {
      edges {
        node {
          id
          name
          description
          members {
            id
            email
            displayName
            enabled
          }
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
`;

// Get roles with pagination
export const GET_ROLES = gql`
  query GetRoles($first: Int, $after: String, $query: String, $orderBy: [RoleOrderByInput!]) {
    roles(first: $first, after: $after, query: $query, orderBy: $orderBy) {
      edges {
        node {
          id
          name
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
`;

// Get permissions with pagination
export const GET_PERMISSIONS = gql`
  query GetPermissions($first: Int, $after: String, $query: String) {
    permissions(first: $first, after: $after, query: $query) {
      edges {
        node {
          id
          name
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
`;

// Get user with relationships (groups, roles, permissions)
export const GET_USER_WITH_RELATIONSHIPS = gql`
  query GetUserWithRelationships($id: ID!) {
    user(id: $id) {
      id
      email
      displayName
      enabled
      groups {
        id
        name
        description
        members {
          id
          email
          displayName
          enabled
        }
      }
      roles {
        id
        name
      }
      permissions {
        id
        name
      }
    }
  }
`;

// Get group with relationships (members, roles)
export const GET_GROUP_WITH_RELATIONSHIPS = gql`
  query GetGroupWithRelationships($id: ID!) {
    group(id: $id) {
      id
      name
      description
      members {
        id
        email
        displayName
        enabled
      }
      roles {
        id
        name
      }
    }
  }
`;

// Get roles with permissions
// Note: Since there's no role(id: ID!) query in the schema, we query roles
// and filter client-side by the provided roleId
export const GET_ROLES_WITH_PERMISSIONS = gql`
  query GetRolesWithPermissions {
    roles(first: 100) {
      edges {
        node {
          id
          name
          permissions {
            id
            name
          }
        }
      }
    }
  }
`;

// Get users and groups with their roles
// Note: Since there's no role(id: ID!) query in the schema, we query users and groups
// with their roles and filter client-side to find which users/groups have the specific role
// Note: Using first: 100 (API maximum). If there are more than 100 users/groups,
// pagination would need to be implemented to fetch all items.
export const GET_ROLE_WITH_USERS_AND_GROUPS = gql`
  query GetRoleWithUsersAndGroups {
    users(first: 100) {
      edges {
        node {
          id
          email
          displayName
          enabled
          roles {
            id
            name
          }
        }
      }
    }
    groups(first: 100) {
      edges {
        node {
          id
          name
          description
          members {
            id
            email
            displayName
            enabled
          }
          roles {
            id
            name
          }
        }
      }
    }
  }
`;
