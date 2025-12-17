"use client";

import React, { useMemo } from "react";
import { Typography, Box } from "@mui/material";
import type { User } from "@/shared/hooks/authorization/useUserManagement";
import { UserStatusToggle } from "./UserStatusToggle";
import { ManagementTable, type Column } from "@/shared/components/management";
import type { PageInfo, PaginationRangeData } from "@/shared/components/ui/pagination";
import type { UserSortState, UserOrderField } from "@/shared/utils/sorting";
import { useTranslation } from "@/shared/i18n";
import { authorizationManagementTranslations } from "@/app/(settings)/settings/i18n";
import { PermissionGate } from "@/shared/components/authorization";
import { Avatar } from "@/shared/components/ui/primitives/Avatar";
import { EditActionButton } from "@/shared/components/ui/primitives";

export interface UserListProps {
  users: User[];
  /**
   * Loading state for the main query. When true and users array is empty, shows skeleton loaders.
   * When true and users array has items, shows loading progress bar and reduces opacity of existing rows.
   * @default false
   */
  loading?: boolean;
  onEdit: (user: User) => void;
  onToggleStatus: (userId: string, enabled: boolean) => Promise<void>;
  /**
   * Loading state for the toggle status operation. When true, disables the status toggle switch.
   * @default false
   */
  toggleLoading?: boolean;
  /**
   * Message to display when the users list is empty. Should be contextual (e.g., different for search vs. no data).
   * @default "No users found"
   */
  emptyMessage?: string;
  /**
   * PageInfo object from Relay connection containing pagination metadata (hasNextPage, hasPreviousPage, cursors).
   * When null or undefined, pagination controls are hidden.
   */
  pageInfo?: PageInfo | null;
  /**
   * Pagination range data containing start index, end index, and total count.
   * Used to display pagination summary (e.g., "1-10 of 100").
   */
  paginationRange?: PaginationRangeData;
  /**
   * Callback to load the next page of users. Required for pagination to work.
   */
  onLoadNext?: () => void;
  /**
   * Callback to load the previous page of users. Required for pagination to work.
   */
  onLoadPrevious?: () => void;
  /**
   * Callback to navigate to the first page. Required for pagination to work.
   */
  onGoToFirst?: () => void;
  /**
   * Optional flag to independently control whether the "Previous" action is available.
   * If not provided, falls back to pageInfo.hasPreviousPage.
   */
  canLoadPreviousPage?: boolean;
  /**
   * Callback invoked when the table container is resized and a new page size is calculated.
   */
  onTableResize?: (pageSize: number) => void;
  /**
   * Optional key that, when changed, forces recalculation even if container size remains the same.
   * Useful to re-evaluate measurements when table content changes (e.g., loading state resolved).
   */
  recalculationKey?: string | number | boolean;
  /**
   * Current sort state (null for default sorting).
   */
  orderBy?: UserSortState;
  /**
   * Callback invoked when sort changes (column header clicked).
   */
  onSortChange?: (field: UserOrderField) => void;
}

/**
 * UserList component displaying users in a table format
 */
export const UserList: React.FC<UserListProps> = ({
  users,
  loading = false,
  onEdit,
  onToggleStatus,
  toggleLoading = false,
  emptyMessage = "No users found",
  pageInfo,
  paginationRange,
  onLoadNext,
  onLoadPrevious,
  onGoToFirst,
  canLoadPreviousPage,
  onTableResize,
  recalculationKey,
  orderBy = null,
  onSortChange,
}) => {
  const { t } = useTranslation(authorizationManagementTranslations);

  // Define columns declaratively
  const columns: Column<User, UserOrderField>[] = useMemo(
    () => [
      {
        id: "name",
        label: t("userManagement.table.name"),
        accessor: (user) => (
          <Box
            sx={{
              display: "flex",
              alignItems: "center",
              gap: 1.5,
            }}
          >
            <Avatar
              name={user.displayName || user.email}
              src={user.avatarUrl || undefined}
              size="medium"
            />
            <Typography variant="body2" fontWeight="medium">
              {user.displayName || user.email}
            </Typography>
          </Box>
        ),
        sortable: !!onSortChange,
        sortField: "DISPLAY_NAME",
      },
      {
        id: "email",
        label: t("userManagement.table.email"),
        accessor: (user) => (
          <Typography variant="body2" color="text.secondary">
            {user.email}
          </Typography>
        ),
        sortable: !!onSortChange,
        sortField: "EMAIL",
      },
      {
        id: "status",
        label: t("userManagement.table.status"),
        align: "center",
        render: (user) => (
          <Box
            sx={{
              display: "flex",
              justifyContent: "center",
              alignItems: "center",
            }}
          >
            <PermissionGate require="security:user:save">
              <UserStatusToggle
                user={user}
                enabled={user.enabled}
                onToggle={onToggleStatus}
                loading={toggleLoading}
              />
            </PermissionGate>
          </Box>
        ),
        sortable: !!onSortChange,
        sortField: "ENABLED",
      },
    ],
    [t, onSortChange, onToggleStatus, toggleLoading]
  );

  // Render actions column
  const renderActions = useMemo(
    () => {
      function UserActionsRenderer(user: User) {
        return (
          <PermissionGate require="security:user:view">
            <EditActionButton
              onClick={() => onEdit(user)}
              tooltipTitle={t("userManagement.editUser")}
              ariaLabel={`${t("userManagement.editUser")} ${user.email}`}
              data-testid={`edit-user-${user.id}`}
              size="small"
            />
          </PermissionGate>
        );
      }
      return UserActionsRenderer;
    },
    [t, onEdit]
  );

  // Build pagination object if all required props are present
  const pagination = useMemo(() => {
    if (
      pageInfo &&
      paginationRange &&
      onLoadNext &&
      onLoadPrevious &&
      onGoToFirst
    ) {
      return {
        pageInfo,
        paginationRange,
        onLoadNext,
        onLoadPrevious,
        onGoToFirst,
        canLoadPreviousPage,
      };
    }
    return undefined;
  }, [
    pageInfo,
    paginationRange,
    onLoadNext,
    onLoadPrevious,
    onGoToFirst,
    canLoadPreviousPage,
  ]);

  return (
    <ManagementTable<User, UserOrderField>
      columns={columns}
      data={users}
      loading={loading}
      emptyMessage={emptyMessage}
      sortState={orderBy}
      onSortChange={onSortChange}
      renderActions={renderActions}
      actionsLabel={t("userManagement.table.actions")}
      pagination={pagination}
      onTableResize={onTableResize}
      recalculationKey={recalculationKey}
      tableId="user-list-table"
      getRowId={(user) => user.id}
    />
  );
};
