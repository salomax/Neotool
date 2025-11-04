"use client";

import React from "react";
import Select from '@mui/material/Select';
import FormControl from '@mui/material/FormControl';
import InputLabel from '@mui/material/InputLabel';
import MenuItem from '@mui/material/MenuItem';
import { ComponentRendererProps } from '../types';

export const SelectRenderer: React.FC<ComponentRendererProps> = ({ example, selectValue = '', setSelectValue = () => {} }) => {
  switch (example) {
    case 'Basic Select':
      return (
        <FormControl fullWidth>
          <InputLabel>Choose option</InputLabel>
          <Select 
            value={selectValue} 
            label="Choose option"
            onChange={(e) => setSelectValue(e.target.value)}
          >
            <MenuItem value="1">Option 1</MenuItem>
            <MenuItem value="2">Option 2</MenuItem>
          </Select>
        </FormControl>
      );
    
    case 'Multiple Select':
      return (
        <FormControl fullWidth>
          <InputLabel>Choose multiple</InputLabel>
          <Select 
            multiple 
            value={[]} 
            label="Choose multiple"
            onChange={(e) => {
              console.log('Multiple selection:', e.target.value);
            }}
          >
            <MenuItem value="1">Option 1</MenuItem>
            <MenuItem value="2">Option 2</MenuItem>
          </Select>
        </FormControl>
      );
    
    case 'With Options':
      return (
        <FormControl fullWidth>
          <InputLabel>Country</InputLabel>
          <Select 
            value={selectValue} 
            label="Country"
            onChange={(e) => setSelectValue(e.target.value)}
          >
            <MenuItem value="us">United States</MenuItem>
            <MenuItem value="ca">Canada</MenuItem>
            <MenuItem value="mx">Mexico</MenuItem>
          </Select>
        </FormControl>
      );
    
    default:
      return (
        <FormControl fullWidth>
          <InputLabel>Select</InputLabel>
          <Select 
            value={selectValue} 
            label="Select"
            onChange={(e) => setSelectValue(e.target.value)}
          >
            <MenuItem value="">None</MenuItem>
          </Select>
        </FormControl>
      );
  }
};
