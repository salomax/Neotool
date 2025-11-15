import React from "react";
import type { Meta, StoryObj } from "@storybook/react";
import type { ColDef } from "ag-grid-community";
import { ListPage } from "../ListPage";
import { useDataTableQuery } from "@/shared/hooks/useDataTableQuery";
import { Button, Stack, TextField } from "@mui/material";
import "ag-grid-community/styles/ag-grid.css";
import "ag-grid-community/styles/ag-theme-quartz.css";

type Row = { id: number; name: string; email: string };

const columns: ColDef<Row>[] = [
  { headerName: "ID", field: "id", maxWidth: 100 },
  { headerName: "Name", field: "name" },
  { headerName: "Email", field: "email" },
];

// Fake "server" function
async function fetchUsers({
  page,
  pageSize,
}: {
  page: number;
  pageSize: number;
}) {
  const total = 500;
  const start = page * pageSize + 1;
  const end = Math.min(start + pageSize - 1, total);
  const rows: Row[] = [];
  for (let i = start; i <= end; i++)
    rows.push({ id: i, name: `User ${i}`, email: `user${i}@example.com` });
  // Simulate latency
  await new Promise((r) => setTimeout(r, 300));
  return { rows, total };
}

const meta: Meta<any> = {
  title: "Templates/ListPage (Server-side)",
  parameters: { layout: "fullscreen" },
};
export default meta;

export const Default: StoryObj = {
  render: () => <ServerSideList />,
};

const ServerSideList: React.FC = () => {
  const q = useDataTableQuery<Row>({
    key: "users",
    fetcher: ({ page, pageSize }) => fetchUsers({ page, pageSize }),
    initialPageSize: 25,
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
          <Button variant="contained">New</Button>
        </Stack>
      }
      filters={
        <Stack direction="row" spacing={1} sx={{ py: 1 }}>
          <TextField size="small" label="Search" placeholder="(demo only)" />
        </Stack>
      }
    />
  );
};
