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
import type { Role } from "@/shared/hooks/authorization/useRoleManagement";

export interface RoleListProps {
  roles: Role[];
  loading?: boolean;
  onEdit: (role: Role) => void;
  onDelete?: (role: Role) => void;
  emptyMessage?: string;
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
      <Table>
        <TableHead>
          <TableRow>
            <TableCell>Name</TableCell>
            <TableCell align="right">Actions</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {roles.length === 0 ? (
            <TableRow>
              <TableCell colSpan={2} align="center" sx={{ py: 4 }}>
                <Typography color="text.secondary">{emptyMessage}</Typography>
              </TableCell>
            </TableRow>
          ) : (
            roles.map((role) => (
              <TableRow key={role.id} hover>
                <TableCell>
                  <Typography variant="body2" fontWeight="medium">
                    {role.name}
                  </Typography>
                </TableCell>
                <TableCell align="right">
                  <Stack direction="row" spacing={1} justifyContent="flex-end">
                    <Tooltip title="Edit role">
                      <IconButton
                        color="primary"
                        onClick={() => onEdit(role)}
                        size="small"
                        aria-label={`Edit role ${role.name}`}
                        data-testid={`edit-role-${role.id}`}
                      >
                        <EditIcon />
                      </IconButton>
                    </Tooltip>
                    {onDelete && (
                      <Tooltip title="Delete role">
                        <IconButton
                          color="error"
                          onClick={() => onDelete(role)}
                          size="small"
                          aria-label={`Delete role ${role.name}`}
                          data-testid={`delete-role-${role.id}`}
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




