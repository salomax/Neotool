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
          <Drawer.Header title="Left Drawer" />
          <Drawer.Body>
            <Typography>Content slides in from the left</Typography>
          </Drawer.Body>
        </Drawer>
        
        <Drawer anchor="right" open={rightOpen} onClose={() => setRightOpen(false)}>
          <Drawer.Header title="Right Drawer" />
          <Drawer.Body>
            <Typography>Content slides in from the right</Typography>
          </Drawer.Body>
        </Drawer>
        
        <Drawer anchor="top" open={topOpen} onClose={() => setTopOpen(false)} height={200}>
          <Drawer.Header title="Top Drawer" />
          <Drawer.Body>
            <Typography>Content slides in from the top</Typography>
          </Drawer.Body>
        </Drawer>
        
        <Drawer anchor="bottom" open={bottomOpen} onClose={() => setBottomOpen(false)} height={200}>
          <Drawer.Header title="Bottom Drawer" />
          <Drawer.Body>
            <Typography>Content slides in from the bottom</Typography>
          </Drawer.Body>
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
          <Drawer.Header title="Temporary Drawer" />
          <Drawer.Body>
            <Typography>Overlays content and can be closed by clicking outside</Typography>
          </Drawer.Body>
        </Drawer>
        
        <Drawer 
          variant="persistent" 
          open={persistentOpen} 
          onClose={() => setPersistentOpen(false)}
        >
          <Drawer.Header title="Persistent Drawer" />
          <Drawer.Body>
            <Typography>Pushes content and stays open until explicitly closed</Typography>
          </Drawer.Body>
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
        >
          <Drawer.Header title="Navigation Menu" showCloseButton={true} />
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
            </List>
          </Drawer.Body>
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
        >
          <Drawer.Header 
            title="Settings"
            showCloseButton={true}
            showMenuButton={true}
            onMenuClick={() => {
              setMenuClicked(true);
              setTimeout(() => setMenuClicked(false), 3000);
            }}
          />
          <Drawer.Body>
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
          </Drawer.Body>
        </Drawer>
      </Box>
    );
  },
};

export const CustomSizes: Story = {
  render: () => {
    const [smOpen, setSmOpen] = useState(false);
    const [mdOpen, setMdOpen] = useState(false);
    const [lgOpen, setLgOpen] = useState(false);
    const [fullOpen, setFullOpen] = useState(false);
    const [customOpen, setCustomOpen] = useState(false);
    const [topOpen, setTopOpen] = useState(false);
    const [bottomOpen, setBottomOpen] = useState(false);
    
    return (
      <Box sx={{ p: 2 }}>
        <Stack direction="row" spacing={2} flexWrap="wrap" sx={{ mb: 2 }}>
          <Button onClick={() => setSmOpen(true)}>Small (sm - 400px)</Button>
          <Button onClick={() => setMdOpen(true)}>Medium (md - 600px)</Button>
          <Button onClick={() => setLgOpen(true)}>Large (lg - 800px)</Button>
          <Button onClick={() => setFullOpen(true)}>Full Width</Button>
          <Button onClick={() => setCustomOpen(true)}>Custom (200px)</Button>
          <Button onClick={() => setTopOpen(true)}>Tall Top (300px)</Button>
          <Button onClick={() => setBottomOpen(true)}>Short Bottom (150px)</Button>
        </Stack>
        
        <Drawer 
          anchor="right" 
          open={smOpen} 
          onClose={() => setSmOpen(false)}
          size="sm"
        >
          <Drawer.Header title="Small Drawer" />
          <Drawer.Body>
            <Typography>This drawer uses size=&quot;sm&quot; (400px)</Typography>
          </Drawer.Body>
        </Drawer>
        
        <Drawer 
          anchor="right" 
          open={mdOpen} 
          onClose={() => setMdOpen(false)}
          size="md"
        >
          <Drawer.Header title="Medium Drawer" />
          <Drawer.Body>
            <Typography>This drawer uses size=&quot;md&quot; (600px)</Typography>
          </Drawer.Body>
        </Drawer>
        
        <Drawer 
          anchor="right" 
          open={lgOpen} 
          onClose={() => setLgOpen(false)}
          size="lg"
        >
          <Drawer.Header title="Large Drawer" />
          <Drawer.Body>
            <Typography>This drawer uses size=&quot;lg&quot; (800px)</Typography>
          </Drawer.Body>
        </Drawer>
        
        <Drawer 
          anchor="right" 
          open={fullOpen} 
          onClose={() => setFullOpen(false)}
          size="full"
        >
          <Drawer.Header title="Full Width Drawer" />
          <Drawer.Body>
            <Typography>This drawer uses size=&quot;full&quot; (100% - sidebar width)</Typography>
          </Drawer.Body>
        </Drawer>
        
        <Drawer 
          anchor="right" 
          open={customOpen} 
          onClose={() => setCustomOpen(false)}
          width={200}
        >
          <Drawer.Header title="Custom Width Drawer" />
          <Drawer.Body>
            <Typography>This drawer uses custom width={200} (backward compatible)</Typography>
          </Drawer.Body>
        </Drawer>
        
        <Drawer 
          anchor="top" 
          open={topOpen} 
          onClose={() => setTopOpen(false)}
          height={300}
        >
          <Drawer.Header title="Tall Top Drawer" />
          <Drawer.Body>
            <Typography>This drawer is 300px tall</Typography>
          </Drawer.Body>
        </Drawer>
        
        <Drawer 
          anchor="bottom" 
          open={bottomOpen} 
          onClose={() => setBottomOpen(false)}
          height={150}
        >
          <Drawer.Header title="Short Bottom Drawer" />
          <Drawer.Body>
            <Typography>This drawer is 150px tall</Typography>
          </Drawer.Body>
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
        >
          <Drawer.Body>
            <Typography variant="h6" gutterBottom>No Header</Typography>
            <Typography>
              This drawer has no header, title, or close button.
              Click outside to close.
            </Typography>
          </Drawer.Body>
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
        >
          <Drawer.Header title="Long Content" />
          <Drawer.Body>
            <List>
              {Array.from({ length: 50 }, (_, i) => (
                <ListItem key={i}>
                  <ListItemText primary={`Item ${i + 1}`} />
                </ListItem>
              ))}
            </List>
          </Drawer.Body>
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
        >
          <Drawer.Header title="Empty Drawer" />
          <Drawer.Body>
            <Typography color="text.secondary">
              This drawer has minimal content
            </Typography>
          </Drawer.Body>
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
        >
          <Drawer.Header title="Form Drawer" />
          <Drawer.Body>
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
          </Drawer.Body>
          <Drawer.Footer>
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
          </Drawer.Footer>
        </Drawer>
      </Box>
    );
  },
};
