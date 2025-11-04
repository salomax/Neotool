import type { Meta, StoryObj } from '@storybook/react';
import { DateRangePicker } from '@/shared/components/ui/primitives/DateRangePicker';
import { Box, Typography } from '@mui/material';
import { useState } from 'react';
import dayjs, { Dayjs } from 'dayjs';

type DateRange = {
  start: Dayjs | null;
  end: Dayjs | null;
};

const meta: Meta<typeof DateRangePicker> = {
  title: 'Components/Primitives/DateRangePicker',
  component: DateRangePicker,
  parameters: {
    layout: 'centered',
    docs: {
      description: {
        component: 'Date range picker component for selecting a range of dates.'
      }
    }
  },
  argTypes: {
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
    const [value, setValue] = useState<DateRange | null>({
      start: dayjs().subtract(7, 'days'),
      end: dayjs(),
    });
    
    return (
      <Box sx={{ width: 400 }}>
        <DateRangePicker
          label="Select date range"
          value={value}
          onChange={(newValue) => setValue(newValue)}
        />
        {value && (value.start || value.end) && (
          <Typography variant="caption" color="text.secondary" sx={{ mt: 1, display: 'block' }}>
            Selected: {value.start?.format('YYYY-MM-DD') || '...'} to {value.end?.format('YYYY-MM-DD') || '...'}
          </Typography>
        )}
      </Box>
    );
  },
  parameters: {
    docs: {
      source: {
        code: `
<DateRangePicker
  label="Select date range"
  value={value}
  onChange={(newValue) => setValue(newValue)}
/>`,
      },
    },
  },
};

export const Empty: Story = {
  render: () => {
    const [value, setValue] = useState<DateRange | null>(null);
    
    return (
      <Box sx={{ width: 400 }}>
        <DateRangePicker
          label="Select date range"
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
<DateRangePicker
  label="Select date range"
  value={value}
  onChange={(newValue) => setValue(newValue)}
/>`,
      },
    },
  },
};

export const CustomPlaceholder: Story = {
  render: () => {
    const [value, setValue] = useState<DateRange | null>({
      start: dayjs().subtract(30, 'days'),
      end: dayjs(),
    });
    
    return (
      <Box sx={{ width: 400 }}>
        <DateRangePicker
          label="Booking Period"
          placeholder="Select check-in and check-out dates"
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
<DateRangePicker
  label="Booking Period"
  placeholder="Select check-in and check-out dates"
  value={value}
  onChange={(newValue) => setValue(newValue)}
/>`,
      },
    },
  },
};

export const WithConstraints: Story = {
  render: () => {
    const [value, setValue] = useState<DateRange | null>(null);
    
    return (
      <Box sx={{ width: 400 }}>
        <DateRangePicker
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
<DateRangePicker
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

export const WithMinMax: Story = {
  render: () => {
    const [value, setValue] = useState<DateRange | null>(null);
    
    return (
      <Box sx={{ width: 400 }}>
        <DateRangePicker
          label="Select date range"
          value={value}
          onChange={(newValue) => setValue(newValue)}
          minDate={dayjs().subtract(30, 'days')}
          maxDate={dayjs().add(30, 'days')}
          helperText="Select a date within the next 30 days"
        />
      </Box>
    );
  },
  parameters: {
    docs: {
      source: {
        code: `
<DateRangePicker
  label="Select date range"
  value={value}
  onChange={setValue}
  minDate={dayjs().subtract(30, 'days')}
  maxDate={dayjs().add(30, 'days')}
  helperText="Select a date within the next 30 days"
/>`,
      },
    },
  },
};

export const Disabled: Story = {
  render: () => (
    <Box sx={{ width: 600 }}>
      <DateRangePicker
        label="Disabled"
        value={{
          start: dayjs().subtract(7, 'days'),
          end: dayjs(),
        }}
        disabled
      />
    </Box>
  ),
  parameters: {
    docs: {
      source: {
        code: `
<DateRangePicker
  label="Disabled"
  value={{
    start: dayjs().subtract(7, 'days'),
    end: dayjs(),
  }}
  disabled
/>`,
      },
    },
  },
};

export const WithError: Story = {
  render: () => {
    const [value, setValue] = useState<DateRange | null>(null);
    
    return (
      <Box sx={{ width: 400 }}>
        <DateRangePicker
          label="Required field"
          value={value}
          onChange={(newValue) => setValue(newValue)}
          required
          error={!value || !value.start || !value.end}
          errorMessage={(!value || !value.start || !value.end) ? "Please select both start and end dates" : undefined}
        />
      </Box>
    );
  },
  parameters: {
    docs: {
      source: {
        code: `
<DateRangePicker
  label="Required field"
  value={value}
  onChange={setValue}
  required
  error={!value || !value.start || !value.end}
  errorMessage={(!value || !value.start || !value.end) ? "Please select both start and end dates" : undefined}
/>`,
      },
    },
  },
};

export const CustomFormat: Story = {
  render: () => {
    const [value, setValue] = useState<DateRange | null>({
      start: dayjs().subtract(7, 'days'),
      end: dayjs(),
    });
    
    return (
      <Box sx={{ width: 400 }}>
        <DateRangePicker
          label="Select date range"
          value={value}
          onChange={(newValue) => setValue(newValue)}
          format="MM/DD/YYYY"
          helperText="Using MM/DD/YYYY format"
        />
      </Box>
    );
  },
  parameters: {
    docs: {
      source: {
        code: `
<DateRangePicker
  label="Select date range"
  value={value}
  onChange={setValue}
  format="MM/DD/YYYY"
  helperText="Using MM/DD/YYYY format"
/>`,
      },
    },
  },
};

