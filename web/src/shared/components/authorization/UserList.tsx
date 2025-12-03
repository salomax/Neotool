"use client";

import React from "react";
import {
  Paper,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Box,
  Typography,
  IconButton,
  Tooltip,
  CircularProgress,
} from "@mui/material";
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
}) => {
  if (loading) {
    return (
      <Paper>
        <Box sx={{ display: "flex", justifyContent: "center", p: 4 }}>
          <CircularProgress />
        </Box>
      </Paper>
    );
  }

  return (
    <Box
      sx={{
        display: "flex",
        flexDirection: "column",
        height: "100%",
        minHeight: 0,
      }}
    >
      <Paper
        sx={{
          flex: 1,
          display: "flex",
          flexDirection: "column",
          minHeight: 0,
          overflow: "hidden",
        }}
      >
        <Box sx={{ flex: 1, overflow: "auto", minHeight: 0 }}>
          <Table stickyHeader>
            <TableHead>
              <TableRow>
                <TableCell>Name</TableCell>
                <TableCell>Email</TableCell>
                <TableCell align="center">Status</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {users.length === 0 ? (
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
      </Paper>
      {pageInfo && paginationRange && onLoadNext && onLoadPrevious && onGoToFirst && (
        <Box sx={{ mt: 2 }}>
          <RelayPagination
            pageInfo={pageInfo}
            paginationRange={paginationRange}
            loading={loading}
            onLoadNext={onLoadNext}
            onLoadPrevious={onLoadPrevious}
            onGoToFirst={onGoToFirst}
          />
        </Box>
      )}
    </Box>
  );
};

