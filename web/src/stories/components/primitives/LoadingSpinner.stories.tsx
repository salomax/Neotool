import type { Meta, StoryObj } from '@storybook/react';
import { LoadingSpinner } from '@/shared/components/ui/primitives/LoadingSpinner';
import { Stack, Box } from '@mui/material';

const meta: Meta<typeof LoadingSpinner> = {
  title: 'Components/Primitives/LoadingSpinner',
  component: LoadingSpinner,
  parameters: {
    layout: 'centered',
    docs: {
      description: {
        component: 'Loading spinner component for displaying loading states with an optional customizable message.',
      },
    },
  },
  argTypes: {
    message: {
      control: { type: 'text' },
      description: 'The message to display below the spinner',
    },
  },
  tags: ['autodocs'],
};

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  args: {
    message: 'Loading...',
  },
};

export const CustomMessage: Story = {
  args: {
    message: 'Please wait...',
  },
};

export const NoMessage: Story = {
  args: {
    message: '',
  },
};

