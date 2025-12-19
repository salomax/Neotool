"use client";

import React from "react";
import {
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
  CircularProgress,
  LinearProgress,
  TableSortLabel,
  Skeleton,
} from "@mui/material";
import {
  Box,
  DynamicTableBox,
  DynamicTableContainer,
  getTableSizeConfig,
  type TableSize,
} from "@/shared/components/ui/layout";
import { Table } from "@/shared/components/ui/data-display";
import { RelayPagination, type PageInfo, type PaginationRangeData } from "@/shared/components/ui/pagination";
import type { SortState } from "@/shared/hooks/sorting";

/**
 * Column definition for ManagementTable
 */
export interface Column<T, F extends string = string> {
  /**
   * Unique identifier for the column
   */
  id: string;
  /**
   * Column header label
   */
  label: string;
  /**
   * Function to extract cell value from row data.
   * Returns ReactNode for rendering.
   * Use either `accessor` or `render`, not both.
   */
  accessor?: (row: T) => React.ReactNode;
  /**
   * Custom render function for the cell.
   * Use either `accessor` or `render`, not both.
   */
  render?: (row: T) => React.ReactNode;
  /**
   * Text alignment for the column
   * @default 'left'
   */
  align?: "left" | "center" | "right";
  /**
   * Column width (CSS value)
   */
  width?: string | number;
  /**
   * Whether the column is sortable
   * @default false
   */
  sortable?: boolean;
  /**
   * Sort field identifier (used when sortable is true)
   * Must match the type parameter F
   */
  sortField?: F;
}

/**
 * Pagination configuration for ManagementTable
 */
export interface ManagementTablePagination {
  /**
   * PageInfo object from Relay connection
   */
  pageInfo: PageInfo | null;
  /**
   * Pagination range data (start, end, total)
   */
  paginationRange: PaginationRangeData;
  /**
   * Callback to load the next page
   */
  onLoadNext: () => void;
  /**
   * Callback to load the previous page
   */
  onLoadPrevious: () => void;
  /**
   * Callback to navigate to the first page
   */
  onGoToFirst: () => void;
  /**
   * Optional flag to independently control whether the "Previous" action is available.
   * If not provided, falls back to pageInfo.hasPreviousPage.
   */
  canLoadPreviousPage?: boolean;
}

/**
 * Props for ManagementTable component
 */
export interface ManagementTableProps<T, F extends string = string> {
  /**
   * Column definitions
   */
  columns: Column<T, F>[];
  /**
   * Array of data rows
   */
  data: T[];
  /**
   * Loading state. When true and data is empty, shows skeleton loaders.
   * When true and data has items, shows loading progress bar and reduces opacity of existing rows.
   * @default false
   */
  loading?: boolean;
  /**
   * Message to display when the table is empty.
   * Should be contextual (e.g., different for search vs. no data).
   * @default "No items found"
   */
  emptyMessage?: string;
  /**
   * Custom empty state renderer. If provided, overrides the default empty message.
   */
  renderEmptyState?: () => React.ReactNode;
  /**
   * Custom loading state renderer. If provided, overrides the default skeleton rows.
   */
  renderLoadingState?: () => React.ReactNode;
  /**
   * Current sort state (null for default sorting)
   */
  sortState?: SortState<F>;
  /**
   * Callback invoked when sort changes (column header clicked)
   */
  onSortChange?: (field: F) => void;
  /**
   * Custom renderer for row actions. Can return a single action or multiple actions.
   * If provided, adds an "Actions" column at the end.
   * 
   * @example
   * ```tsx
   * // Single action
   * renderActions={(row) => (
   *   <IconButton onClick={() => onEdit(row)}>
   *     <EditIcon />
   *   </IconButton>
   * )}
   * 
   * // Multiple actions
   * renderActions={(row) => (
   *   <Box sx={{ display: "flex", justifyContent: "flex-end", gap: 1 }}>
   *     <Tooltip title="Edit">
   *       <IconButton onClick={() => onEdit(row)}>
   *         <EditIcon />
   *       </IconButton>
   *     </Tooltip>
   *     <Tooltip title="Delete">
   *       <IconButton onClick={() => onDelete(row)}>
   *         <DeleteIcon />
   *       </IconButton>
   *     </Tooltip>
   *   </Box>
   * )}
   * ```
   */
  renderActions?: (row: T) => React.ReactNode;
  /**
   * Label for the actions column. Only used when renderActions is provided.
   * @default "Actions"
   */
  actionsLabel?: string;
  /**
   * Pagination configuration. When provided, renders RelayPagination footer.
   */
  pagination?: ManagementTablePagination;
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
   * HTML id attribute for the table element
   */
  tableId?: string;
  /**
   * Number of skeleton rows to show during initial loading
   * @default 5
   */
  skeletonRowCount?: number;
  /**
   * Function to extract a unique identifier from a row.
   * Used as the React key for table rows.
   * If not provided, uses the row index.
   */
  getRowId?: (row: T) => string | number;
  /**
   * Table size affecting row height, header height, and pagination footer height.
   * Passed to MUI Table component and used for dynamic page size calculations.
   * @default "medium"
   */
  size?: TableSize;
}

