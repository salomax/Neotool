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
    title: {
      control: { type: 'text' },
      description: 'Optional title displayed in the drawer header'
    },
    showCloseButton: {
      control: { type: 'boolean' },
      description: 'Show close button in header. Default: true'
    },
    showMenuButton: {
      control: { type: 'boolean' },
      description: 'Show menu button in header. Default: false'
    },
    onMenuClick: {
      description: 'Callback function called when the menu button is clicked'
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
    width: {
      control: { type: 'number' },
      description: 'Drawer width (for left/right anchors). Default: 280'
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
          <Box sx={{ width: 250, p: 2 }}>
            <Typography variant="h6" gutterBottom>
              Navigation
            </Typography>
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
          </Box>
        </Drawer>
      </Box>
    );
  },
};

export const Anchors: Story = {
  render: () => {
    const [leftOpen, setLeftOpen] = useState(false);
    const [rightOpen, setRightOpen] = useState(false);
    const [topOpen, setTopOpen] = useState(false);
    const [bottomOpen, setBottomOpen] = useState(false);
    
    return (
      <Box sx={{ p: 2 }}>
        <Stack direction="row" spacing={2} flexWrap="wrap">
          <Button onClick={() => setLeftOpen(true)}>Left Drawer</Button>
          <Button onClick={() => setRightOpen(true)}>Right Drawer</Button>
          <Button onClick={() => setTopOpen(true)}>Top Drawer</Button>
          <Button onClick={() => setBottomOpen(true)}>Bottom Drawer</Button>
        </Stack>
        
        <Drawer anchor="left" open={leftOpen} onClose={() => setLeftOpen(false)}>
          <Box sx={{ width: 250, p: 2 }}>
            <Typography variant="h6">Left Drawer</Typography>
            <Typography>Content slides in from the left</Typography>
          </Box>
        </Drawer>
        
        <Drawer anchor="right" open={rightOpen} onClose={() => setRightOpen(false)}>
          <Box sx={{ width: 250, p: 2 }}>
            <Typography variant="h6">Right Drawer</Typography>
            <Typography>Content slides in from the right</Typography>
          </Box>
        </Drawer>
        
        <Drawer anchor="top" open={topOpen} onClose={() => setTopOpen(false)}>
          <Box sx={{ height: 200, p: 2 }}>
            <Typography variant="h6">Top Drawer</Typography>
            <Typography>Content slides in from the top</Typography>
          </Box>
        </Drawer>
        
        <Drawer anchor="bottom" open={bottomOpen} onClose={() => setBottomOpen(false)}>
          <Box sx={{ height: 200, p: 2 }}>
            <Typography variant="h6">Bottom Drawer</Typography>
            <Typography>Content slides in from the bottom</Typography>
          </Box>
        </Drawer>
      </Box>
    );
  },
};

export const Variants: Story = {
  render: () => {
    const [temporaryOpen, setTemporaryOpen] = useState(false);
    const [persistentOpen, setPersistentOpen] = useState(false);
    
    return (
      <Box sx={{ p: 2 }}>
        <Stack direction="row" spacing={2}>
          <Button onClick={() => setTemporaryOpen(true)}>Temporary Drawer</Button>
          <Button onClick={() => setPersistentOpen(true)}>Persistent Drawer</Button>
        </Stack>
        
        <Drawer 
          variant="temporary" 
          open={temporaryOpen} 
          onClose={() => setTemporaryOpen(false)}
        >
          <Box sx={{ width: 250, p: 2 }}>
            <Typography variant="h6">Temporary Drawer</Typography>
            <Typography>Overlays content and can be closed by clicking outside</Typography>
          </Box>
        </Drawer>
        
        <Drawer 
          variant="persistent" 
          open={persistentOpen} 
          onClose={() => setPersistentOpen(false)}
        >
          <Box sx={{ width: 250, p: 2 }}>
            <Typography variant="h6">Persistent Drawer</Typography>
            <Typography>Pushes content and stays open until explicitly closed</Typography>
          </Box>
        </Drawer>
      </Box>
    );
  },
};

export const WithTitle: Story = {
  render: () => {
    const [open, setOpen] = useState(false);
    
    return (
      <Box sx={{ p: 2 }}>
        <Button onClick={() => setOpen(true)}>Open Drawer with Title</Button>
        <Drawer 
          open={open} 
          onClose={() => setOpen(false)}
          title="Navigation Menu"
          showCloseButton={true}
        >
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
          </List>
        </Drawer>
      </Box>
    );
  },
};

export const WithMenuButton: Story = {
  render: () => {
    const [open, setOpen] = useState(false);
    const [menuClicked, setMenuClicked] = useState(false);
    
    return (
      <Box sx={{ p: 2 }}>
        <Button onClick={() => setOpen(true)}>Open Drawer with Menu Button</Button>
        {menuClicked && (
          <Typography sx={{ mt: 2 }} color="success.main">
            Menu button was clicked!
          </Typography>
        )}
        <Drawer 
          open={open} 
          onClose={() => setOpen(false)}
          title="Settings"
          showCloseButton={true}
          showMenuButton={true}
          onMenuClick={() => {
            setMenuClicked(true);
            setTimeout(() => setMenuClicked(false), 3000);
          }}
        >
          <List>
            <ListItem>
              <ListItemText primary="Account Settings" />
            </ListItem>
            <ListItem>
              <ListItemText primary="Privacy" />
            </ListItem>
            <ListItem>
              <ListItemText primary="Notifications" />
            </ListItem>
          </List>
        </Drawer>
      </Box>
    );
  },
};

