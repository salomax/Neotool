import React from "react";
import type { Meta, StoryObj } from "@storybook/react";
import type { ColDef } from "ag-grid-community";
import { CrudPage } from "../CrudPage";
import { actionsColumn } from "@/shared/components/ui/patterns/actions";
import { z } from "zod";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm, FormProvider } from "react-hook-form";
import { Stack } from "@mui/material";
import { Button } from "@/shared/components/ui/primitives/Button";
import { FormTextField } from "@/shared/components/ui/forms/form/FormField";
import "ag-grid-community/styles/ag-grid.css";
import "ag-grid-community/styles/ag-theme-quartz.css";

type Row = { id: number; name: string; email: string };

const baseColumns: ColDef<Row>[] = [
  { headerName: "ID", field: "id", maxWidth: 100 },
  { headerName: "Name", field: "name" },
  { headerName: "Email", field: "email" },
];

const initialRows: Row[] = Array.from({ length: 10 }, (_, i) => ({
  id: i + 1,
  name: `User ${i + 1}`,
  email: `user${i + 1}@example.com`,
}));

const schema = z.object({
  name: z.string().min(2, "Name must have at least 2 characters"),
  email: z.string().email("Invalid email"),
});
type FormData = z.infer<typeof schema>;

const meta: Meta<any> = {
  title: "Templates/CrudPage (with Actions)",
  parameters: { layout: "fullscreen" },
};
export default meta;

export const Default: StoryObj = {
  render: () => <CrudWithActions />,
};

const CrudWithActions: React.FC = () => {
  const [rows, setRows] = React.useState<Row[]>(initialRows);

  const Form: React.FC<{
    mode: "create" | "edit";
    item?: Row;
    close: () => void;
  }> = ({ mode, item, close }) => {
    const methods = useForm<FormData>({
      resolver: zodResolver(schema),
      defaultValues: { name: item?.name ?? "", email: item?.email ?? "" },
    });
    const onSubmit = (data: FormData) => {
      if (mode === "create") {
        const nextId = rows.length ? Math.max(...rows.map((r) => r.id)) + 1 : 1;
        setRows([...rows, { id: nextId, ...data }]);
      } else if (item) {
        setRows(rows.map((r) => (r.id === item.id ? { ...r, ...data } : r)));
      }
      close();
    };
    return (
      <FormProvider {...methods}>
        <form onSubmit={methods.handleSubmit(onSubmit)}>
          <Stack spacing={2}>
            <FormTextField name="name" label="Name" />
            <FormTextField name="email" label="Email" />
            <Button type="submit">
              {mode === "create" ? "Create" : "Save"}
            </Button>
          </Stack>
        </form>
      </FormProvider>
    );
  };

  return (
    <CrudPage<Row>
      title="Users"
      rows={rows}
      columns={({ openEdit }) => [
        ...baseColumns,
        actionsColumn<Row>({
          onEdit: (r) => openEdit(r),
          onDelete: (r) => setRows(rows.filter((x) => x.id !== r.id)),
        }),
      ]}
      renderForm={({ mode, item, close }) => (
        <Form mode={mode} {...(item && { item })} close={close} />
      )}
    />
  );
};
