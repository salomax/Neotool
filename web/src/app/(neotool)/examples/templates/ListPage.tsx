"use client";

import * as React from "react";
import { Paper, Box, Stack, Typography, Divider } from "@mui/material";
import type { ColDef } from "ag-grid-community";
import { DataTable } from "@/shared/components/ui/data-display/DataTable";

export interface ListPageProps<T extends object> {
  title: string;
  columns: ColDef<T>[];
  rows: T[];
  actions?: React.ReactNode; // e.g., buttons on the right of header
  filters?: React.ReactNode; // optional filter bar above the table
  loading?: boolean;
  error?: string;
  totalRows?: number;
  page?: number;
  pageSize?: number;
  onPageChange?: (_page: number, _pageSize: number) => void;
}

export function ListPage<T extends object>(props: ListPageProps<T>) {
  const {
    title,
    actions,
    filters,
    columns,
    rows,
    loading,
    error,
    totalRows,
    page: _page,
    pageSize: _pageSize,
    onPageChange: _onPageChange,
  } = props;

  return (
    <Paper elevation={0} sx={{ p: 2 }}>
      <Stack
        direction="row"
        alignItems="center"
        justifyContent="space-between"
        sx={{ mb: 1 }}
      >
        <Typography variant="h5">{title}</Typography>
        <Box>{actions}</Box>
      </Stack>
      {filters && (
        <Box sx={{ mb: 1 }}>
          {filters}
          <Divider sx={{ mt: 1 }} />
        </Box>
      )}
      <DataTable
        columns={columns}
        rows={rows}
        {...(loading !== undefined && { loading })}
        {...(error && { error })}
        {...(totalRows !== undefined && { totalRows })}
        {...(_page !== undefined && { page: _page })}
        {...(_pageSize !== undefined && { pageSize: _pageSize })}
        {...(_onPageChange && { onPageChange: _onPageChange })}
      />
    </Paper>
  );
}

export default ListPage;
