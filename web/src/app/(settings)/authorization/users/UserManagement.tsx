"use client";

import React, { useState, useCallback, useRef } from "react";
import { useUserManagement, type User } from "@/shared/hooks/authorization/useUserManagement";
import { UserSearch } from "./UserSearch";
import { UserList } from "./UserList";
import { UserDrawer } from "./UserDrawer";
import { useTranslation } from "@/shared/i18n";
import { authorizationManagementTranslations } from "@/app/(settings)/settings/i18n";
import { useToggleStatus } from "@/shared/hooks/mutations";
import { ManagementLayout } from "@/shared/components/management/ManagementLayout";

export interface UserManagementProps {
  initialSearchQuery?: string;
}

/**
 * UserManagement component orchestrates measurement + content rendering.
 */
export const UserManagement: React.FC<UserManagementProps> = ({
  initialSearchQuery = "",
}) => {
  const [initialPageSize, setInitialPageSize] = useState<number | null>(null);

  const handleMeasurement = useCallback((size: number) => {
    if (size > 0) {
      setInitialPageSize((prev) => (prev ?? size));
    }
  }, []);

  if (initialPageSize === null) {
    return <UserManagementSizer onMeasured={handleMeasurement} />;
  }

  return (
    <UserManagementContent
      initialPageSize={initialPageSize}
      initialSearchQuery={initialSearchQuery}
    />
  );
};

const UserManagementContent: React.FC<{
  initialPageSize: number;
  initialSearchQuery: string;
}> = ({ initialPageSize, initialSearchQuery }) => {
  const { t } = useTranslation(authorizationManagementTranslations);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editingUser, setEditingUser] = useState<User | null>(null);

  const {
    users,
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
    initialFirst: initialPageSize,
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

  const handleToggleStatus = useToggleStatus({
    enableFn: enableUser,
    disableFn: disableUser,
    enableSuccessMessage: "userManagement.toast.userEnabled",
    disableSuccessMessage: "userManagement.toast.userDisabled",
    enableErrorMessage: "userManagement.toast.userEnableError",
    disableErrorMessage: "userManagement.toast.userDisableError",
    t,
  });

  return (
    <ManagementLayout
      error={error}
      onErrorRetry={refetch}
      errorFallbackMessage={t("errors.loadFailed")}
    >
      <ManagementLayout.Header>
        <UserSearch
          value={inputValue}
          onChange={handleInputChange}
          onSearch={handleSearch}
          placeholder={t("userManagement.searchPlaceholder")}
          maxWidth="sm"
        />
      </ManagementLayout.Header>
      <ManagementLayout.Content>
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
      </ManagementLayout.Content>
      <ManagementLayout.Drawer>
        <UserDrawer
          open={drawerOpen}
          onClose={handleCloseDrawer}
          userId={editingUser?.id || null}
        />
      </ManagementLayout.Drawer>
    </ManagementLayout>
  );
};

const UserManagementSizer: React.FC<{ onMeasured: (size: number) => void }> = ({
  onMeasured,
}) => {
  const { t } = useTranslation(authorizationManagementTranslations);
  const hasReported = useRef(false);

  const handleTableResize = useCallback(
    (size: number) => {
      if (size > 0 && !hasReported.current) {
        hasReported.current = true;
        onMeasured(size);
      }
    },
    [onMeasured]
  );

  return (
    <ManagementLayout
      error={undefined}
      onErrorRetry={undefined}
      errorFallbackMessage={t("errors.loadFailed")}
    >
      <ManagementLayout.Header>
        <UserSearch
          value=""
          onChange={() => {}}
          onSearch={() => {}}
          placeholder={t("userManagement.searchPlaceholder")}
          maxWidth="sm"
        />
      </ManagementLayout.Header>
      <ManagementLayout.Content>
        <UserList
          users={[]}
          loading
          onEdit={() => {}}
          onToggleStatus={async () => {}}
          toggleLoading
          emptyMessage={t("userManagement.emptyList")}
          pageInfo={null}
          paginationRange={undefined}
          onTableResize={handleTableResize}
          recalculationKey="user-measurement"
        />
      </ManagementLayout.Content>
    </ManagementLayout>
  );
};
