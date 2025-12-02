"use client";

import React, { useState, useCallback } from "react";
import {
  Box,
  Button,
  Alert,
  Stack,
} from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import { useRoleManagement, type Role } from "@/shared/hooks/authorization/useRoleManagement";
import { SearchField } from "@/shared/components/ui/forms/SearchField";
import { RoleList } from "./RoleList";
import { RoleDrawer } from "./RoleDrawer";
import { useTranslation } from "@/shared/i18n";
import { authorizationManagementTranslations } from "@/app/(neotool)/settings/i18n";

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
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editingRole, setEditingRole] = useState<Role | null>(null);

  const {
    roles,
    searchQuery,
    setSearchQuery,
    pageInfo,
    loadNextPage,
    loadPreviousPage,
    goToFirstPage,
    loading,
    error,
    refetch,
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
        emptyMessage={
          searchQuery
            ? t("roleManagement.emptySearchResults")
            : t("roleManagement.emptyList")
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
      <RoleDrawer
        open={drawerOpen}
        onClose={handleCloseDrawer}
        role={editingRole}
      />
    </Box>
  );
};

