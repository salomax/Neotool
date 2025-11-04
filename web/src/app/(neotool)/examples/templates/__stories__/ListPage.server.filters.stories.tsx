import React from "react";
import type { Meta, StoryObj } from "@storybook/react";
import type { ColDef } from "ag-grid-community";
import { ListPage } from "../ListPage";
import { useDataTableQuery } from "@/shared/hooks/useDataTableQuery";
import { Button, Stack, TextField, MenuItem } from "@mui/material";
import "ag-grid-community/styles/ag-grid.css";
import "ag-grid-community/styles/ag-theme-quartz.css";

type Row = {
  id: number;
  name: string;
  email: string;
  role: "Admin" | "Manager" | "Viewer";
};

const columns: ColDef<Row>[] = [
  { headerName: "ID", field: "id", maxWidth: 100 },
  { headerName: "Name", field: "name" },
  { headerName: "Email", field: "email" },
  { headerName: "Role", field: "role" },
];

// Fake "server" with filter and sort support
async function fetchUsers({
  page,
  pageSize,
  sort,
  filter,
}: {
  page: number;
  pageSize: number;
  sort?: string;
  filter?: Record<string, any>;
}) {
  const total = 500;
  const all: Row[] = Array.from({ length: total }, (_, i) => ({
    id: i + 1,
    name: `User ${i + 1}`,
    email: `user${i + 1}@example.com`,
    role: (i % 3 === 0
      ? "Admin"
      : i % 3 === 1
        ? "Manager"
        : "Viewer") as Row["role"],
  }));

  // Apply filter
  let filtered = all;
  if (filter?.q) {
    const q = String(filter.q).toLowerCase();
    filtered = filtered.filter(
      (r) =>
        r.name.toLowerCase().includes(q) || r.email.toLowerCase().includes(q),
    );
  }
  if (filter?.role && filter.role !== "all") {
    filtered = filtered.filter((r) => r.role === filter.role);
  }

  // Apply sort
  if (sort) {
    const parts = sort.split(",").map((s) => s.split(":")) as [
      string,
      "asc" | "desc",
    ][];
    filtered = filtered.slice().sort((a, b) => {
      for (const [col, dir] of parts) {
        const av = (a as any)[col];
        const bv = (b as any)[col];
        if (av < bv) return dir === "asc" ? -1 : 1;
        if (av > bv) return dir === "asc" ? 1 : -1;
      }
      return 0;
    });
  }

  const start = page * pageSize;
  const rows = filtered.slice(start, start + pageSize);

  await new Promise((r) => setTimeout(r, 250)); // latency
  return { rows, total: filtered.length };
}

const meta: Meta<any> = {
  title: "Templates/ListPage (Server-side Filters & Sort)",
  parameters: { layout: "fullscreen" },
};
export default meta;

export const Default: StoryObj = {
  render: () => <ServerSideListWithFilters />,
};

const ServerSideListWithFilters: React.FC = () => {
  const q = useDataTableQuery<Row>({
    key: "users-filters",
    fetcher: ({ page, pageSize, sort, filter }) => {
      const params: any = { page, pageSize };
      if (sort) params.sort = sort;
      if (filter) params.filter = filter;
      return fetchUsers(params);
    },
    initialPageSize: 25,
    initialSort: "id:asc",
    initialFilter: { q: "", role: "all" },
  });

  return (
    <ListPage<Row>
      title="Users"
      columns={columns}
      rows={q.data?.rows ?? []}
      loading={q.isLoading}
      error={
        q.isError ? ((q.error as any)?.message ?? "Failed to load") : undefined
      }
      {...(q.data?.total && { totalRows: q.data.total })}
      page={q.page}
      pageSize={q.pageSize}
      onPageChange={q.onPageChange}
      actions={
        <Stack direction="row" spacing={1}>
          <Button variant="outlined" onClick={() => q.refetch()}>
            Refresh
          </Button>
          <Button variant="contained" onClick={() => alert("External create!")}>
            New
          </Button>
        </Stack>
      }
      filters={
        <Stack direction="row" spacing={1} sx={{ py: 1 }}>
          <TextField
            size="small"
            label="Search"
            value={q.filter?.q ?? ""}
            onChange={(e) =>
              q.setFilter({ ...(q.filter ?? {}), q: e.target.value })
            }
          />
          <TextField
            size="small"
            label="Role"
            select
            value={q.filter?.role ?? "all"}
            onChange={(e) =>
              q.setFilter({ ...(q.filter ?? {}), role: e.target.value })
            }
          >
            <MenuItem value="all">All</MenuItem>
            <MenuItem value="Admin">Admin</MenuItem>
            <MenuItem value="Manager">Manager</MenuItem>
            <MenuItem value="Viewer">Viewer</MenuItem>
          </TextField>
        </Stack>
      }
    />
  );
};
