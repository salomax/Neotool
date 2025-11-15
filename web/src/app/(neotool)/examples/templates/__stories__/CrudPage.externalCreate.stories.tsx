import React from "react";
import type { Meta, StoryObj } from "@storybook/react";
import type { ColDef } from "ag-grid-community";
import { CrudPage } from "../CrudPage";
import { actionsColumn } from "@/shared/components/ui/patterns/actions";
import { Dialog, DialogTitle, DialogContent, Stack } from "@mui/material";
import { Button } from "@/shared/components/ui/primitives/Button";
import { useForm, FormProvider } from "react-hook-form";
import { z } from "zod";
import { zodResolver } from "@hookform/resolvers/zod";
import { FormTextField } from "@/shared/components/ui/forms/form/FormField";
import "ag-grid-community/styles/ag-grid.css";
import "ag-grid-community/styles/ag-theme-quartz.css";

type Row = { id: number; name: string; email: string };

const columnsBase: ColDef<Row>[] = [
  { headerName: "ID", field: "id", maxWidth: 100 },
  { headerName: "Name", field: "name" },
  { headerName: "Email", field: "email" },
];

const schema = z.object({
  name: z.string().min(2, "Name must have at least 2 characters"),
  email: z.string().email("Invalid email"),
});
type FormData = z.infer<typeof schema>;

const meta: Meta<any> = {
  title: "Templates/CrudPage (External onCreate)",
  parameters: { layout: "fullscreen" },
};
export default meta;

export const Default: StoryObj = {
  render: () => <ExternalCreateDemo />,
};

const FormComponent: React.FC<{ item?: Row; onClose: () => void }> = ({ item, onClose }) => {
  const methods = useForm<FormData>({
    resolver: zodResolver(schema),
    defaultValues: { name: item?.name ?? "", email: item?.email ?? "" },
  });
  const onSubmit = (_data: FormData) => {
    /* demo only */ onClose();
  };
  return (
    <FormProvider {...methods}>
      <form onSubmit={methods.handleSubmit(onSubmit)}>
        <Stack spacing={2}>
          <FormTextField name="name" label="Name" />
          <FormTextField name="email" label="Email" />
          <Button type="submit">Save</Button>
        </Stack>
      </form>
    </FormProvider>
  );
};

const ExternalCreateDemo: React.FC = () => {
  const [rows, setRows] = React.useState<Row[]>(
    Array.from({ length: 6 }, (_, i) => ({
      id: i + 1,
      name: `User ${i + 1}`,
      email: `user${i + 1}@example.com`,
    })),
  );
  const [open, setOpen] = React.useState(false);

  const Form: React.FC<{ onSuccess: (_r: Row) => void }> = ({ onSuccess }) => {
    const methods = useForm<FormData>({
      resolver: zodResolver(schema),
      defaultValues: { name: "", email: "" },
    });
    const onSubmit = (data: FormData) => {
      const nextId = rows.length ? Math.max(...rows.map((r) => r.id)) + 1 : 1;
      onSuccess({ id: nextId, ...data });
      setOpen(false);
    };
    return (
      <FormProvider {...methods}>
        <form onSubmit={methods.handleSubmit(onSubmit)}>
          <Stack spacing={2}>
            <FormTextField name="name" label="Name" />
            <FormTextField name="email" label="Email" />
            <Button type="submit">Create</Button>
          </Stack>
        </form>
      </FormProvider>
    );
  };

  return (
    <>
      <CrudPage<Row>
        title="Users"
        rows={rows}
        columns={({ openEdit }) => [
          ...columnsBase,
          actionsColumn<Row>({
            onEdit: (row) => openEdit(row),
            onDelete: (row) => setRows(rows.filter((x) => x.id !== row.id)),
          }),
        ]}
        onCreate={() => setOpen(true)} // external create
        renderForm={({ mode: _mode, item, close }) => (
          <FormComponent {...(item && { item })} onClose={close} />
        )}
      />

      <Dialog open={open} onClose={() => setOpen(false)}>
        <DialogTitle>New User</DialogTitle>
        <DialogContent>
          <Form onSuccess={(r) => setRows([...rows, r])} />
        </DialogContent>
      </Dialog>
    </>
  );
};
