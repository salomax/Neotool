"use client";

import React from "react";
import Box from '@mui/material/Box';
import Checkbox from '@mui/material/Checkbox';
import Select from '@mui/material/Select';
import FormControl from '@mui/material/FormControl';
import InputLabel from '@mui/material/InputLabel';
import MenuItem from '@mui/material/MenuItem';
import Typography from '@mui/material/Typography';
import { ComponentRendererProps } from '../types';

export const CheckboxRenderer: React.FC<ComponentRendererProps> = ({ example }) => {
  switch (example) {
    case 'Basic Checkbox':
      return (
        <Box sx={{ display: 'flex', alignItems: 'center' }}>
          <Checkbox />
          <Typography variant="body2" sx={{ ml: 1 }}>Accept terms</Typography>
        </Box>
      );
    
    case 'States':
      return (
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
          <Box sx={{ display: 'flex', alignItems: 'center' }}>
            <Checkbox />
            <Typography variant="body2" sx={{ ml: 1 }}>Unchecked</Typography>
          </Box>
          <Box sx={{ display: 'flex', alignItems: 'center' }}>
            <Checkbox checked />
            <Typography variant="body2" sx={{ ml: 1 }}>Checked</Typography>
          </Box>
          <Box sx={{ display: 'flex', alignItems: 'center' }}>
            <Checkbox disabled />
            <Typography variant="body2" sx={{ ml: 1 }}>Disabled</Typography>
          </Box>
        </Box>
      );
    
    case 'Indeterminate':
      return (
        <Box sx={{ display: 'flex', alignItems: 'center' }}>
          <Checkbox indeterminate />
          <Typography variant="body2" sx={{ ml: 1 }}>Select all</Typography>
        </Box>
      );
    
    default:
      return (
        <Box sx={{ display: 'flex', alignItems: 'center' }}>
          <Checkbox />
          <Typography variant="body2" sx={{ ml: 1 }}>Checkbox</Typography>
        </Box>
      );
  }
};
