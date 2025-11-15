import type { Meta, StoryObj } from '@storybook/react';
import { SelectField } from '@/shared/components/ui/forms/form/SelectField';
import { Stack, Box } from '@mui/material';

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
    placeholder: {
      control: { type: 'text' },
      description: 'Placeholder text'
    },
    helperText: {
      control: { type: 'text' },
      description: 'Helper text below the field'
    },
    error: {
      control: { type: 'boolean' },
      description: 'Whether the field is in error state'
    },
    disabled: {
      control: { type: 'boolean' },
      description: 'Whether the field is disabled'
    },
    required: {
      control: { type: 'boolean' },
      description: 'Whether the field is required'
    },
    multiple: {
      control: { type: 'boolean' },
      description: 'Whether multiple selections are allowed'
    }
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
    label: 'Select Option',
    options,
  },
};

export const States: Story = {
  render: () => (
    <Stack spacing={3} sx={{ width: 300 }}>
      <SelectField label="Normal" options={options} />
      <SelectField label="With Helper Text" options={options} helperText="Choose an option" />
      <SelectField label="Error State" options={options} error helperText="This field has an error" />
      <SelectField label="Disabled" options={options} disabled />
      <SelectField label="Required" options={options} required />
    </Stack>
  ),
};

export const Multiple: Story = {
  render: () => (
    <Box sx={{ width: 300 }}>
      <SelectField 
        label="Multiple Selection" 
        options={options} 
        multiple 
        helperText="Hold Ctrl/Cmd to select multiple options"
      />
    </Box>
  ),
};

export const WithGroups: Story = {
  render: () => {
    const groupedOptions = [
      { value: 'fruits', label: 'Fruits', group: 'Category 1' },
      { value: 'apple', label: 'Apple', group: 'Category 1' },
      { value: 'banana', label: 'Banana', group: 'Category 1' },
      { value: 'vegetables', label: 'Vegetables', group: 'Category 2' },
      { value: 'carrot', label: 'Carrot', group: 'Category 2' },
      { value: 'broccoli', label: 'Broccoli', group: 'Category 2' },
    ];
    
    return (
      <Box sx={{ width: 300 }}>
        <SelectField 
          label="Grouped Options" 
          options={groupedOptions}
          helperText="Options are grouped by category"
        />
      </Box>
    );
  },
};
