import { gql } from '@apollo/client';

// Enable user account
export const ENABLE_USER = gql`
  mutation EnableUser($userId: ID!) {
    enableUser(userId: $userId) {
      id
      email
      displayName
      enabled
    }
  }
`;

// Disable user account
export const DISABLE_USER = gql`
  mutation DisableUser($userId: ID!) {
    disableUser(userId: $userId) {
      id
      email
      displayName
      enabled
    }
  }
`;

// Create new group
export const CREATE_GROUP = gql`
  mutation CreateGroup($input: CreateGroupInput!) {
    createGroup(input: $input) {
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
  }
`;

// Update existing group
export const UPDATE_GROUP = gql`
  mutation UpdateGroup($groupId: ID!, $input: UpdateGroupInput!) {
    updateGroup(groupId: $groupId, input: $input) {
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
  mutation CreateRole($input: CreateRoleInput!) {
    createRole(input: $input) {
      id
      name
    }
  }
`;

// Update existing role
export const UPDATE_ROLE = gql`
  mutation UpdateRole($roleId: ID!, $input: UpdateRoleInput!) {
    updateRole(roleId: $roleId, input: $input) {
      id
      name
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
  mutation AssignPermissionToRole($roleId: ID!, $permissionId: ID!) {
    assignPermissionToRole(roleId: $roleId, permissionId: $permissionId) {
      id
      name
    }
  }
`;

// Remove permission from role
export const REMOVE_PERMISSION_FROM_ROLE = gql`
  mutation RemovePermissionFromRole($roleId: ID!, $permissionId: ID!) {
    removePermissionFromRole(roleId: $roleId, permissionId: $permissionId) {
      id
      name
    }
  }
`;

// Assign group to user
export const ASSIGN_GROUP_TO_USER = gql`
  mutation AssignGroupToUser($userId: ID!, $groupId: ID!) {
    assignGroupToUser(userId: $userId, groupId: $groupId) {
      id
      email
      displayName
      enabled
    }
  }
`;

// Remove group from user
export const REMOVE_GROUP_FROM_USER = gql`
  mutation RemoveGroupFromUser($userId: ID!, $groupId: ID!) {
    removeGroupFromUser(userId: $userId, groupId: $groupId) {
      id
      email
      displayName
      enabled
    }
  }
`;

// Assign role to group
export const ASSIGN_ROLE_TO_GROUP = gql`
  mutation AssignRoleToGroup($groupId: ID!, $roleId: ID!) {
    assignRoleToGroup(groupId: $groupId, roleId: $roleId) {
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
  }
`;

// Remove role from group
export const REMOVE_ROLE_FROM_GROUP = gql`
  mutation RemoveRoleFromGroup($groupId: ID!, $roleId: ID!) {
    removeRoleFromGroup(groupId: $groupId, roleId: $roleId) {
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
  }
`;
