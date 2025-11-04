import type { Meta, StoryObj } from '@storybook/react';
import { PageSkeleton } from '@/shared/components/ui/primitives/PageSkeleton';
import { Box, Stack } from '@mui/material';

const meta: Meta<typeof PageSkeleton> = {
  title: 'Components/Primitives/PageSkeleton',
  component: PageSkeleton,
  parameters: {
    layout: 'fullscreen',
    docs: {
      description: {
        component: 'Page skeleton component for displaying a loading placeholder pattern for generic page layouts.',
      },
    },
  },
  tags: ['autodocs'],
};

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const MultiplePages: Story = {
  render: () => (
    <Box sx={{ p: 3 }}>
      <Stack spacing={3}>
        <PageSkeleton />
        <PageSkeleton />
        <PageSkeleton />
      </Stack>
    </Box>
  ),
  parameters: {
    docs: {
      source: {
        code: `
<Box sx={{ p: 3 }}>
  <Stack spacing={3}>
    <PageSkeleton />
    <PageSkeleton />
    <PageSkeleton />
  </Stack>
</Box>`,
      },
    },
  },
};

