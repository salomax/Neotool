"use client";

import React, { useMemo } from "react";
import { Typography, IconButton, Tooltip, Box } from "@mui/material";
import EditIcon from "@mui/icons-material/Edit";
import DeleteIcon from "@mui/icons-material/Delete";
import type { Role } from "@/shared/hooks/authorization/useRoleManagement";
import { ManagementTable, type Column } from "@/shared/components/management";
import type { PageInfo, PaginationRangeData } from "@/shared/components/ui/pagination";
import type { RoleSortState, RoleOrderField } from "@/shared/utils/sorting";
import { useTranslation } from "@/shared/i18n";
import { authorizationManagementTranslations } from "@/app/(settings)/settings/i18n";

export interface RoleListProps {
  roles: Role[];
  /**
   * Loading state for the main query. When true and roles array is empty, shows skeleton loaders.
   * When true and roles array has items, shows loading progress bar and reduces opacity of existing rows.
   * @default false
   */
  loading?: boolean;
  onEdit: (role: Role) => void;
  onDelete?: (role: Role) => void;
  /**
   * Message to display when the roles list is empty. Should be contextual (e.g., different for search vs. no data).
   * @default "No roles found"
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
   * Callback to load the next page of roles. Required for pagination to work.
   */
  onLoadNext?: () => void;
  /**
   * Callback to load the previous page of roles. Required for pagination to work.
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
  orderBy?: RoleSortState;
  /**
   * Callback invoked when sort changes (column header clicked).
   */
  onSortChange?: (field: RoleOrderField) => void;
}

/**
 * RoleList component displaying roles in a table format
 */
export const RoleList: React.FC<RoleListProps> = ({
  roles,
  loading = false,
  onEdit,
  onDelete,
  emptyMessage = "No roles found",
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
  const columns: Column<Role, RoleOrderField>[] = useMemo(
    () => [
      {
        id: "name",
        label: t("roleManagement.table.name"),
        accessor: (role) => (
          <Typography variant="body2" fontWeight="medium">
            {role.name}
          </Typography>
        ),
        sortable: !!onSortChange,
        sortField: "NAME",
      },
    ],
    [t, onSortChange]
  );

  // Render actions column
  const renderActions = useMemo(
    () => (role: Role) => (
      <Box sx={{ display: "flex", justifyContent: "flex-end", gap: 1 }}>
        <Tooltip title={t("roleManagement.editRole", "Edit role")}>
          <IconButton
            color="primary"
            onClick={() => onEdit(role)}
            size="small"
            aria-label={`${t("roleManagement.editRole", "Edit role")} ${role.name}`}
            data-testid={`edit-role-${role.id}`}
          >
            <EditIcon />
          </IconButton>
        </Tooltip>
        {onDelete && (
          <Tooltip title={t("roleManagement.deleteRole", "Delete role")}>
            <IconButton
              color="error"
              onClick={() => onDelete(role)}
              size="small"
              aria-label={`${t("roleManagement.deleteRole", "Delete role")} ${role.name}`}
              data-testid={`delete-role-${role.id}`}
            >
              <DeleteIcon />
            </IconButton>
          </Tooltip>
        )}
      </Box>
    ),
    [t, onEdit, onDelete]
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
    <ManagementTable<Role, RoleOrderField>
      columns={columns}
      data={roles}
      loading={loading}
      emptyMessage={emptyMessage}
      sortState={orderBy}
      onSortChange={onSortChange}
      renderActions={renderActions}
      actionsLabel={t("roleManagement.table.actions")}
      pagination={pagination}
      onTableResize={onTableResize}
      recalculationKey={recalculationKey}
      tableId="role-list-table"
      getRowId={(role) => role.id}
    />
  );
};
