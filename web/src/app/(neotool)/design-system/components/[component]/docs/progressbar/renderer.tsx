"use client";

import React from "react";
import Box from '@mui/material/Box';
import { ProgressBar } from '@/shared/components/ui/primitives/ProgressBar';
import { ComponentRendererProps } from '../types';

export const ProgressbarRenderer: React.FC<ComponentRendererProps> = ({ example }) => {
  switch (example) {
    case 'Basic ProgressBar':
      return (
        <ProgressBar
          value={50}
          label="Progress"
          helperText="Current progress status"
        />
      );
    
    case 'Linear Progress':
      return (
        <ProgressBar
          variant="linear"
          value={75}
          label="Linear Progress"
          helperText="Linear progress bar example"
        />
      );
    
    case 'Circular Progress':
      return (
        <ProgressBar
          variant="circular"
          value={60}
          label="Circular Progress"
          helperText="Circular progress bar example"
        />
      );
    
    case 'Step Progress':
      return (
        <ProgressBar
          variant="step"
          currentStep={2}
          totalSteps={5}
          steps={['Start', 'Process', 'Review', 'Approve', 'Complete']}
          label="Step Progress"
          helperText="Step-by-step progress example"
        />
      );
    
    case 'Indeterminate':
      return (
        <ProgressBar
          indeterminate
          label="Loading..."
          helperText="Indeterminate progress for loading states"
        />
      );
    
    case 'With Status':
      return (
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          <ProgressBar
            variant="linear"
            value={100}
            color="success"
            label="Completed"
            helperText="Task completed successfully"
          />
          <ProgressBar
            variant="linear"
            value={30}
            color="error"
            label="Failed"
            error
            errorMessage="Process failed"
            helperText="Task failed with error"
          />
          <ProgressBar
            variant="step"
            currentStep={1}
            totalSteps={4}
            steps={['Start', 'Process', 'Review', 'Complete']}
            stepStatus={['completed', 'active', 'pending', 'pending']}
            label="Step with Status"
            helperText="Steps with different status indicators"
          />
        </Box>
      );
    
    default:
      return (
        <ProgressBar
          value={30}
          label="Default Progress"
          helperText="Default progress bar"
        />
      );
  }
};
