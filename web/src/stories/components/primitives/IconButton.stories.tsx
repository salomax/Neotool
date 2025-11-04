import type { Meta, StoryObj } from '@storybook/react';
import React from 'react';
import { IconButton } from '@/shared/components/ui/primitives/IconButton';
import { Stack, Box } from '@mui/material';
import {
  FavoriteIcon,
  FavoriteBorderIcon,
  DeleteIcon,
  EditIcon,
  ShareIcon,
  SearchIcon,
  SettingsIcon,
  AddIcon,
} from '@/shared/ui/mui-imports';

const meta: Meta<typeof IconButton> = {
  title: 'Components/Primitives/IconButton',
  component: IconButton,
  parameters: {
    layout: 'centered',
    docs: {
      description: {
        component: 'Icon button component for displaying clickable icons.'
      }
    }
  },
  argTypes: {
    size: {
      control: { type: 'select' },
      options: ['small', 'medium', 'large'],
      description: 'Size of the icon button'
    },
    color: {
      control: { type: 'select' },
      options: ['default', 'primary', 'secondary', 'error', 'warning', 'info', 'success'],
      description: 'Color of the icon button'
    },
    disabled: {
      control: { type: 'boolean' },
      description: 'Whether the button is disabled'
    }
  },
  tags: ['autodocs'],
};

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  render: () => {
    const [favorited, setFavorited] = React.useState(false);
    
    return (
      <Box>
        <IconButton
          onClick={() => setFavorited(!favorited)}
          color={favorited ? 'error' : 'default'}
        >
          {favorited ? <FavoriteIcon /> : <FavoriteBorderIcon />}
        </IconButton>
      </Box>
    );
  },
};

export const Colors: Story = {
  render: () => (
    <Stack direction="row" spacing={2}>
      <IconButton color="default">
        <SettingsIcon />
      </IconButton>
      <IconButton color="primary">
        <FavoriteIcon />
      </IconButton>
      <IconButton color="secondary">
        <ShareIcon />
      </IconButton>
      <IconButton color="error">
        <DeleteIcon />
      </IconButton>
      <IconButton color="warning">
        <EditIcon />
      </IconButton>
      <IconButton color="info">
        <SearchIcon />
      </IconButton>
      <IconButton color="success">
        <AddIcon />
      </IconButton>
    </Stack>
  ),
  parameters: {
    docs: {
      source: {
        code: `
<Stack direction="row" spacing={2}>
  <IconButton color="default"><SettingsIcon /></IconButton>
  <IconButton color="primary"><FavoriteIcon /></IconButton>
  <IconButton color="secondary"><ShareIcon /></IconButton>
  <IconButton color="error"><DeleteIcon /></IconButton>
  <IconButton color="warning"><EditIcon /></IconButton>
  <IconButton color="info"><SearchIcon /></IconButton>
  <IconButton color="success"><AddIcon /></IconButton>
</Stack>`,
      },
    },
  },
};

export const Sizes: Story = {
  render: () => (
    <Stack direction="row" spacing={2} alignItems="center">
      <IconButton size="small">
        <DeleteIcon />
      </IconButton>
      <IconButton size="medium">
        <DeleteIcon />
      </IconButton>
      <IconButton size="large">
        <DeleteIcon />
      </IconButton>
    </Stack>
  ),
  parameters: {
    docs: {
      source: {
        code: `
<Stack direction="row" spacing={2} alignItems="center">
  <IconButton size="small"><DeleteIcon /></IconButton>
  <IconButton size="medium"><DeleteIcon /></IconButton>
  <IconButton size="large"><DeleteIcon /></IconButton>
</Stack>`,
      },
    },
  },
};

export const States: Story = {
  render: () => (
    <Stack direction="row" spacing={2}>
      <IconButton>
        <DeleteIcon />
      </IconButton>
      <IconButton disabled>
        <DeleteIcon />
      </IconButton>
      <IconButton color="primary">
        <FavoriteIcon />
      </IconButton>
      <IconButton color="primary" disabled>
        <FavoriteIcon />
      </IconButton>
    </Stack>
  ),
  parameters: {
    docs: {
      source: {
        code: `
<Stack direction="row" spacing={2}>
  <IconButton><DeleteIcon /></IconButton>
  <IconButton disabled><DeleteIcon /></IconButton>
  <IconButton color="primary"><FavoriteIcon /></IconButton>
  <IconButton color="primary" disabled><FavoriteIcon /></IconButton>
</Stack>`,
      },
    },
  },
};