/**
 * Loading bar component - shows progress when loading with existing data
 */
function LoadingBar({ show }: { show: boolean }) {
  if (!show) return null;
  
  return (
    <Box
      sx={{
        height: 4,
        position: "relative",
        flexShrink: 0,
      }}
    >
      <LinearProgress
        sx={{
          position: "absolute",
          top: 0,
          left: 0,
          right: 0,
          width: "100%",
        }}
      />
    </Box>
  );
}

/**
 * Renders skeleton cells for a column
 */
function SkeletonCell<T, F extends string = string>({ column }: { column: Column<T, F> }) {
  if (column.id === "actions") {
    return (
      <Box
        sx={{
          display: "flex",
          justifyContent: column.align === "right" ? "flex-end" : "flex-start",
          gap: 1,
        }}
      >
        <Skeleton variant="circular" width={32} height={32} />
        <Skeleton variant="circular" width={32} height={32} />
      </Box>
    );
  }
  
  return <Skeleton variant="text" width="60%" />;
}

/**
 * Generic ManagementTable component for displaying tabular data with common management features.
 * 
 * Provides:
 * - Loading states (skeleton rows, progress bar, spinner)
 * - Empty state handling
 * - Column-based sorting
 * - Relay pagination
 * - Responsive table container
 * - Customizable row actions
 * 
 * @example
 * ```tsx
 * // Basic usage with single action
 * const columns: Column<User>[] = [
 *   { id: 'name', label: 'Name', accessor: (u) => u.displayName, sortable: true, sortField: 'DISPLAY_NAME' },
 *   { id: 'email', label: 'Email', accessor: (u) => u.email, sortable: true, sortField: 'EMAIL' },
 * ];
 * 
 * <ManagementTable
 *   columns={columns}
 *   data={users}
 *   loading={loading}
 *   sortState={orderBy}
 *   onSortChange={handleSort}
 *   renderActions={(user) => (
 *     <IconButton onClick={() => onEdit(user)}>
 *       <EditIcon />
 *     </IconButton>
 *   )}
 *   pagination={{ pageInfo, paginationRange, onLoadNext, onLoadPrevious, onGoToFirst }}
 * />
 * ```
 */
