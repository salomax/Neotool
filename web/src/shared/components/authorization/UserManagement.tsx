"use client";

import React, { useState, useCallback, useRef, useEffect } from "react";
import { Box } from "@/shared/components/ui/layout";
import { Alert } from "@mui/material";
import { useUserManagement, type User } from "@/shared/hooks/authorization/useUserManagement";
import { useDynamicPageSize } from "@/shared/hooks/ui";
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
  const tableContainerRef = useRef<HTMLDivElement>(null);

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
  } = useUserManagement({
    initialSearchQuery,
    initialFirst: 10, // Default initial value, will be updated by dynamicPageSize
  });

  // Calculate dynamic page size based on container height and actual table measurements
  const dynamicPageSize = useDynamicPageSize(tableContainerRef, {
    minRows: 5,
    maxRows: 50,
    rowHeight: 53, // Fallback row height when rows are unavailable
    reservedHeight: 0,
    autoDetectHeaderHeight: true,
    autoDetectRowHeight: true,
    recalculationKey: `${users.length}-${loading ? "loading" : "ready"}`,
  });

  // Update page size when container size changes
  useEffect(() => {
    if (dynamicPageSize > 0) {
      setFirst(dynamicPageSize);
    }
  }, [dynamicPageSize, setFirst]);

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
    <Box
      sx={{
        padding: 2,
      }}
      fullHeight
    >
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
      <Box sx={{ flex: 1, minHeight: 0, mt: 2 }}>
        <UserList
          users={users}
          loading={loading}
          onEdit={handleEdit}
          onToggleStatus={handleToggleStatus}
          toggleLoading={enableLoading || disableLoading}
          tableContainerRef={tableContainerRef}
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
