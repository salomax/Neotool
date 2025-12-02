"use client";

import React, { useState, useCallback } from "react";
import {
  Box,
  Typography,
  Button,
  Alert,
  Stack,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
} from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import { useForm, FormProvider } from "react-hook-form";
import { Drawer } from "@/shared/components/ui/layout/Drawer";
import { useGroupManagement, type Group } from "@/shared/hooks/authorization/useGroupManagement";
import { SearchField } from "@/shared/components/ui/forms/SearchField";
import { GroupList } from "./GroupList";
import { GroupForm, type GroupFormData } from "./GroupForm";
import { GroupUserAssignment } from "./GroupUserAssignment";
import { useTranslation } from "@/shared/i18n";
import { authorizationManagementTranslations } from "@/app/(neotool)/settings/i18n";
import { useToast } from "@/shared/providers";
import { extractErrorMessage } from "@/shared/utils/error";

export interface GroupManagementProps {
  initialSearchQuery?: string;
}

/**
 * GroupManagement component - Main orchestrator for group management
 * Displays list of groups with search, pagination, and edit functionality
 */
export const GroupManagement: React.FC<GroupManagementProps> = ({
  initialSearchQuery = "",
}) => {
  const { t } = useTranslation(authorizationManagementTranslations);
  const toast = useToast();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editingGroup, setEditingGroup] = useState<Group | null>(null);
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);
  const [groupToDelete, setGroupToDelete] = useState<Group | null>(null);

  const {
    groups,
    searchQuery,
    setSearchQuery,
    pageInfo,
    loadNextPage,
    loadPreviousPage,
    goToFirstPage,
    loading,
    error,
    refetch,
    createGroup,
    updateGroup,
    deleteGroup,
    createLoading,
    updateLoading,
    deleteLoading,
  } = useGroupManagement({
    initialSearchQuery,
  });

  // Form setup
  const methods = useForm<GroupFormData>({
    defaultValues: {
      name: "",
      description: "",
      userIds: [],
    },
  });

  const handleCreate = useCallback(() => {
    setEditingGroup(null);
    setDrawerOpen(true);
  }, []);

  const handleEdit = useCallback((group: Group) => {
    setEditingGroup(group);
    setDrawerOpen(true);
  }, []);

  const handleCloseDrawer = useCallback(() => {
    setDrawerOpen(false);
    setEditingGroup(null);
    methods.reset();
  }, [methods]);

  // Update form when editingGroup changes
  React.useEffect(() => {
    if (editingGroup) {
      // Extract user IDs from members if available
      const userIds = editingGroup.members?.map((member) => member.id) || [];
      methods.reset({
        name: editingGroup.name,
        description: editingGroup.description || "",
        userIds,
      });
    } else {
      methods.reset({
        name: "",
        description: "",
        userIds: [],
      });
    }
  }, [editingGroup, methods]);

  const handleSubmit = useCallback(
    async (data: GroupFormData) => {
      try {
        // Extract user IDs from objects if needed (AutocompleteField returns full objects)
        const userIds = (data.userIds || []).map((item) => {
          if (typeof item === "string") {
            return item;
          }
          // If it's an object, extract the id property
          return (item as any)?.id || item;
        }).filter((id): id is string => typeof id === "string" && id.length > 0);

        const formData: GroupFormData = {
          ...data,
          userIds,
        };

        if (editingGroup) {
          await updateGroup(editingGroup.id, formData);
          toast.success(t("groupManagement.toast.groupUpdated", { name: data.name }));
        } else {
          await createGroup(formData);
          toast.success(t("groupManagement.toast.groupCreated", { name: data.name }));
        }
        handleCloseDrawer();
      } catch (err) {
        console.error("Error submitting group form:", err);
        const errorMessage = extractErrorMessage(
          err,
          editingGroup
            ? t("groupManagement.toast.groupUpdateError")
            : t("groupManagement.toast.groupCreateError")
        );
        toast.error(errorMessage);
      }
    },
    [editingGroup, createGroup, updateGroup, handleCloseDrawer, toast, t]
  );

  const handleSearchChange = useCallback(
    (value: string) => {
      setSearchQuery(value);
      // Reset to first page when search changes
      goToFirstPage();
    },
    [setSearchQuery, goToFirstPage]
  );

  const handleDeleteClick = useCallback((group: Group) => {
    setGroupToDelete(group);
    setDeleteConfirmOpen(true);
  }, []);

  const handleDeleteConfirm = useCallback(async () => {
    if (groupToDelete) {
      try {
        await deleteGroup(groupToDelete.id);
        toast.success(t("groupManagement.toast.groupDeleted", { name: groupToDelete.name }));
        setDeleteConfirmOpen(false);
        setGroupToDelete(null);
      } catch (err) {
        console.error("Error deleting group:", err);
        const errorMessage = extractErrorMessage(
          err,
          t("groupManagement.toast.groupDeleteError")
        );
        toast.error(errorMessage);
      }
    }
  }, [groupToDelete, deleteGroup, toast, t]);

  const handleDeleteCancel = useCallback(() => {
    setDeleteConfirmOpen(false);
    setGroupToDelete(null);
  }, []);

  return (
    <Box>
      {/* Error Alert */}
      {error && (
        <Alert severity="error" sx={{ mb: 3 }} onClose={() => refetch()}>
          {error.message || t("errors.loadFailed")}
        </Alert>
      )}

      {/* Search and New Button */}
      <Box sx={{ mb: 2, display: "flex", gap: 2, alignItems: "center" }}>
        <Box sx={{ flexGrow: 1 }}>
          <SearchField
            value={searchQuery}
            onChange={handleSearchChange}
            placeholder={t("groupManagement.searchPlaceholder")}
            fullWidth
            debounceMs={300}
            name="group-search"
            data-testid="group-search"
          />
        </Box>
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          onClick={handleCreate}
          data-testid="create-group-button"
        >
          {t("groupManagement.newButton")}
        </Button>
      </Box>

      {/* Group List */}
      <GroupList
        groups={groups}
        loading={loading}
        onEdit={handleEdit}
        onDelete={handleDeleteClick}
        emptyMessage={
          searchQuery
            ? t("groupManagement.emptySearchResults")
            : t("groupManagement.emptyList")
        }
      />

      {/* Pagination Controls */}
      {pageInfo && (pageInfo.hasNextPage || pageInfo.hasPreviousPage) && (
        <Box sx={{ display: "flex", justifyContent: "center", mt: 3 }}>
          <Stack direction="row" spacing={2} alignItems="center">
            <Button
              variant="outlined"
              onClick={goToFirstPage}
              disabled={!pageInfo.hasPreviousPage || loading}
              size="small"
            >
              {t("pagination.first")}
            </Button>
            <Button
              variant="outlined"
              onClick={loadPreviousPage}
              disabled={!pageInfo.hasPreviousPage || loading}
              size="small"
            >
              {t("pagination.previous")}
            </Button>
            <Button
              variant="outlined"
              onClick={loadNextPage}
              disabled={!pageInfo.hasNextPage || loading}
              size="small"
            >
              {t("pagination.next")}
            </Button>
          </Stack>
        </Box>
      )}

      {/* Create/Edit Drawer */}
      <Drawer
        open={drawerOpen}
        onClose={handleCloseDrawer}
        title={editingGroup ? t("groupManagement.editGroup") : t("groupManagement.createGroup")}
        anchor="right"
        width={600}
        variant="temporary"
      >
        <FormProvider {...methods}>
          <Box
            component="form"
            onSubmit={methods.handleSubmit(handleSubmit)}
            sx={{ p: 3 }}
            noValidate
          >
            <Stack spacing={3}>
              <GroupForm initialValues={editingGroup ? { name: editingGroup.name, description: editingGroup.description } : undefined} />
              <GroupUserAssignment initialUserIds={editingGroup?.members?.map((m) => m.id) || undefined} />
              
              <Stack direction="row" spacing={2} justifyContent="flex-end" sx={{ mt: 3 }}>
                <Button
                  variant="outlined"
                  onClick={handleCloseDrawer}
                  disabled={createLoading || updateLoading}
                >
                  {t("groupManagement.form.cancel")}
                </Button>
                <Button
                  type="submit"
                  variant="contained"
                  disabled={createLoading || updateLoading}
                  data-testid="group-form-submit"
                >
                  {createLoading || updateLoading
                    ? t("groupManagement.form.saving")
                    : editingGroup
                    ? t("groupManagement.form.save")
                    : t("groupManagement.form.create")}
                </Button>
              </Stack>
            </Stack>
          </Box>
        </FormProvider>
      </Drawer>

      {/* Delete Confirmation Dialog */}
      <Dialog
        open={deleteConfirmOpen}
        onClose={handleDeleteCancel}
        aria-labelledby="delete-dialog-title"
        aria-describedby="delete-dialog-description"
      >
        <DialogTitle id="delete-dialog-title">
          {t("groupManagement.deleteDialog.title")}
        </DialogTitle>
        <DialogContent>
          <DialogContentText id="delete-dialog-description">
            {groupToDelete
              ? t("groupManagement.deleteDialog.message").replace("{name}", groupToDelete.name)
              : t("groupManagement.deleteDialog.message")}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button
            onClick={handleDeleteCancel}
            disabled={deleteLoading}
            data-testid="delete-dialog-cancel"
          >
            {t("groupManagement.deleteDialog.cancel")}
          </Button>
          <Button
            onClick={handleDeleteConfirm}
            color="error"
            variant="contained"
            disabled={deleteLoading}
            data-testid="delete-dialog-confirm"
          >
            {deleteLoading
              ? t("groupManagement.deleteDialog.deleting")
              : t("groupManagement.deleteDialog.delete")}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

