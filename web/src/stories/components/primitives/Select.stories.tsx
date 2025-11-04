import type { Meta, StoryObj } from '@storybook/react';
import { Select } from '@/shared/components/ui/primitives/Select';
import { Stack, Box, Typography } from '@mui/material';
import { useState } from 'react';

const meta: Meta<typeof Select> = {
  title: 'Components/Primitives/Select',
  component: Select,
  parameters: {
    layout: 'centered',
    docs: {
      description: {
        component: 'Select component for choosing an option from a dropdown menu.'
      }
    }
  },
  argTypes: {
    disabled: {
      control: { type: 'boolean' },
      description: 'Whether the select is disabled'
    }
  },
  tags: ['autodocs'],
};

export default meta;
type Story = StoryObj<typeof meta>;

const options = [
  { label: 'Option 1', value: '1' },
  { label: 'Option 2', value: '2' },
  { label: 'Option 3', value: '3' },
  { label: 'Option 4', value: '4' },
  { label: 'Option 5', value: '5' },
];

export const Default: Story = {
  render: () => {
    const [value, setValue] = useState<string | number | null>(null);
    
    return (
      <Box sx={{ width: 200 }}>
        <Select
          label="Choose an option"
          options={options}
          value={value}
          onChange={(newValue) => setValue(newValue)}
        />
      </Box>
    );
  },
};

export const WithValue: Story = {
  render: () => {
    const [value, setValue] = useState<string | number | null>('2');
    
    return (
      <Box sx={{ width: 200 }}>
        <Typography gutterBottom>Selected: {value ?? '(none)'}</Typography>
        <Select
          label="Choose an option"
          options={options}
          value={value}
          onChange={(newValue) => setValue(newValue)}
          allowClear
        />
      </Box>
    );
  },
  parameters: {
    docs: {
      source: {
        code: `
<Select
  label="Choose an option"
  options={options}
  value="2"
  onChange={(newValue) => setValue(newValue)}
/>`,
      },
    },
  },
};

export const WithNumericValues: Story = {
  render: () => {
    const [value, setValue] = useState<number | null>(1);
    const numericOptions = [
      { label: 'One', value: 1 },
      { label: 'Two', value: 2 },
      { label: 'Three', value: 3 },
      { label: 'Four', value: 4 },
    ];
    
    return (
      <Box sx={{ width: 200 }}>
        <Typography gutterBottom>Selected: {value ?? '(none)'}</Typography>
        <Select
          label="Choose a number"
          options={numericOptions}
          value={value}
          onChange={(newValue) => setValue(newValue as number | null)}
          allowClear
        />
      </Box>
    );
  },
  parameters: {
    docs: {
      source: {
        code: `
<Select
  label="Choose a number"
  options={numericOptions}
  value={1}
  onChange={(newValue) => setValue(newValue)}
/>`,
      },
    },
  },
};

export const Disabled: Story = {
  render: () => (
    <Box sx={{ width: 200 }}>
      <Select
        label="Disabled select"
        options={options}
        value="1"
        disabled
      />
    </Box>
  ),
  parameters: {
    docs: {
      source: {
        code: `
<Select
  label="Disabled select"
  options={options}
  value="1"
  disabled
/>`,
      },
    },
  },
};

export const WithoutLabel: Story = {
  render: () => {
    const [value, setValue] = useState<string | number | null>(null);
    
    return (
      <Box sx={{ width: 200 }}>
        <Select
          options={options}
          value={value}
          onChange={(newValue) => setValue(newValue)}
        />
      </Box>
    );
  },
  parameters: {
    docs: {
      source: {
        code: `
<Select
  options={options}
  value={value}
  onChange={(newValue) => setValue(newValue)}
/>`,
      },
    },
  },
};

export const WithClear: Story = {
  render: () => {
    const [value, setValue] = useState<string | number | null>('2');
    
    return (
      <Box sx={{ width: 200 }}>
        <Typography gutterBottom>Selected: {value ?? '(none)'}</Typography>
        <Select
          label="Choose an option"
          options={options}
          value={value}
          onChange={(newValue) => setValue(newValue)}
          allowClear
          clearLabel="Clear selection"
        />
      </Box>
    );
  },
  parameters: {
    docs: {
      source: {
        code: `
<Select
  label="Choose an option"
  options={options}
  value={value}
  onChange={(newValue) => setValue(newValue)}
  allowClear
  clearLabel="Clear selection"
/>`,
      },
    },
  },
};

