import type { Meta, StoryObj } from '@storybook/react';
import { Badge } from '@/shared/components/ui/primitives/Badge';
import { Stack } from '@mui/material';

const meta: Meta<typeof Badge> = {
  title: 'Components/Primitives/Badge',
  component: Badge,
  parameters: {
    layout: 'centered',
    docs: {
      description: {
        component: 'Badge component (Chip wrapper) for displaying status indicators, labels, and categories.'
      },
    },
  },
  argTypes: {
    label: {
      control: { type: 'text' },
      description: 'The content of the badge'
    },
    color: {
      control: { type: 'select' },
      options: ['default', 'success', 'warning', 'error', 'info'],
      description: 'Color of the badge'
    },
    size: {
      control: { type: 'select' },
      options: ['small', 'medium', 'large'],
      description: 'Size of the badge'
    },
    variant: {
      control: { type: 'select' },
      options: ['outlined', 'filled'],
      description: 'Variant of the badge'
    },
    disabled: {
      control: { type: 'boolean' },
      description: 'Whether the badge is disabled'
    }
  },
  tags: ['autodocs'],
};

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  args: {
    label: 'Default',
  },
};

export const Colors: Story = {
  render: () => (
    <Stack direction="row" spacing={2} flexWrap="wrap" alignItems="center">
      <Badge label="Default" color="default" />
      <Badge label="Success" color="success" />
      <Badge label="Warning" color="warning" />
      <Badge label="Error" color="error" />
      <Badge label="Info" color="info" />
    </Stack>
  ),
  parameters: {
    docs: {
      source: {
        code: `
<Stack direction="row" spacing={2} flexWrap="wrap" alignItems="center">
  <Badge label="Default" color="default" />
  <Badge label="Success" color="success" />
  <Badge label="Warning" color="warning" />
  <Badge label="Error" color="error" />
  <Badge label="Info" color="info" />
</Stack>`,
      },
    },
  },
};

export const Sizes: Story = {
  render: () => (
    <Stack direction="row" spacing={2} alignItems="center">
      <Badge label="Small" size="small" />
      <Badge label="Medium" size="medium" />
      <Badge label="Large" size="large" />
    </Stack>
  ),
  parameters: {
    docs: {
      source: {
        code: `
<Stack direction="row" spacing={2} alignItems="center">
  <Badge label="Small" size="small" />
  <Badge label="Medium" size="medium" />
  <Badge label="Large" size="large" />
</Stack>`,
      },
    },
  },
};

export const Variants: Story = {
  render: () => (
    <Stack direction="row" spacing={2} flexWrap="wrap" alignItems="center">
      <Badge label="Outlined" variant="outlined" />
      <Badge label="Filled" variant="filled" />
      <Badge label="Outlined Success" variant="outlined" color="success" />
      <Badge label="Filled Error" variant="filled" color="error" />
    </Stack>
  ),
  parameters: {
    docs: {
      source: {
        code: `
<Stack direction="row" spacing={2} flexWrap="wrap" alignItems="center">
  <Badge label="Outlined" variant="outlined" />
  <Badge label="Filled" variant="filled" />
  <Badge label="Outlined Success" variant="outlined" color="success" />
  <Badge label="Filled Error" variant="filled" color="error" />
</Stack>`,
      },
    },
  },
};

export const States: Story = {
  render: () => (
    <Stack direction="row" spacing={2} flexWrap="wrap" alignItems="center">
      <Badge label="Normal" />
      <Badge label="Disabled" disabled />
    </Stack>
  ),
  parameters: {
    docs: {
      source: {
        code: `
<Stack direction="row" spacing={2} flexWrap="wrap" alignItems="center">
  <Badge label="Normal" />
  <Badge label="Disabled" disabled />
</Stack>`,
      },
    },
  },
};

export const StatusBadges: Story = {
  render: () => (
    <Stack spacing={2}>
      <Stack direction="row" spacing={1} flexWrap="wrap">
        <Badge label="Active" color="success" />
        <Badge label="Pending" color="warning" />
        <Badge label="Inactive" color="error" />
        <Badge label="Draft" color="default" />
      </Stack>
      <Stack direction="row" spacing={1} flexWrap="wrap">
        <Badge label="Paid" color="success" />
        <Badge label="Unpaid" color="error" />
        <Badge label="Refunded" color="info" />
        <Badge label="Processing" color="warning" />
      </Stack>
    </Stack>
  ),
  parameters: {
    docs: {
      source: {
        code: `
<Stack spacing={2}>
  <Stack direction="row" spacing={1} flexWrap="wrap">
    <Badge label="Active" color="success" />
    <Badge label="Pending" color="warning" />
    <Badge label="Inactive" color="error" />
    <Badge label="Draft" color="default" />
  </Stack>
  <Stack direction="row" spacing={1} flexWrap="wrap">
    <Badge label="Paid" color="success" />
    <Badge label="Unpaid" color="error" />
    <Badge label="Refunded" color="info" />
    <Badge label="Processing" color="warning" />
  </Stack>
</Stack>`,
      },
    },
  },
};

export const CategoryBadges: Story = {
  render: () => (
    <Stack spacing={2}>
      <Stack direction="row" spacing={1} flexWrap="wrap">
        <Badge label="Technology" color="info" />
        <Badge label="Business" color="success" />
        <Badge label="Design" color="warning" />
        <Badge label="Marketing" color="error" />
      </Stack>
      <Stack direction="row" spacing={1} flexWrap="wrap">
        <Badge label="Frontend" color="info" />
        <Badge label="Backend" color="success" />
        <Badge label="DevOps" color="warning" />
      </Stack>
    </Stack>
  ),
  parameters: {
    docs: {
      source: {
        code: `
<Stack spacing={2}>
  <Stack direction="row" spacing={1} flexWrap="wrap">
    <Badge label="Technology" color="info" />
    <Badge label="Business" color="success" />
    <Badge label="Design" color="warning" />
    <Badge label="Marketing" color="error" />
  </Stack>
  <Stack direction="row" spacing={1} flexWrap="wrap">
    <Badge label="Frontend" color="info" />
    <Badge label="Backend" color="success" />
    <Badge label="DevOps" color="warning" />
  </Stack>
</Stack>`,
      },
    },
  },
};

export const WithLongText: Story = {
  render: () => (
    <Stack spacing={2} sx={{ width: 300 }}>
      <Badge label="Short" />
      <Badge label="This is a longer badge label" />
      <Badge label="Very long badge label text that might wrap" />
    </Stack>
  ),
  parameters: {
    docs: {
      source: {
        code: `
<Stack spacing={2} sx={{ width: 300 }}>
  <Badge label="Short" />
  <Badge label="This is a longer badge label" />
  <Badge label="Very long badge label text that might wrap" />
</Stack>`,
      },
    },
  },
};
