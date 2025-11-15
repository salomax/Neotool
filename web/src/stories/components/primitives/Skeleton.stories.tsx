import type { Meta, StoryObj } from '@storybook/react';
import { Skeleton } from '@/shared/components/ui/primitives/Skeleton';
import { Stack, Box, Typography } from '@mui/material';

const meta: Meta<typeof Skeleton> = {
  title: 'Components/Primitives/Skeleton',
  component: Skeleton,
  parameters: {
    layout: 'padded',
    docs: {
      description: {
        component: 'Skeleton component for displaying loading placeholders with multiple variants including text, rectangular, circular, table, form, card, and list layouts.',
      },
    },
  },
  argTypes: {
    variant: {
      control: { type: 'select' },
      options: ['text', 'rectangular', 'circular', 'table', 'form', 'card', 'list'],
      description: 'The type of skeleton to display',
    },
    width: {
      control: { type: 'text' },
      description: 'Width of the skeleton',
    },
    height: {
      control: { type: 'text' },
      description: 'Height of the skeleton',
    },
    animation: {
      control: { type: 'select' },
      options: ['pulse', 'wave', false],
      description: 'Animation style for the skeleton',
    },
  },
  tags: ['autodocs'],
};

export default meta;
type Story = StoryObj<typeof meta>;

export const Rectangular: Story = {
  args: {
    variant: 'rectangular',
    width: 300,
    height: 100,
  },
};

export const Circular: Story = {
  args: {
    variant: 'circular',
    width: 60,
    height: 60,
  },
};

export const Text: Story = {
  args: {
    variant: 'text',
    width: '100%',
  },
};

export const Table: Story = {
  args: {
    variant: 'table',
    rows: 5,
    columns: 4,
  },
};

export const Form: Story = {
  args: {
    variant: 'form',
    rows: 4,
  },
};

export const Card: Story = {
  args: {
    variant: 'card',
  },
};

export const List: Story = {
  args: {
    variant: 'list',
    rows: 5,
  },
};

export const Animations: Story = {
  render: () => (
    <Stack spacing={3} sx={{ width: 300 }}>
      <Box>
        <Typography variant="subtitle2" gutterBottom>Pulse</Typography>
        <Skeleton variant="rectangular" width={300} height={60} animation="pulse" />
      </Box>
      <Box>
        <Typography variant="subtitle2" gutterBottom>Wave</Typography>
        <Skeleton variant="rectangular" width={300} height={60} animation="wave" />
      </Box>
      <Box>
        <Typography variant="subtitle2" gutterBottom>No Animation</Typography>
        <Skeleton variant="rectangular" width={300} height={60} animation={false} />
      </Box>
    </Stack>
  ),
  parameters: {
    docs: {
      source: {
        code: `
<Stack spacing={3} sx={{ width: 300 }}>
  <Box>
    <Typography variant="subtitle2" gutterBottom>Pulse</Typography>
    <Skeleton variant="rectangular" width={300} height={60} animation="pulse" />
  </Box>
  <Box>
    <Typography variant="subtitle2" gutterBottom>Wave</Typography>
    <Skeleton variant="rectangular" width={300} height={60} animation="wave" />
  </Box>
  <Box>
    <Typography variant="subtitle2" gutterBottom>No Animation</Typography>
    <Skeleton variant="rectangular" width={300} height={60} animation={false} />
  </Box>
</Stack>`,
      },
    },
  },
};

