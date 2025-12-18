import type { Meta, StoryObj } from '@storybook/react';
import { SelectField } from '@/shared/components/ui/forms/form/SelectField';
import { Stack, Box } from '@mui/material';
import { FormProvider, useForm } from 'react-hook-form';

const meta: Meta<typeof SelectField> = {
  title: 'Components/Forms/Select',
  component: SelectField,
  parameters: {
    layout: 'centered',
    docs: {
      description: {
        component: 'Select component for choosing from a list of options.'
      }
    }
  },
  argTypes: {
    label: {
      control: { type: 'text' },
      description: 'Label for the select field'
    },
    helperText: {
      control: { type: 'text' },
      description: 'Helper text below the field'
    },
  },
  tags: ['autodocs'],
};

export default meta;
type Story = StoryObj<typeof meta>;

const options = [
  { value: 'option1', label: 'Option 1' },
  { value: 'option2', label: 'Option 2' },
  { value: 'option3', label: 'Option 3' },
  { value: 'option4', label: 'Option 4' },
];

export const Default: Story = {
  args: {
    name: 'select',
    label: 'Select Option',
    options,
  },
};

const StatesWrapper = () => {
  const methods = useForm();
  return (
    <FormProvider {...methods}>
      <Stack spacing={3} sx={{ width: 300 }}>
        <SelectField name="normal" label="Normal" options={options} />
        <SelectField name="helper" label="With Helper Text" options={options} helperText="Choose an option" />
        <SelectField name="error" label="Error State" options={options} helperText="This field has an error" />
        <SelectField name="disabled" label="Disabled" options={options} />
        <SelectField name="required" label="Required" options={options} />
      </Stack>
    </FormProvider>
  );
};

const MultipleWrapper = () => {
  const methods = useForm();
  return (
    <FormProvider {...methods}>
      <Box sx={{ width: 300 }}>
        <SelectField 
          name="multiple"
          label="Multiple Selection" 
          options={options} 
          helperText="Select an option"
        />
      </Box>
    </FormProvider>
  );
};

const WithGroupsWrapper = () => {
  const groupedOptions = [
    { value: 'fruits', label: 'Fruits', group: 'Category 1' },
    { value: 'apple', label: 'Apple', group: 'Category 1' },
    { value: 'banana', label: 'Banana', group: 'Category 1' },
    { value: 'vegetables', label: 'Vegetables', group: 'Category 2' },
    { value: 'carrot', label: 'Carrot', group: 'Category 2' },
    { value: 'broccoli', label: 'Broccoli', group: 'Category 2' },
  ];
  
  const methods = useForm();
  return (
    <FormProvider {...methods}>
      <Box sx={{ width: 300 }}>
        <SelectField 
          name="grouped"
          label="Grouped Options" 
          options={groupedOptions}
          helperText="Options are grouped by category"
        />
      </Box>
    </FormProvider>
  );
};

export const States: Story = {
  render: () => <StatesWrapper />,
};

export const Multiple: Story = {
  render: () => <MultipleWrapper />,
};

export const WithGroups: Story = {
  render: () => <WithGroupsWrapper />,
};
