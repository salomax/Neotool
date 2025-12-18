"use client";

import React from "react";
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import { ComponentRendererProps } from '../types';

export const ChipRenderer: React.FC<ComponentRendererProps> = ({ example }) => {
  switch (example) {
    case 'Basic Chip':
      return (
        <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
          <Chip label="Basic" />
          <Chip label="Primary" color="primary" />
          <Chip label="Secondary" color="secondary" />
        </Box>
      );
    
    case 'Deletable':
      return (
        <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
          <Chip label="Deletable" onDelete={() => {}} />
          <Chip label="Clickable" clickable onDelete={() => {}} />
        </Box>
      );
    
    case 'Clickable':
      return (
        <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
          <Chip label="Clickable" clickable />
          <Chip label="Outlined" variant="outlined" clickable />
        </Box>
      );
    default:
      return <Chip label="Chip" />;
  }
};
