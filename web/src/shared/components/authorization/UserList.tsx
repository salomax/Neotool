"use client";

import React from "react";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
  IconButton,
  Tooltip,
  CircularProgress,
  LinearProgress,
} from "@mui/material";
import { Box } from "@/shared/components/ui/layout";
import EditIcon from "@mui/icons-material/Edit";
import type { User } from "@/shared/hooks/authorization/useUserManagement";
import { UserStatusToggle } from "./UserStatusToggle";
import { RelayPagination, type PageInfo, type PaginationRangeData } from "@/shared/components/ui/pagination";

export interface UserListProps {
  users: User[];
  loading?: boolean;
  onEdit: (user: User) => void;
  onToggleStatus: (userId: string, enabled: boolean) => Promise<void>;
  toggleLoading?: boolean;
  emptyMessage?: string;
  pageInfo?: PageInfo | null;
  paginationRange?: PaginationRangeData;
  onLoadNext?: () => void;
  onLoadPrevious?: () => void;
  onGoToFirst?: () => void;
  canLoadPreviousPage?: boolean;
  /**
   * Optional ref to the scrollable table container. Used for dynamic sizing calculations.
   */
  tableContainerRef?: React.Ref<HTMLDivElement>;
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
  tableContainerRef,
}) => {
  const isInitialLoading = loading && users.length === 0;
  const showEmptyState = !loading && users.length === 0;

  return (
    <Box fullHeight>
      {loading && users.length > 0 && <LinearProgress />}
        <Box ref={tableContainerRef} sx={{ flex: 1, overflow: "auto", minHeight: 0 }}>
          <Table stickyHeader id="user-list-table">
            <TableHead>
              <TableRow>
                <TableCell>Name</TableCell>
                <TableCell>Email</TableCell>
                <TableCell align="center">Status</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {isInitialLoading ? (
                <TableRow>
                  <TableCell colSpan={4} align="center" sx={{ py: 6 }}>
                    <CircularProgress />
                  </TableCell>
                </TableRow>
              ) : showEmptyState ? (
                <TableRow>
                  <TableCell colSpan={4} align="center" sx={{ py: 4 }}>
                    <Typography color="text.secondary">{emptyMessage}</Typography>
                  </TableCell>
                </TableRow>
              ) : (
                users.map((user) => (
                  <TableRow key={user.id} hover>
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
                ))
              )}
            </TableBody>
          </Table>
        </Box>
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
