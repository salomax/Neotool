"use client";

import React, { useMemo, useEffect, useState, useCallback, startTransition } from "react";
import {
  Box,
  Button,
  Stack,
} from "@mui/material";
import { useForm, FormProvider } from "react-hook-form";
import { Drawer } from "@/shared/components/ui/layout/Drawer";
import { LoadingState, ErrorAlert } from "@/shared/components/ui/feedback";
import { useGetRoleWithUsersAndGroupsQuery } from "@/lib/graphql/operations/authorization-management/queries.generated";
import { useRoleMutations } from "@/shared/hooks/authorization/useRoleMutations";
import { useRoleDrawer } from "@/shared/hooks/authorization/useRoleDrawer";
import { useTranslation } from "@/shared/i18n";
import { authorizationManagementTranslations } from "@/app/(settings)/settings/i18n";
import { useToast } from "@/shared/providers";
import { extractErrorMessage } from "@/shared/utils/error";
import { RoleForm, type RoleFormData } from "./RoleForm";
import { RolePermissionAssignment } from "./RolePermissionAssignment";
import { RoleUserAssignment, type User } from "./RoleUserAssignment";
import { RoleGroupAssignment, type Group } from "./RoleGroupAssignment";
import type { Permission } from "../permissions/PermissionList";
import { PermissionGate } from "@/shared/components/authorization";

export interface RoleDrawerProps {
  open: boolean;
  onClose: () => void;
  roleId: string | null;
}

/**
 * RoleDrawer component for editing role attributes and managing permissions
 * Supports both create mode (roleId === null) and edit mode (roleId !== null)
 */
