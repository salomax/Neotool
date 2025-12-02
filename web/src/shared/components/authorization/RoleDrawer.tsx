"use client";

import React, { useMemo, useEffect } from "react";
import {
  Box,
  Button,
  Stack,
  CircularProgress,
  Alert,
} from "@mui/material";
import { useForm, FormProvider } from "react-hook-form";
import { Drawer } from "@/shared/components/ui/layout/Drawer";
import { useGetRolesWithPermissionsQuery, useGetRoleWithUsersAndGroupsQuery } from "@/lib/graphql/operations/authorization-management/queries.generated";
import { useRoleManagement, type Role } from "@/shared/hooks/authorization/useRoleManagement";
import { useTranslation } from "@/shared/i18n";
import { authorizationManagementTranslations } from "@/app/(neotool)/settings/i18n";
import { useToast } from "@/shared/providers";
import { extractErrorMessage } from "@/shared/utils/error";
import { RoleForm, type RoleFormData } from "./RoleForm";
import { RolePermissionAssignment } from "./RolePermissionAssignment";
import { RoleUserAssignment, type User } from "./RoleUserAssignment";
import { RoleGroupAssignment, type Group } from "./RoleGroupAssignment";
import type { Permission } from "./PermissionList";

export interface RoleDrawerProps {
  open: boolean;
  onClose: () => void;
  role: Role | null;
}

/**
 * RoleDrawer component for editing role attributes and managing permissions
 */
