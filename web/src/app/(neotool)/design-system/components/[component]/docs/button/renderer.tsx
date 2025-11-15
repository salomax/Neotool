"use client";

import React from "react";
import Box from '@mui/material/Box';
import { Button } from '@/shared/components/ui/primitives/Button';
import { ComponentRendererProps } from '../types';

export const ButtonRenderer: React.FC<ComponentRendererProps> = ({ example }) => {
  switch (example) {
    case 'Basic Usage':
      return <Button variant="contained" color="primary">Click me</Button>;
    
    case 'Variants':
      return (
        <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>
          <Button variant="contained" color="primary">Contained</Button>
          <Button variant="outlined" color="primary">Outlined</Button>
          <Button variant="text" color="primary">Text</Button>
        </Box>
      );
    
    case 'Colors':
      return (
        <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>
          <Button variant="contained" color="primary">Primary</Button>
          <Button variant="contained" color="secondary">Secondary</Button>
          <Button variant="contained" color="success">Success</Button>
          <Button variant="contained" color="error">Error</Button>
        </Box>
      );
    
    case 'Sizes':
      return (
        <Box sx={{ display: 'flex', gap: 2, alignItems: 'center' }}>
          <Button size="small" variant="contained">Small</Button>
          <Button size="medium" variant="contained">Medium</Button>
          <Button size="large" variant="contained">Large</Button>
        </Box>
      );
    
    case 'Loading':
      return (
        <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>
          <Button variant="contained" color="primary" loading>Loading...</Button>
          <Button variant="outlined" color="primary" loading loadingText="Processing">Submit</Button>
          <Button variant="contained" color="secondary" loading loadingText="Saving" size="small">Save</Button>
        </Box>
      );
    
    default:
      return <Button variant="contained" color="primary">Button</Button>;
  }
};
