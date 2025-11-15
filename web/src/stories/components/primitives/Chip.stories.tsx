import type { Meta, StoryObj } from '@storybook/react';
import { Chip } from '@/shared/components/ui/primitives/Chip';
import { Stack, Box } from '@mui/material';

const meta: Meta<typeof Chip> = {
  title: 'Components/Primitives/Chip',
  component: Chip,
  parameters: {
    layout: 'centered',
    docs: {
      description: {
        component: 'Chip component for displaying compact information, tags, or actions.'
      }
    }
  },
  argTypes: {
    label: {
      control: { type: 'text' },
      description: 'Label text for the chip'
    },
    color: {
      control: { type: 'select' },
      options: ['default', 'primary', 'secondary', 'error', 'info', 'success', 'warning'],
      description: 'Color of the chip'
    },
    variant: {
      control: { type: 'select' },
      options: ['filled', 'outlined'],
      description: 'Variant of the chip'
    },
    size: {
      control: { type: 'select' },
      options: ['small', 'medium', 'large'],
      description: 'Size of the chip'
    },
    deletable: {
      control: { type: 'boolean' },
      description: 'Whether the chip can be deleted'
    },
    disabled: {
      control: { type: 'boolean' },
      description: 'Whether the chip is disabled'
    }
  },
  tags: ['autodocs'],
};

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  args: {
    label: 'Chip',
  },
};

export const Variants: Story = {
  render: () => (
    <Stack direction="row" spacing={2} flexWrap="wrap">
      <Chip label="Filled" variant="filled" />
      <Chip label="Outlined" variant="outlined" />
    </Stack>
  ),
  parameters: {
    docs: {
      source: {
        code: `
<Stack direction="row" spacing={2} flexWrap="wrap">
  <Chip label="Filled" variant="filled" />
  <Chip label="Outlined" variant="outlined" />
</Stack>`,
      },
    },
  },
};

export const Colors: Story = {
  render: () => (
    <Stack direction="row" spacing={2} flexWrap="wrap">
      <Chip label="Default" color="default" />
      <Chip label="Primary" color="primary" />
      <Chip label="Secondary" color="secondary" />
      <Chip label="Success" color="success" />
      <Chip label="Error" color="error" />
      <Chip label="Warning" color="warning" />
      <Chip label="Info" color="info" />
    </Stack>
  ),
  parameters: {
    docs: {
      source: {
        code: `
<Stack direction="row" spacing={2} flexWrap="wrap">
  <Chip label="Default" color="default" />
  <Chip label="Primary" color="primary" />
  <Chip label="Secondary" color="secondary" />
  <Chip label="Success" color="success" />
  <Chip label="Error" color="error" />
  <Chip label="Warning" color="warning" />
  <Chip label="Info" color="info" />
</Stack>`,
      },
    },
  },
};

export const Sizes: Story = {
  render: () => (
    <Stack direction="row" spacing={2} alignItems="center">
      <Chip label="Small" size="small" />
      <Chip label="Medium" size="medium" />
      <Chip label="Large" size="large" />
    </Stack>
  ),
  parameters: {
    docs: {
      source: {
        code: `
<Stack direction="row" spacing={2} alignItems="center">
  <Chip label="Small" size="small" />
  <Chip label="Medium" size="medium" />
  <Chip label="Large" size="large" />
</Stack>`,
      },
    },
  },
};

export const Deletable: Story = {
  render: () => (
    <Stack direction="row" spacing={2} flexWrap="wrap">
      <Chip label="Deletable" onDelete={() => {}} />
      <Chip label="With Icon" onDelete={() => {}} />
      <Chip label="Disabled" onDelete={() => {}} disabled />
    </Stack>
  ),
  parameters: {
    docs: {
      source: {
        code: `
<Stack direction="row" spacing={2} flexWrap="wrap">
  <Chip label="Deletable" onDelete={() => {}} />
  <Chip label="With Icon" onDelete={() => {}} />
  <Chip label="Disabled" onDelete={() => {}} disabled />
</Stack>`,
      },
    },
  },
};

export const WithIcons: Story = {
  render: () => (
    <Stack direction="row" spacing={2} flexWrap="wrap">
      <Chip label="With Icon" icon="✓" />
      <Chip label="Deletable with Icon" icon="✓" onDelete={() => {}} />
    </Stack>
  ),
  parameters: {
    docs: {
      source: {
        code: `
<Stack direction="row" spacing={2} flexWrap="wrap">
  <Chip label="With Icon" icon="✓" />
  <Chip label="Deletable with Icon" icon="✓" onDelete={() => {}} />
</Stack>`,
      },
    },
  },
};
