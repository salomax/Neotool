import type { Meta, StoryObj } from '@storybook/react';
import { Drawer } from '@/shared/components/ui/layout/Drawer';
import { useState } from 'react';
import { Button, List, ListItem, ListItemText, ListItemIcon, Divider, Box, Typography, Stack } from '@mui/material';
import { Home, Person, Settings, Mail, Notifications, Menu } from '@mui/icons-material';

const meta: Meta<typeof Drawer> = {
  title: 'Components/Layout/Drawer',
  component: Drawer,
  parameters: {
    layout: 'fullscreen',
    docs: {
      description: {
        component: 'A slide-out panel component for navigation, forms, and supplementary content.'
      }
    }
  },
  argTypes: {
    open: {
      control: { type: 'boolean' },
      description: 'Whether the drawer is open'
    },
    onClose: {
      description: 'Callback function called when the drawer is closed'
    },
    anchor: {
      control: { type: 'select' },
      options: ['left', 'right', 'top', 'bottom'],
      description: 'The side from which the drawer slides in'
    },
    variant: {
      control: { type: 'select' },
      options: ['temporary', 'persistent', 'permanent'],
      description: 'The drawer variant'
    },
    size: {
      control: { type: 'select' },
      options: ['sm', 'md', 'lg', 'full'],
      description: 'Predefined drawer size: sm (400px), md (600px), lg (800px), or full (100% - sidebar)'
    },
    width: {
      control: { type: 'number' },
      description: 'Custom drawer width (for left/right anchors). Ignored if size is provided. Default: 600'
    },
    height: {
      control: { type: 'text' },
      description: 'Drawer height (for top/bottom anchors). Default: "100%"'
    },
    forceMobileTemporary: {
      control: { type: 'boolean' },
      description: 'If true, forces temporary variant on mobile devices. Default: true'
    }
  },
  tags: ['autodocs'],
};

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  args: {
    open: true,
    anchor: 'left',
  },
  render: (args) => {
    const [open, setOpen] = useState(args.open);
    
    return (
      <Box>
        <Button onClick={() => setOpen(true)} startIcon={<Menu />}>
          Open Drawer
        </Button>
        <Drawer {...args} open={open} onClose={() => setOpen(false)}>
          <Drawer.Header title="Navigation" />
          <Drawer.Body>
            <List>
              <ListItem>
                <ListItemIcon><Home /></ListItemIcon>
                <ListItemText primary="Home" />
              </ListItem>
              <ListItem>
                <ListItemIcon><Person /></ListItemIcon>
                <ListItemText primary="Profile" />
              </ListItem>
              <ListItem>
                <ListItemIcon><Settings /></ListItemIcon>
                <ListItemText primary="Settings" />
              </ListItem>
              <Divider sx={{ my: 1 }} />
              <ListItem>
                <ListItemIcon><Mail /></ListItemIcon>
                <ListItemText primary="Messages" />
              </ListItem>
              <ListItem>
                <ListItemIcon><Notifications /></ListItemIcon>
                <ListItemText primary="Notifications" />
              </ListItem>
            </List>
          </Drawer.Body>
        </Drawer>
      </Box>
    );
  }
};

export const WithIconHeader: Story = {
  args: {
    open: true,
    anchor: 'right',
  },
  render: (args) => {
    const [open, setOpen] = useState(args.open);
    
    return (
      <Box>
        <Button onClick={() => setOpen(true)} startIcon={<Person />}>
          Open User Drawer
        </Button>
        <Drawer {...args} open={open} onClose={() => setOpen(false)}>
          <Drawer.Header 
            title="User Profile" 
            icon={<Person />} 
          />
          <Drawer.Body>
            <Box sx={{ p: 2 }}>
              <Typography>Drawer content with icon header</Typography>
            </Box>
          </Drawer.Body>
        </Drawer>
      </Box>
    );
  }
};

export const WithCustomTitleStyle: Story = {
  args: {
    open: true,
    anchor: 'right',
  },
  render: (args) => {
    const [open, setOpen] = useState(args.open);
    
    return (
      <Box>
        <Button onClick={() => setOpen(true)} startIcon={<Settings />}>
          Open Custom Header Drawer
        </Button>
        <Drawer {...args} open={open} onClose={() => setOpen(false)}>
          <Drawer.Header 
            title="SETTINGS" 
            icon={<Settings />}
            titleTypographyProps={{
              sx: { textTransform: 'uppercase', letterSpacing: 2, color: 'primary.main', fontWeight: 'bold' }
            }}
          />
          <Drawer.Body>
            <Box sx={{ p: 2 }}>
              <Typography>Drawer with custom title style</Typography>
            </Box>
          </Drawer.Body>
        </Drawer>
      </Box>
    );
  }
};
