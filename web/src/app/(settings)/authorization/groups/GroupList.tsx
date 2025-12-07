"use client";

import React, { useMemo } from "react";
import { Typography, IconButton, Tooltip, Box } from "@mui/material";
import EditIcon from "@mui/icons-material/Edit";
import DeleteIcon from "@mui/icons-material/Delete";
import type { Group, GroupOrderField } from "@/shared/hooks/authorization/useGroupManagement";
import { ManagementTable, type Column } from "@/shared/components/management";
import type { PageInfo, PaginationRangeData } from "@/shared/components/ui/pagination";
import type { SortState } from "@/shared/hooks/sorting";
import { useTranslation } from "@/shared/i18n";
import { authorizationManagementTranslations } from "@/app/(settings)/settings/i18n";
import { PermissionGate } from "@/shared/components/authorization";

export type GroupSortState = SortState<GroupOrderField>;

export interface GroupListProps {
  groups: Group[];
  /**
   * Loading state for the main query. When true and groups array is empty, shows skeleton loaders.
   * When true and groups array has items, shows loading progress bar and reduces opacity of existing rows.
   * @default false
   */
  loading?: boolean;
  onEdit: (group: Group) => void;
  onDelete?: (group: Group) => void;
  /**
   * Message to display when the groups list is empty. Should be contextual (e.g., different for search vs. no data).
   * @default "No groups found"
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
   * Callback to load the next page of groups. Required for pagination to work.
   */
  onLoadNext?: () => void;
  /**
   * Callback to load the previous page of groups. Required for pagination to work.
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
  orderBy?: GroupSortState;
  /**
   * Callback invoked when sort changes (column header clicked).
   */
  onSortChange?: (field: GroupOrderField) => void;
}

/**
 * GroupList component displaying groups in a table format
 */
export const GroupList: React.FC<GroupListProps> = ({
  groups,
  loading = false,
  onEdit,
  onDelete,
  emptyMessage = "No groups found",
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
  const columns: Column<Group, GroupOrderField>[] = useMemo(
    () => [
      {
        id: "name",
        label: t("groupManagement.table.name"),
        accessor: (group) => (
          <Typography variant="body2" fontWeight="medium">
            {group.name}
          </Typography>
        ),
        sortable: !!onSortChange,
        sortField: "NAME",
      },
      {
        id: "description",
        label: t("groupManagement.table.description"),
        accessor: (group) => (
          <Typography variant="body2" color="text.secondary">
            {group.description || "-"}
          </Typography>
        ),
      },
    ],
    [t, onSortChange]
  );

  // Render actions column
  const renderActions = useMemo(
    () => (group: Group) => (
      <Box sx={{ display: "flex", justifyContent: "flex-end", gap: 1 }}>
        <PermissionGate require="security:group:view">
          <Tooltip title={t("groupManagement.editGroup")}>
            <IconButton
              color="primary"
              onClick={() => onEdit(group)}
              size="small"
              aria-label={`${t("groupManagement.editGroup")} ${group.name}`}
              data-testid={`edit-group-${group.id}`}
            >
              <EditIcon />
            </IconButton>
          </Tooltip>
        </PermissionGate>
        {onDelete && (
          <PermissionGate require="security:group:delete">
            <Tooltip title={t("groupManagement.deleteGroup", "Delete group")}>
              <IconButton
                color="error"
                onClick={() => onDelete(group)}
                size="small"
                aria-label={`${t("groupManagement.deleteGroup", "Delete group")} ${group.name}`}
                data-testid={`delete-group-${group.id}`}
              >
                <DeleteIcon />
              </IconButton>
            </Tooltip>
          </PermissionGate>
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
    <ManagementTable<Group, GroupOrderField>
      columns={columns}
      data={groups}
      loading={loading}
      emptyMessage={emptyMessage}
      sortState={orderBy}
      onSortChange={onSortChange}
      renderActions={renderActions}
      actionsLabel={t("groupManagement.table.actions")}
      pagination={pagination}
      onTableResize={onTableResize}
      recalculationKey={recalculationKey}
      tableId="group-list-table"
      getRowId={(group) => group.id}
    />
  );
};

