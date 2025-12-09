"use client";

import React, { useMemo, useEffect, useState, startTransition } from "react";
import {
  Box,
  Button,
  Stack,
} from "@mui/material";
import { useForm, FormProvider } from "react-hook-form";
import { Drawer } from "@/shared/components/ui/layout/Drawer";
import { LoadingState, ErrorAlert } from "@/shared/components/ui/feedback";
import { useGetRolesWithPermissionsQuery, useGetRoleWithUsersAndGroupsQuery } from "@/lib/graphql/operations/authorization-management/queries.generated";
import { useRoleManagement, type Role } from "@/shared/hooks/authorization/useRoleManagement";
import { usePermissionManagement } from "@/shared/hooks/authorization/usePermissionManagement";
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

  // Optimistic state for immediate UI updates without waiting for refetch
  const [optimisticPermissions, setOptimisticPermissions] = useState<Permission[] | null>(null);
  const [optimisticUsers, setOptimisticUsers] = useState<User[] | null>(null);
  const [optimisticGroups, setOptimisticGroups] = useState<Group[] | null>(null);

  // Fetch all permissions for optimistic updates (need permission names)
  const { permissions: allPermissions } = usePermissionManagement({
    initialFirst: 1000, // Fetch all permissions
  });

  // Query roles with permissions to get current role's permissions
  // Only query when editing (roleId is provided), skip in create mode
  const { data: permissionsData, loading: permissionsLoading, error: permissionsError, refetch: refetchPermissions } = useGetRolesWithPermissionsQuery({
    skip: !open || !roleId, // Skip when drawer is closed or in create mode
    fetchPolicy: 'network-only', // Always fetch fresh data when drawer opens
    notifyOnNetworkStatusChange: true,
  });

  // Query users and groups with their roles to find which users/groups have this role
  // Always fetch when drawer is open (needed for both create and edit modes)
  const { data: usersGroupsData, loading: usersGroupsLoading, error: usersGroupsError, refetch: refetchUsersGroups } = useGetRoleWithUsersAndGroupsQuery({
    skip: !open, // Always fetch when drawer is open
    fetchPolicy: 'network-only', // Always fetch fresh data when drawer opens
    notifyOnNetworkStatusChange: true,
  });

  // Only show loading spinner on initial load, not on refetches
  const initialLoading = (permissionsLoading && !permissionsData) || (usersGroupsLoading && !usersGroupsData);
  const queryError = permissionsError || usersGroupsError;

  // Find the specific role from the query results
  const roleWithPermissions = useMemo(() => {
    if (!permissionsData?.roles?.edges || !roleId) return null;
    return permissionsData.roles.edges.map(e => e.node).find((r) => r.id === roleId) || null;
  }, [permissionsData, roleId]);

  // Derive role object from roleId and query data
  const role = useMemo(() => {
    if (!roleId) return null;
    if (roleWithPermissions) {
      return {
        id: roleWithPermissions.id,
        name: roleWithPermissions.name,
      };
    }
    return null;
  }, [roleId, roleWithPermissions]);

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
      if (!usersGroupsData?.users?.edges) return [];
      return usersGroupsData.users.edges.map(e => e.node)
        .filter((user) => user.roles.some((r) => r.id === role.id))
        .map((user) => ({
          id: user.id,
          email: user.email,
          displayName: user.displayName,
          enabled: user.enabled,
        }));
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
  }, [usersGroupsData, role, pendingUsers, optimisticUsers]);

  // Extract assigned groups - use optimistic state if available
  const assignedGroups = useMemo(() => {
    // Use optimistic state if available (for immediate UI updates)
    if (optimisticGroups !== null) {
      return optimisticGroups;
    }
    
    if (role) {
      if (!usersGroupsData?.groups?.edges) return [];
      return usersGroupsData.groups.edges.map(e => e.node)
        .filter((group) => group.roles.some((r) => r.id === role.id))
        .map((group) => ({
          id: group.id,
          name: group.name,
          description: group.description,
        }));
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
  }, [usersGroupsData, role, pendingGroups, optimisticGroups]);

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
    if (isCreateMode) {
      // Reset form for create mode
      methods.reset({
        name: "",
      });
      // Clear pending and optimistic state when opening create drawer
      startTransition(() => {
        setPendingPermissions([]);
        setPendingUsers([]);
        setPendingGroups([]);
        setOptimisticPermissions(null);
        setOptimisticUsers(null);
        setOptimisticGroups(null);
      });
    } else if (role) {
      // Initialize form with role data for edit mode
      methods.reset({
        name: role.name,
      });
      // Clear pending and optimistic state when editing existing role
      startTransition(() => {
        setPendingPermissions([]);
        setPendingUsers([]);
        setPendingGroups([]);
        setOptimisticPermissions(null);
        setOptimisticUsers(null);
        setOptimisticGroups(null);
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
        setOptimisticPermissions(null);
        setOptimisticUsers(null);
        setOptimisticGroups(null);
      });
    }
  }, [open, methods]);

  // Refetch when drawer opens to ensure fresh data
  useEffect(() => {
    if (open && roleId) {
      refetchPermissions().catch(() => {
        // Silently handle refetch failures - data will refresh on next interaction
      });
      refetchUsersGroups().catch(() => {
        // Silently handle refetch failures - data will refresh on next interaction
      });
      // Clear optimistic state when opening drawer to ensure fresh data
      startTransition(() => {
        setOptimisticPermissions(null);
        setOptimisticUsers(null);
        setOptimisticGroups(null);
      });
    }
  }, [open, roleId, refetchPermissions, refetchUsersGroups]);

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
      const errorMessage = extractErrorMessage(
        err,
        isCreateMode
          ? t("roleManagement.toast.roleCreateError")
          : t("roleManagement.toast.roleUpdateError")
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
        refetchPermissions().catch(() => {
          // Silently handle refetch failures - data will refresh on next interaction
        });
        refetchRoles();
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
        refetchPermissions().catch(() => {
          // Silently handle refetch failures - data will refresh on next interaction
        });
        refetchRoles();
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
      const user = usersGroupsData?.users?.edges?.map(e => e.node).find((u) => u.id === userId);
      if (user) {
        setOptimisticUsers((prev) => {
          // Get current base value from query data if prev is null
          const baseUsers = prev || (usersGroupsData?.users?.edges?.map(e => e.node) || [])
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
        refetchUsersGroups().catch(() => {
          // Silently handle refetch failures - data will refresh on next interaction
        });
        refetchRoles();
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
        const baseUsers = prev || (usersGroupsData?.users?.edges?.map(e => e.node) || [])
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
        refetchUsersGroups().catch(() => {
          // Silently handle refetch failures - data will refresh on next interaction
        });
        refetchRoles();
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
      const group = usersGroupsData?.groups?.edges?.map(e => e.node).find((g) => g.id === groupId);
      if (group) {
        setOptimisticGroups((prev) => {
          // Get current base value from query data if prev is null
          const baseGroups = prev || (usersGroupsData?.groups?.edges?.map(e => e.node) || [])
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
        refetchUsersGroups().catch(() => {
          // Silently handle refetch failures - data will refresh on next interaction
        });
        refetchRoles();
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
        const baseGroups = prev || (usersGroupsData?.groups?.edges?.map(e => e.node) || [])
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
        refetchUsersGroups().catch(() => {
          // Silently handle refetch failures - data will refresh on next interaction
        });
        refetchRoles();
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
            refetchPermissions();
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
                    onAssignPermission={handleAssignPermission}
                    onRemovePermission={handleRemovePermission}
                    assignLoading={assignPermissionLoading}
                    removeLoading={removePermissionLoading}
                    onPermissionsChange={handlePermissionsChange}
                  />

                  <RoleUserAssignment
                    roleId={roleId}
                    assignedUsers={assignedUsers}
                    onAssignUser={handleAssignUser}
                    onRemoveUser={handleRemoveUser}
                    assignLoading={assignRoleToUserLoading}
                    removeLoading={removeRoleFromUserLoading}
                    onUsersChange={handleUsersChange}
                  />

                  <RoleGroupAssignment
                    roleId={roleId}
                    assignedGroups={assignedGroups}
                    onAssignGroup={handleAssignGroup}
                    onRemoveGroup={handleRemoveGroup}
                    assignLoading={assignRoleToGroupLoading}
                    removeLoading={removeRoleFromGroupLoading}
                    onGroupsChange={handleGroupsChange}
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

