"use client";

import React, { useMemo, useEffect, useState, useCallback, useRef } from "react";
import {
  Box,
  Typography,
  Stack,
  Button,
  Alert,
  IconButton,
} from "@mui/material";
import GroupIcon from "@mui/icons-material/Group";
import { Drawer } from "@/shared/components/ui/layout/Drawer";
import { TextField } from "@/shared/components/ui/primitives";
import { CloseIcon } from "@/shared/ui/mui-imports";
import { LoadingState, ErrorAlert } from "@/shared/components/ui/feedback";
import { useForm, FormProvider } from "react-hook-form";
import { useGetGroupWithRelationshipsQuery, GetGroupWithRelationshipsDocument } from "@/lib/graphql/operations/authorization-management/queries.generated";
import { 
  useCreateGroupMutation,
  useUpdateGroupMutation,
} from "@/lib/graphql/operations/authorization-management/mutations.generated";
import { useTranslation } from "@/shared/i18n";
import { authorizationManagementTranslations } from "@/app/(settings)/settings/i18n";
import { useGroupMutations } from "@/shared/hooks/authorization/useGroupMutations";
import { useGroupManagement, type GroupFormData } from "@/shared/hooks/authorization/useGroupManagement";
import { useGroupDrawer } from "@/shared/hooks/authorization/useGroupDrawer";
import { GroupRoleAssignment } from "./GroupRoleAssignment";
import { GroupUserAssignment, type User as GroupUser } from "./GroupUserAssignment";
import { GroupForm } from "./GroupForm";
import { useToast } from "@/shared/providers";
import { extractErrorMessage } from "@/shared/utils/error";
import { GetGroupsDocument } from "@/lib/graphql/operations/authorization-management/queries.generated";
import { PermissionGate } from "@/shared/components/authorization";
import { useKeyboardFormSubmit, useDrawerAutoFocus } from "@/shared/hooks/forms";

export interface GroupDrawerProps {
  open: boolean;
  onClose: () => void;
  groupId: string | null;
}

/**
 * GroupDrawer component for creating and editing group details including members and roles
 * Supports both create mode (groupId === null) and edit mode (groupId !== null)
 */
