import type { Meta, StoryObj } from '@storybook/react';
import { FileUploader } from '@/shared/components/ui/forms/form/FileUploader';
import { Stack, Box, FormProvider, useForm } from '@mui/material';

const meta: Meta<typeof FileUploader> = {
  title: 'Components/Forms/FileUpload',
  component: FileUploader,
  parameters: {
    layout: 'centered',
    docs: {
      description: {
        component: 'File upload component for selecting and uploading files.'
      }
    }
  },
  argTypes: {
    label: {
      control: { type: 'text' },
      description: 'Label for the file upload'
    },
    accept: {
      control: { type: 'text' },
      description: 'Accepted file types'
    },
    multiple: {
      control: { type: 'boolean' },
      description: 'Whether multiple files can be selected'
    },
    maxSize: {
      control: { type: 'number' },
      description: 'Maximum file size in MB'
    },
    helperText: {
      control: { type: 'text' },
      description: 'Helper text below the field'
    },
    error: {
      control: { type: 'boolean' },
      description: 'Whether the field is in error state'
    },
    disabled: {
      control: { type: 'boolean' },
      description: 'Whether the field is disabled'
    },
    required: {
      control: { type: 'boolean' },
      description: 'Whether the field is required'
    }
  },
  tags: ['autodocs'],
};

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  render: () => {
    const methods = useForm();
    return (
      <FormProvider {...methods}>
        <FileUploader name="files" label="Upload File" />
      </FormProvider>
    );
  },
};

export const FileTypes: Story = {
  render: () => {
    const methods = useForm();
    return (
      <FormProvider {...methods}>
        <Stack spacing={3} sx={{ width: 400 }}>
          <FileUploader 
            name="images"
            label="Images Only" 
            accept="image/*"
          />
          <FileUploader 
            name="documents"
            label="Documents" 
            accept=".pdf,.doc,.docx"
          />
          <FileUploader 
            name="any"
            label="Any File" 
          />
        </Stack>
      </FormProvider>
    );
  },
};

export const MultipleFiles: Story = {
  render: () => {
    const methods = useForm();
    return (
      <FormProvider {...methods}>
        <Stack spacing={3} sx={{ width: 400 }}>
          <FileUploader 
            name="single"
            label="Single File" 
            multiple={false}
          />
          <FileUploader 
            name="multiple"
            label="Multiple Files" 
            multiple={true}
          />
        </Stack>
      </FormProvider>
    );
  },
};

export const WithSizeLimit: Story = {
  render: () => {
    const methods = useForm();
    return (
      <FormProvider {...methods}>
        <Stack spacing={3} sx={{ width: 400 }}>
          <FileUploader 
            name="small"
            label="Small Files (1MB max)" 
            maxSizeMb={1}
          />
          <FileUploader 
            name="large"
            label="Large Files (10MB max)" 
            maxSizeMb={10}
          />
        </Stack>
      </FormProvider>
    );
  },
};
