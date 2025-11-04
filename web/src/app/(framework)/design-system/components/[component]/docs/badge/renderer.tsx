"use client";

import React from "react";
import Box from '@mui/material/Box';
import Badge from '@mui/material/Badge';
import Button from '@mui/material/Button';
import { ComponentRendererProps } from '../types';

export const BadgeRenderer: React.FC<ComponentRendererProps> = ({ example }) => {
  switch (example) {
    case 'Notification Badge':
      return (
        <Box sx={{ display: 'flex', gap: 2 }}>
          <Badge badgeContent={4} color="primary">
            <Button>Messages</Button>
          </Badge>
          <Badge badgeContent={99} color="error">
            <Button>Notifications</Button>
          </Badge>
        </Box>
      );
    
    case 'Status Indicator':
      return (
        <Box sx={{ display: 'flex', gap: 2 }}>
          <Badge variant="dot" color="success">
            <Button>Online</Button>
          </Badge>
          <Badge variant="dot" color="error">
            <Button>Offline</Button>
          </Badge>
        </Box>
      );
    
    case 'Custom Content':
      return (
        <Badge badgeContent="NEW" color="primary">
          <Button>Feature</Button>
        </Badge>
      );
    
    default:
      return (
        <Badge badgeContent={4} color="primary">
          <Button>Badge</Button>
        </Badge>
      );
  }
};
