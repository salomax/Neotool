"use client";

import React from "react";
import TextField from '@mui/material/TextField';
import { maskCEP } from '@/shared/components/ui/forms/components/masks/br';
import { ComponentRendererProps } from '../types';

export const CepRenderer: React.FC<ComponentRendererProps> = ({ example, cepValue = '', setCepValue = () => {} }) => {
  switch (example) {
    case 'Basic CEP':
      return (
        <TextField
          label="CEP"
          placeholder="00000-000"
          value={cepValue}
          onChange={(e) => setCepValue(maskCEP(e.target.value))}
          helperText="Format: 00000-000"
        />
      );
    
    case 'With Validation':
      return (
        <TextField
          label="Postal Code"
          placeholder="00000-000"
          value={cepValue}
          onChange={(e) => setCepValue(maskCEP(e.target.value))}
          helperText="Valid CEP format"
        />
      );
    
    case 'Auto Complete':
      return (
        <TextField
          label="CEP"
          placeholder="00000-000"
          value={cepValue}
          onChange={(e) => setCepValue(maskCEP(e.target.value))}
          helperText="Address: Rua Example, São Paulo - SP"
        />
      );
    
    default:
      return (
        <TextField
          label="CEP"
          placeholder="00000-000"
          value={cepValue}
          onChange={(e) => setCepValue(maskCEP(e.target.value))}
        />
      );
  }
};
