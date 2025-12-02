import { gql } from '@apollo/client';
import { USER_FIELDS, GROUP_FIELDS, ROLE_FIELDS, PERMISSION_FIELDS } from '../../fragments/common';

// Get users with pagination
export const GET_USERS = gql`
  ${USER_FIELDS}
  query GetUsers($first: Int, $after: String, $query: String) {
    users(first: $first, after: $after, query: $query) {
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

