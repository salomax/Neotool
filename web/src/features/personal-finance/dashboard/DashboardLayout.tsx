import React, { useState } from 'react';
import { Box } from '@mui/material';
import { DashboardGrid } from './components/DashboardGrid';
import { DashboardControls } from './components/DashboardControls';
import { AddWidgetDrawer } from './components/AddWidgetDrawer';

export const DashboardLayout: React.FC = () => {
  const [isAddDrawerOpen, setIsAddDrawerOpen] = useState(false);

  return (
    <Box>
      <DashboardControls onAddWidget={() => setIsAddDrawerOpen(true)} />
      <DashboardGrid onAddWidget={() => setIsAddDrawerOpen(true)} />
      <AddWidgetDrawer
        open={isAddDrawerOpen}
        onClose={() => setIsAddDrawerOpen(false)}
      />
    </Box>
  );
};