export const RoleDrawer: React.FC<RoleDrawerProps> = ({
  open,
  onClose,
  role,
}) => {
  const { t } = useTranslation(authorizationManagementTranslations);
  const toast = useToast();

  // Query roles with permissions to get current role's permissions
  const { data: permissionsData, loading: permissionsLoading, error: permissionsError, refetch: refetchPermissions } = useGetRolesWithPermissionsQuery({
    skip: !open || !role,
    fetchPolicy: 'network-only',
    notifyOnNetworkStatusChange: true,
  });

  // Query users and groups with their roles to find which users/groups have this role
  const { data: usersGroupsData, loading: usersGroupsLoading, error: usersGroupsError, refetch: refetchUsersGroups } = useGetRoleWithUsersAndGroupsQuery({
    skip: !open || !role,
    fetchPolicy: 'network-only',
    notifyOnNetworkStatusChange: true,
  });

  const queryLoading = permissionsLoading || usersGroupsLoading;
  const queryError = permissionsError || usersGroupsError;

  // Find the specific role from the query results
  const roleWithPermissions = useMemo(() => {
    if (!permissionsData?.roles?.nodes || !role) return null;
    return permissionsData.roles.nodes.find((r) => r.id === role.id) || null;
  }, [permissionsData?.roles?.nodes, role]);

  // Extract permissions
  const assignedPermissions = useMemo(() => {
    return (roleWithPermissions?.permissions || []).map((p) => ({
      id: p.id,
      name: p.name,
    }));
  }, [roleWithPermissions?.permissions]);

  // Extract assigned users (users that have this role)
  const assignedUsers = useMemo(() => {
    if (!usersGroupsData?.users?.nodes || !role) return [];
    return usersGroupsData.users.nodes
      .filter((user) => user.roles.some((r) => r.id === role.id))
      .map((user) => ({
        id: user.id,
        email: user.email,
        displayName: user.displayName,
        enabled: user.enabled,
      }));
  }, [usersGroupsData?.users?.nodes, role]);

  // Extract assigned groups (groups that have this role)
  const assignedGroups = useMemo(() => {
    if (!usersGroupsData?.groups?.nodes || !role) return [];
    return usersGroupsData.groups.nodes
      .filter((group) => group.roles.some((r) => r.id === role.id))
      .map((group) => ({
        id: group.id,
        name: group.name,
        description: group.description,
      }));
  }, [usersGroupsData?.groups?.nodes, role]);

  const {
    createRole,
    updateRole,
    assignPermissionToRole,
    removePermissionFromRole,
    assignRoleToUser,
    removeRoleFromUser,
    assignRoleToGroup,
    removeRoleFromGroup,
    createLoading,
    updateLoading,
    assignPermissionLoading,
    removePermissionLoading,
    assignRoleToUserLoading,
    removeRoleFromUserLoading,
    assignRoleToGroupLoading,
    removeRoleFromGroupLoading,
    refetch: refetchRoles,
  } = useRoleManagement();

  // Form setup
  const methods = useForm<RoleFormData>({
    defaultValues: {
      name: "",
    },
  });

  // Update form when role changes
  useEffect(() => {
    if (role) {
      methods.reset({
        name: role.name,
      });
    } else {
      methods.reset({
        name: "",
      });
    }
  }, [role, methods]);

  // Refetch when drawer opens to ensure fresh data
  useEffect(() => {
    if (open && role) {
      refetchPermissions();
      refetchUsersGroups();
    }
  }, [open, role, refetchPermissions, refetchUsersGroups]);

  const handleSubmit = async (data: RoleFormData) => {
    try {
      if (role) {
        await updateRole(role.id, data);
        toast.success(t("roleManagement.toast.roleUpdated", { name: data.name }));
      } else {
        await createRole(data);
        toast.success(t("roleManagement.toast.roleCreated", { name: data.name }));
      }
      refetchRoles();
      onClose();
    } catch (err) {
      console.error("Error submitting role form:", err);
      const errorMessage = extractErrorMessage(
        err,
        role
          ? t("roleManagement.toast.roleUpdateError")
          : t("roleManagement.toast.roleCreateError")
      );
      toast.error(errorMessage);
    }
  };

  const handleAssignPermission = async (permissionId: string) => {
    if (!role) return;
    try {
      await assignPermissionToRole(role.id, permissionId);
      refetchPermissions(); // Refetch to get updated permissions
      refetchRoles(); // Refetch roles list
    } catch (err) {
      throw err; // Let RolePermissionAssignment handle the error
    }
  };

  const handleRemovePermission = async (permissionId: string) => {
    if (!role) return;
    try {
      await removePermissionFromRole(role.id, permissionId);
      refetchPermissions(); // Refetch to get updated permissions
      refetchRoles(); // Refetch roles list
    } catch (err) {
      throw err; // Let RolePermissionAssignment handle the error
    }
  };

  const handlePermissionsChange = () => {
    refetchPermissions();
    refetchRoles();
  };

  const handleAssignUser = async (userId: string) => {
    if (!role) return;
    try {
      await assignRoleToUser(userId, role.id);
      refetchUsersGroups(); // Refetch to get updated users/groups
      refetchRoles(); // Refetch roles list
    } catch (err) {
      throw err; // Let RoleUserAssignment handle the error
    }
  };

  const handleRemoveUser = async (userId: string) => {
    if (!role) return;
    try {
      await removeRoleFromUser(userId, role.id);
      refetchUsersGroups(); // Refetch to get updated users/groups
      refetchRoles(); // Refetch roles list
    } catch (err) {
      throw err; // Let RoleUserAssignment handle the error
    }
  };

  const handleUsersChange = () => {
    refetchUsersGroups();
    refetchRoles();
  };

  const handleAssignGroup = async (groupId: string) => {
    if (!role) return;
    try {
      await assignRoleToGroup(groupId, role.id);
      refetchUsersGroups(); // Refetch to get updated users/groups
      refetchRoles(); // Refetch roles list
    } catch (err) {
      throw err; // Let RoleGroupAssignment handle the error
    }
  };

  const handleRemoveGroup = async (groupId: string) => {
    if (!role) return;
    try {
      await removeRoleFromGroup(groupId, role.id);
      refetchUsersGroups(); // Refetch to get updated users/groups
      refetchRoles(); // Refetch roles list
    } catch (err) {
      throw err; // Let RoleGroupAssignment handle the error
    }
  };

  const handleGroupsChange = () => {
    refetchUsersGroups();
    refetchRoles();
  };

  return (
    <Drawer
      open={open}
      onClose={onClose}
      title={role ? t("roleManagement.editRole") : t("roleManagement.createRole")}
      anchor="right"
      width={600}
      variant="temporary"
      footer={
        !queryLoading && !queryError ? (
          <Stack direction="row" spacing={2} justifyContent="flex-end">
            <Button
              variant="outlined"
              onClick={onClose}
              disabled={createLoading || updateLoading}
            >
              {t("roleManagement.form.cancel")}
            </Button>
            <Button
              type="submit"
              form="role-form"
              variant="contained"
              disabled={createLoading || updateLoading}
              data-testid="role-form-submit"
            >
              {createLoading || updateLoading
                ? t("roleManagement.form.saving")
                : role
                ? t("roleManagement.form.save")
                : t("roleManagement.form.create")}
            </Button>
          </Stack>
        ) : undefined
      }
    >
      <Box sx={{ p: 3 }}>
        {queryLoading && (
          <Box sx={{ display: "flex", justifyContent: "center", p: 4 }}>
            <CircularProgress />
          </Box>
        )}

        {queryError && (
          <Alert severity="error" sx={{ mb: 3 }}>
            {t("roleManagement.drawer.errorLoading")}
          </Alert>
        )}

        {!queryLoading && !queryError && (
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

                {role && (
                  <>
                    <RolePermissionAssignment
                      roleId={role.id}
                      assignedPermissions={assignedPermissions}
                      onAssignPermission={handleAssignPermission}
                      onRemovePermission={handleRemovePermission}
                      assignLoading={assignPermissionLoading}
                      removeLoading={removePermissionLoading}
                      onPermissionsChange={handlePermissionsChange}
                    />

                    <RoleUserAssignment
                      roleId={role.id}
                      assignedUsers={assignedUsers}
                      onAssignUser={handleAssignUser}
                      onRemoveUser={handleRemoveUser}
                      assignLoading={assignRoleToUserLoading}
                      removeLoading={removeRoleFromUserLoading}
                      onUsersChange={handleUsersChange}
                    />

                    <RoleGroupAssignment
                      roleId={role.id}
                      assignedGroups={assignedGroups}
                      onAssignGroup={handleAssignGroup}
                      onRemoveGroup={handleRemoveGroup}
                      assignLoading={assignRoleToGroupLoading}
                      removeLoading={removeRoleFromGroupLoading}
                      onGroupsChange={handleGroupsChange}
                    />
                  </>
                )}
              </Stack>
            </Box>
          </FormProvider>
        )}
      </Box>
    </Drawer>
  );
};

