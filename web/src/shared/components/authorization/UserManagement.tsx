"use client";

import React, { useState, useCallback } from "react";
import {
  Box,
  Alert,
} from "@mui/material";
import { useUserManagement, type User } from "@/shared/hooks/authorization/useUserManagement";
import { UserSearch } from "./UserSearch";
import { UserList } from "./UserList";
import { UserDrawer } from "./UserDrawer";
import { useTranslation } from "@/shared/i18n";
import { authorizationManagementTranslations } from "@/app/(neotool)/settings/i18n";
import { useToast } from "@/shared/providers";
import { extractErrorMessage } from "@/shared/utils/error";

export interface UserManagementProps {
  initialSearchQuery?: string;
}

/**
 * UserManagement component - Main orchestrator for user management
 * Displays list of users with search, pagination, and edit functionality
 */
export const UserManagement: React.FC<UserManagementProps> = ({
  initialSearchQuery = "",
}) => {
  const { t } = useTranslation(authorizationManagementTranslations);
  const toast = useToast();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editingUser, setEditingUser] = useState<User | null>(null);

  const {
    users,
    searchQuery,
    setSearchQuery,
    pageInfo,
    paginationRange,
    loadNextPage,
    loadPreviousPage,
    goToFirstPage,
    enableUser,
    disableUser,
    loading,
    enableLoading,
    disableLoading,
    error,
    refetch,
  } = useUserManagement({
    initialSearchQuery,
  });

  const handleEdit = useCallback((user: User) => {
    setEditingUser(user);
    setDrawerOpen(true);
  }, []);

  const handleCloseDrawer = useCallback(() => {
    setDrawerOpen(false);
    setEditingUser(null);
  }, []);

  const handleToggleStatus = useCallback(
    async (userId: string, enabled: boolean) => {
      try {
        if (enabled) {
          await enableUser(userId);
          toast.success(t("userManagement.toast.userEnabled"));
        } else {
          await disableUser(userId);
          toast.success(t("userManagement.toast.userDisabled"));
        }
      } catch (err) {
        console.error("Error toggling user status:", err);
        const errorMessage = extractErrorMessage(
          err,
          enabled
            ? t("userManagement.toast.userEnableError")
            : t("userManagement.toast.userDisableError")
        );
        toast.error(errorMessage);
      }
    },
    [enableUser, disableUser, toast, t]
  );

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

      {/* Search */}
      <UserSearch
        value={searchQuery}
        onChange={handleSearchChange}
        placeholder={t("userManagement.searchPlaceholder")}
      />

      {/* User List */}
      <UserList
        users={users}
        loading={loading}
        onEdit={handleEdit}
        onToggleStatus={handleToggleStatus}
        toggleLoading={enableLoading || disableLoading}
        emptyMessage={
          searchQuery
            ? t("userManagement.emptySearchResults")
            : t("userManagement.emptyList")
        }
        pageInfo={pageInfo}
        paginationRange={paginationRange}
        onLoadNext={loadNextPage}
        onLoadPrevious={loadPreviousPage}
        onGoToFirst={goToFirstPage}
      />

      {/* User Drawer */}
      <UserDrawer
        open={drawerOpen}
        onClose={handleCloseDrawer}
        userId={editingUser?.id || null}
      />
    </Box>
  );
};

