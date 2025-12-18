import React from "react";
import type { Meta, StoryObj } from "@storybook/react";
import { Stack } from "@mui/material";
import { z } from "zod";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm, FormProvider } from "react-hook-form";
import type { ColDef } from "ag-grid-community";
import { CrudPage } from "../CrudPage";
import { Button } from "@/shared/components/ui/primitives/Button";
import { FormTextField } from "@/shared/components/ui/forms/form/FormField";

type Row = { id: number; name: string; email: string };

const columns: ColDef<Row>[] = [
  { headerName: "ID", field: "id", maxWidth: 100 },
  { headerName: "Name", field: "name" },
  { headerName: "Email", field: "email" },
];

const initialRows: Row[] = Array.from({ length: 20 }, (_, i) => ({
  id: i + 1,
  name: `User ${i + 1}`,
  email: `user${i + 1}@example.com`,
}));

const schema = z.object({
  name: z.string().min(2, "Name must have at least 2 characters"),
  email: z.string().email("Invalid email"),
});
type FormData = z.infer<typeof schema>;

const meta: Meta<typeof CrudPage<Row>> = {
  title: "Templates/CrudPage",
  component: CrudPage<Row>,
  parameters: { layout: "fullscreen" },
};
export default meta;

type Story = StoryObj<typeof CrudPage<Row>>;

export const Default: Story = {
  render: () => <CrudPageDemo />,
};

const CrudPageDemo: React.FC = () => {
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
      columns={columns}
      rows={rows}
      renderForm={({ mode, item, close }) => (
        <Form mode={mode} {...(item && { item })} close={close} />
      )}
    />
  );
};
