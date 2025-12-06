"use client";

import React from "react";
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Typography from '@mui/material/Typography';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemText from '@mui/material/ListItemText';
import dynamic from 'next/dynamic';
import { ComponentRendererProps } from '../types';

const Drawer = dynamic(() => 
  import('@/shared/components/ui/layout/Drawer')
    .then(mod => ({ default: mod.Drawer }))
    .catch(() => ({ default: () => <div>Component not available</div> })), 
  { 
    ssr: false,
    loading: () => <div>Loading...</div>
  }
) as React.ComponentType<any> & {
  Header: React.ComponentType<any>;
  Body: React.ComponentType<any>;
  Footer: React.ComponentType<any>;
};

// Interactive Drawer Example Component
const DrawerExample = () => {
  const [open, setOpen] = React.useState(false);

  return (
    <Box sx={{ position: 'relative', height: 200, border: '1px dashed #ccc', borderRadius: 1, p: 2 }}>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Click the button to open the drawer
      </Typography>
      <Button 
        variant="outlined" 
        size="small"
        onClick={() => setOpen(true)}
      >
        Open Drawer
      </Button>
      
      <Drawer
        open={open}
        onClose={() => setOpen(false)}
        variant="temporary"
        anchor="left"
        size="sm"
      >
        <Drawer.Header title="Navigation" showCloseButton={true} />
        <Drawer.Body>
          <List>
            <ListItem>
              <ListItemText primary="Home" />
            </ListItem>
            <ListItem>
              <ListItemText primary="Profile" />
            </ListItem>
            <ListItem>
              <ListItemText primary="Settings" />
            </ListItem>
          </List>
        </Drawer.Body>
      </Drawer>
    </Box>
  );
};

// Drawer with Title Example
const DrawerWithTitleExample = () => {
  const [open, setOpen] = React.useState(false);

  return (
    <Box sx={{ position: 'relative', height: 200, border: '1px dashed #ccc', borderRadius: 1, p: 2 }}>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Drawer with custom title and close button
      </Typography>
      <Button 
        variant="outlined" 
        size="small"
        onClick={() => setOpen(true)}
      >
        Open Drawer with Title
      </Button>
      
      <Drawer
        open={open}
        onClose={() => setOpen(false)}
        variant="temporary"
        anchor="left"
        size="md"
      >
        <Drawer.Header title="My Application" showCloseButton={true} showMenuButton={false} />
        <Drawer.Body>
          <List>
            <ListItem>
              <ListItemText primary="Dashboard" />
            </ListItem>
            <ListItem>
              <ListItemText primary="Users" />
            </ListItem>
            <ListItem>
              <ListItemText primary="Reports" />
            </ListItem>
            <ListItem>
              <ListItemText primary="Settings" />
            </ListItem>
          </List>
        </Drawer.Body>
      </Drawer>
    </Box>
  );
};

// Different Anchors Example
const DrawerAnchorsExample = () => {
  const [leftOpen, setLeftOpen] = React.useState(false);
  const [rightOpen, setRightOpen] = React.useState(false);
  const [topOpen, setTopOpen] = React.useState(false);
  const [bottomOpen, setBottomOpen] = React.useState(false);

  return (
    <Box sx={{ position: 'relative', height: 200, border: '1px dashed #ccc', borderRadius: 1, p: 2 }}>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Try different drawer positions
      </Typography>
      <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
        <Button variant="outlined" size="small" onClick={() => setLeftOpen(true)}>
          Left Drawer
        </Button>
        <Button variant="outlined" size="small" onClick={() => setRightOpen(true)}>
          Right Drawer
        </Button>
        <Button variant="outlined" size="small" onClick={() => setTopOpen(true)}>
          Top Drawer
        </Button>
        <Button variant="outlined" size="small" onClick={() => setBottomOpen(true)}>
          Bottom Drawer
        </Button>
      </Box>
      
      {/* Left Drawer */}
      <Drawer
        open={leftOpen}
        onClose={() => setLeftOpen(false)}
        variant="temporary"
        anchor="left"
        width={280}
      >
        <Drawer.Header title="Left Panel" showCloseButton={true} />
        <Drawer.Body>
          <List>
            <ListItem><ListItemText primary="Left Menu Item 1" /></ListItem>
            <ListItem><ListItemText primary="Left Menu Item 2" /></ListItem>
          </List>
        </Drawer.Body>
      </Drawer>

      {/* Right Drawer */}
      <Drawer
        open={rightOpen}
        onClose={() => setRightOpen(false)}
        variant="temporary"
        anchor="right"
        size="md"
      >
        <Drawer.Header title="Right Panel" showCloseButton={true} />
        <Drawer.Body>
          <List>
            <ListItem><ListItemText primary="Right Menu Item 1" /></ListItem>
            <ListItem><ListItemText primary="Right Menu Item 2" /></ListItem>
          </List>
        </Drawer.Body>
      </Drawer>

      {/* Top Drawer */}
      <Drawer
        open={topOpen}
        onClose={() => setTopOpen(false)}
        variant="temporary"
        anchor="top"
        height={200}
      >
        <Drawer.Header title="Top Panel" showCloseButton={true} />
        <Drawer.Body>
          <List>
            <ListItem><ListItemText primary="Top Menu Item 1" /></ListItem>
            <ListItem><ListItemText primary="Top Menu Item 2" /></ListItem>
          </List>
        </Drawer.Body>
      </Drawer>

      {/* Bottom Drawer */}
      <Drawer
        open={bottomOpen}
        onClose={() => setBottomOpen(false)}
        variant="temporary"
        anchor="bottom"
        height={300}
      >
        <Drawer.Header title="Bottom Panel" showCloseButton={true} />
        <Drawer.Body>
          <List>
            <ListItem><ListItemText primary="Bottom Menu Item 1" /></ListItem>
            <ListItem><ListItemText primary="Bottom Menu Item 2" /></ListItem>
          </List>
        </Drawer.Body>
      </Drawer>
    </Box>
  );
};

