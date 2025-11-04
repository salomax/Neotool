"use client";

import React from "react";
import Box from '@mui/material/Box';
import { ColorPicker } from '@/shared/components/ui/primitives/ColorPicker';
import { ComponentRendererProps } from '../types';

// Controlled ColorPicker component for interactive examples
const ControlledColorPicker: React.FC<{
  value: string;
  label?: string;
  showPresets?: boolean;
  showCustomInput?: boolean;
  showHexInput?: boolean;
  showRgbInput?: boolean;
  showHslInput?: boolean;
  variant?: 'standard' | 'outlined' | 'filled';
  size?: 'small' | 'medium' | 'large';
}> = ({ 
  value: initialValue, 
  label = 'Choose Color',
  showPresets = true,
  showCustomInput = true,
  showHexInput = true,
  showRgbInput = false,
  showHslInput = false,
  variant = 'standard',
  size = 'medium'
}) => {
  const [colorValue, setColorValue] = React.useState(initialValue);
  
  return (
    <ColorPicker
      value={colorValue}
      onChange={(color: string) => {
        console.log('Color changed:', color);
        setColorValue(color);
      }}
      label={label}
      showPresets={showPresets}
      showCustomInput={showCustomInput}
      showHexInput={showHexInput}
      showRgbInput={showRgbInput}
      showHslInput={showHslInput}
      variant={variant}
      size={size}
    />
  );
};

export const ColorpickerRenderer: React.FC<ComponentRendererProps> = ({ example }) => {
  switch (example) {
    case 'Basic Color Picker':
      return <ControlledColorPicker value="#3F51B5" label="Choose Color" />;
    
    case 'With Custom Input':
      return (
        <ControlledColorPicker 
          value="#E91E63" 
          label="Custom Color" 
          showPresets={false}
          showHexInput
          showRgbInput
          showHslInput
        />
      );
    
    case 'All Formats':
      return (
        <ControlledColorPicker 
          value="#4CAF50" 
          label="All Color Formats"
          showHexInput
          showRgbInput
          showHslInput
        />
      );
    
    case 'Different Variants':
      return (
        <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>
          <ControlledColorPicker value="#FF5722" variant="standard" label="Standard" />
          <ControlledColorPicker value="#FF5722" variant="outlined" label="Outlined" />
          <ControlledColorPicker value="#FF5722" variant="filled" label="Filled" />
        </Box>
      );
    
    default:
      return <ControlledColorPicker value="#9C27B0" label="Choose Color" />;
  }
};
