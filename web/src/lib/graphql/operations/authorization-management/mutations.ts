import { gql } from '@apollo/client';
import { USER_FIELDS, GROUP_FIELDS, ROLE_FIELDS } from '../../fragments/common';

// Enable user account
export const ENABLE_USER = gql`
  ${USER_FIELDS}
  mutation EnableUser($userId: ID!) {
    enableUser(userId: $userId) {
      ...UserFields
    }
  }
`;

// Disable user account
export const DISABLE_USER = gql`
  ${USER_FIELDS}
  mutation DisableUser($userId: ID!) {
    disableUser(userId: $userId) {
      ...UserFields
    }
  }
`;

// Create new group
export const CREATE_GROUP = gql`
  ${GROUP_FIELDS}
  mutation CreateGroup($input: CreateGroupInput!) {
    createGroup(input: $input) {
      ...GroupFields
    }
  }
`;

// Update existing group
export const UPDATE_GROUP = gql`
  ${GROUP_FIELDS}
  mutation UpdateGroup($groupId: ID!, $input: UpdateGroupInput!) {
    updateGroup(groupId: $groupId, input: $input) {
      ...GroupFields
    }
  }
`;

// Delete group
export const DELETE_GROUP = gql`
  mutation DeleteGroup($groupId: ID!) {
    deleteGroup(groupId: $groupId)
  }
`;

// Create new role
export const CREATE_ROLE = gql`
  ${ROLE_FIELDS}
  mutation CreateRole($input: CreateRoleInput!) {
    createRole(input: $input) {
      ...RoleFields
    }
  }
`;

// Update existing role
export const UPDATE_ROLE = gql`
  ${ROLE_FIELDS}
  mutation UpdateRole($roleId: ID!, $input: UpdateRoleInput!) {
    updateRole(roleId: $roleId, input: $input) {
      ...RoleFields
    }
  }
`;

// Delete role
export const DELETE_ROLE = gql`
  mutation DeleteRole($roleId: ID!) {
    deleteRole(roleId: $roleId)
  }
`;

// Assign permission to role
export const ASSIGN_PERMISSION_TO_ROLE = gql`
  ${ROLE_FIELDS}
  mutation AssignPermissionToRole($roleId: ID!, $permissionId: ID!) {
    assignPermissionToRole(roleId: $roleId, permissionId: $permissionId) {
      ...RoleFields
    }
  }
`;

// Remove permission from role
export const REMOVE_PERMISSION_FROM_ROLE = gql`
  ${ROLE_FIELDS}
  mutation RemovePermissionFromRole($roleId: ID!, $permissionId: ID!) {
    removePermissionFromRole(roleId: $roleId, permissionId: $permissionId) {
      ...RoleFields
    }
  }
`;

// Assign role to user
export const ASSIGN_ROLE_TO_USER = gql`
  ${USER_FIELDS}
  mutation AssignRoleToUser($userId: ID!, $roleId: ID!) {
    assignRoleToUser(userId: $userId, roleId: $roleId) {
      ...UserFields
    }
  }
`;

// Remove role from user
export const REMOVE_ROLE_FROM_USER = gql`
  ${USER_FIELDS}
  mutation RemoveRoleFromUser($userId: ID!, $roleId: ID!) {
    removeRoleFromUser(userId: $userId, roleId: $roleId) {
      ...UserFields
    }
  }
`;

// Assign role to group
export const ASSIGN_ROLE_TO_GROUP = gql`
  ${GROUP_FIELDS}
  mutation AssignRoleToGroup($groupId: ID!, $roleId: ID!) {
    assignRoleToGroup(groupId: $groupId, roleId: $roleId) {
      ...GroupFields
    }
  }
`;

// Remove role from group
export const REMOVE_ROLE_FROM_GROUP = gql`
  ${GROUP_FIELDS}
  mutation RemoveRoleFromGroup($groupId: ID!, $roleId: ID!) {
    removeRoleFromGroup(groupId: $groupId, roleId: $roleId) {
      ...GroupFields
    }
  }
`;

