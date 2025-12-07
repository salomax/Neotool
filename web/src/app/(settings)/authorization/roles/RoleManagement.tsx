"use client";

import React, { useState, useCallback } from "react";
import { Button } from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import { useRoleManagement, type Role } from "@/shared/hooks/authorization/useRoleManagement";
import { RoleSearch } from "./RoleSearch";
import { RoleList } from "./RoleList";
import { RoleDrawer } from "./RoleDrawer";
import { useTranslation } from "@/shared/i18n";
import { authorizationManagementTranslations } from "@/app/(settings)/settings/i18n";
import { useToast } from "@/shared/providers";
import { extractErrorMessage } from "@/shared/utils/error";
import { DeleteConfirmationDialog } from "@/shared/components/ui/feedback";
import { ManagementLayout } from "@/shared/components/management/ManagementLayout";
import { Box } from "@/shared/components/ui/layout";
import { PermissionGate } from "@/shared/components/authorization";

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
  const [drawerRoleId, setDrawerRoleId] = useState<string | null>(null);
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);
  const [roleToDelete, setRoleToDelete] = useState<Role | null>(null);

  const {
    roles,
    searchQuery,
    inputValue,
    handleInputChange,
    handleSearch,
    pageInfo,
    paginationRange,
    canLoadPreviousPage,
    loadNextPage,
    loadPreviousPage,
    goToFirstPage,
    loading,
    error,
    refetch,
    deleteRole,
    deleteLoading,
    orderBy,
    handleSort,
    setFirst,
  } = useRoleManagement({
    initialSearchQuery,
    initialFirst: 10, // Default initial value, will be updated by DynamicTableBox
  });

  const handleCreate = useCallback(() => {
    setDrawerRoleId(null);
    setDrawerOpen(true);
  }, []);

  const handleEdit = useCallback((role: Role) => {
    setDrawerRoleId(role.id);
    setDrawerOpen(true);
  }, []);

  const handleCloseDrawer = useCallback(() => {
    setDrawerOpen(false);
    setDrawerRoleId(null);
  }, []);

  const handleTableResize = useCallback(
    (pageSize: number) => {
      if (pageSize > 0) {
        setFirst(pageSize);
      }
    },
    [setFirst]
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
    <ManagementLayout
      error={error}
      onErrorRetry={refetch}
      errorFallbackMessage={t("errors.loadFailed")}
    >
      <ManagementLayout.Header>
        <Box sx={{ display: "flex", gap: 2, alignItems: "flex-end" }}>
          <Box sx={{ flexGrow: 1 }} maxWidth="sm">
            <RoleSearch
              value={inputValue}
              onChange={handleInputChange}
              onSearch={handleSearch}
              placeholder={t("roleManagement.searchPlaceholder")}
            />
          </Box>
          <Box sx={{ mb: 2 }}>
            <PermissionGate require="security:role:save">
              <Button
                variant="contained"
                startIcon={<AddIcon />}
                onClick={handleCreate}
                data-testid="create-role-button"
              >
                {t("roleManagement.newButton")}
              </Button>
            </PermissionGate>
          </Box>
        </Box>
      </ManagementLayout.Header>
      <ManagementLayout.Content>
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
          pageInfo={pageInfo}
          paginationRange={paginationRange}
          onLoadNext={loadNextPage}
          onLoadPrevious={loadPreviousPage}
          onGoToFirst={goToFirstPage}
          canLoadPreviousPage={canLoadPreviousPage}
          onTableResize={handleTableResize}
          recalculationKey={`${roles.length}-${loading ? "loading" : "ready"}`}
          orderBy={orderBy}
          onSortChange={handleSort}
        />
        <DeleteConfirmationDialog
          open={deleteConfirmOpen}
          item={roleToDelete}
          loading={deleteLoading}
          onConfirm={handleDeleteConfirm}
          onCancel={handleDeleteCancel}
          titleKey="roleManagement.deleteDialog.title"
          messageKey="roleManagement.deleteDialog.message"
          cancelKey="roleManagement.deleteDialog.cancel"
          deleteKey="roleManagement.deleteDialog.delete"
          deletingKey="roleManagement.deleteDialog.deleting"
          t={t}
        />
      </ManagementLayout.Content>
      <ManagementLayout.Drawer>
        <RoleDrawer
          open={drawerOpen}
          onClose={handleCloseDrawer}
          roleId={drawerRoleId}
        />
      </ManagementLayout.Drawer>
    </ManagementLayout>
  );
};
