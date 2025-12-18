import type { Meta, StoryObj } from '@storybook/react';
import { ImageUpload } from '@/shared/components/ui/primitives/ImageUpload';
import { Stack, Box, Typography } from '@mui/material';
import { useState } from 'react';

const meta: Meta<typeof ImageUpload> = {
  title: 'Components/Primitives/ImageUpload',
  component: ImageUpload,
  parameters: {
    layout: 'centered',
    docs: {
      description: {
        component: 'Image upload component with drag and drop support, preview, and validation.'
      }
    }
  },
  argTypes: {
    multiple: {
      control: { type: 'boolean' },
      description: 'Whether to allow multiple files'
    },
    disabled: {
      control: { type: 'boolean' },
      description: 'Whether the upload is disabled'
    },
    maxFiles: {
      control: { type: 'number' },
      description: 'Maximum number of files allowed'
    }
  },
  tags: ['autodocs'],
};

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  render: () => {
    const [files, setFiles] = useState<File[]>([]);
    
    return (
      <Box sx={{ width: 500 }}>
        <Typography gutterBottom>Files: {files.length}</Typography>
        <ImageUpload
          value={files}
          onChange={(newFiles) => setFiles(newFiles)}
          label="Upload images"
          multiple
        />
      </Box>
    );
  },
};

export const SingleFile: Story = {
  render: () => {
    const [files, setFiles] = useState<File[]>([]);
    
    return (
      <Box sx={{ width: 500 }}>
        <ImageUpload
          value={files}
          onChange={(newFiles) => setFiles(newFiles)}
          label="Upload single image"
          multiple={false}
          maxFiles={1}
        />
      </Box>
    );
  },
  parameters: {
    docs: {
      source: {
        code: `
<ImageUpload
  value={files}
  onChange={setFiles}
  label="Upload single image"
  multiple={false}
  maxFiles={1}
/>`,
      },
    },
  },
};

export const WithMaxFiles: Story = {
  render: () => {
    const [files, setFiles] = useState<File[]>([]);
    
    return (
      <Box sx={{ width: 500 }}>
        <Typography gutterBottom>Files: {files.length} / 3</Typography>
        <ImageUpload
          value={files}
          onChange={(newFiles) => setFiles(newFiles)}
          label="Upload up to 3 images"
          maxFiles={3}
          helperText="Maximum 3 images allowed"
        />
      </Box>
    );
  },
  parameters: {
    docs: {
      source: {
        code: `
<ImageUpload
  value={files}
  onChange={setFiles}
  label="Upload up to 3 images"
  maxFiles={3}
  helperText="Maximum 3 images allowed"
/>`,
      },
    },
  },
};

export const WithoutDragDrop: Story = {
  render: () => {
    const [files, setFiles] = useState<File[]>([]);
    
    return (
      <Box sx={{ width: 500 }}>
        <ImageUpload
          value={files}
          onChange={(newFiles) => setFiles(newFiles)}
          label="Upload images"
          showDragDrop={false}
        />
      </Box>
    );
  },
  parameters: {
    docs: {
      source: {
        code: `
<ImageUpload
  value={files}
  onChange={setFiles}
  label="Upload images"
  showDragDrop={false}
/>`,
      },
    },
  },
};

export const WithoutPreview: Story = {
  render: () => {
    const [files, setFiles] = useState<File[]>([]);
    
    return (
      <Box sx={{ width: 500 }}>
        <ImageUpload
          value={files}
          onChange={(newFiles) => setFiles(newFiles)}
          label="Upload images"
          showPreview={false}
        />
      </Box>
    );
  },
  parameters: {
    docs: {
      source: {
        code: `
<ImageUpload
  value={files}
  onChange={setFiles}
  label="Upload images"
  showPreview={false}
/>`,
      },
    },
  },
};

export const WithFileSizeLimit: Story = {
  render: () => {
    const [files, setFiles] = useState<File[]>([]);
    
    return (
      <Box sx={{ width: 500 }}>
        <ImageUpload
          value={files}
          onChange={(newFiles) => setFiles(newFiles)}
          label="Upload images (max 2MB)"
          maxFileSize={2 * 1024 * 1024}
          helperText="Maximum file size: 2MB"
        />
      </Box>
    );
  },
  parameters: {
    docs: {
      source: {
        code: `
<ImageUpload
  value={files}
  onChange={setFiles}
  label="Upload images (max 2MB)"
  maxFileSize={2 * 1024 * 1024}
  helperText="Maximum file size: 2MB"
/>`,
      },
    },
  },
};

export const Disabled: Story = {
  render: () => (
    <Box sx={{ width: 500 }}>
      <ImageUpload
        label="Disabled upload"
        disabled
      />
    </Box>
  ),
  parameters: {
    docs: {
      source: {
        code: `
<ImageUpload
  label="Disabled upload"
  disabled
/>`,
      },
    },
  },
};

export const WithError: Story = {
  render: () => {
    const [files, setFiles] = useState<File[]>([]);
    
    return (
      <Box sx={{ width: 500 }}>
        <ImageUpload
          value={files}
          onChange={(newFiles) => setFiles(newFiles)}
          label="Required upload"
          required
          error={files.length === 0}
          errorMessage={files.length === 0 ? "At least one image is required" : undefined}
        />
      </Box>
    );
  },
  parameters: {
    docs: {
      source: {
        code: `
<ImageUpload
  value={files}
  onChange={setFiles}
  label="Required upload"
  required
  error={files.length === 0}
  errorMessage={files.length === 0 ? "At least one image is required" : undefined}
/>`,
      },
    },
  },
};

