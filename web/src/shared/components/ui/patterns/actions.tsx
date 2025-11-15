"use client";

import * as React from "react";
import type { ColDef, ICellRendererParams } from "ag-grid-community";
import { Stack, IconButton, Tooltip } from "@mui/material";
import EditIcon from "@mui/icons-material/Edit";
import DeleteIcon from "@mui/icons-material/Delete";

type AnyRow = Record<string, any>;

function ActionsCell<T extends AnyRow>(props: ICellRendererParams<T>) {
  const row = props.data as T;
  const onEdit = (props.colDef as any).__onEdit as ((_r: T) => void) | undefined;
  const onDelete = (props.colDef as any).__onDelete as
    | ((_r: T) => void)
    | undefined;

  return (
    <Stack direction="row" spacing={1} alignItems="center">
      {onEdit && (
        <Tooltip title="Edit">
          <IconButton
            size="small"
            onClick={() => onEdit(row)}
            aria-label="edit row"
          >
            <EditIcon fontSize="small" />
          </IconButton>
        </Tooltip>
      )}
      {onDelete && (
        <Tooltip title="Delete">
          <IconButton
            size="small"
            color="error"
            onClick={() => onDelete(row)}
            aria-label="delete row"
          >
            <DeleteIcon fontSize="small" />
          </IconButton>
        </Tooltip>
      )}
    </Stack>
  );
}

export function actionsColumn<T extends AnyRow>(opts: {
  headerName?: string;
  width?: number;
  onEdit?: (_row: T) => void;
  onDelete?: (_row: T) => void;
}): ColDef<T> {
  return {
    headerName: opts.headerName ?? "Actions",
    field: "__actions__" as any,
    cellRenderer: ActionsCell as any,
    sortable: false,
    filter: false,
    width: opts.width ?? 120,
    maxWidth: Math.max(opts.width ?? 120, 140),
    // Pass handlers via a private field on colDef (read in ActionsCell)
    __onEdit: opts.onEdit,
    __onDelete: opts.onDelete,
  } as any;
}
