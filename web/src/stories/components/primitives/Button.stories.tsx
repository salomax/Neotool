import type { Meta, StoryObj } from '@storybook/react';
import { Button } from '@/shared/components/ui/primitives/Button';
import { Stack, Box } from '@mui/material';

const meta: Meta<typeof Button> = {
  title: 'Components/Primitives/Button',
  component: Button,
  parameters: {
    layout: 'centered',
    docs: {
      description: {
        component: 'A versatile button component with multiple variants and sizes for different use cases.'
      }
    }
  },
  argTypes: {
    variant: {
      control: { type: 'select' },
      options: ['contained', 'outlined', 'text'],
      description: 'The visual style variant of the button'
    },
    size: {
      control: { type: 'select' },
      options: ['small', 'medium', 'large'],
      description: 'The size of the button',
      table: {
        type: { summary: "'small' | 'medium' | 'large'" },
        defaultValue: { summary: "'medium'" }
      }
    },
    color: {
      control: { type: 'select' },
      options: ['primary', 'secondary', 'success', 'error', 'warning', 'info'],
      description: 'The color theme of the button'
    },
    disabled: {
      control: { type: 'boolean' },
      description: 'Whether the button is disabled'
    },
    fullWidth: {
      control: { type: 'boolean' },
      description: 'Whether the button should take full width of its container'
    }
  },
  tags: ['autodocs'],
};

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  args: {
    children: 'Button',
  },
};

export const Variants: Story = {
  render: () => (
    <Stack spacing={2} direction="row" flexWrap="wrap">
      <Button variant="contained">Contained</Button>
      <Button variant="outlined">Outlined</Button>
      <Button variant="text">Text</Button>
    </Stack>
  ),
  parameters: {
    docs: {
      source: {
        code: `
<Stack spacing={2} direction="row" flexWrap="wrap">
  <Button variant="contained">Contained</Button>
  <Button variant="outlined">Outlined</Button>
  <Button variant="text">Text</Button>
</Stack>`,
      },
    },
  },
};

export const Colors: Story = {
  render: () => (
    <Stack spacing={2} direction="row" flexWrap="wrap">
      <Button color="primary">Primary</Button>
      <Button color="secondary">Secondary</Button>
      <Button color="success">Success</Button>
      <Button color="error">Error</Button>
      <Button color="warning">Warning</Button>
      <Button color="info">Info</Button>
    </Stack>
  ),
  parameters: {
    docs: {
      source: {
        code: `
<Stack spacing={2} direction="row" flexWrap="wrap">
  <Button color="primary">Primary</Button>
  <Button color="secondary">Secondary</Button>
  <Button color="success">Success</Button>
  <Button color="error">Error</Button>
  <Button color="warning">Warning</Button>
  <Button color="info">Info</Button>
</Stack>`,
      },
    },
  },
};

export const Sizes: Story = {
  render: () => (
    <Stack spacing={2} direction="row" alignItems="center" flexWrap="wrap">
      <Button size="small">Small</Button>
      <Button size="medium">Medium</Button>
      <Button size="large">Large</Button>
    </Stack>
  ),
  parameters: {
    docs: {
      source: {
        code: `
<Stack spacing={2} direction="row" alignItems="center" flexWrap="wrap">
  <Button size="small">Small</Button>
  <Button size="medium">Medium</Button>
  <Button size="large">Large</Button>
</Stack>`,
      },
    },
  },
};

export const States: Story = {
  render: () => (
    <Stack spacing={2} direction="row" flexWrap="wrap">
      <Button>Normal</Button>
      <Button disabled>Disabled</Button>
      <Button fullWidth>Full Width</Button>
    </Stack>
  ),
  parameters: {
    docs: {
      source: {
        code: `
<Stack spacing={2} direction="row" flexWrap="wrap">
  <Button>Normal</Button>
  <Button disabled>Disabled</Button>
  <Button fullWidth>Full Width</Button>
</Stack>`,
      },
    },
  },
};

export const WithIcons: Story = {
  render: () => (
    <Stack spacing={2} direction="row" flexWrap="wrap">
      <Button startIcon="+">Add Item</Button>
      <Button endIcon="→">Continue</Button>
      <Button startIcon="✓" color="success">Save</Button>
    </Stack>
  ),
  parameters: {
    docs: {
      source: {
        code: `
<Stack spacing={2} direction="row" flexWrap="wrap">
  <Button startIcon="+">Add Item</Button>
  <Button endIcon="→">Continue</Button>
  <Button startIcon="✓" color="success">Save</Button>
</Stack>`,
      },
    },
  },
};
