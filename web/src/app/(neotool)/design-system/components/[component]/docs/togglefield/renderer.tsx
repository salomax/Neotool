"use client";

import React from "react";
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import { Switch } from '@/shared/components/ui/primitives/Switch';
import { ComponentRendererProps } from '../types';

export const ToggleRenderer: React.FC<ComponentRendererProps> = ({ example }) => {
  switch (example) {
    case 'Basic Toggle':
      return (
        <Box sx={{ display: 'flex', alignItems: 'center' }}>
          <Switch />
          <Typography variant="body2" sx={{ ml: 1 }}>Enable notifications</Typography>
        </Box>
      );
    case 'With Labels':
      return (
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          <Box sx={{ display: 'flex', alignItems: 'center' }}>
            <Switch checked />
            <Typography variant="body2" sx={{ ml: 1 }}>Dark mode</Typography>
          </Box>
          <Box sx={{ display: 'flex', alignItems: 'center' }}>
            <Switch />
            <Typography variant="body2" sx={{ ml: 1 }}>Email notifications</Typography>
          </Box>
        </Box>
      );
    
    case 'Different States':
      return (
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          <Box sx={{ display: 'flex', alignItems: 'center' }}>
            <Switch checked color="primary" />
            <Typography variant="body2" sx={{ ml: 1 }}>Primary</Typography>
          </Box>
          <Box sx={{ display: 'flex', alignItems: 'center' }}>
            <Switch checked color="success" />
            <Typography variant="body2" sx={{ ml: 1 }}>Success</Typography>
          </Box>
          <Box sx={{ display: 'flex', alignItems: 'center' }}>
            <Switch disabled />
            <Typography variant="body2" sx={{ ml: 1 }}>Disabled</Typography>
          </Box>
        </Box>
      );
    
    default:
      return (
        <Box sx={{ display: 'flex', alignItems: 'center' }}>
          <Switch />
          <Typography variant="body2" sx={{ ml: 1 }}>Toggle</Typography>
        </Box>
      );
  }
};
