import type { Meta, StoryObj } from '@storybook/react';
import { RichTextEditor } from '@/shared/components/ui/forms/RichTextEditor';
import { Stack, Box, Typography } from '@mui/material';
import { useState } from 'react';

const meta: Meta<typeof RichTextEditor> = {
  title: 'Components/Forms/RichTextEditor',
  component: RichTextEditor,
  parameters: {
    layout: 'padded',
    docs: {
      description: {
        component: 'Rich text editor component for creating formatted content.'
      }
    }
  },
  argTypes: {
    value: {
      control: { type: 'text' },
      description: 'HTML content value'
    },
    placeholder: {
      control: { type: 'text' },
      description: 'Placeholder text'
    },
    readOnly: {
      control: { type: 'boolean' },
      description: 'Whether the editor is read-only'
    },
    minHeight: {
      control: { type: 'number' },
      description: 'Minimum height of the editor in pixels'
    },
    maxHeight: {
      control: { type: 'number' },
      description: 'Maximum height of the editor in pixels'
    },
    showToolbar: {
      control: { type: 'boolean' },
      description: 'Whether to show the formatting toolbar'
    }
  },
  tags: ['autodocs'],
};

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  args: {
    value: '<p>Start typing your content here...</p>',
    minHeight: 200,
    maxHeight: 400,
  },
};

const WithContentWrapper = () => {
  const [value, setValue] = useState(`
    <h2>Welcome to Rich Text Editor</h2>
    <p>This is a <strong>powerful</strong> rich text editor with <em>formatting</em> options.</p>
    <ul>
      <li>Bold and italic text</li>
      <li>Lists and headings</li>
      <li>Links and more</li>
    </ul>
  `);
  
  return (
    <Box sx={{ height: 400 }}>
      <RichTextEditor 
        value={value}
        onChange={setValue}
        minHeight={300}
        maxHeight={400}
      />
    </Box>
  );
};

export const WithContent: Story = {
  render: () => <WithContentWrapper />,
};

export const States: Story = {
  render: () => (
    <Stack spacing={3}>
      <Box>
        <Typography variant="h6" gutterBottom>Normal</Typography>
        <RichTextEditor 
          value="<p>Normal editor state</p>"
          minHeight={200}
          maxHeight={300}
        />
      </Box>
      <Box>
        <Typography variant="h6" gutterBottom>Read Only</Typography>
        <RichTextEditor 
          value="<p>This content is read-only</p>"
          readOnly
          minHeight={200}
          maxHeight={300}
        />
      </Box>
      <Box>
        <Typography variant="h6" gutterBottom>Read Only</Typography>
        <RichTextEditor 
          value="<p>This editor is read-only</p>"
          readOnly
          minHeight={200}
          maxHeight={300}
        />
      </Box>
    </Stack>
  ),
};

const WithoutToolbarWrapper = () => {
  const [value, setValue] = useState('<p>Editor without toolbar</p>');
  
  return (
    <Box sx={{ height: 300 }}>
      <RichTextEditor 
        value={value}
        onChange={setValue}
        showToolbar={false}
        minHeight={200}
        maxHeight={300}
      />
    </Box>
  );
};

const WithPlaceholderWrapper = () => {
  const [value, setValue] = useState('');
  
  return (
    <Box sx={{ height: 300 }}>
      <RichTextEditor 
        value={value}
        onChange={setValue}
        placeholder="Enter your content here..."
        minHeight={200}
        maxHeight={300}
      />
    </Box>
  );
};

export const WithoutToolbar: Story = {
  render: () => <WithoutToolbarWrapper />,
};

export const WithPlaceholder: Story = {
  render: () => <WithPlaceholderWrapper />,
};
