"use client";

import React from "react";
import Box from '@mui/material/Box';
import { Switch } from '@/shared/components/ui/primitives/Switch';
import { ComponentRendererProps } from '../types';

export const SwitchRenderer: React.FC<ComponentRendererProps> = ({ example }) => {
  switch (example) {
    case 'Basic Switch':
      return (
        <Switch
          label="Enable notifications"
          helperText="Turn on to receive notifications"
          onChange={(checked) => console.log('Switch changed:', checked)}
        />
      );
    
    case 'With Status':
      return (
        <Switch
          checked={true}
          label="Dark mode"
          showStatus
          helperText="Switch to dark theme"
          onChange={(checked) => console.log('Switch changed:', checked)}
        />
      );
    
    case 'Custom Labels':
      return (
        <Switch
          checked={true}
          label="WiFi"
          checkedLabel="Connected"
          uncheckedLabel="Disconnected"
          helperText="Network connection status"
          onChange={(checked) => console.log('Switch changed:', checked)}
        />
      );
    
    case 'Different Placements':
      return (
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          <Switch
            checked={true}
            label="Label Start"
            labelPlacement="start"
            showStatus
            onChange={(checked) => console.log('Switch changed:', checked)}
          />
          <Switch
            checked={false}
            label="Label Top"
            labelPlacement="top"
            showStatus
            onChange={(checked) => console.log('Switch changed:', checked)}
          />
          <Switch
            checked={true}
            label="Label Bottom"
            labelPlacement="bottom"
            showStatus
            onChange={(checked) => console.log('Switch changed:', checked)}
          />
        </Box>
      );
    
    case 'Different Sizes':
      return (
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          <Switch
            checked={true}
            label="Small Switch"
            size="small"
            showStatus
            onChange={(checked) => console.log('Switch changed:', checked)}
          />
          <Switch
            checked={true}
            label="Medium Switch"
            size="medium"
            showStatus
            onChange={(checked) => console.log('Switch changed:', checked)}
          />
        </Box>
      );
    
    case 'Different Colors':
      return (
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          <Switch
            checked={true}
            label="Primary"
            color="primary"
            showStatus
            onChange={(checked) => console.log('Switch changed:', checked)}
          />
          <Switch
            checked={true}
            label="Success"
            color="success"
            showStatus
            onChange={(checked) => console.log('Switch changed:', checked)}
          />
          <Switch
            checked={true}
            label="Warning"
            color="warning"
            showStatus
            onChange={(checked) => console.log('Switch changed:', checked)}
          />
          <Switch
            checked={true}
            label="Error"
            color="error"
            showStatus
            onChange={(checked) => console.log('Switch changed:', checked)}
          />
        </Box>
      );
    
    default:
      return (
        <Switch
          checked={false}
          label="Default Switch"
          showStatus
          onChange={(checked) => console.log('Switch changed:', checked)}
        />
      );
  }
};
