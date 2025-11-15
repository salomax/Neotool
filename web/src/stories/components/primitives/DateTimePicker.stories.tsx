import type { Meta, StoryObj } from '@storybook/react';
import { DateTimePicker } from '@/shared/components/ui/primitives/DateTimePicker';
import { Stack, Box, Typography } from '@mui/material';
import { useState } from 'react';
import dayjs, { Dayjs } from 'dayjs';

const meta: Meta<typeof DateTimePicker> = {
  title: 'Components/Primitives/DateTimePicker',
  component: DateTimePicker,
  parameters: {
    layout: 'centered',
    docs: {
      description: {
        component: 'Date and time picker component for selecting dates and times.'
      }
    }
  },
  argTypes: {
    variant: {
      control: { type: 'select' },
      options: ['standard', 'outlined', 'filled'],
      description: 'Variant of the input'
    },
    size: {
      control: { type: 'select' },
      options: ['small', 'medium'],
      description: 'Size of the picker'
    },
    disabled: {
      control: { type: 'boolean' },
      description: 'Whether the picker is disabled'
    }
  },
  tags: ['autodocs'],
};

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  render: () => {
    const [value, setValue] = useState<Dayjs | null>(dayjs());
    
    return (
      <Box sx={{ width: 300 }}>
        <DateTimePicker
          label="Select date and time"
          value={value}
          onChange={(newValue) => setValue(newValue)}
        />
        {value && (
          <Typography variant="caption" color="text.secondary" sx={{ mt: 1, display: 'block' }}>
            Selected: {value.format('YYYY-MM-DD HH:mm')}
          </Typography>
        )}
      </Box>
    );
  },
};

export const DateOnly: Story = {
  render: () => {
    const [value, setValue] = useState<Dayjs | null>(dayjs());
    
    return (
      <Box sx={{ width: 300 }}>
        <DateTimePicker
          label="Select date"
          value={value}
          onChange={(newValue) => setValue(newValue)}
          showTime={false}
        />
      </Box>
    );
  },
  parameters: {
    docs: {
      source: {
        code: `
<DateTimePicker
  label="Select date"
  value={value}
  onChange={setValue}
  showTime={false}
/>`,
      },
    },
  },
};

export const TimeOnly: Story = {
  render: () => {
    const [value, setValue] = useState<Dayjs | null>(dayjs());
    
    return (
      <Box sx={{ width: 300 }}>
        <DateTimePicker
          label="Select time"
          value={value}
          onChange={(newValue) => setValue(newValue)}
          showDate={false}
        />
      </Box>
    );
  },
  parameters: {
    docs: {
      source: {
        code: `
<DateTimePicker
  label="Select time"
  value={value}
  onChange={setValue}
  showDate={false}
/>`,
      },
    },
  },
};

export const Variants: Story = {
  render: () => {
    const [value1, setValue1] = useState<Dayjs | null>(dayjs());
    const [value2, setValue2] = useState<Dayjs | null>(dayjs());
    const [value3, setValue3] = useState<Dayjs | null>(dayjs());
    
    return (
      <Stack spacing={3} sx={{ width: 300 }}>
        <DateTimePicker
          variant="standard"
          label="Standard"
          value={value1}
          onChange={(newValue) => setValue1(newValue)}
        />
        <DateTimePicker
          variant="outlined"
          label="Outlined"
          value={value2}
          onChange={(newValue) => setValue2(newValue)}
        />
        <DateTimePicker
          variant="filled"
          label="Filled"
          value={value3}
          onChange={(newValue) => setValue3(newValue)}
        />
      </Stack>
    );
  },
  parameters: {
    docs: {
      source: {
        code: `
<DateTimePicker variant="standard" label="Standard" value={value} onChange={setValue} />
<DateTimePicker variant="outlined" label="Outlined" value={value} onChange={setValue} />
<DateTimePicker variant="filled" label="Filled" value={value} onChange={setValue} />`,
      },
    },
  },
};

export const Sizes: Story = {
  render: () => {
    const [value1, setValue1] = useState<Dayjs | null>(dayjs());
    const [value2, setValue2] = useState<Dayjs | null>(dayjs());
    
    return (
      <Stack spacing={3} sx={{ width: 300 }}>
        <DateTimePicker
          size="small"
          label="Small"
          value={value1}
          onChange={(newValue) => setValue1(newValue)}
        />
        <DateTimePicker
          size="medium"
          label="Medium"
          value={value2}
          onChange={(newValue) => setValue2(newValue)}
        />
      </Stack>
    );
  },
  parameters: {
    docs: {
      source: {
        code: `
<DateTimePicker size="small" label="Small" value={value} onChange={setValue} />
<DateTimePicker size="medium" label="Medium" value={value} onChange={setValue} />`,
      },
    },
  },
};

export const WithConstraints: Story = {
  render: () => {
    const [value, setValue] = useState<Dayjs | null>(dayjs());
    
    return (
      <Box sx={{ width: 300 }}>
        <DateTimePicker
          label="Future dates only"
          value={value}
          onChange={(newValue) => setValue(newValue)}
          disablePast
          helperText="Only future dates are allowed"
        />
      </Box>
    );
  },
  parameters: {
    docs: {
      source: {
        code: `
<DateTimePicker
  label="Future dates only"
  value={value}
  onChange={setValue}
  disablePast
  helperText="Only future dates are allowed"
/>`,
      },
    },
  },
};

export const Disabled: Story = {
  render: () => (
    <Box sx={{ width: 300 }}>
      <DateTimePicker
        label="Disabled"
        value={dayjs()}
        disabled
      />
    </Box>
  ),
  parameters: {
    docs: {
      source: {
        code: `
<DateTimePicker
  label="Disabled"
  value={dayjs()}
  disabled
/>`,
      },
    },
  },
};

export const WithError: Story = {
  render: () => {
    const [value, setValue] = useState<Dayjs | null>(null);
    
    return (
      <Box sx={{ width: 300 }}>
        <DateTimePicker
          label="Required field"
          value={value}
          onChange={(newValue) => setValue(newValue)}
          required
          error={!value}
          errorMessage={!value ? "This field is required" : undefined}
        />
      </Box>
    );
  },
  parameters: {
    docs: {
      source: {
        code: `
<DateTimePicker
  label="Required field"
  value={value}
  onChange={setValue}
  required
  error={!value}
  errorMessage={!value ? "This field is required" : undefined}
/>`,
      },
    },
  },
};

