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
        component: 'Loading spinner component for displaying loading states.',
      },
    },
  },
  argTypes: {
    message: {
      control: { type: 'text' },
      description: 'Accessible label for the loader (not rendered as visible text)',
    },
  },
  tags: ['autodocs'],
};

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  args: {
    message: 'Loading',
  },
};

export const CustomMessage: Story = {
  args: {
    message: 'Loading application',
  },
};

export const NoMessage: Story = {
  args: {
    message: undefined,
  },
};
