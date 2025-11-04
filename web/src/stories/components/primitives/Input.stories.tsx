import type { Meta, StoryObj } from '@storybook/react';
import { TextField } from '@/shared/components/ui/forms/form/TextField';
import { Stack, Box } from '@mui/material';
import { FormProvider, useForm } from 'react-hook-form';
import { NumberField } from '@/shared/components/ui/forms/form/NumberField';
import { MaskedField } from '@/shared/components/ui/forms/form/MaskedField';

const meta: Meta<typeof TextField> = {
  title: 'Components/Primitives/Input',
  component: TextField,
  parameters: {
    layout: 'centered',
    docs: {
      description: {
        component: 'Text input field component with validation, labels, and helper text support.'
      }
    }
  },
  argTypes: {
    label: {
      control: { type: 'text' },
      description: 'The label text for the input'
    },
    placeholder: {
      control: { type: 'text' },
      description: 'Placeholder text shown when input is empty'
    },
    helperText: {
      control: { type: 'text' },
      description: 'Helper text shown below the input'
    },
    error: {
      control: { type: 'boolean' },
      description: 'Whether the input is in an error state'
    },
    disabled: {
      control: { type: 'boolean' },
      description: 'Whether the input is disabled'
    },
    required: {
      control: { type: 'boolean' },
      description: 'Whether the input is required'
    },
    type: {
      control: { type: 'select' },
      options: ['text', 'password', 'number'],
      description: 'The input type'
    }
  },
  tags: ['autodocs'],
};

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  args: {
    label: 'Label',
    placeholder: 'Enter text...',
  },
};

export const States: Story = {
  render: () => (
    <Stack spacing={3} sx={{ width: 300 }}>
      <TextField label="Normal" placeholder="Enter text..." />
      <TextField label="With Helper Text" placeholder="Enter text..." helperText="This is helper text" />
      <TextField label="Error State" placeholder="Enter text..." error helperText="This field has an error" />
      <TextField label="Disabled" placeholder="Enter text..." disabled />
      <TextField label="Required" placeholder="Enter text..." required />
    </Stack>
  ),
  parameters: {
    docs: {
      source: {
        code: `
<Stack spacing={3} sx={{ width: 300 }}>
  <TextField label="Normal" placeholder="Enter text..." />
  <TextField label="With Helper Text" placeholder="Enter text..." helperText="This is helper text" />
  <TextField label="Error State" placeholder="Enter text..." error helperText="This field has an error" />
  <TextField label="Disabled" placeholder="Enter text..." disabled />
  <TextField label="Required" placeholder="Enter text..." required />
</Stack>`,
      },
    },
  },
};

export const Types: Story = {
  render: () => (
    <Stack spacing={3} sx={{ width: 300 }}>
      <TextField label="Text" type="text" placeholder="Enter text..." />
      <TextField label="Password" type="password" placeholder="Enter password..." />
      <TextField label="Number" type="number" placeholder="Enter number..." />
    </Stack>
  ),
  parameters: {
    docs: {
      source: {
        code: `
<Stack spacing={3} sx={{ width: 300 }}>
  <TextField label="Text" type="text" placeholder="Enter text..." />
  <TextField label="Password" type="password" placeholder="Enter password..." />
  <TextField label="Number" type="number" placeholder="Enter number..." />
</Stack>`,
      },
    },
  },
};

export const Formats: Story = {
  render: () => {
    const methods = useForm({
      defaultValues: { phone: '', email: '', url: '' }
    });
    return (
      <FormProvider {...methods}>
        <Stack spacing={3} sx={{ width: 320 }}>
          {/* Phone mask */}
          <MaskedField name="phone" label="Phone" mask="(999) 999-9999" placeholder="(555) 123-4567" />
          {/* Email and URL as text with icon hints */}
          <TextField label="Email" placeholder="name@example.com" startIcon="email" />
          <TextField label="URL" placeholder="https://example.com" />
        </Stack>
      </FormProvider>
    );
  },
  parameters: {
    docs: {
      source: {
        code: `
<FormProvider {...methods}>
  <Stack spacing={3} sx={{ width: 320 }}>
    <MaskedField name="phone" label="Phone" mask="(999) 999-9999" placeholder="(555) 123-4567" />
    <TextField label="Email" placeholder="name@example.com" startIcon="email" />
    <TextField label="URL" placeholder="https://example.com" />
  </Stack>
</FormProvider>`,
      },
    },
  },
};

export const WithIcons: Story = {
  render: () => (
    <Stack spacing={3} sx={{ width: 300 }}>
      <TextField 
        label="Search" 
        placeholder="Search..." 
        startIcon="search"
      />
      <TextField 
        label="Email" 
        placeholder="Enter email..." 
        type="email"
        startIcon="email"
      />
      <TextField 
        label="Password" 
        placeholder="Enter password..." 
        type="password"
        endIcon="visibility"
      />
    </Stack>
  ),
  parameters: {
    docs: {
      source: {
        code: `
<Stack spacing={3} sx={{ width: 300 }}>
  <TextField 
    label="Search" 
    placeholder="Search..." 
    startIcon="search"
  />
  <TextField 
    label="Email" 
    placeholder="Enter email..." 
    type="email"
    startIcon="email"
  />
  <TextField 
    label="Password" 
    placeholder="Enter password..." 
    type="password"
    endIcon="visibility"
  />
</Stack>`,
      },
    },
  },
};

export const Sizes: Story = {
  render: () => (
    <Stack spacing={3} sx={{ width: 300 }}>
      <TextField label="Small" size="small" placeholder="Small input..." />
      <TextField label="Medium" size="medium" placeholder="Medium input..." />
      <TextField label="Large" size="large" placeholder="Large input..." />
    </Stack>
  ),
  parameters: {
    docs: {
      source: {
        code: `
<Stack spacing={3} sx={{ width: 300 }}>
  <TextField label="Small" size="small" placeholder="Small input..." />
  <TextField label="Medium" size="medium" placeholder="Medium input..." />
  <TextField label="Large" size="large" placeholder="Large input..." />
</Stack>`,
      },
    },
  },
};
