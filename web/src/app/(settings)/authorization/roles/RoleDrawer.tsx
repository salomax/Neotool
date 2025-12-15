"use client";

import React, { useMemo, useEffect, useState, useCallback, startTransition } from "react";
import {
  Box,
  Button,
  Stack,
  Typography,
  IconButton,
} from "@mui/material";
import { TextField } from "@/shared/components/ui/primitives";
import { ShieldCheckIcon } from "@/shared/components/ui/icons/ShieldCheckIcon";
import { useForm, FormProvider } from "react-hook-form";
import { Drawer } from "@/shared/components/ui/layout/Drawer";
import { CloseIcon } from "@/shared/ui/mui-imports";
import { LoadingState, ErrorAlert } from "@/shared/components/ui/feedback";
import { useCreateRoleMutation, useUpdateRoleMutation } from "@/lib/graphql/operations/authorization-management/mutations.generated";
import { useRoleMutations } from "@/shared/hooks/authorization/useRoleMutations";
import { useRoleDrawer } from "@/shared/hooks/authorization/useRoleDrawer";
import { useTranslation } from "@/shared/i18n";
import { authorizationManagementTranslations } from "@/app/(settings)/settings/i18n";
import { useToast } from "@/shared/providers";
import { extractErrorMessage } from "@/shared/utils/error";
import { RoleForm, type RoleFormData } from "./RoleForm";
import { RolePermissionAssignment } from "./RolePermissionAssignment";
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
  const [pendingGroups, setPendingGroups] = useState<Group[]>([]);
  
  // Use role drawer hook for edit mode (handles users, groups, and permissions state and mutations)
  const {
    role: roleFromHook,
    selectedGroups,
    selectedPermissions,
    hasChanges: hasUserGroupPermissionChanges,
    saving: savingUserGroups,
    updateSelectedGroups,
    updateSelectedPermissions,
    handleSave: handleSaveUserGroups,
    resetChanges: resetUserGroupChanges,
  } = useRoleDrawer(roleId, open && !isCreateMode);

  // Use role from hook in edit mode, or null in create mode
  const role = isCreateMode ? null : roleFromHook;

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

  // Extract assigned groups - use hook state for edit mode, pending state for create mode
  const assignedGroups = useMemo(() => {
    if (role) {
      // Edit mode: use selectedGroups from hook
      return selectedGroups;
    } else {
      // Create mode: use pending groups directly
      return pendingGroups;
    }
  }, [role, selectedGroups, pendingGroups]);

  // Use mutation directly for create and update to get refetchQueries
  // Using query name as string to refetch all active queries regardless of variables
  const [createRoleMutation, { loading: createLoading }] = useCreateRoleMutation({
    refetchQueries: ['GetRoles'],
    awaitRefetchQueries: true,
  });

  const [updateRoleMutation, { loading: updateLoading }] = useUpdateRoleMutation({
    refetchQueries: ['GetRoles'],
    awaitRefetchQueries: true,
  });

  // Use mutation hook for other operations
  const {
    assignPermissionToRole,
    assignRoleToGroup,
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
        setPendingGroups([]);
      });
    }
  }, [open, methods]);

  const handleSubmit = async (data: RoleFormData) => {
    try {
      if (role) {
        // Edit mode: update role name first using mutation directly
        await updateRoleMutation({
          variables: {
            roleId: role.id,
            input: {
              name: data.name.trim(),
            },
          },
        });
        
        // Save user, group, and permission changes if any
        if (hasUserGroupPermissionChanges) {
          await handleSaveUserGroups();
        }
        
        toast.success(t("roleManagement.toast.roleUpdated", { name: data.name }));
      } else {
        // Create role first using mutation directly
        const result = await createRoleMutation({
          variables: {
            input: {
              name: data.name.trim(),
            },
          },
        });

        const createdRole = result.data?.createRole;
        if (!createdRole) {
          throw new Error("Failed to create role: no data returned");
        }

        const newRole = {
          id: createdRole.id,
          name: createdRole.name,
        };
        
        // Assign all pending selections
        try {
          // Assign permissions
          for (const permissionId of pendingPermissions) {
            await assignPermissionToRole(newRole.id, permissionId);
          }

          // Assign groups
          for (const group of pendingGroups) {
            await assignRoleToGroup(group.id, newRole.id);
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
      setPendingPermissions(permissions.map((p) => p.id));
    }
  }, [isCreateMode]);
  
  // Handle group changes for create mode
  const handleCreateModeGroupChange = useCallback((groups: Group[]) => {
    if (isCreateMode) {
      setPendingGroups(groups);
    }
  }, [isCreateMode]);

  const drawerTitle = isCreateMode ? t("roleManagement.createRole") : t("roleManagement.editRole");

  return (
    <Drawer
      id="role-drawer"
      data-testid="role-drawer"
      open={open}
      onClose={onClose}
      anchor="right"
      size="md"
      variant="temporary"
    >
      <Drawer.Header>
        <Box sx={{ display: "flex", alignItems: "center", gap: 1, flex: 1 }}>
          <ShieldCheckIcon sx={{ color: "text.secondary" }} />
          <Typography variant="h6" component="h2" sx={{ flex: 1 }}>
            {drawerTitle}
          </Typography>
        </Box>
        <IconButton
          onClick={onClose}
          size="small"
          aria-label={`Close ${drawerTitle}`}
        >
          <CloseIcon />
        </IconButton>
      </Drawer.Header>
      <Drawer.Body>
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
                  <RoleGroupAssignment
                    roleId={roleId}
                    assignedGroups={assignedGroups}
                    onAssignGroup={undefined}
                    onRemoveGroup={undefined}
                    assignLoading={false}
                    removeLoading={false}
                    onGroupsChange={undefined}
                    active={open}
                    onChange={isCreateMode ? handleCreateModeGroupChange : updateSelectedGroups}
                  />

                  <RolePermissionAssignment
                    roleId={roleId}
                    assignedPermissions={assignedPermissions}
                    onAssignPermission={undefined}
                    onRemovePermission={undefined}
                    assignLoading={false}
                    removeLoading={false}
                    onPermissionsChange={undefined}
                    active={open}
                    onChange={isCreateMode ? handleCreateModePermissionChange : updateSelectedPermissions}
                  />
                </PermissionGate>

                {/* Timestamps - only show in edit mode, at the bottom in 2 columns */}
                {!isCreateMode && role?.createdAt && role?.updatedAt && (
                  <Box sx={{ display: "flex", gap: 2 }}>
                    <Box sx={{ flex: 1 }}>
                      <Typography variant="body2" color="text.secondary" gutterBottom>
                        {t("roleManagement.drawer.createdAt")}
                      </Typography>
                      <TextField
                        fullWidth
                        value={new Date(role.createdAt).toLocaleString()}
                        readOnly
                        data-testid="role-drawer-created-at"
                      />
                    </Box>
                    <Box sx={{ flex: 1 }}>
                      <Typography variant="body2" color="text.secondary" gutterBottom>
                        {t("roleManagement.drawer.updatedAt")}
                      </Typography>
                      <TextField
                        fullWidth
                        value={new Date(role.updatedAt).toLocaleString()}
                        readOnly
                        data-testid="role-drawer-updated-at"
                      />
                    </Box>
                  </Box>
                )}
              </Stack>
            </Box>
          </FormProvider>
      </Drawer.Body>
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
    </Drawer>
  );
};