export function ManagementTable<T, F extends string = string>({
  columns,
  data,
  loading = false,
  emptyMessage = "No items found",
  renderEmptyState,
  renderLoadingState,
  sortState = null,
  onSortChange,
  renderActions,
  actionsLabel = "Actions",
  pagination,
  onTableResize,
  recalculationKey,
  tableId,
  skeletonRowCount = 5,
  getRowId,
  size = "medium",
}: ManagementTableProps<T, F>) {
  const sizeConfig = getTableSizeConfig(size);
  const hasData = data.length > 0;
  const isInitialLoading = loading && !hasData;
  const isLoadingMore = loading && hasData;

  // Build columns array - only memoize if renderActions changes
  const allColumns: Column<T, F>[] = React.useMemo(() => {
    if (!renderActions) return columns;
    
    return [
      ...columns,
      {
        id: "actions",
        label: actionsLabel,
        align: "right" as const,
        render: renderActions,
      } as Column<T, F>,
    ];
  }, [columns, renderActions, actionsLabel]);

  const handleSortClick = React.useCallback(
    (field: F) => {
      onSortChange?.(field);
    },
    [onSortChange]
  );

  const getSortDirection = React.useCallback(
    (field: F): "asc" | "desc" | false => {
      return sortState?.field === field ? sortState.direction : false;
    },
    [sortState]
  );

  const renderCell = React.useCallback(
    (row: T, column: Column<T, F>) => {
      return column.render?.(row) ?? column.accessor?.(row) ?? null;
    },
    []
  );

  // Validate pagination props - if pagination exists, all required properties are present (enforced by type)
  const hasPagination = Boolean(
    pagination &&
    pagination.pageInfo &&
    pagination.paginationRange
  );

  return (
    <DynamicTableContainer
      size={size}
      recalculationKey={recalculationKey}
      onTableResize={onTableResize}
    >
      <Box fullHeight>
        <LoadingBar show={isLoadingMore} />
        
        <DynamicTableBox>
          <Table stickyHeader id={tableId} data-testid={tableId} size={size}>
            <TableHead>
              <TableRow>
                {allColumns.map((column) => (
                  <TableCell
                    key={column.id}
                    align={column.align || "left"}
                    width={column.width}
                  >
                    {column.sortable && onSortChange && column.sortField ? (
                      <TableSortLabel
                        active={sortState?.field === column.sortField}
                        direction={getSortDirection(column.sortField) || "asc"}
                        onClick={() => handleSortClick(column.sortField!)}
                        sx={
                          column.align === "center"
                            ? { justifyContent: "center" }
                            : undefined
                        }
                      >
                        {column.label}
                      </TableSortLabel>
                    ) : (
                      column.label
                    )}
                  </TableCell>
                ))}
              </TableRow>
            </TableHead>
            
            <TableBody
              sx={{
                "& .MuiTableRow-root": {
                  transition: "opacity 0.2s ease-in-out",
                },
              }}
            >
              {isInitialLoading ? (
                renderLoadingState ? (
                  <TableRow>
                    <TableCell colSpan={allColumns.length}>
                      {renderLoadingState()}
                    </TableCell>
                  </TableRow>
                ) : (
                  Array.from({ length: skeletonRowCount }).map((_, index) => (
                    <TableRow key={`skeleton-${index}`}>
                      {allColumns.map((column) => (
                        <TableCell
                          key={column.id}
                          align={column.align || "left"}
                        >
                          <SkeletonCell column={column} />
                        </TableCell>
                      ))}
                    </TableRow>
                  ))
                )
              ) : !hasData ? (
                <TableRow data-testid="table-empty-state-row">
                  <TableCell
                    colSpan={allColumns.length}
                    align="center"
                    sx={{ py: 4 }}
                  >
                    {renderEmptyState ? (
                      renderEmptyState()
                    ) : (
                      <Typography color="text.secondary">
                        {emptyMessage}
                      </Typography>
                    )}
                  </TableCell>
                </TableRow>
              ) : (
                <>
                  {data.map((row, rowIndex) => {
                    const rowKey = getRowId ? getRowId(row) : rowIndex;
                    return (
                      <TableRow
                        key={rowKey}
                        hover
                        sx={{
                          opacity: isLoadingMore ? 0.6 : 1,
                        }}
                      >
                        {allColumns.map((column) => (
                          <TableCell
                            key={column.id}
                            align={column.align || "left"}
                          >
                            {renderCell(row, column)}
                          </TableCell>
                        ))}
                      </TableRow>
                    );
                  })}
                  {isLoadingMore && (
                    <TableRow>
                      <TableCell
                        colSpan={allColumns.length}
                        align="center"
                        sx={{ py: 2 }}
                      >
                        <CircularProgress size={24} />
                      </TableCell>
                    </TableRow>
                  )}
                </>
              )}
            </TableBody>
          </Table>
        </DynamicTableBox>
        
        {hasPagination && (
          <Box
            data-pagination-footer
            sx={{
              px: 2,
              minHeight: sizeConfig.footerHeight,
              flexShrink: 0,
            }}
          >
            <RelayPagination
              pageInfo={pagination!.pageInfo}
              paginationRange={pagination!.paginationRange}
              loading={loading}
              onLoadNext={pagination!.onLoadNext}
              onLoadPrevious={pagination!.onLoadPrevious}
              onGoToFirst={pagination!.onGoToFirst}
              canLoadPreviousPage={pagination!.canLoadPreviousPage}
            />
          </Box>
        )}
      </Box>
    </DynamicTableContainer>
  );
}
