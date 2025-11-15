import type { Meta, StoryObj } from '@storybook/react';
import { Avatar } from '@/shared/components/ui/primitives/Avatar';
import { Stack, Box } from '@mui/material';

const meta: Meta<typeof Avatar> = {
  title: 'Components/Primitives/Avatar',
  component: Avatar,
  parameters: {
    layout: 'centered',
    docs: {
      description: {
        component: 'Avatar component for displaying user profile images, initials, or icons.'
      }
    }
  },
  argTypes: {
    src: {
      control: { type: 'text' },
      description: 'Image source URL'
    },
    alt: {
      control: { type: 'text' },
      description: 'Alt text for the image'
    },
    children: {
      control: { type: 'text' },
      description: 'Text content (initials) to display'
    },
    size: {
      control: { type: 'select' },
      options: ['small', 'medium', 'large'],
      description: 'Size of the avatar',
      defaultValue: 'medium',
    },
    variant: {
      control: { type: 'select' },
      options: ['circular', 'rounded', 'square'],
      description: 'Shape variant of the avatar'
    }
  },
  tags: ['autodocs'],
};

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  args: {
    children: 'JD',
    size: 'medium',
  },
  parameters: {
    docs: {
      description: {
        story: 'Use the Controls panel to change the size dynamically.'
      }
    }
  }
};

export const WithImage: Story = {
  args: {
    src: 'https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?w=150&h=150&fit=crop&crop=face',
    alt: 'John Doe',
  },
  parameters: {
    docs: {
      source: {
        code: `<Avatar src="https://..." alt="John Doe" />`,
      }
    }
  }
};

export const Sizes: Story = {
  render: () => (
    <Stack direction="row" spacing={2} alignItems="center">
      <Avatar size="small">JD</Avatar>
      <Avatar size="medium">JD</Avatar>
      <Avatar size="large">JD</Avatar>
    </Stack>
  ),
  parameters: {
    docs: {
      source: {
        code: `
<Stack direction="row" spacing={2} alignItems="center">
  <Avatar size="small">JD</Avatar>
  <Avatar size="medium">JD</Avatar>
  <Avatar size="large">JD</Avatar>
</Stack>`,
      },
      description: {
        story: 'Avatar supports three predefined sizes: small, medium, and large.'
      }
    }
  }
};

export const Variants: Story = {
  render: () => (
    <Stack direction="row" spacing={2} alignItems="center">
      <Avatar variant="circular">JD</Avatar>
      <Avatar variant="rounded">JD</Avatar>
      <Avatar variant="square">JD</Avatar>
    </Stack>
  ),
  parameters: {
    docs: {
      source: {
        code: `
<Stack direction="row" spacing={2} alignItems="center">
  <Avatar variant="circular">JD</Avatar>
  <Avatar variant="rounded">JD</Avatar>
  <Avatar variant="square">JD</Avatar>
</Stack>`,
      }
    }
  }
};

export const WithColors: Story = {
  render: () => (
    <Stack direction="row" spacing={2} alignItems="center">
      <Avatar sx={{ bgcolor: 'primary.main' }}>JD</Avatar>
      <Avatar sx={{ bgcolor: 'secondary.main' }}>AB</Avatar>
      <Avatar sx={{ bgcolor: 'success.main' }}>CD</Avatar>
      <Avatar sx={{ bgcolor: 'error.main' }}>EF</Avatar>
    </Stack>
  ),
  parameters: {
    docs: {
      source: {
        code: `
<Stack direction="row" spacing={2} alignItems="center">
  <Avatar sx={{ bgcolor: 'primary.main' }}>JD</Avatar>
  <Avatar sx={{ bgcolor: 'secondary.main' }}>AB</Avatar>
  <Avatar sx={{ bgcolor: 'success.main' }}>CD</Avatar>
  <Avatar sx={{ bgcolor: 'error.main' }}>EF</Avatar>
</Stack>`,
      }
    }
  }
};
