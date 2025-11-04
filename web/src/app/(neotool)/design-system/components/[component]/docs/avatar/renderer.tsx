"use client";

import React from "react";
import Box from '@mui/material/Box';
import Avatar from '@mui/material/Avatar';
import { ComponentRendererProps } from '../types';

export const AvatarRenderer: React.FC<ComponentRendererProps> = ({ example }) => {
  switch (example) {
    case 'With Image':
      return <Avatar src="https://mui.com/static/images/avatar/1.jpg" alt="User" />;
    
    case 'With Initials':
      return (
        <Box sx={{ display: 'flex', gap: 2 }}>
          <Avatar>JD</Avatar>
          <Avatar>AB</Avatar>
          <Avatar>CD</Avatar>
        </Box>
      );
    
    case 'Sizes':
      return (
        <Box sx={{ display: 'flex', gap: 2, alignItems: 'center' }}>
          <Avatar sx={{ width: 24, height: 24 }}>S</Avatar>
          <Avatar sx={{ width: 32, height: 32 }}>M</Avatar>
          <Avatar sx={{ width: 40, height: 40 }}>L</Avatar>
        </Box>
      );
    
    default:
      return <Avatar>U</Avatar>;
  }
};
