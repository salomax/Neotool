import type { Meta, StoryObj } from '@storybook/react';
import { FormSkeleton } from '@/shared/components/ui/forms/FormSkeleton';
import { Stack, Box, Typography } from '@mui/material';

const meta: Meta<typeof FormSkeleton> = {
  title: 'Components/Forms/FormSkeleton',
  component: FormSkeleton,
  parameters: {
    layout: 'fullscreen',
    docs: {
      description: {
        component: 'Form skeleton component for displaying loading states of forms with configurable number of fields, optional title, and action buttons.',
      },
    },
  },
  argTypes: {
    fields: {
      control: { type: 'number' },
      description: 'Number of form fields to display',
    },
    showTitle: {
      control: { type: 'boolean' },
      description: 'Whether to show the title skeleton',
    },
    showActions: {
      control: { type: 'boolean' },
      description: 'Whether to show the action buttons skeleton',
    },
  },
  tags: ['autodocs'],
};

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  args: {
    fields: 4,
    showTitle: true,
    showActions: true,
  },
};

export const WithoutTitle: Story = {
  args: {
    fields: 4,
    showTitle: false,
    showActions: true,
  },
};

export const WithoutActions: Story = {
  args: {
    fields: 4,
    showTitle: true,
    showActions: false,
  },
};

export const Minimal: Story = {
  args: {
    fields: 3,
    showTitle: false,
    showActions: false,
  },
};

