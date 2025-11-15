"use client";

import React from "react";
import TextField from '@mui/material/TextField';
import { maskCPF } from '@/shared/components/ui/forms/components/masks/br';
import { ComponentRendererProps } from '../types';

export const CpfRenderer: React.FC<ComponentRendererProps> = ({ example, cpfValue = '', setCpfValue = () => {} }) => {
  switch (example) {
    case 'Basic CPF':
      return (
        <TextField
          label="CPF"
          placeholder="000.000.000-00"
          value={cpfValue}
          onChange={(e) => setCpfValue(maskCPF(e.target.value))}
          helperText="Format: 000.000.000-00"
        />
      );
    
    case 'With Formatting':
      return (
        <TextField
          label="Individual CPF"
          placeholder="000.000.000-00"
          value={cpfValue}
          onChange={(e) => setCpfValue(maskCPF(e.target.value))}
          helperText="Format: 000.000.000-00"
        />
      );
    
    case 'With Validation':
      return (
        <TextField
          label="CPF"
          placeholder="000.000.000-00"
          value={cpfValue}
          onChange={(e) => setCpfValue(maskCPF(e.target.value))}
          helperText="Valid CPF format"
        />
      );
    
    default:
      return (
        <TextField
          label="CPF"
          placeholder="000.000.000-00"
          value={cpfValue}
          onChange={(e) => setCpfValue(maskCPF(e.target.value))}
        />
      );
  }
};
