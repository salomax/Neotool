"use client";

import React from "react";
import Box from '@mui/material/Box';
import dynamic from 'next/dynamic';
import { FormProvider, useForm } from 'react-hook-form';
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs';
import { ComponentRendererProps } from '../types';

const DatePickerField = dynamic(() => 
  import('@/shared/components/ui/forms/form/DatePickers')
    .then(mod => ({ default: mod.DatePickerField }))
    .catch(() => ({ default: () => <div>Component not available</div> })), 
  { 
    ssr: false,
    loading: () => <div>Loading...</div>
  }
);

const MockDatePickerForm = ({ children }: { children: React.ReactNode }) => {
  const methods = useForm();
  return (
    <FormProvider {...methods}>
      <LocalizationProvider dateAdapter={AdapterDayjs}>
        {children}
      </LocalizationProvider>
    </FormProvider>
  );
};

export const DatepickerRenderer: React.FC<ComponentRendererProps> = ({ example }) => {
  switch (example) {
    case 'Basic Date Picker':
      return (
        <Box sx={{ minWidth: 200 }}>
          <MockDatePickerForm>
            <DatePickerField 
              name="date" 
              label="Select date" 
            />
          </MockDatePickerForm>
        </Box>
      );
    
    case 'With Constraints':
      return (
        <Box sx={{ minWidth: 200 }}>
          <MockDatePickerForm>
            <DatePickerField 
              name="birthDate" 
              label="Birth date" 
              helperText="Select your birth date" 
            />
          </MockDatePickerForm>
        </Box>
      );
    
    case 'Different Formats':
      return (
        <Box sx={{ minWidth: 200 }}>
          <MockDatePickerForm>
            <DatePickerField 
              name="startDate" 
              label="Start date" 
              helperText="Choose start date" 
            />
          </MockDatePickerForm>
        </Box>
      );
    
    default:
      return (
        <Box sx={{ minWidth: 200 }}>
          <MockDatePickerForm>
            <DatePickerField 
              name="date" 
              label="Date" 
            />
          </MockDatePickerForm>
        </Box>
      );
  }
};
