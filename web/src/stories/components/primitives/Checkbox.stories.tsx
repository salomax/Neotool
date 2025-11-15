import type { Meta, StoryObj } from '@storybook/react';
import { CheckboxField } from '@/shared/components/ui/forms/form/CheckboxField';
import { Stack, Box, FormControlLabel } from '@mui/material';
import { FormProvider, useForm } from 'react-hook-form';

// Wrapper component to provide form context
const CheckboxWrapper = (args: any) => {
  const methods = useForm({
    defaultValues: {
      checkbox: args.checked || false,
    },
  });
  
  return (
    <FormProvider {...methods}>
      <CheckboxField name="checkbox" label={args.label} />
    </FormProvider>
  );
};

const meta: Meta<typeof CheckboxWrapper> = {
  title: 'Components/Primitives/Checkbox',
  component: CheckboxWrapper,
  parameters: {
    layout: 'centered',
    docs: {
      description: {
        component: 'Checkbox component for binary choice selection with label and validation support.'
      }
    }
  },
  argTypes: {
    label: {
      control: { type: 'text' },
      description: 'The label text for the checkbox'
    },
    checked: {
      control: { type: 'boolean' },
      description: 'Whether the checkbox is checked'
    }
  },
  tags: ['autodocs'],
};

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  args: {
    label: 'Checkbox',
  },
};

export const States: Story = {
  render: () => {
    const methods = useForm({
      defaultValues: {
        unchecked: false,
        checked: true,
        indeterminate: false,
        disabled: false,
        disabledChecked: true,
      },
    });
    
    return (
      <FormProvider {...methods}>
        <Stack spacing={2}>
          <CheckboxField name="unchecked" label="Unchecked" />
          <CheckboxField name="checked" label="Checked" />
          <CheckboxField name="indeterminate" label="Indeterminate" />
          <CheckboxField name="disabled" label="Disabled" />
          <CheckboxField name="disabledChecked" label="Disabled Checked" />
        </Stack>
      </FormProvider>
    );
  },
  parameters: {
    docs: {
      source: {
        code: `
<Stack spacing={2}>
  <CheckboxField name="unchecked" label="Unchecked" />
  <CheckboxField name="checked" label="Checked" />
  <CheckboxField name="indeterminate" label="Indeterminate" />
  <CheckboxField name="disabled" label="Disabled" />
  <CheckboxField name="disabledChecked" label="Disabled Checked" />
</Stack>`,
      },
    },
  },
};

export const Colors: Story = {
  render: () => {
    const methods = useForm({
      defaultValues: {
        primary: false,
        secondary: false,
        success: false,
        error: false,
        warning: false,
        info: false,
      },
    });
    
    return (
      <FormProvider {...methods}>
        <Stack spacing={2}>
          <CheckboxField name="primary" label="Primary" color="primary" />
          <CheckboxField name="secondary" label="Secondary" color="secondary" />
          <CheckboxField name="success" label="Success" color="success" />
          <CheckboxField name="error" label="Error" color="error" />
          <CheckboxField name="warning" label="Warning" color="warning" />
          <CheckboxField name="info" label="Info" color="info" />
        </Stack>
      </FormProvider>
    );
  },
  parameters: {
    docs: {
      source: {
        code: `
<Stack spacing={2}>
  <CheckboxField name="primary" label="Primary" color="primary" />
  <CheckboxField name="secondary" label="Secondary" color="secondary" />
  <CheckboxField name="success" label="Success" color="success" />
  <CheckboxField name="error" label="Error" color="error" />
  <CheckboxField name="warning" label="Warning" color="warning" />
  <CheckboxField name="info" label="Info" color="info" />
</Stack>`,
      },
    },
  },
};

export const WithHelperText: Story = {
  render: () => {
    const methods = useForm({
      defaultValues: {
        terms: false,
        newsletter: false,
      },
    });
    
    return (
      <FormProvider {...methods}>
        <Stack spacing={2}>
          <CheckboxField 
            name="terms"
            label="Accept terms and conditions" 
          />
          <CheckboxField 
            name="newsletter"
            label="Subscribe to newsletter" 
          />
        </Stack>
      </FormProvider>
    );
  },
  parameters: {
    docs: {
      source: {
        code: `
<Stack spacing={2}>
  <CheckboxField name="terms"
    label="Accept terms and conditions" 
  />
  <CheckboxField name="newsletter"
    label="Subscribe to newsletter" 
  />
</Stack>`,
      },
    },
  },
};

export const CheckboxGroup: Story = {
  render: () => {
    const methods = useForm({
      defaultValues: {
        option1: false,
        option2: false,
        option3: false,
      },
    });
    
    return (
      <FormProvider {...methods}>
        <Box>
          <CheckboxField name="option1" label="Option 1" />
          <CheckboxField name="option2" label="Option 2" />
          <CheckboxField name="option3" label="Option 3" />
        </Box>
      </FormProvider>
    );
  },
  parameters: {
    docs: {
      source: {
        code: `
<Box>
  <CheckboxField name="option1" label="Option 1" />
  <CheckboxField name="option2" label="Option 2" />
  <CheckboxField name="option3" label="Option 3" />
</Box>`,
      },
    },
  },
};