export const GroupDrawer: React.FC<GroupDrawerProps> = ({
  open,
  onClose,
  groupId,
}) => {
  const { t } = useTranslation(authorizationManagementTranslations);
  const toast = useToast();
  const isCreateMode = groupId === null;
  
  // Use group drawer hook for edit mode (handles roles and users state and mutations)
  const {
    group: groupFromHook,
    loading: drawerLoading,
    error: drawerError,
    selectedRoles,
    selectedUsers,
    hasChanges: hasRoleOrUserChanges,
    saving: savingRoles,
    updateSelectedRoles,
    updateSelectedUsers,
    handleSave: handleSaveRolesAndUsers,
    resetChanges: resetRoleChanges,
    refetch: refetchGroup,
  } = useGroupDrawer(groupId, open && !isCreateMode);
  
  // Use mutation hook directly - drawer doesn't need the query
  const {
    updateGroup,
    assignRoleToGroup,
    updateLoading,
  } = useGroupMutations({
    // No refetch needed - drawer manages its own queries
  });

  // Use mutation directly for create to get the created group ID
  const [createGroupMutation, { loading: createLoading }] = useCreateGroupMutation({
    refetchQueries: ['GetGroups'],
    awaitRefetchQueries: true,
  });

  // Direct mutation for updating group name/description only (without userIds)
  const [updateGroupMutation] = useUpdateGroupMutation();

  // Form setup with react-hook-form
  const methods = useForm<GroupFormData>({
    defaultValues: {
      name: "",
      description: "",
      userIds: [],
    },
  });

  // Track selected roles for create mode (before group is created)
  const [pendingRoles, setPendingRoles] = useState<Array<{ id: string; name: string }>>([]);
  // Track selected users for create mode (before group is created)
  const [pendingUsers, setPendingUsers] = useState<GroupUser[]>([]);
  const [saving, setSaving] = useState(false);

  // Use group from hook in edit mode, or query directly in create mode
  const group = isCreateMode ? null : groupFromHook;
  const loading = isCreateMode ? false : drawerLoading;
  const error = isCreateMode ? undefined : drawerError;

  // Initialize form when group data loads (edit mode) or drawer opens (create mode)
  useEffect(() => {
    if (isCreateMode) {
      // Reset form for create mode
      methods.reset({
        name: "",
        description: "",
        userIds: [],
      });
      setPendingRoles([]);
      setPendingUsers([]);
    } else if (group) {
      // Initialize form with group data for edit mode
      methods.reset({
        name: group.name || "",
        description: group.description || "",
        userIds: [], // Users are now managed by hook, not form
      });
      setPendingRoles([]);
    }
  }, [group, isCreateMode, methods]);

  // Reset form when drawer closes
  useEffect(() => {
    if (!open) {
      methods.reset({
        name: "",
        description: "",
        userIds: [],
      });
      setPendingRoles([]);
      setPendingUsers([]);
    }
  }, [open, methods]);


  const handleSave = useCallback(async () => {
    setSaving(true);
    try {
      const formData = methods.getValues();

      const submitData: GroupFormData = {
        name: formData.name.trim(),
        description: formData.description?.trim() || null,
        // In create mode, users are selected via pendingUsers state.
        // In edit mode, users are managed by the useGroupDrawer hook and
        // are not updated through this mutation.
        userIds: isCreateMode ? pendingUsers.map((user) => user.id) : [],
      };

      if (isCreateMode) {
        // Create mode: create group first, then assign roles
        const input = {
          name: submitData.name,
          description: submitData.description,
          userIds: submitData.userIds ?? [],
        };

        const result = await createGroupMutation({
          variables: { input },
        });

        const createdGroup = result.data?.createGroup;
        if (!createdGroup) {
          throw new Error("Failed to create group: no data returned");
        }

        const newGroupId = createdGroup.id;

        // Assign pending roles to the newly created group
        if (pendingRoles.length > 0 && newGroupId) {
          try {
            for (const role of pendingRoles) {
              await assignRoleToGroup(newGroupId, role.id);
            }
          } catch (roleErr) {
            // Don't fail the entire operation
            toast.error(
              t("groupManagement.roles.assignError") +
                " " +
                extractErrorMessage(roleErr, "")
            );
          }
        }

        toast.success(t("groupManagement.toast.groupCreated", { name: submitData.name }));
        
        // Note: No need to refetch groups list - mutations update cache and parent will refetch when needed
      } else {
        // Edit mode: update group (name, description) and handle users/roles via hook
        if (!groupId) return;
        
        // Check if name or description changed
        const nameChanged = submitData.name.trim() !== (group?.name || "").trim();
        const descriptionChanged = submitData.description?.trim() !== (group?.description || "").trim();
        
        // Only update group if name or description changed
        if (nameChanged || descriptionChanged) {
          await updateGroupMutation({
            variables: {
              groupId,
              input: {
                name: submitData.name.trim(),
                description: submitData.description?.trim() || null,
                // Don't include userIds - undefined means don't change memberships
              },
            },
            refetchQueries: [GetGroupWithRelationshipsDocument, 'GetGroups'],
            awaitRefetchQueries: true,
          });
        }
        
        // Save role and user changes (handled by hook, like roles)
        if (hasRoleOrUserChanges) {
          await handleSaveRolesAndUsers();
        }
        
        toast.success(t("groupManagement.toast.groupUpdated", { name: submitData.name }));
      }
      
      // After successful save in either create or edit mode, close the drawer
      onClose();
    } catch (err) {
      const errorMessage = extractErrorMessage(
        err,
        isCreateMode
          ? t("groupManagement.toast.groupCreateError")
          : t("groupManagement.toast.groupUpdateError")
      );
      toast.error(errorMessage);
    } finally {
      setSaving(false);
    }
  }, [
    isCreateMode,
    methods,
    createGroupMutation,
    updateGroupMutation,
    groupId,
    toast,
    t,
    onClose,
    pendingRoles,
    assignRoleToGroup,
    hasRoleOrUserChanges,
    handleSaveRolesAndUsers,
    group,
  ]);

  const handleCancel = useCallback(() => {
    if (isCreateMode) {
      methods.reset({
        name: "",
        description: "",
        userIds: [],
      });
      setPendingRoles([]);
      setPendingUsers([]);
    } else {
      // Reset form
      if (group) {
        const userIds: string[] = [];
        methods.reset({
          name: group.name || "",
          description: group.description || "",
          userIds,
        });
      }
      // Reset role changes
      resetRoleChanges();
    }
    onClose();
  }, [isCreateMode, group, methods, onClose, resetRoleChanges]);

  const drawerTitle = isCreateMode
    ? t("groupManagement.createGroup")
    : t("groupManagement.drawer.title");

  // Ref for drawer body to scope keyboard handling
  const bodyRef = useRef<HTMLDivElement>(null);

  // Auto-focus first input when drawer opens
  useDrawerAutoFocus({
    containerRef: bodyRef,
    open: open,
    enabled: true,
  });

  // Enable keyboard form submission
  // Uses react-hook-form's handleSubmit since this drawer uses FormProvider without a native form element
  useKeyboardFormSubmit({
    onSubmit: () => methods.handleSubmit(handleSave)(),
    isSubmitEnabled: () =>
      !saving &&
      !createLoading &&
      !updateLoading &&
      !savingRoles &&
      (isCreateMode || hasRoleOrUserChanges || methods.formState.isDirty),
    containerRef: bodyRef,
    enabled: open,
  });

  return (
    <Drawer
      id="group-drawer"
      data-testid="group-drawer"
      open={open}
      onClose={onClose}
      anchor="right"
      size="md"
      variant="temporary"
    >
      <Drawer.Header>
        <Box sx={{ display: "flex", alignItems: "center", gap: 1, flex: 1 }}>
          <GroupIcon sx={{ color: "text.secondary" }} />
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
      <Drawer.Body ref={bodyRef}>
        <FormProvider {...methods}>
          {/* Loading state for edit mode */}
          <LoadingState isLoading={!isCreateMode && loading} />

          {/* Error state for edit mode */}
          {!isCreateMode && (
            <ErrorAlert
              error={error || undefined}
              onRetry={() => {
                // Refetch is handled by useGroupDrawer hook
              }}
              fallbackMessage={t("groupManagement.drawer.errorLoading")}
            />
          )}

          {/* Form content - shown in both create and edit modes */}
          {(!loading || isCreateMode) && !error && (
            <Stack spacing={4}>
              {/* Form Section */}
              <Stack spacing={3}>
                {/* Name and Description */}
                <GroupForm
                  initialValues={
                    group
                      ? { name: group.name, description: group.description }
                      : undefined
                  }
                />

                {/* Users Assignment */}
                <PermissionGate require="security:group:save">
                  <Box>
                    <Typography variant="body2" color="text.secondary" gutterBottom>
                      {t("groupManagement.form.users")}
                    </Typography>
                    <GroupUserAssignment
                      assignedUsers={
                        isCreateMode
                          ? pendingUsers
                          : selectedUsers
                      }
                      onChange={isCreateMode ? setPendingUsers : updateSelectedUsers}
                    />
                  </Box>
                </PermissionGate>

                {/* Roles Assignment */}
                <PermissionGate require="security:group:save">
                  <Box>
                    <Typography variant="body2" color="text.secondary" gutterBottom>
                      {t("groupManagement.drawer.roles")}
                    </Typography>
                    <GroupRoleAssignment
                      groupId={groupId}
                      assignedRoles={
                        isCreateMode
                          ? pendingRoles
                          : selectedRoles
                      }
                      onAssignRole={undefined}
                      onRemoveRole={undefined}
                      assignLoading={false}
                      removeLoading={false}
                      onRolesChange={undefined}
                      onPendingRolesChange={isCreateMode ? setPendingRoles : undefined}
                      onChange={isCreateMode ? undefined : updateSelectedRoles}
                    />
                  </Box>
                </PermissionGate>

                {/* Timestamps - only show in edit mode, at the bottom in 2 columns */}
                {!isCreateMode && group?.createdAt && group?.updatedAt && (
                  <Box sx={{ display: "flex", gap: 2 }}>
                    <Box sx={{ flex: 1 }}>
                      <Typography variant="body2" color="text.secondary" gutterBottom>
                        {t("groupManagement.drawer.createdAt")}
                      </Typography>
                      <TextField
                        fullWidth
                        value={new Date(group.createdAt).toLocaleString()}
                        readOnly
                        data-testid="group-drawer-created-at"
                      />
                    </Box>
                    <Box sx={{ flex: 1 }}>
                      <Typography variant="body2" color="text.secondary" gutterBottom>
                        {t("groupManagement.drawer.updatedAt")}
                      </Typography>
                      <TextField
                        fullWidth
                        value={new Date(group.updatedAt).toLocaleString()}
                        readOnly
                        data-testid="group-drawer-updated-at"
                      />
                    </Box>
                  </Box>
                )}
              </Stack>
            </Stack>
          )}

          {/* Group not found (edit mode only) */}
          {!isCreateMode && !loading && !error && !group && groupId && (
            <Alert severity="warning">
              {t("groupManagement.drawer.groupNotFound")}
            </Alert>
          )}
        </FormProvider>
      </Drawer.Body>
      <Drawer.Footer>
        <PermissionGate require="security:group:save">
          <Box sx={{ display: "flex", justifyContent: "flex-end", gap: 2 }}>
            <Button
              variant="outlined"
              onClick={handleCancel}
              disabled={saving || createLoading || updateLoading}
            >
              {t("common.cancel")}
            </Button>
            <Button
              variant="contained"
              onClick={methods.handleSubmit(handleSave)}
              disabled={saving || createLoading || updateLoading || savingRoles || (!isCreateMode && !hasRoleOrUserChanges && !methods.formState.isDirty)}
              color="primary"
            >
              {saving || createLoading || updateLoading || savingRoles
                ? t("common.saving")
                : isCreateMode
                ? t("groupManagement.form.create")
                : t("groupManagement.form.save")}
            </Button>
          </Box>
        </PermissionGate>
      </Drawer.Footer>
    </Drawer>
  );
};

