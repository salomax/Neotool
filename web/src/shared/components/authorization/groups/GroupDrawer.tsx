"use client";

import React, { useMemo, useEffect, useState, useCallback } from "react";
import {
  Box,
  Typography,
  Alert,
  Stack,
  Button,
} from "@mui/material";
import { Drawer } from "@/shared/components/ui/layout/Drawer";
import { LoadingState } from "@/shared/components/ui/feedback";
import { useForm, FormProvider } from "react-hook-form";
import { useGetGroupWithRelationshipsQuery } from "@/lib/graphql/operations/authorization-management/queries.generated";
import { useCreateGroupMutation } from "@/lib/graphql/operations/authorization-management/mutations.generated";
import { useTranslation } from "@/shared/i18n";
import { authorizationManagementTranslations } from "@/app/(neotool)/settings/i18n";
import { useGroupManagement, type GroupFormData } from "@/shared/hooks/authorization/useGroupManagement";
import { GroupRoleAssignment } from "./GroupRoleAssignment";
import { GroupUserAssignment } from "./GroupUserAssignment";
import { GroupForm } from "./GroupForm";
import { useToast } from "@/shared/providers";
import { extractErrorMessage } from "@/shared/utils/error";
import { GetGroupsDocument } from "@/lib/graphql/operations/authorization-management/queries.generated";

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
  
  const {
    updateGroup,
    assignRoleToGroup,
    removeRoleFromGroup,
    updateLoading,
    assignRoleLoading,
    removeRoleLoading,
    refetch: refetchGroups,
  } = useGroupManagement();

  // Use mutation directly for create to get the created group ID
  const [createGroupMutation, { loading: createLoading }] = useCreateGroupMutation({
    refetchQueries: [{ query: GetGroupsDocument }],
  });

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
  const [saving, setSaving] = useState(false);

  // Query group with relationships (only in edit mode)
  const { data, loading, error, refetch } = useGetGroupWithRelationshipsQuery({
    skip: !open || !groupId,
    variables: { id: groupId! },
    fetchPolicy: 'network-only',
    notifyOnNetworkStatusChange: true,
  });

  // Get the group directly from the query result
  const group = useMemo(() => {
    if (!data?.group || !groupId) return null;
    return data.group;
  }, [data?.group, groupId]);

  // Extract relationships
  const members = useMemo(() => group?.members || [], [group?.members]);
  const roles = useMemo(() => group?.roles || [], [group?.roles]);

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
    } else if (group) {
      // Initialize form with group data for edit mode
      const userIds = group.members?.map((member: { id: string }) => member.id) || [];
      methods.reset({
        name: group.name || "",
        description: group.description || "",
        userIds,
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
    }
  }, [open, methods]);

  const handleAssignRole = useCallback(
    async (roleId: string) => {
      if (!groupId) return;
      try {
        await assignRoleToGroup(groupId, roleId);
        await refetch();
      } catch (err) {
        const errorMessage = extractErrorMessage(
          err,
          t("groupManagement.roles.assignError")
        );
        toast.error(errorMessage);
        throw err;
      }
    },
    [groupId, assignRoleToGroup, refetch, toast, t]
  );

  const handleRemoveRole = useCallback(
    async (roleId: string) => {
      if (!groupId) return;
      try {
        await removeRoleFromGroup(groupId, roleId);
        await refetch();
      } catch (err) {
        const errorMessage = extractErrorMessage(
          err,
          t("groupManagement.roles.removeError")
        );
        toast.error(errorMessage);
        throw err;
      }
    },
    [groupId, removeRoleFromGroup, refetch, toast, t]
  );

  const handleSave = useCallback(async () => {
    setSaving(true);
    try {
      const formData = methods.getValues();
      
      // Extract user IDs from form data
      const userIds = (formData.userIds || []).map((item) => {
        if (typeof item === "string") {
          return item;
        }
        return (item as any)?.id || item;
      }).filter((id): id is string => typeof id === "string" && id.length > 0);

      const submitData: GroupFormData = {
        name: formData.name.trim(),
        description: formData.description?.trim() || null,
        userIds,
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
            // Log error but don't fail the entire operation
            console.error("Error assigning roles to new group:", roleErr);
            toast.error(
              t("groupManagement.roles.assignError") +
                " " +
                extractErrorMessage(roleErr, "")
            );
          }
        }

        toast.success(t("groupManagement.toast.groupCreated", { name: submitData.name }));
        
        // Refetch groups list
        await refetchGroups();
        
        // Close drawer
        onClose();
      } else {
        // Edit mode: update group
        if (!groupId) return;
        await updateGroup(groupId, submitData);
        toast.success(t("groupManagement.toast.groupUpdated", { name: submitData.name }));
        await refetch();
      }
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
    updateGroup,
    groupId,
    refetch,
    refetchGroups,
    toast,
    t,
    onClose,
    pendingRoles,
    assignRoleToGroup,
  ]);

  const handleCancel = useCallback(() => {
    if (isCreateMode) {
      methods.reset({
        name: "",
        description: "",
        userIds: [],
      });
      setPendingRoles([]);
    } else if (group) {
      const userIds = group.members?.map((member: { id: string }) => member.id) || [];
      methods.reset({
        name: group.name || "",
        description: group.description || "",
        userIds,
      });
    }
    onClose();
  }, [isCreateMode, group, methods, onClose]);

  const handleRolesChange = useCallback(() => {
    if (!isCreateMode && groupId) {
      refetch();
    }
  }, [isCreateMode, groupId, refetch]);

  const drawerTitle = isCreateMode
    ? t("groupManagement.createGroup")
    : t("groupManagement.drawer.title");

  return (
    <Drawer
      open={open}
      onClose={onClose}
      anchor="right"
      size="md"
      variant="temporary"
    >
      <Drawer.Header title={drawerTitle} />
      <Drawer.Body>
        <FormProvider {...methods}>
          {/* Loading state for edit mode */}
          <LoadingState isLoading={!isCreateMode && loading} />

          {/* Error state for edit mode */}
          {!isCreateMode && error && (
            <Alert severity="error" sx={{ mb: 3 }}>
              {t("groupManagement.drawer.errorLoading")}
            </Alert>
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
                <Box>
                  <Typography variant="body2" color="text.secondary" gutterBottom>
                    {t("groupManagement.form.users")}
                  </Typography>
                  <GroupUserAssignment
                    initialUserIds={
                      isCreateMode
                        ? undefined
                        : group?.members?.map((m: { id: string }) => m.id)
                    }
                  />
                </Box>

                {/* Roles Assignment */}
                <Box>
                  <Typography variant="body2" color="text.secondary" gutterBottom>
                    {t("groupManagement.drawer.roles")}
                  </Typography>
                  <GroupRoleAssignment
                    groupId={groupId}
                    assignedRoles={
                      isCreateMode
                        ? pendingRoles
                        : roles.map((r: { id: string; name: string }) => ({
                            id: r.id,
                            name: r.name,
                          }))
                    }
                    onAssignRole={isCreateMode ? undefined : handleAssignRole}
                    onRemoveRole={isCreateMode ? undefined : handleRemoveRole}
                    assignLoading={assignRoleLoading}
                    removeLoading={removeRoleLoading}
                    onRolesChange={handleRolesChange}
                    onPendingRolesChange={setPendingRoles}
                  />
                </Box>
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
            disabled={saving || createLoading || updateLoading}
            color="primary"
          >
            {saving || createLoading || updateLoading
              ? t("common.saving")
              : isCreateMode
              ? t("groupManagement.form.create")
              : t("groupManagement.form.save")}
          </Button>
        </Box>
      </Drawer.Footer>
    </Drawer>
  );
};