// Persistent Drawer Example
const DrawerPersistentExample = () => {
  const [open, setOpen] = React.useState(false);

  return (
    <Box sx={{ position: 'relative', height: 200, border: '1px dashed #ccc', borderRadius: 1, p: 2 }}>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Persistent drawer stays open and pushes content
      </Typography>
      <Button 
        variant="outlined" 
        size="small"
        onClick={() => setOpen(true)}
      >
        Open Persistent Drawer
      </Button>
      
      <Drawer
        open={open}
        onClose={() => setOpen(false)}
        variant="persistent"
        anchor="left"
        size="sm"
      >
        <Drawer.Header title="Persistent Sidebar" showCloseButton={true} />
        <Drawer.Body>
          <List>
            <ListItem>
              <ListItemText primary="Persistent Item 1" />
            </ListItem>
            <ListItem>
              <ListItemText primary="Persistent Item 2" />
            </ListItem>
            <ListItem>
              <ListItemText primary="Persistent Item 3" />
            </ListItem>
          </List>
        </Drawer.Body>
      </Drawer>
    </Box>
  );
};

// Full Width Drawer Example
const DrawerFullWidthExample = () => {
  const [open, setOpen] = React.useState(false);

  return (
    <Box sx={{ position: 'relative', height: 200, border: '1px dashed #ccc', borderRadius: 1, p: 2 }}>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Full width drawer (100% - sidebar width)
      </Typography>
      <Button 
        variant="outlined" 
        size="small"
        onClick={() => setOpen(true)}
      >
        Open Full Width Drawer
      </Button>
      
      <Drawer
        open={open}
        onClose={() => setOpen(false)}
        variant="temporary"
        anchor="right"
        size="full"
      >
        <Drawer.Header title="Full Width Drawer" showCloseButton={true} />
        <Drawer.Body>
          <Typography variant="body1" paragraph>
            This drawer uses size="full" which makes it take up 100% of the viewport width
            minus the sidebar width (84px).
          </Typography>
          <List>
            <ListItem>
              <ListItemText primary="Full Width Item 1" />
            </ListItem>
            <ListItem>
              <ListItemText primary="Full Width Item 2" />
            </ListItem>
            <ListItem>
              <ListItemText primary="Full Width Item 3" />
            </ListItem>
          </List>
        </Drawer.Body>
      </Drawer>
    </Box>
  );
};

// Custom Styling Drawer Example
const DrawerStyledExample = () => {
  const [open, setOpen] = React.useState(false);

  return (
    <Box sx={{ position: 'relative', height: 200, border: '1px dashed #ccc', borderRadius: 1, p: 2, bgcolor: 'grey.50' }}>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Drawer with custom colors and styling
      </Typography>
      <Button 
        variant="outlined" 
        size="small" 
        sx={{ bgcolor: 'primary.light', color: 'white' }}
        onClick={() => setOpen(true)}
      >
        Open Styled Drawer
      </Button>
      
      <Drawer
        open={open}
        onClose={() => setOpen(false)}
        variant="temporary"
        anchor="left"
        size="sm"
        sx={{
          '& .MuiDrawer-paper': {
            backgroundColor: '#f5f5f5',
            borderRight: '2px solid #e0e0e0',
          },
        }}
      >
        <Drawer.Header title="Styled Drawer" showCloseButton={true} />
        <Drawer.Body>
          <List>
            <ListItem>
              <ListItemText primary="Styled Item 1" />
            </ListItem>
            <ListItem>
              <ListItemText primary="Styled Item 2" />
            </ListItem>
            <ListItem>
              <ListItemText primary="Styled Item 3" />
            </ListItem>
          </List>
        </Drawer.Body>
      </Drawer>
    </Box>
  );
};

export const DrawerRenderer: React.FC<ComponentRendererProps> = ({ example }) => {
  switch (example) {
    case 'Basic Drawer':
      return <DrawerExample />;
    
    case 'With Title':
      return <DrawerWithTitleExample />;
    
    case 'Different Anchors':
      return <DrawerAnchorsExample />;
    
    case 'Persistent Drawer':
      return <DrawerPersistentExample />;
    
    case 'Custom Styling':
      return <DrawerStyledExample />;
    
    case 'Full Width':
      return <DrawerFullWidthExample />;
    
    default:
      return (
        <Box sx={{ position: 'relative', height: 200, border: '1px dashed #ccc', borderRadius: 1, p: 2 }}>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Drawer component preview
          </Typography>
          <Button variant="outlined" size="small">
            Open Drawer
          </Button>
        </Box>
      );
  }
};
