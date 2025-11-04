"use client";

import React from "react";
import TextField from '@mui/material/TextField';
import { maskCNPJ } from '@/shared/components/ui/forms/components/masks/br';
import { ComponentRendererProps } from '../types';

export const CnpjRenderer: React.FC<ComponentRendererProps> = ({ example, cnpjValue = '', setCnpjValue = () => {} }) => {
  switch (example) {
    case 'Basic CNPJ':
      return (
        <TextField
          label="CNPJ"
          placeholder="00.000.000/0000-00"
          value={cnpjValue}
          onChange={(e) => setCnpjValue(maskCNPJ(e.target.value))}
          helperText="Format: 00.000.000/0000-00"
        />
      );
    
    case 'With Formatting':
      return (
        <TextField
          label="Company CNPJ"
          placeholder="00.000.000/0000-00"
          value={cnpjValue}
          onChange={(e) => setCnpjValue(maskCNPJ(e.target.value))}
          helperText="Format: 00.000.000/0000-00"
        />
      );
    
    case 'With Validation':
      return (
        <TextField
          label="CNPJ"
          placeholder="00.000.000/0000-00"
          value={cnpjValue}
          onChange={(e) => setCnpjValue(maskCNPJ(e.target.value))}
          helperText="Valid CNPJ format"
        />
      );
    
    default:
      return (
        <TextField
          label="CNPJ"
          placeholder="00.000.000/0000-00"
          value={cnpjValue}
          onChange={(e) => setCnpjValue(maskCNPJ(e.target.value))}
        />
      );
  }
};
