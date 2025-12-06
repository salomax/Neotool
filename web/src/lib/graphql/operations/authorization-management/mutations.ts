import { gql } from '@apollo/client';
import { UserFieldsFragmentDoc, GroupFieldsFragmentDoc, RoleFieldsFragmentDoc } from '../../fragments/common.generated';

// Enable user account
export const ENABLE_USER = gql`
  mutation EnableUser($userId: ID!) {
    enableUser(userId: $userId) {
      ...UserFields
    }
  }
  ${UserFieldsFragmentDoc}
`;

// Disable user account
export const DISABLE_USER = gql`
  mutation DisableUser($userId: ID!) {
    disableUser(userId: $userId) {
      ...UserFields
    }
  }
  ${UserFieldsFragmentDoc}
`;

// Create new group
export const CREATE_GROUP = gql`
  mutation CreateGroup($input: CreateGroupInput!) {
    createGroup(input: $input) {
      ...GroupFields
    }
  }
  ${GroupFieldsFragmentDoc}
`;

// Update existing group
export const UPDATE_GROUP = gql`
  mutation UpdateGroup($groupId: ID!, $input: UpdateGroupInput!) {
    updateGroup(groupId: $groupId, input: $input) {
      ...GroupFields
    }
  }
  ${GroupFieldsFragmentDoc}
`;

// Delete group
export const DELETE_GROUP = gql`
  mutation DeleteGroup($groupId: ID!) {
    deleteGroup(groupId: $groupId)
  }
`;

// Create new role
export const CREATE_ROLE = gql`
  mutation CreateRole($input: CreateRoleInput!) {
    createRole(input: $input) {
      ...RoleFields
    }
  }
  ${RoleFieldsFragmentDoc}
`;

// Update existing role
export const UPDATE_ROLE = gql`
  mutation UpdateRole($roleId: ID!, $input: UpdateRoleInput!) {
    updateRole(roleId: $roleId, input: $input) {
      ...RoleFields
    }
  }
  ${RoleFieldsFragmentDoc}
`;

// Delete role
export const DELETE_ROLE = gql`
  mutation DeleteRole($roleId: ID!) {
    deleteRole(roleId: $roleId)
  }
`;

// Assign permission to role
export const ASSIGN_PERMISSION_TO_ROLE = gql`
  mutation AssignPermissionToRole($roleId: ID!, $permissionId: ID!) {
    assignPermissionToRole(roleId: $roleId, permissionId: $permissionId) {
      ...RoleFields
    }
  }
  ${RoleFieldsFragmentDoc}
`;

// Remove permission from role
export const REMOVE_PERMISSION_FROM_ROLE = gql`
  mutation RemovePermissionFromRole($roleId: ID!, $permissionId: ID!) {
    removePermissionFromRole(roleId: $roleId, permissionId: $permissionId) {
      ...RoleFields
    }
  }
  ${RoleFieldsFragmentDoc}
`;

// Assign role to user
export const ASSIGN_ROLE_TO_USER = gql`
  mutation AssignRoleToUser($userId: ID!, $roleId: ID!) {
    assignRoleToUser(userId: $userId, roleId: $roleId) {
      ...UserFields
    }
  }
  ${UserFieldsFragmentDoc}
`;

// Remove role from user
export const REMOVE_ROLE_FROM_USER = gql`
  mutation RemoveRoleFromUser($userId: ID!, $roleId: ID!) {
    removeRoleFromUser(userId: $userId, roleId: $roleId) {
      ...UserFields
    }
  }
  ${UserFieldsFragmentDoc}
`;

// Assign group to user
export const ASSIGN_GROUP_TO_USER = gql`
  mutation AssignGroupToUser($userId: ID!, $groupId: ID!) {
    assignGroupToUser(userId: $userId, groupId: $groupId) {
      ...UserFields
    }
  }
  ${UserFieldsFragmentDoc}
`;

// Remove group from user
export const REMOVE_GROUP_FROM_USER = gql`
  mutation RemoveGroupFromUser($userId: ID!, $groupId: ID!) {
    removeGroupFromUser(userId: $userId, groupId: $groupId) {
      ...UserFields
    }
  }
  ${UserFieldsFragmentDoc}
`;

// Assign role to group
export const ASSIGN_ROLE_TO_GROUP = gql`
  mutation AssignRoleToGroup($groupId: ID!, $roleId: ID!) {
    assignRoleToGroup(groupId: $groupId, roleId: $roleId) {
      ...GroupFields
    }
  }
  ${GroupFieldsFragmentDoc}
`;

// Remove role from group
export const REMOVE_ROLE_FROM_GROUP = gql`
  mutation RemoveRoleFromGroup($groupId: ID!, $roleId: ID!) {
    removeRoleFromGroup(groupId: $groupId, roleId: $roleId) {
      ...GroupFields
    }
  }
  ${GroupFieldsFragmentDoc}
`;
