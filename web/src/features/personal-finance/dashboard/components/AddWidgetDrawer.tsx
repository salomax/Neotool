import React from 'react';
import {
  Drawer,
  Box,
  Typography,
  List,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  IconButton,
  Divider,
  Button,
} from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import AddIcon from '@mui/icons-material/Add';
import { AVAILABLE_WIDGETS } from '../registry';
import { useDashboardStore } from '../store';

interface AddWidgetDrawerProps {
  open: boolean;
  onClose: () => void;
}

export const AddWidgetDrawer: React.FC<AddWidgetDrawerProps> = ({ open, onClose }) => {
  const { addWidget } = useDashboardStore();

  const handleAdd = (type: string) => {
    addWidget(type);
    onClose();
  };

  return (
    <Drawer
      anchor="right"
      open={open}
      onClose={onClose}
      PaperProps={{
        sx: { width: { xs: '100%', sm: 400 } }
      }}
    >
      <Box sx={{ p: 2, display: 'flex', alignItems: 'center', justifyContent: 'space-between', borderBottom: 1, borderColor: 'divider' }}>
        <Typography variant="h6">Add Widget</Typography>
        <IconButton onClick={onClose}>
          <CloseIcon />
        </IconButton>
      </Box>
      
      <List sx={{ p: 2 }}>
        {AVAILABLE_WIDGETS.map((widget) => (
          <React.Fragment key={widget.type}>
            <ListItem 
              disablePadding 
              sx={{ 
                mb: 2, 
                border: 1, 
                borderColor: 'divider', 
                borderRadius: 2, 
                overflow: 'hidden' 
              }}
            >
              <ListItemButton onClick={() => handleAdd(widget.type)} sx={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', p: 2 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', width: '100%', mb: 1 }}>
                  <Box sx={{ 
                    mr: 2, 
                    p: 1, 
                    borderRadius: 1, 
                    bgcolor: 'primary.light', 
                    color: 'primary.contrastText',
                    display: 'flex'
                  }}>
                    {widget.icon}
                  </Box>
                  <Typography variant="subtitle1" fontWeight="bold">
                    {widget.defaultTitle}
                  </Typography>
                  <Box sx={{ flexGrow: 1 }} />
                  <AddIcon color="primary" />
                </Box>
                <Typography variant="body2" color="text.secondary">
                  {widget.description}
                </Typography>
              </ListItemButton>
            </ListItem>
          </React.Fragment>
        ))}
      </List>
    </Drawer>
  );
};
