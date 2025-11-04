"use client";

import React from "react";
import TextField from '@mui/material/TextField';
import { maskPhoneBR } from '@/shared/components/ui/forms/components/masks/br';
import { ComponentRendererProps } from '../types';

export const MaskedRenderer: React.FC<ComponentRendererProps> = ({ 
  example, 
  phoneValue = '', 
  setPhoneValue = () => {},
  dateMaskValue = '',
  setDateMaskValue = () => {},
  customMaskValue = '',
  setCustomMaskValue = () => {}
}) => {
  switch (example) {
    case 'Phone Mask':
      return (
        <TextField
          label="Phone Number"
          placeholder="(555) 123-4567"
          value={phoneValue}
          onChange={(e) => setPhoneValue(maskPhoneBR(e.target.value))}
          helperText="Format: (555) 123-4567"
        />
      );
    
    case 'Date Mask':
      return (
        <TextField
          label="Date"
          placeholder="MM/DD/YYYY"
          value={dateMaskValue}
          onChange={(e) => setDateMaskValue(e.target.value)}
          helperText="Format: MM/DD/YYYY"
        />
      );
    
    case 'Custom Mask':
      return (
        <TextField
          label="Custom Format"
          placeholder="123-ABC-456"
          value={customMaskValue}
          onChange={(e) => setCustomMaskValue(e.target.value)}
          helperText="Format: 123-ABC-456"
        />
      );
    
    default:
      return (
        <TextField
          label="Masked Input"
          placeholder="999-999-9999"
          value={phoneValue}
          onChange={(e) => setPhoneValue(maskPhoneBR(e.target.value))}
          helperText="Format: 999-999-9999"
        />
      );
  }
};
