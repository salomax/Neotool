import React, { useState } from 'react';
import { Box } from '@mui/material';
import { DashboardGrid } from './components/DashboardGrid';
import { DashboardControls } from './components/DashboardControls';
import { AddWidgetDrawer } from './components/AddWidgetDrawer';
import { DashboardDateRangeParamsProvider } from './hooks/DashboardDateRangeParamsContext';

export const DashboardLayout: React.FC = () => {
  const [isAddDrawerOpen, setIsAddDrawerOpen] = useState(false);

  return (
    <DashboardDateRangeParamsProvider>
      <Box>
        <DashboardControls />
        <DashboardGrid onAddWidget={() => setIsAddDrawerOpen(true)} />
        <AddWidgetDrawer
          open={isAddDrawerOpen}
          onClose={() => setIsAddDrawerOpen(false)}
        />
      </Box>
    </DashboardDateRangeParamsProvider>
  );
};
