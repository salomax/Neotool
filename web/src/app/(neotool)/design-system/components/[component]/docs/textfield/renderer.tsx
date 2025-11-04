"use client";

import React from "react";
import Box from '@mui/material/Box';
import TextField from '@mui/material/TextField';
import { ComponentRendererProps } from '../types';

export const TextfieldRenderer: React.FC<ComponentRendererProps> = ({ example }) => {
  switch (example) {
    case 'Basic Input':
      return <TextField label="Label" placeholder="Enter text" />;
    
    case 'Validation':
      return (
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          <TextField label="Valid input" value="Valid text" />
          <TextField label="Error input" error helperText="This field has an error" />
          <TextField label="Disabled input" disabled value="Disabled" />
        </Box>
      );
    
    case 'Types':
      return (
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          <TextField label="Text" type="text" placeholder="Enter text" />
          <TextField label="Email" type="email" placeholder="Enter email" />
          <TextField label="Password" type="password" placeholder="Enter password" />
          <TextField label="Number" type="number" placeholder="Enter number" />
        </Box>
      );
    
    default:
      return <TextField label="Label" placeholder="Enter text" />;
  }
};
