"use client";

import React from "react";
import {
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
  IconButton,
  Tooltip,
  CircularProgress,
  LinearProgress,
  TableSortLabel,
  Skeleton,
} from "@mui/material";
import { Box, DynamicTableBox } from "@/shared/components/ui/layout";
import { Table } from "@/shared/components/ui/data-display";
import EditIcon from "@mui/icons-material/Edit";
import type { User } from "@/shared/hooks/authorization/useUserManagement";
import { UserStatusToggle } from "./UserStatusToggle";
import { RelayPagination, type PageInfo, type PaginationRangeData } from "@/shared/components/ui/pagination";
import type { UserSortState, UserOrderField } from "@/shared/utils/sorting";

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
  const isInitialLoading = loading && users.length === 0;
  const showEmptyState = !loading && users.length === 0;
  const showSkeletons = loading && users.length > 0; // Show skeletons when loading with existing data

  const handleSortClick = (field: UserOrderField) => {
    if (onSortChange) {
      onSortChange(field);
    }
  };

  const getSortDirection = (field: UserOrderField): 'asc' | 'desc' | false => {
    if (orderBy && orderBy.field === field) {
      return orderBy.direction;
    }
    return false;
  };

  return (
    <Box fullHeight>
      {/* Always reserve space for loading bar to prevent layout shift */}
      <Box
        sx={{
          height: 4, // Fixed height for LinearProgress (default MUI height)
          position: 'relative',
          flexShrink: 0,
        }}
      >
        {loading && users.length > 0 && (
          <LinearProgress
            sx={{
              position: 'absolute',
              top: 0,
              left: 0,
              right: 0,
              width: '100%',
            }}
          />
        )}
      </Box>
      <DynamicTableBox
        sx={{ 
          flex: 1, 
          overflow: "auto", 
          minHeight: 0,
          // Hide scrollbar while maintaining scroll functionality
          scrollbarWidth: 'none', // Firefox
          '&::-webkit-scrollbar': {
            display: 'none', // Chrome, Safari, Edge
          },
        }}
          pageSizeOptions={{
            minRows: 5,
            maxRows: 50,
            rowHeight: 53, // Fallback row height when rows are unavailable
            reservedHeight: 0,
            autoDetectHeaderHeight: true,
            autoDetectRowHeight: true,
          }}
          recalculationKey={recalculationKey}
          onTableResize={onTableResize}
        >
          <Table stickyHeader id="user-list-table">
            <TableHead>
              <TableRow>
                <TableCell>
                  {onSortChange ? (
                    <TableSortLabel
                      active={orderBy?.field === 'DISPLAY_NAME'}
                      direction={getSortDirection('DISPLAY_NAME') || 'asc'}
                      onClick={() => handleSortClick('DISPLAY_NAME')}
                    >
                      Name
                    </TableSortLabel>
                  ) : (
                    'Name'
                  )}
                </TableCell>
                <TableCell>
                  {onSortChange ? (
                    <TableSortLabel
                      active={orderBy?.field === 'EMAIL'}
                      direction={getSortDirection('EMAIL') || 'asc'}
                      onClick={() => handleSortClick('EMAIL')}
                    >
                      Email
                    </TableSortLabel>
                  ) : (
                    'Email'
                  )}
                </TableCell>
                <TableCell align="center">
                  {onSortChange ? (
                    <TableSortLabel
                      active={orderBy?.field === 'ENABLED'}
                      direction={getSortDirection('ENABLED') || 'asc'}
                      onClick={() => handleSortClick('ENABLED')}
                      sx={{ justifyContent: 'center' }}
                    >
                      Status
                    </TableSortLabel>
                  ) : (
                    'Status'
                  )}
                </TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody
              sx={{
                '& .MuiTableRow-root': {
                  transition: 'opacity 0.2s ease-in-out',
                },
              }}
            >
              {isInitialLoading ? (
                // Show multiple skeleton rows for initial load
                Array.from({ length: 5 }).map((_, index) => (
                  <TableRow key={`skeleton-${index}`}>
                    <TableCell>
                      <Skeleton variant="text" width="60%" />
                    </TableCell>
                    <TableCell>
                      <Skeleton variant="text" width="80%" />
                    </TableCell>
                    <TableCell align="center">
                      <Skeleton variant="circular" width={40} height={40} sx={{ mx: 'auto' }} />
                    </TableCell>
                    <TableCell align="right">
                      <Skeleton variant="circular" width={32} height={32} sx={{ ml: 'auto' }} />
                    </TableCell>
                  </TableRow>
                ))
              ) : showEmptyState ? (
                <TableRow>
                  <TableCell colSpan={4} align="center" sx={{ py: 4 }}>
                    <Typography color="text.secondary">{emptyMessage}</Typography>
                  </TableCell>
                </TableRow>
              ) : (
                <>
                  {users.map((user) => (
                    <TableRow 
                      key={user.id} 
                      hover
                      sx={{
                        opacity: showSkeletons ? 0.6 : 1,
                      }}
                    >
                      <TableCell>
                        <Typography variant="body2" fontWeight="medium">
                          {user.displayName || user.email}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2" color="text.secondary">
                          {user.email}
                        </Typography>
                      </TableCell>
                      <TableCell align="center">
                        <Box sx={{ display: "flex", justifyContent: "center", alignItems: "center" }}>
                          <UserStatusToggle
                            user={user}
                            enabled={user.enabled}
                            onToggle={onToggleStatus}
                            loading={toggleLoading}
                          />
                        </Box>
                      </TableCell>
                      <TableCell align="right">
                        <Tooltip title="Edit user">
                          <IconButton
                            color="primary"
                            onClick={() => onEdit(user)}
                            size="small"
                            aria-label={`Edit user ${user.email}`}
                            data-testid={`edit-user-${user.id}`}
                          >
                            <EditIcon />
                          </IconButton>
                        </Tooltip>
                      </TableCell>
                    </TableRow>
                  ))}
                  {showSkeletons && (
                    // Show a single skeleton row at the bottom while loading
                    <TableRow>
                      <TableCell colSpan={4} align="center" sx={{ py: 2 }}>
                        <CircularProgress size={24} />
                      </TableCell>
                    </TableRow>
                  )}
                </>
              )}
            </TableBody>
          </Table>
        </DynamicTableBox>
      {pageInfo && paginationRange && onLoadNext && onLoadPrevious && onGoToFirst && (
        <Box sx={{ px: 2 }}>
          <RelayPagination
            pageInfo={pageInfo}
            paginationRange={paginationRange}
            loading={loading}
            onLoadNext={onLoadNext}
            onLoadPrevious={onLoadPrevious}
            onGoToFirst={onGoToFirst}
            canLoadPreviousPage={canLoadPreviousPage}
          />
        </Box>
      )}
    </Box>
  );
};
