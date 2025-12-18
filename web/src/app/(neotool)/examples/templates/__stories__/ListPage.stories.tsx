import React from "react";
import type { Meta, StoryObj } from "@storybook/react";
import { Button, Stack, TextField } from "@mui/material";
import type { ColDef } from "ag-grid-community";
import { ListPage } from "../ListPage";

type Row = { id: number; name: string; role: string };

const columns: ColDef<Row>[] = [
  { headerName: "ID", field: "id", maxWidth: 100 },
  { headerName: "Name", field: "name" },
  { headerName: "Role", field: "role" },
];

const data: Row[] = Array.from({ length: 75 }, (_, i) => ({
  id: i + 1,
  name: `Employee ${i + 1}`,
  role: i % 3 === 0 ? "Admin" : i % 3 === 1 ? "Manager" : "Viewer",
}));

const meta: Meta<typeof ListPage<Row>> = {
  title: "Templates/ListPage",
  component: ListPage<Row>,
  args: {
    title: "Employees",
    columns,
    rows: data,
    actions: (
      <Stack direction="row" spacing={1}>
        <Button variant="outlined">Export</Button>
        <Button variant="contained">New</Button>
      </Stack>
    ),
    filters: (
      <Stack direction="row" spacing={1} sx={{ py: 1 }}>
        <TextField size="small" label="Search name" />
        <TextField size="small" label="Role" />
      </Stack>
    ),
  },
};
export default meta;

type Story = StoryObj<typeof ListPage<Row>>;
export const Default: Story = {};
