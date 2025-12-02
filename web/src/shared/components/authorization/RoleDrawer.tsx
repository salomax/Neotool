"use client";

import React, { useMemo, useEffect, useState } from "react";
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

  // State to track pending selections during role creation
  const [pendingPermissions, setPendingPermissions] = useState<string[]>([]);
  const [pendingUsers, setPendingUsers] = useState<string[]>([]);
  const [pendingGroups, setPendingGroups] = useState<string[]>([]);

  // Query roles with permissions to get current role's permissions
  // Skip only if drawer is closed (not when creating, as we need to show sections)
  const { data: permissionsData, loading: permissionsLoading, error: permissionsError, refetch: refetchPermissions } = useGetRolesWithPermissionsQuery({
    skip: !open || !role, // Still skip when creating, but we'll handle permissions differently
    fetchPolicy: 'network-only',
    notifyOnNetworkStatusChange: true,
  });

  // Query users and groups with their roles to find which users/groups have this role
  // Always fetch when drawer is open (needed for both create and edit modes)
  const { data: usersGroupsData, loading: usersGroupsLoading, error: usersGroupsError, refetch: refetchUsersGroups } = useGetRoleWithUsersAndGroupsQuery({
    skip: !open, // Always fetch when drawer is open
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

  // Extract permissions - use pending state when creating
  const assignedPermissions = useMemo(() => {
    if (role) {
      return (roleWithPermissions?.permissions || []).map((p) => ({
        id: p.id,
        name: p.name,
      }));
    } else {
      // Create mode: return pending permissions as Permission objects
      // The component will fetch all permissions and match by ID
      return pendingPermissions.map((id) => ({
        id,
        name: "", // Will be filled by the component when it fetches all permissions
      }));
    }
  }, [roleWithPermissions?.permissions, role, pendingPermissions]);

  // Extract assigned users - use pending state when creating
  const assignedUsers = useMemo(() => {
    if (role) {
      if (!usersGroupsData?.users?.nodes) return [];
      return usersGroupsData.users.nodes
        .filter((user) => user.roles.some((r) => r.id === role.id))
        .map((user) => ({
          id: user.id,
          email: user.email,
          displayName: user.displayName,
          enabled: user.enabled,
        }));
    } else {
      // Create mode: fetch user data for pending user IDs
      if (!usersGroupsData?.users?.nodes) return [];
      return usersGroupsData.users.nodes
        .filter((user) => pendingUsers.includes(user.id))
        .map((user) => ({
          id: user.id,
          email: user.email,
          displayName: user.displayName,
          enabled: user.enabled,
        }));
    }
  }, [usersGroupsData?.users?.nodes, role, pendingUsers]);

  // Extract assigned groups - use pending state when creating
  const assignedGroups = useMemo(() => {
    if (role) {
      if (!usersGroupsData?.groups?.nodes) return [];
      return usersGroupsData.groups.nodes
        .filter((group) => group.roles.some((r) => r.id === role.id))
        .map((group) => ({
          id: group.id,
          name: group.name,
          description: group.description,
        }));
    } else {
      // Create mode: fetch group data for pending group IDs
      if (!usersGroupsData?.groups?.nodes) return [];
      return usersGroupsData.groups.nodes
        .filter((group) => pendingGroups.includes(group.id))
        .map((group) => ({
          id: group.id,
          name: group.name,
          description: group.description,
        }));
    }
  }, [usersGroupsData?.groups?.nodes, role, pendingGroups]);

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
      // Clear pending state when editing existing role
      setPendingPermissions([]);
      setPendingUsers([]);
      setPendingGroups([]);
    } else {
      methods.reset({
        name: "",
      });
      // Clear pending state when opening create drawer
      setPendingPermissions([]);
      setPendingUsers([]);
      setPendingGroups([]);
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
          console.error("Error assigning selections to new role:", assignErr);
          // Role was created but assignments failed - show warning
          toast.warning(
            t("roleManagement.toast.roleCreated", { name: data.name }) +
            " " +
            "Some assignments may have failed. Please check and update the role."
          );
        }
        
        toast.success(t("roleManagement.toast.roleCreated", { name: data.name }));
      }
      refetchRoles();
      // Clear pending state
      setPendingPermissions([]);
      setPendingUsers([]);
      setPendingGroups([]);
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
    if (role) {
      // Edit mode: assign immediately
      try {
        await assignPermissionToRole(role.id, permissionId);
        refetchPermissions(); // Refetch to get updated permissions
        refetchRoles(); // Refetch roles list
      } catch (err) {
        throw err; // Let RolePermissionAssignment handle the error
      }
    } else {
      // Create mode: add to pending state
      setPendingPermissions((prev) => {
        if (!prev.includes(permissionId)) {
          return [...prev, permissionId];
        }
        return prev;
      });
    }
  };

  const handleRemovePermission = async (permissionId: string) => {
    if (role) {
      // Edit mode: remove immediately
      try {
        await removePermissionFromRole(role.id, permissionId);
        refetchPermissions(); // Refetch to get updated permissions
        refetchRoles(); // Refetch roles list
      } catch (err) {
        throw err; // Let RolePermissionAssignment handle the error
      }
    } else {
      // Create mode: remove from pending state
      setPendingPermissions((prev) => prev.filter((id) => id !== permissionId));
    }
  };

  const handlePermissionsChange = () => {
    refetchPermissions();
    refetchRoles();
  };

  const handleAssignUser = async (userId: string) => {
    if (role) {
      // Edit mode: assign immediately
      try {
        await assignRoleToUser(userId, role.id);
        refetchUsersGroups(); // Refetch to get updated users/groups
        refetchRoles(); // Refetch roles list
      } catch (err) {
        throw err; // Let RoleUserAssignment handle the error
      }
    } else {
      // Create mode: add to pending state
      setPendingUsers((prev) => {
        if (!prev.includes(userId)) {
          return [...prev, userId];
        }
        return prev;
      });
    }
  };

  const handleRemoveUser = async (userId: string) => {
    if (role) {
      // Edit mode: remove immediately
      try {
        await removeRoleFromUser(userId, role.id);
        refetchUsersGroups(); // Refetch to get updated users/groups
        refetchRoles(); // Refetch roles list
      } catch (err) {
        throw err; // Let RoleUserAssignment handle the error
      }
    } else {
      // Create mode: remove from pending state
      setPendingUsers((prev) => prev.filter((id) => id !== userId));
    }
  };

  const handleUsersChange = () => {
    refetchUsersGroups();
    refetchRoles();
  };

  const handleAssignGroup = async (groupId: string) => {
    if (role) {
      // Edit mode: assign immediately
      try {
        await assignRoleToGroup(groupId, role.id);
        refetchUsersGroups(); // Refetch to get updated users/groups
        refetchRoles(); // Refetch roles list
      } catch (err) {
        throw err; // Let RoleGroupAssignment handle the error
      }
    } else {
      // Create mode: add to pending state
      setPendingGroups((prev) => {
        if (!prev.includes(groupId)) {
          return [...prev, groupId];
        }
        return prev;
      });
    }
  };

  const handleRemoveGroup = async (groupId: string) => {
    if (role) {
      // Edit mode: remove immediately
      try {
        await removeRoleFromGroup(groupId, role.id);
        refetchUsersGroups(); // Refetch to get updated users/groups
        refetchRoles(); // Refetch roles list
      } catch (err) {
        throw err; // Let RoleGroupAssignment handle the error
      }
    } else {
      // Create mode: remove from pending state
      setPendingGroups((prev) => prev.filter((id) => id !== groupId));
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

                {/* Show permissions, users, and groups sections for both create and edit */}
                <RolePermissionAssignment
                  roleId={role?.id || null}
                  assignedPermissions={assignedPermissions}
                  onAssignPermission={handleAssignPermission}
                  onRemovePermission={handleRemovePermission}
                  assignLoading={assignPermissionLoading}
                  removeLoading={removePermissionLoading}
                  onPermissionsChange={handlePermissionsChange}
                />

                <RoleUserAssignment
                  roleId={role?.id || null}
                  assignedUsers={assignedUsers}
                  onAssignUser={handleAssignUser}
                  onRemoveUser={handleRemoveUser}
                  assignLoading={assignRoleToUserLoading}
                  removeLoading={removeRoleFromUserLoading}
                  onUsersChange={handleUsersChange}
                />

                <RoleGroupAssignment
                  roleId={role?.id || null}
                  assignedGroups={assignedGroups}
                  onAssignGroup={handleAssignGroup}
                  onRemoveGroup={handleRemoveGroup}
                  assignLoading={assignRoleToGroupLoading}
                  removeLoading={removeRoleFromGroupLoading}
                  onGroupsChange={handleGroupsChange}
                />
              </Stack>
            </Box>
          </FormProvider>
        )}
      </Box>
    </Drawer>
  );
};

