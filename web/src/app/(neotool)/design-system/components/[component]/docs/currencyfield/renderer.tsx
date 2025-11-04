"use client";

import React from "react";
import Box from '@mui/material/Box';
import TextField from '@mui/material/TextField';
import { ComponentRendererProps } from '../types';

export const CurrencyRenderer: React.FC<ComponentRendererProps> = ({ example }) => {
  switch (example) {
    case 'Basic Currency':
      return (
        <TextField 
          label="Amount" 
          type="text" 
          placeholder="R$ 0,00"
          value="R$ 1.234,56"
          InputProps={{
            readOnly: true
          }}
          helperText="Brazilian Real format (R$ 1.234,56)"
        />
      );
    
    case 'Different Currencies':
      return (
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          <TextField 
            label="USD" 
            type="text" 
            placeholder="$0.00"
            value="$1,234.56"
            InputProps={{
              readOnly: true
            }}
          />
          <TextField 
            label="EUR" 
            type="text" 
            placeholder="€0,00"
            value="€1.234,56"
            InputProps={{
              readOnly: true
            }}
          />
          <TextField 
            label="BRL" 
            type="text" 
            placeholder="R$ 0,00"
            value="R$ 1.234,56"
            InputProps={{
              readOnly: true
            }}
          />
        </Box>
      );
    
    case 'Formatted Display':
      return (
        <TextField 
          label="Price" 
          type="text" 
          value="R$ 99,99" 
          placeholder="R$ 0,00"
          InputProps={{
            readOnly: true
          }}
          helperText="Brazilian Real format with proper formatting"
        />
      );
    
    default:
      return (
        <TextField 
          label="Amount" 
          type="text" 
          placeholder="R$ 0,00"
          value="R$ 1.234,56"
          InputProps={{
            readOnly: true
          }}
        />
      );
  }
};
