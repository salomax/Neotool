"use client";

import React, { useState, useCallback, useEffect } from "react";
import { Box } from "@/shared/components/ui/layout";
import { Alert } from "@mui/material";
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
    canLoadPreviousPage,
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
    setFirst,
    orderBy,
    handleSort,
  } = useUserManagement({
    initialSearchQuery,
    initialFirst: 10, // Default initial value, will be updated by DynamicTableBox
  });

  const handleTableResize = useCallback(
    (pageSize: number) => {
      if (pageSize > 0) {
        setFirst(pageSize);
      }
    },
    [setFirst]
  );

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

  // Local state for input value (immediate updates)
  const [inputValue, setInputValue] = useState(searchQuery);

  // Sync input value when searchQuery changes externally
  useEffect(() => {
    setInputValue(searchQuery);
  }, [searchQuery]);

  // Immediate input update (for display)
  const handleInputChange = useCallback(
    (value: string) => {
      setInputValue(value);
    },
    []
  );

  // Debounced search update (triggers actual search)
  const handleSearch = useCallback(
    (value: string) => {
      setSearchQuery(value);
      // Reset to first page when search changes
      goToFirstPage();
    },
    [setSearchQuery, goToFirstPage]
  );

  return (
    <Box fullHeight>
      {/* Error Alert */}
      {error && (
        <Alert severity="error" sx={{ mb: 3 }} onClose={() => refetch()}>
          {error.message || t("errors.loadFailed")}
        </Alert>
      )}

      {/* Search */}
      <UserSearch
        value={inputValue}
        onChange={handleInputChange}
        onSearch={handleSearch}
        placeholder={t("userManagement.searchPlaceholder")}
        maxWidth="sm"
      />

      {/* User List */}
      <Box sx={{ flex: 1, minHeight: 0 }}>
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
          canLoadPreviousPage={canLoadPreviousPage}
          onTableResize={handleTableResize}
          recalculationKey={`${users.length}-${loading ? "loading" : "ready"}`}
          orderBy={orderBy}
          onSortChange={handleSort}
        />
      </Box>

      {/* User Drawer */}
      <UserDrawer
        open={drawerOpen}
        onClose={handleCloseDrawer}
        userId={editingUser?.id || null}
      />
    </Box>
  );
};
