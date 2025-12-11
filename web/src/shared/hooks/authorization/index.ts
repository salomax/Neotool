// Export hooks
export { useRoleManagement } from './useRoleManagement';
export { useUserManagement } from './useUserManagement';
export { useGroupManagement } from './useGroupManagement';
export { usePermissionManagement } from './usePermissionManagement';

// Export mutation hooks
export { useRoleMutations } from './useRoleMutations';
export { useUserMutations } from './useUserMutations';
export { useGroupMutations } from './useGroupMutations';

// Export types explicitly to avoid conflicts
export type {
  Role,
  RoleFormData,
  UseRoleManagementOptions,
  UseRoleManagementReturn,
} from './useRoleManagement';

export type {
  User,
  UseUserManagementOptions,
  UseUserManagementReturn,
} from './useUserManagement';

export type {
  Group,
  GroupFormData,
  UseGroupManagementOptions,
  UseGroupManagementReturn,
} from './useGroupManagement';

// Permission type - exported from usePermissionManagement (primary source)
export type {
  Permission,
  UsePermissionManagementOptions,
  UsePermissionManagementReturn,
} from './usePermissionManagement';

// Export mutation hook types
export type {
  UseRoleMutationsOptions,
  UseRoleMutationsReturn,
} from './useRoleMutations';

export type {
  UseUserMutationsOptions,
  UseUserMutationsReturn,
} from './useUserMutations';

export type {
  UseGroupMutationsOptions,
  UseGroupMutationsReturn,
} from './useGroupMutations';