export const CustomSizes: Story = {
  render: () => {
    const [leftOpen, setLeftOpen] = useState(false);
    const [rightOpen, setRightOpen] = useState(false);
    const [topOpen, setTopOpen] = useState(false);
    const [bottomOpen, setBottomOpen] = useState(false);
    
    return (
      <Box sx={{ p: 2 }}>
        <Stack direction="row" spacing={2} flexWrap="wrap">
          <Button onClick={() => setLeftOpen(true)}>Wide Left (400px)</Button>
          <Button onClick={() => setRightOpen(true)}>Narrow Right (200px)</Button>
          <Button onClick={() => setTopOpen(true)}>Tall Top (300px)</Button>
          <Button onClick={() => setBottomOpen(true)}>Short Bottom (150px)</Button>
        </Stack>
        
        <Drawer 
          anchor="left" 
          open={leftOpen} 
          onClose={() => setLeftOpen(false)}
          width={400}
          title="Wide Drawer"
        >
          <Box sx={{ p: 2 }}>
            <Typography>This drawer is 400px wide</Typography>
          </Box>
        </Drawer>
        
        <Drawer 
          anchor="right" 
          open={rightOpen} 
          onClose={() => setRightOpen(false)}
          width={200}
          title="Narrow Drawer"
        >
          <Box sx={{ p: 2 }}>
            <Typography>This drawer is 200px wide</Typography>
          </Box>
        </Drawer>
        
        <Drawer 
          anchor="top" 
          open={topOpen} 
          onClose={() => setTopOpen(false)}
          height={300}
          title="Tall Top Drawer"
        >
          <Box sx={{ p: 2 }}>
            <Typography>This drawer is 300px tall</Typography>
          </Box>
        </Drawer>
        
        <Drawer 
          anchor="bottom" 
          open={bottomOpen} 
          onClose={() => setBottomOpen(false)}
          height={150}
          title="Short Bottom Drawer"
        >
          <Box sx={{ p: 2 }}>
            <Typography>This drawer is 150px tall</Typography>
          </Box>
        </Drawer>
      </Box>
    );
  },
};

export const WithoutHeader: Story = {
  render: () => {
    const [open, setOpen] = useState(false);
    
    return (
      <Box sx={{ p: 2 }}>
        <Button onClick={() => setOpen(true)}>Open Drawer Without Header</Button>
        <Drawer 
          open={open} 
          onClose={() => setOpen(false)}
          showCloseButton={false}
        >
          <Box sx={{ p: 2 }}>
            <Typography variant="h6" gutterBottom>No Header</Typography>
            <Typography>
              This drawer has no header, title, or close button.
              Click outside to close.
            </Typography>
          </Box>
        </Drawer>
      </Box>
    );
  },
};

export const LongContent: Story = {
  render: () => {
    const [open, setOpen] = useState(false);
    
    return (
      <Box sx={{ p: 2 }}>
        <Button onClick={() => setOpen(true)}>Open Drawer with Long Content</Button>
        <Drawer 
          open={open} 
          onClose={() => setOpen(false)}
          title="Long Content"
        >
          <Box sx={{ p: 2 }}>
            <List>
              {Array.from({ length: 50 }, (_, i) => (
                <ListItem key={i}>
                  <ListItemText primary={`Item ${i + 1}`} />
                </ListItem>
              ))}
            </List>
          </Box>
        </Drawer>
      </Box>
    );
  },
};

export const EmptyContent: Story = {
  render: () => {
    const [open, setOpen] = useState(false);
    
    return (
      <Box sx={{ p: 2 }}>
        <Button onClick={() => setOpen(true)}>Open Empty Drawer</Button>
        <Drawer 
          open={open} 
          onClose={() => setOpen(false)}
          title="Empty Drawer"
        >
          <Box sx={{ p: 2 }}>
            <Typography color="text.secondary">
              This drawer has minimal content
            </Typography>
          </Box>
        </Drawer>
      </Box>
    );
  },
};

export const WithFooter: Story = {
  render: () => {
    const [open, setOpen] = useState(false);
    
    return (
      <Box sx={{ p: 2 }}>
        <Button onClick={() => setOpen(true)}>Open Drawer with Footer</Button>
        <Drawer 
          open={open} 
          onClose={() => setOpen(false)}
          title="Form Drawer"
          footer={
            <Stack direction="row" spacing={2} justifyContent="flex-end">
              <Button 
                variant="outlined" 
                onClick={() => setOpen(false)}
              >
                Cancel
              </Button>
              <Button 
                variant="contained" 
                onClick={() => {
                  alert('Saved!');
                  setOpen(false);
                }}
              >
                Save
              </Button>
            </Stack>
          }
        >
          <Box sx={{ p: 2 }}>
            <Typography variant="body1" paragraph>
              This drawer demonstrates the footer with action buttons.
              The footer is always visible at the bottom, and the content area is scrollable.
            </Typography>
            <List>
              {Array.from({ length: 20 }, (_, i) => (
                <ListItem key={i}>
                  <ListItemText 
                    primary={`Form Field ${i + 1}`}
                    secondary="This is a form field that can be edited"
                  />
                </ListItem>
              ))}
            </List>
          </Box>
        </Drawer>
      </Box>
    );
  },
};
