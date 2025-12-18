import type { Meta, StoryObj } from '@storybook/react';
import { Switch } from '@/shared/components/ui/primitives/Switch';
import { Stack, Box, FormControlLabel } from '@mui/material';
import { useState } from 'react';

const meta: Meta<typeof Switch> = {
  title: 'Components/Primitives/Switch',
  component: Switch,
  parameters: {
    layout: 'centered',
    docs: {
      description: {
        component: 'Switch component for toggling between two states.'
      }
    }
  },
  argTypes: {
    checked: {
      control: { type: 'boolean' },
      description: 'Whether the switch is checked'
    },
    disabled: {
      control: { type: 'boolean' },
      description: 'Whether the switch is disabled'
    },
    color: {
      control: { type: 'select' },
      options: ['primary', 'secondary', 'success', 'error', 'warning', 'info'],
      description: 'Color of the switch'
    },
    size: {
      control: { type: 'select' },
      options: ['small', 'medium', 'large'],
      description: 'Size of the switch'
    }
  },
  tags: ['autodocs'],
};

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  render: () => {
    const [checked, setChecked] = useState(false);
    
    return (
      <Box>
        <Switch
          checked={checked}
          onChange={(newChecked) => setChecked(newChecked)}
          label="Toggle me"
        />
      </Box>
    );
  },
};

export const States: Story = {
  render: () => (
    <Stack spacing={2}>
      <FormControlLabel control={<Switch />} label="Unchecked" />
      <FormControlLabel control={<Switch checked />} label="Checked" />
      <FormControlLabel control={<Switch disabled />} label="Disabled" />
      <FormControlLabel control={<Switch checked disabled />} label="Disabled Checked" />
    </Stack>
  ),
  parameters: {
    docs: {
      source: {
        code: `
<Stack spacing={2}>
  <FormControlLabel control={<Switch />} label="Unchecked" />
  <FormControlLabel control={<Switch checked />} label="Checked" />
  <FormControlLabel control={<Switch disabled />} label="Disabled" />
  <FormControlLabel control={<Switch checked disabled />} label="Disabled Checked" />
</Stack>`,
      },
    },
  },
};

export const Colors: Story = {
  render: () => (
    <Stack spacing={2}>
      <FormControlLabel control={<Switch color="primary" />} label="Primary" />
      <FormControlLabel control={<Switch color="secondary" />} label="Secondary" />
      <FormControlLabel control={<Switch color="success" />} label="Success" />
      <FormControlLabel control={<Switch color="error" />} label="Error" />
      <FormControlLabel control={<Switch color="warning" />} label="Warning" />
      <FormControlLabel control={<Switch color="info" />} label="Info" />
    </Stack>
  ),
  parameters: {
    docs: {
      source: {
        code: `
<Stack spacing={2}>
  <FormControlLabel control={<Switch color="primary" />} label="Primary" />
  <FormControlLabel control={<Switch color="secondary" />} label="Secondary" />
  <FormControlLabel control={<Switch color="success" />} label="Success" />
  <FormControlLabel control={<Switch color="error" />} label="Error" />
  <FormControlLabel control={<Switch color="warning" />} label="Warning" />
  <FormControlLabel control={<Switch color="info" />} label="Info" />
</Stack>`,
      },
    },
  },
};

export const Sizes: Story = {
  render: () => (
    <Stack spacing={2}>
      <FormControlLabel control={<Switch size="small" />} label="Small" />
      <FormControlLabel control={<Switch size="medium" />} label="Medium" />
      <FormControlLabel control={<Switch size="large" />} label="Large" />
    </Stack>
  ),
  parameters: {
    docs: {
      source: {
        code: `
<Stack spacing={2}>
  <FormControlLabel control={<Switch size="small" />} label="Small" />
  <FormControlLabel control={<Switch size="medium" />} label="Medium" />
  <FormControlLabel control={<Switch size="large" />} label="Large" />
</Stack>`,
      },
    },
  },
};
