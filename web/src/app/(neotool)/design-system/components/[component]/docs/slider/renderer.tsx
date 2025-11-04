"use client";

import React from "react";
import Box from '@mui/material/Box';
import { Slider } from '@/shared/components/ui/primitives/Slider';
import { ComponentRendererProps } from '../types';

// Controlled Slider component for interactive examples
const ControlledSlider: React.FC<{
  value: number | number[];
  min?: number;
  max?: number;
  step?: number;
  range?: boolean;
  label?: string;
  helperText?: string;
  showValue?: boolean;
  showChips?: boolean;
  showMarks?: boolean;
  showMinMax?: boolean;
  orientation?: 'horizontal' | 'vertical';
  size?: 'small' | 'medium';
  color?: 'primary' | 'secondary' | 'error' | 'warning' | 'info' | 'success';
  disabled?: boolean;
  readOnly?: boolean;
}> = ({ 
  value: initialValue, 
  min = 0,
  max = 100,
  step = 1,
  range = false,
  label = 'Slider',
  helperText = '',
  showValue = true,
  showChips = false,
  showMarks = false,
  showMinMax = false,
  orientation = 'horizontal',
  size = 'medium',
  color = 'primary',
  disabled = false,
  readOnly = false
}) => {
  const [sliderValue, setSliderValue] = React.useState(initialValue);
  
  return (
    <Slider
      value={sliderValue}
      onChange={(value: number | number[]) => {
        console.log('Slider changed:', value);
        setSliderValue(value);
      }}
      min={min}
      max={max}
      step={step}
      range={range}
      label={label}
      helperText={helperText}
      showValue={showValue}
      showChips={showChips}
      showMarks={showMarks}
      showMinMax={showMinMax}
      orientation={orientation}
      size={size}
      color={color}
      disabled={disabled}
      readOnly={readOnly}
    />
  );
};

export const SliderRenderer: React.FC<ComponentRendererProps> = ({ example }) => {
  switch (example) {
    case 'Basic Slider':
      return <ControlledSlider value={50} label="Volume" helperText="Adjust the volume level" />;
    
    case 'With Value Display':
      return <ControlledSlider value={75} label="Progress" showValue showChips helperText="Current progress: 75%" />;
    
    case 'Range Slider':
      return (
        <ControlledSlider 
          value={[20, 80]} 
          range 
          label="Price Range" 
          showValue 
          showChips 
          min={0} 
          max={1000} 
          step={10} 
          helperText="Select your price range" 
        />
      );
    
    case 'With Marks':
      return <ControlledSlider value={60} label="Difficulty Level" showMarks showValue step={10} helperText="Choose difficulty level" />;
    
    case 'Vertical Slider':
      return (
        <Box sx={{ 
          height: 200, 
          display: 'flex', 
          justifyContent: 'center', 
          alignItems: 'center',
          width: '100%',
          '& .MuiFormControl-root': {
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            width: 'auto'
          }
        }}>
          <ControlledSlider 
            value={40} 
            label="Volume" 
            orientation="vertical" 
            showValue 
            helperText="Vertical slider" 
          />
        </Box>
      );
    
    case 'Custom Formatter':
      return (
        <ControlledSlider 
          value={2500} 
          label="Price" 
          min={0} 
          max={10000} 
          step={100} 
          showValue 
          showMinMax 
          helperText="Select price range" 
        />
      );
    
    default:
      return <ControlledSlider value={30} label="Default Slider" showValue />;
  }
};
