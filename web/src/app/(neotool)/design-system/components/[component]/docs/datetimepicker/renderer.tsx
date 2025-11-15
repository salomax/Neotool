"use client";

import React from "react";
import Box from '@mui/material/Box';
import dynamic from 'next/dynamic';
import dayjs from 'dayjs';
import { ComponentRendererProps } from '../types';

const DateTimePicker = dynamic(() => 
  import('@/shared/components/ui/primitives/DateTimePicker')
    .catch(() => ({ default: () => <div>Component not available</div> })), 
  { 
    ssr: false,
    loading: () => <div>Loading...</div>
  }
);

export const DatetimepickerRenderer: React.FC<ComponentRendererProps> = ({ example }) => {
  switch (example) {
    case 'Basic DateTimePicker':
      return (
        <DateTimePicker
          label="Select Date & Time"
          helperText="Choose your preferred date and time"
          onChange={(value) => console.log('DateTime changed:', value)}
        />
      );
    
    case 'Date Only':
      return (
        <DateTimePicker
          showDate
          showTime={false}
          label="Birth Date"
          placeholder="Select your birth date"
          helperText="Date of birth"
          onChange={(value) => console.log('Date changed:', value)}
        />
      );
    
    case 'Time Only':
      return (
        <DateTimePicker
          showDate={false}
          showTime
          label="Meeting Time"
          placeholder="Select meeting time"
          helperText="When should the meeting start?"
          onChange={(value) => console.log('Time changed:', value)}
        />
      );
    
    case 'With Seconds':
      return (
        <DateTimePicker
          showSeconds
          label="Precise Time"
          helperText="Include seconds for precise timing"
          onChange={(value) => console.log('DateTime changed:', value)}
        />
      );
    
    case '12-Hour Format':
      return (
        <DateTimePicker
          use24HourFormat={false}
          label="Event Time"
          helperText="12-hour format with AM/PM"
          onChange={(value) => console.log('DateTime changed:', value)}
        />
      );
    
    case 'With Constraints':
      return (
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          <DateTimePicker
            label="Future Date Only"
            disablePast
            helperText="Cannot select past dates"
            onChange={(value) => console.log('DateTime changed:', value)}
          />
          <DateTimePicker
            label="Past Date Only"
            disableFuture
            helperText="Cannot select future dates"
            onChange={(value) => console.log('DateTime changed:', value)}
          />
          <DateTimePicker
            label="Within Range"
            minDateTime={dayjs().subtract(1, 'month')}
            maxDateTime={dayjs().add(1, 'month')}
            helperText="Select date within the last month to next month"
            onChange={(value) => console.log('DateTime changed:', value)}
          />
        </Box>
      );
    
    default:
      return (
        <DateTimePicker
          label="Default DateTimePicker"
          onChange={(value) => console.log('DateTime changed:', value)}
        />
      );
  }
};