export const RoleDrawer: React.FC<RoleDrawerProps> = ({
  open,
  onClose,
  roleId,
}) => {
  const isCreateMode = roleId === null;
  const { t } = useTranslation(authorizationManagementTranslations);
  const toast = useToast();

  // State to track pending selections during role creation
  const [pendingPermissions, setPendingPermissions] = useState<string[]>([]);
  const [pendingUsers, setPendingUsers] = useState<string[]>([]);
  const [pendingGroups, setPendingGroups] = useState<string[]>([]);
  
  // Use role drawer hook for edit mode (handles users, groups, and permissions state and mutations)
  const {
    role: roleFromHook,
    selectedUsers,
    selectedGroups,
    selectedPermissions,
    hasChanges: hasUserGroupPermissionChanges,
    saving: savingUserGroups,
    updateSelectedUsers,
    updateSelectedGroups,
    updateSelectedPermissions,
    handleSave: handleSaveUserGroups,
    resetChanges: resetUserGroupChanges,
  } = useRoleDrawer(roleId, open && !isCreateMode);

  // Query users and groups with their roles to find which users/groups have this role
  // Always fetch when drawer is open (needed for both create and edit modes)
  const { data: usersGroupsData, loading: usersGroupsLoading, error: usersGroupsError, refetch: refetchUsersGroups } = useGetRoleWithUsersAndGroupsQuery({
    skip: !open, // Always fetch when drawer is open
    fetchPolicy: 'network-only', // Always fetch fresh data when drawer opens
    notifyOnNetworkStatusChange: true,
  });

  // Use role from hook in edit mode, or null in create mode
  const role = isCreateMode ? null : roleFromHook;

  // Only show loading spinner on initial load, not on refetches
  const initialLoading = usersGroupsLoading && !usersGroupsData;
  const queryError = usersGroupsError;

  // Extract permissions - use hook state for edit mode, pending state for create mode
  const assignedPermissions = useMemo(() => {
    if (role) {
      // Edit mode: use selectedPermissions from hook
      return selectedPermissions;
    } else {
      // Create mode: return pending permissions as Permission objects
      // The component will fetch all permissions and match by ID
      return pendingPermissions.map((id) => ({
        id,
        name: "", // Will be filled by the component when it fetches all permissions
      }));
    }
  }, [role, selectedPermissions, pendingPermissions]);

  // Extract assigned users - use hook state for edit mode, pending state for create mode
  const assignedUsers = useMemo(() => {
    if (role) {
      // Edit mode: use selectedUsers from hook
      return selectedUsers;
    } else {
      // Create mode: fetch user data for pending user IDs
      if (!usersGroupsData?.users?.edges) return [];
      return usersGroupsData.users.edges.map(e => e.node)
        .filter((user) => pendingUsers.includes(user.id))
        .map((user) => ({
          id: user.id,
          email: user.email,
          displayName: user.displayName,
          enabled: user.enabled,
        }));
    }
  }, [role, selectedUsers, usersGroupsData, pendingUsers]);

  // Extract assigned groups - use hook state for edit mode, pending state for create mode
  const assignedGroups = useMemo(() => {
    if (role) {
      // Edit mode: use selectedGroups from hook
      return selectedGroups;
    } else {
      // Create mode: fetch group data for pending group IDs
      if (!usersGroupsData?.groups?.edges) return [];
      return usersGroupsData.groups.edges.map(e => e.node)
        .filter((group) => pendingGroups.includes(group.id))
        .map((group) => ({
          id: group.id,
          name: group.name,
          description: group.description,
        }));
    }
  }, [role, selectedGroups, usersGroupsData, pendingGroups]);

  // Use mutation hook directly - drawer doesn't need the query
  const {
    createRole,
    updateRole,
    assignPermissionToRole,
    assignRoleToUser,
    assignRoleToGroup,
    createLoading,
    updateLoading,
  } = useRoleMutations({
    // No refetch needed - drawer manages its own queries
  });

  // Form setup
  const methods = useForm<RoleFormData>({
    defaultValues: {
      name: "",
    },
  });

  // Update form when role changes
  useEffect(() => {
    if (isCreateMode) {
      // Reset form for create mode
      methods.reset({
        name: "",
      });
      // Clear pending state when opening create drawer
      startTransition(() => {
        setPendingPermissions([]);
        setPendingUsers([]);
        setPendingGroups([]);
      });
    } else if (role) {
      // Initialize form with role data for edit mode
      methods.reset({
        name: role.name,
      });
      // Clear pending state when editing existing role
      startTransition(() => {
        setPendingPermissions([]);
        setPendingUsers([]);
        setPendingGroups([]);
      });
    }
  }, [role, isCreateMode, methods]);

  // Reset form when drawer closes
  useEffect(() => {
    if (!open) {
      methods.reset({
        name: "",
      });
      startTransition(() => {
        setPendingPermissions([]);
        setPendingUsers([]);
        setPendingGroups([]);
      });
    }
  }, [open, methods]);

  // Refetch when drawer opens to ensure fresh data
  useEffect(() => {
    if (open && roleId) {
      refetchUsersGroups().catch(() => {
        // Silently handle refetch failures - data will refresh on next interaction
      });
    }
  }, [open, roleId, refetchUsersGroups]);

  const handleSubmit = async (data: RoleFormData) => {
    try {
      if (role) {
        // Edit mode: update role name first, then save user/group/permission changes
        await updateRole(role.id, data);
        
        // Save user, group, and permission changes if any
        if (hasUserGroupPermissionChanges) {
          await handleSaveUserGroups();
        }
        
        toast.success(t("roleManagement.toast.roleUpdated", { name: data.name }));
      } else {
        // Create role first
        const newRole = await createRole(data);
        
        // Assign all pending selections
        try {
          // Assign permissions
          for (const permissionId of pendingPermissions) {
            await assignPermissionToRole(newRole.id, permissionId);
          }
          
          // Assign users
          for (const userId of pendingUsers) {
            await assignRoleToUser(userId, newRole.id);
          }
          
          // Assign groups
          for (const groupId of pendingGroups) {
            await assignRoleToGroup(groupId, newRole.id);
          }
        } catch (assignErr) {
          // Role was created but assignments failed - show warning
          toast.warning(
            t("roleManagement.toast.roleCreated", { name: data.name }) +
            " " +
            "Some assignments may have failed. Please check and update the role."
          );
        }
        
        toast.success(t("roleManagement.toast.roleCreated", { name: data.name }));
      }
      // Note: No need to refetch roles list - mutations update cache and parent will refetch when needed
      // Clear pending state
      setPendingPermissions([]);
      setPendingUsers([]);
      setPendingGroups([]);
      onClose();
    } catch (err) {
      const errorMessage = extractErrorMessage(
        err,
        isCreateMode
          ? t("roleManagement.toast.roleCreateError")
          : t("roleManagement.toast.roleUpdateError")
      );
      toast.error(errorMessage);
    }
  };

  // Handle permission changes for create mode
  const handleCreateModePermissionChange = useCallback((permissions: Permission[]) => {
    if (isCreateMode) {
      setPendingPermissions(permissions.map(p => p.id));
    }
  }, [isCreateMode]);
  
  // Handle permission changes for edit mode (handled by hook via onChange)
  const handlePermissionsChange = useCallback(() => {
    // No-op for edit mode (handled by hook)
    // For create mode, this is handled by pendingPermissions state
  }, []);

  // Handle user changes for create mode
  const handleCreateModeUserChange = useCallback((users: User[]) => {
    if (isCreateMode) {
      setPendingUsers(users.map(u => u.id));
    }
  }, [isCreateMode]);
  
  // Handle group changes for create mode
  const handleCreateModeGroupChange = useCallback((groups: Group[]) => {
    if (isCreateMode) {
      setPendingGroups(groups.map(g => g.id));
    }
  }, [isCreateMode]);
  
  // Handle user changes for create mode (edit mode handled by hook)
  const handleUsersChange = useCallback(() => {
    // No-op for edit mode (handled by hook)
    // For create mode, this is handled by pendingUsers state
  }, []);
  
  // Handle group changes for create mode (edit mode handled by hook)
  const handleGroupsChange = useCallback(() => {
    // No-op for edit mode (handled by hook)
    // For create mode, this is handled by pendingGroups state
  }, []);

  return (
    <Drawer
      open={open}
      onClose={onClose}
      anchor="right"
      size="md"
      variant="temporary"
    >
      <Drawer.Header title={isCreateMode ? t("roleManagement.createRole") : t("roleManagement.editRole")} />
      <Drawer.Body>
        <LoadingState isLoading={initialLoading} />

        <ErrorAlert
          error={queryError || undefined}
          onRetry={() => {
            refetchUsersGroups();
          }}
          fallbackMessage={t("roleManagement.drawer.errorLoading")}
        />

        {!initialLoading && !queryError && (
          <FormProvider {...methods}>
            <Box
              component="form"
              id="role-form"
              onSubmit={methods.handleSubmit(handleSubmit)}
              noValidate
            >
              <Stack spacing={3}>
                <RoleForm
                  initialValues={
                    role
                      ? { name: role.name }
                      : undefined
                  }
                />

                {/* Show permissions, users, and groups sections for both create and edit */}
                <PermissionGate require="security:role:save">
                  <RolePermissionAssignment
                    roleId={roleId}
                    assignedPermissions={assignedPermissions}
                    onAssignPermission={undefined}
                    onRemovePermission={undefined}
                    assignLoading={false}
                    removeLoading={false}
                    onPermissionsChange={handlePermissionsChange}
                    active={open}
                    onChange={isCreateMode ? handleCreateModePermissionChange : updateSelectedPermissions}
                  />

                  <RoleUserAssignment
                    roleId={roleId}
                    assignedUsers={assignedUsers}
                    onAssignUser={undefined}
                    onRemoveUser={undefined}
                    assignLoading={false}
                    removeLoading={false}
                    onUsersChange={handleUsersChange}
                    active={open}
                    onChange={isCreateMode ? handleCreateModeUserChange : updateSelectedUsers}
                  />

                  <RoleGroupAssignment
                    roleId={roleId}
                    assignedGroups={assignedGroups}
                    onAssignGroup={undefined}
                    onRemoveGroup={undefined}
                    assignLoading={false}
                    removeLoading={false}
                    onGroupsChange={handleGroupsChange}
                    active={open}
                    onChange={isCreateMode ? handleCreateModeGroupChange : updateSelectedGroups}
                  />
                </PermissionGate>
              </Stack>
            </Box>
          </FormProvider>
        )}
      </Drawer.Body>
      {!initialLoading && !queryError && (
        <Drawer.Footer>
          <PermissionGate require={isCreateMode ? "security:role:save" : "security:role:save"}>
            <Stack direction="row" spacing={2} justifyContent="flex-end">
              <Button
                variant="outlined"
                onClick={() => {
                  if (!isCreateMode) {
                    resetUserGroupChanges();
                  }
                  onClose();
                }}
                disabled={createLoading || updateLoading || savingUserGroups}
              >
                {t("roleManagement.form.cancel")}
              </Button>
              <Button
                type="submit"
                form="role-form"
                variant="contained"
                disabled={createLoading || updateLoading || savingUserGroups || (!isCreateMode && !hasUserGroupPermissionChanges && !methods.formState.isDirty)}
                data-testid="role-form-submit"
              >
                {createLoading || updateLoading || savingUserGroups
                  ? t("roleManagement.form.saving")
                  : isCreateMode
                  ? t("roleManagement.form.create")
                  : t("roleManagement.form.save")}
              </Button>
            </Stack>
          </PermissionGate>
        </Drawer.Footer>
      )}
    </Drawer>
  );
};
