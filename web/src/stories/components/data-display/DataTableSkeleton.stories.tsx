import type { Meta, StoryObj } from '@storybook/react';
import { DataTableSkeleton } from '@/shared/components/ui/data-display/DataTableSkeleton';
import { Stack, Box, Typography } from '@mui/material';

const meta: Meta<typeof DataTableSkeleton> = {
  title: 'Components/Data Display/DataTableSkeleton',
  component: DataTableSkeleton,
  parameters: {
    layout: 'fullscreen',
    docs: {
      description: {
        component: 'Data table skeleton component for displaying loading states of data tables with optional toolbar and pagination placeholders.',
      },
    },
  },
  argTypes: {
    rows: {
      control: { type: 'number' },
      description: 'Number of rows to display',
    },
    columns: {
      control: { type: 'number' },
      description: 'Number of columns to display',
    },
    showToolbar: {
      control: { type: 'boolean' },
      description: 'Whether to show the toolbar skeleton',
    },
    showPagination: {
      control: { type: 'boolean' },
      description: 'Whether to show the pagination skeleton',
    },
  },
  tags: ['autodocs'],
};

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  args: {
    rows: 8,
    columns: 5,
    showToolbar: true,
    showPagination: true,
  },
};

export const WithoutToolbar: Story = {
  args: {
    rows: 8,
    columns: 5,
    showToolbar: false,
    showPagination: true,
  },
};

export const WithoutPagination: Story = {
  args: {
    rows: 8,
    columns: 5,
    showToolbar: true,
    showPagination: false,
  },
};

export const Minimal: Story = {
  args: {
    rows: 5,
    columns: 3,
    showToolbar: false,
    showPagination: false,
  },
};

