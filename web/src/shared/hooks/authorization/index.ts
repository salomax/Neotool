// Export hooks
export { useRoleManagement } from './useRoleManagement';
export { useUserManagement } from './useUserManagement';
export { useGroupManagement } from './useGroupManagement';
export { usePermissionManagement } from './usePermissionManagement';
export { useCheckPermission } from './useCheckPermission';

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

