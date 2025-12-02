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
import { usePermissionManagement } from "@/shared/hooks/authorization/usePermissionManagement";
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

  // Optimistic state for immediate UI updates without waiting for refetch
  const [optimisticPermissions, setOptimisticPermissions] = useState<Permission[] | null>(null);
  const [optimisticUsers, setOptimisticUsers] = useState<User[] | null>(null);
  const [optimisticGroups, setOptimisticGroups] = useState<Group[] | null>(null);

  // Fetch all permissions for optimistic updates (need permission names)
  const { permissions: allPermissions } = usePermissionManagement({
    initialFirst: 1000, // Fetch all permissions
  });

  // Query roles with permissions to get current role's permissions
  // Skip only if drawer is closed (not when creating, as we need to show sections)
  const { data: permissionsData, loading: permissionsLoading, error: permissionsError, refetch: refetchPermissions } = useGetRolesWithPermissionsQuery({
    skip: !open || !role, // Still skip when creating, but we'll handle permissions differently
    fetchPolicy: 'cache-and-network', // Use cache to prevent loading flicker
    notifyOnNetworkStatusChange: false, // Don't show loading on refetch
  });

  // Query users and groups with their roles to find which users/groups have this role
  // Always fetch when drawer is open (needed for both create and edit modes)
  const { data: usersGroupsData, loading: usersGroupsLoading, error: usersGroupsError, refetch: refetchUsersGroups } = useGetRoleWithUsersAndGroupsQuery({
    skip: !open, // Always fetch when drawer is open
    fetchPolicy: 'cache-and-network', // Use cache to prevent loading flicker
    notifyOnNetworkStatusChange: false, // Don't show loading on refetch
  });

  // Only show loading spinner on initial load, not on refetches
  const initialLoading = (permissionsLoading && !permissionsData) || (usersGroupsLoading && !usersGroupsData);
  const queryError = permissionsError || usersGroupsError;

  // Find the specific role from the query results
  const roleWithPermissions = useMemo(() => {
    if (!permissionsData?.roles?.nodes || !role) return null;
    return permissionsData.roles.nodes.find((r) => r.id === role.id) || null;
  }, [permissionsData?.roles?.nodes, role]);

  // Extract permissions - use optimistic state if available, otherwise use query data
  const assignedPermissions = useMemo(() => {
    // Use optimistic state if available (for immediate UI updates)
    if (optimisticPermissions !== null) {
      return optimisticPermissions;
    }
    
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
  }, [roleWithPermissions?.permissions, role, pendingPermissions, optimisticPermissions]);

  // Extract assigned users - use optimistic state if available
  const assignedUsers = useMemo(() => {
    // Use optimistic state if available (for immediate UI updates)
    if (optimisticUsers !== null) {
      return optimisticUsers;
    }
    
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
  }, [usersGroupsData?.users?.nodes, role, pendingUsers, optimisticUsers]);

  // Extract assigned groups - use optimistic state if available
  const assignedGroups = useMemo(() => {
    // Use optimistic state if available (for immediate UI updates)
    if (optimisticGroups !== null) {
      return optimisticGroups;
    }
    
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
  }, [usersGroupsData?.groups?.nodes, role, pendingGroups, optimisticGroups]);

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
      // Clear pending and optimistic state when editing existing role
      setPendingPermissions([]);
      setPendingUsers([]);
      setPendingGroups([]);
      setOptimisticPermissions(null);
      setOptimisticUsers(null);
      setOptimisticGroups(null);
    } else {
      methods.reset({
        name: "",
      });
      // Clear pending and optimistic state when opening create drawer
      setPendingPermissions([]);
      setPendingUsers([]);
      setPendingGroups([]);
      setOptimisticPermissions(null);
      setOptimisticUsers(null);
      setOptimisticGroups(null);
    }
  }, [role, methods]);

  // Refetch when drawer opens to ensure fresh data
  useEffect(() => {
    if (open && role) {
      refetchPermissions().catch(console.error);
      refetchUsersGroups().catch(console.error);
      // Clear optimistic state when opening drawer to ensure fresh data
      setOptimisticPermissions(null);
      setOptimisticUsers(null);
      setOptimisticGroups(null);
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
      // Edit mode: optimistically update UI immediately
      const permission = allPermissions?.find((p) => p.id === permissionId);
      if (permission) {
        setOptimisticPermissions((prev) => {
          // Get current base value from query data if prev is null
          const basePermissions = prev || (roleWithPermissions?.permissions || []).map((p) => ({
            id: p.id,
            name: p.name,
          }));
          // Don't add if already exists
          if (basePermissions.some((p) => p.id === permissionId)) return basePermissions;
          return [...basePermissions, { id: permission.id, name: permission.name }];
        });
      }
      
      try {
        await assignPermissionToRole(role.id, permissionId);
        // Refetch in background without blocking UI
        refetchPermissions().catch(console.error);
        refetchRoles().catch(console.error);
        // Clear optimistic state after successful refetch (with a small delay to let refetch complete)
        setTimeout(() => {
          setOptimisticPermissions(null);
        }, 1000);
      } catch (err) {
        // Rollback optimistic update on error
        setOptimisticPermissions(null);
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
      // Edit mode: optimistically update UI immediately
      setOptimisticPermissions((prev) => {
        // Get current base value from query data if prev is null
        const basePermissions = prev || (roleWithPermissions?.permissions || []).map((p) => ({
          id: p.id,
          name: p.name,
        }));
        return basePermissions.filter((p) => p.id !== permissionId);
      });
      
      try {
        await removePermissionFromRole(role.id, permissionId);
        // Refetch in background without blocking UI
        refetchPermissions().catch(console.error);
        refetchRoles().catch(console.error);
        // Clear optimistic state after successful refetch
        setTimeout(() => {
          setOptimisticPermissions(null);
        }, 1000);
      } catch (err) {
        // Rollback optimistic update on error
        setOptimisticPermissions(null);
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
      // Edit mode: optimistically update UI immediately
      const user = usersGroupsData?.users?.nodes?.find((u) => u.id === userId);
      if (user) {
        setOptimisticUsers((prev) => {
          // Get current base value from query data if prev is null
          const baseUsers = prev || (usersGroupsData?.users?.nodes || [])
            .filter((u) => u.roles.some((r) => r.id === role.id))
            .map((u) => ({
              id: u.id,
              email: u.email,
              displayName: u.displayName,
              enabled: u.enabled,
            }));
          // Don't add if already exists
          if (baseUsers.some((u) => u.id === userId)) return baseUsers;
          return [
            ...baseUsers,
            {
              id: user.id,
              email: user.email,
              displayName: user.displayName,
              enabled: user.enabled,
            },
          ];
        });
      }
      
      try {
        await assignRoleToUser(userId, role.id);
        // Refetch in background without blocking UI
        refetchUsersGroups().catch(console.error);
        refetchRoles().catch(console.error);
        // Clear optimistic state after successful refetch
        setTimeout(() => {
          setOptimisticUsers(null);
        }, 1000);
      } catch (err) {
        // Rollback optimistic update on error
        setOptimisticUsers(null);
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
      // Edit mode: optimistically update UI immediately
      setOptimisticUsers((prev) => {
        // Get current base value from query data if prev is null
        const baseUsers = prev || (usersGroupsData?.users?.nodes || [])
          .filter((u) => u.roles.some((r) => r.id === role.id))
          .map((u) => ({
            id: u.id,
            email: u.email,
            displayName: u.displayName,
            enabled: u.enabled,
          }));
        return baseUsers.filter((u) => u.id !== userId);
      });
      
      try {
        await removeRoleFromUser(userId, role.id);
        // Refetch in background without blocking UI
        refetchUsersGroups().catch(console.error);
        refetchRoles().catch(console.error);
        // Clear optimistic state after successful refetch
        setTimeout(() => {
          setOptimisticUsers(null);
        }, 1000);
      } catch (err) {
        // Rollback optimistic update on error
        setOptimisticUsers(null);
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
      // Edit mode: optimistically update UI immediately
      const group = usersGroupsData?.groups?.nodes?.find((g) => g.id === groupId);
      if (group) {
        setOptimisticGroups((prev) => {
          // Get current base value from query data if prev is null
          const baseGroups = prev || (usersGroupsData?.groups?.nodes || [])
            .filter((g) => g.roles.some((r) => r.id === role.id))
            .map((g) => ({
              id: g.id,
              name: g.name,
              description: g.description,
            }));
          // Don't add if already exists
          if (baseGroups.some((g) => g.id === groupId)) return baseGroups;
          return [
            ...baseGroups,
            {
              id: group.id,
              name: group.name,
              description: group.description,
            },
          ];
        });
      }
      
      try {
        await assignRoleToGroup(groupId, role.id);
        // Refetch in background without blocking UI
        refetchUsersGroups().catch(console.error);
        refetchRoles().catch(console.error);
        // Clear optimistic state after successful refetch
        setTimeout(() => {
          setOptimisticGroups(null);
        }, 1000);
      } catch (err) {
        // Rollback optimistic update on error
        setOptimisticGroups(null);
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
      // Edit mode: optimistically update UI immediately
      setOptimisticGroups((prev) => {
        // Get current base value from query data if prev is null
        const baseGroups = prev || (usersGroupsData?.groups?.nodes || [])
          .filter((g) => g.roles.some((r) => r.id === role.id))
          .map((g) => ({
            id: g.id,
            name: g.name,
            description: g.description,
          }));
        return baseGroups.filter((g) => g.id !== groupId);
      });
      
      try {
        await removeRoleFromGroup(groupId, role.id);
        // Refetch in background without blocking UI
        refetchUsersGroups().catch(console.error);
        refetchRoles().catch(console.error);
        // Clear optimistic state after successful refetch
        setTimeout(() => {
          setOptimisticGroups(null);
        }, 1000);
      } catch (err) {
        // Rollback optimistic update on error
        setOptimisticGroups(null);
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
        !initialLoading && !queryError ? (
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
        {initialLoading && (
          <Box sx={{ display: "flex", justifyContent: "center", p: 4 }}>
            <CircularProgress />
          </Box>
        )}

        {queryError && (
          <Alert severity="error" sx={{ mb: 3 }}>
            {t("roleManagement.drawer.errorLoading")}
          </Alert>
        )}

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

