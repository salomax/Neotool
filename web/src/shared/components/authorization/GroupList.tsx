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
  Stack,
} from "@mui/material";
import EditIcon from "@mui/icons-material/Edit";
import DeleteIcon from "@mui/icons-material/Delete";
import type { Group } from "@/shared/hooks/authorization/useGroupManagement";

export interface GroupListProps {
  groups: Group[];
  loading?: boolean;
  onEdit: (group: Group) => void;
  onDelete?: (group: Group) => void;
  emptyMessage?: string;
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
    <Paper>
      <Table id="group-list-table" stickyHeader>
        <TableHead>
          <TableRow>
            <TableCell>Name</TableCell>
            <TableCell>Description</TableCell>
            <TableCell align="right">Actions</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {groups.length === 0 ? (
            <TableRow>
              <TableCell colSpan={3} align="center" sx={{ py: 4 }}>
                <Typography color="text.secondary">{emptyMessage}</Typography>
              </TableCell>
            </TableRow>
          ) : (
            groups.map((group) => (
              <TableRow key={group.id} hover>
                <TableCell>
                  <Typography variant="body2" fontWeight="medium">
                    {group.name}
                  </Typography>
                </TableCell>
                <TableCell>
                  <Typography variant="body2" color="text.secondary">
                    {group.description || "-"}
                  </Typography>
                </TableCell>
                <TableCell align="right">
                  <Stack direction="row" spacing={1} justifyContent="flex-end">
                    <Tooltip title="Edit group">
                      <IconButton
                        color="primary"
                        onClick={() => onEdit(group)}
                        size="small"
                        aria-label={`Edit group ${group.name}`}
                        data-testid={`edit-group-${group.id}`}
                      >
                        <EditIcon />
                      </IconButton>
                    </Tooltip>
                    {onDelete && (
                      <Tooltip title="Delete group">
                        <IconButton
                          color="error"
                          onClick={() => onDelete(group)}
                          size="small"
                          aria-label={`Delete group ${group.name}`}
                          data-testid={`delete-group-${group.id}`}
                        >
                          <DeleteIcon />
                        </IconButton>
                      </Tooltip>
                    )}
                  </Stack>
                </TableCell>
              </TableRow>
            ))
          )}
        </TableBody>
      </Table>
    </Paper>
  );
};

