"use client";

import React, { useState, useCallback } from "react";
import {
  Box,
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
import { useRoleManagement, type Role } from "@/shared/hooks/authorization/useRoleManagement";
import { SearchField } from "@/shared/components/ui/forms/SearchField";
import { RoleList } from "./RoleList";
import { RoleDrawer } from "./RoleDrawer";
import { PaginationRange } from "@/shared/components/ui/pagination";
import { useTranslation } from "@/shared/i18n";
import { authorizationManagementTranslations } from "@/app/(neotool)/settings/i18n";
import { useToast } from "@/shared/providers";
import { extractErrorMessage } from "@/shared/utils/error";

export interface RoleManagementProps {
  initialSearchQuery?: string;
}

/**
 * RoleManagement component - Main orchestrator for role management
 * Displays list of roles with search, pagination, and edit functionality
 */
export const RoleManagement: React.FC<RoleManagementProps> = ({
  initialSearchQuery = "",
}) => {
  const { t } = useTranslation(authorizationManagementTranslations);
  const toast = useToast();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editingRole, setEditingRole] = useState<Role | null>(null);
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);
  const [roleToDelete, setRoleToDelete] = useState<Role | null>(null);

  const {
    roles,
    searchQuery,
    setSearchQuery,
    pageInfo,
    paginationRange,
    loadNextPage,
    loadPreviousPage,
    goToFirstPage,
    loading,
    error,
    refetch,
    deleteRole,
    deleteLoading,
  } = useRoleManagement({
    initialSearchQuery,
  });

  const handleCreate = useCallback(() => {
    setEditingRole(null);
    setDrawerOpen(true);
  }, []);

  const handleEdit = useCallback((role: Role) => {
    setEditingRole(role);
    setDrawerOpen(true);
  }, []);

  const handleCloseDrawer = useCallback(() => {
    setDrawerOpen(false);
    setEditingRole(null);
  }, []);

  const handleSearchChange = useCallback(
    (value: string) => {
      setSearchQuery(value);
      // Reset to first page when search changes
      goToFirstPage();
    },
    [setSearchQuery, goToFirstPage]
  );

  const handleDeleteClick = useCallback((role: Role) => {
    setRoleToDelete(role);
    setDeleteConfirmOpen(true);
  }, []);

  const handleDeleteConfirm = useCallback(async () => {
    if (roleToDelete) {
      try {
        await deleteRole(roleToDelete.id);
        toast.success(t("roleManagement.toast.roleDeleted", { name: roleToDelete.name }));
        setDeleteConfirmOpen(false);
        setRoleToDelete(null);
      } catch (err) {
        console.error("Error deleting role:", err);
        const errorMessage = extractErrorMessage(
          err,
          t("roleManagement.toast.roleDeleteError")
        );
        toast.error(errorMessage);
      }
    }
  }, [roleToDelete, deleteRole, toast, t]);

  const handleDeleteCancel = useCallback(() => {
    setDeleteConfirmOpen(false);
    setRoleToDelete(null);
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
            placeholder={t("roleManagement.searchPlaceholder")}
            fullWidth
            debounceMs={300}
            name="role-search"
            data-testid="role-search"
          />
        </Box>
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          onClick={handleCreate}
          data-testid="create-role-button"
        >
          {t("roleManagement.newButton")}
        </Button>
      </Box>

      {/* Role List */}
      <RoleList
        roles={roles}
        loading={loading}
        onEdit={handleEdit}
        onDelete={handleDeleteClick}
        emptyMessage={
          searchQuery
            ? t("roleManagement.emptySearchResults")
            : t("roleManagement.emptyList")
        }
      />

      {/* Pagination Controls */}
      {(pageInfo && (pageInfo.hasNextPage || pageInfo.hasPreviousPage)) || (paginationRange.start > 0 && paginationRange.end > 0) ? (
        <Box sx={{ display: "flex", justifyContent: "center", mt: 3 }}>
          <Stack direction="row" spacing={2} alignItems="center">
            {pageInfo && (pageInfo.hasNextPage || pageInfo.hasPreviousPage) && (
              <>
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
              </>
            )}
            {paginationRange.start > 0 && paginationRange.end > 0 && (
              <PaginationRange
                start={paginationRange.start}
                end={paginationRange.end}
                total={paginationRange.total}
              />
            )}
          </Stack>
        </Box>
      ) : null}

      {/* Create/Edit Drawer */}
      <RoleDrawer
        open={drawerOpen}
        onClose={handleCloseDrawer}
        role={editingRole}
      />

      {/* Delete Confirmation Dialog */}
      <Dialog
        open={deleteConfirmOpen}
        onClose={handleDeleteCancel}
        aria-labelledby="delete-dialog-title"
        aria-describedby="delete-dialog-description"
      >
        <DialogTitle id="delete-dialog-title">
          {t("roleManagement.deleteDialog.title")}
        </DialogTitle>
        <DialogContent>
          <DialogContentText id="delete-dialog-description">
            {roleToDelete
              ? t("roleManagement.deleteDialog.message").replace("{name}", roleToDelete.name)
              : t("roleManagement.deleteDialog.message")}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button
            onClick={handleDeleteCancel}
            disabled={deleteLoading}
            data-testid="delete-dialog-cancel"
          >
            {t("roleManagement.deleteDialog.cancel")}
          </Button>
          <Button
            onClick={handleDeleteConfirm}
            color="error"
            variant="contained"
            disabled={deleteLoading}
            data-testid="delete-dialog-confirm"
          >
            {deleteLoading
              ? t("roleManagement.deleteDialog.deleting")
              : t("roleManagement.deleteDialog.delete")}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

