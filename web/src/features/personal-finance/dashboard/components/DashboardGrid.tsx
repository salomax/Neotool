import React from 'react';
import { Box, Typography, Button, Paper } from '@mui/material';
import { Responsive, useContainerWidth } from 'react-grid-layout';
import type { Layout, ResponsiveLayouts } from 'react-grid-layout';
import { useDashboardStore } from '../store';
import { getWidgetDefinition } from '../registry';
import { WidgetDefinition, WidgetInstance } from '../types';
import AddIcon from '@mui/icons-material/Add';
import 'react-grid-layout/css/styles.css';
import './DashboardGrid.css';

const BREAKPOINTS = { lg: 1200, md: 996, sm: 768, xs: 480, xxs: 0 };
const COLS = { lg: 12, md: 12, sm: 12, xs: 12, xxs: 12 };

interface DashboardGridProps {
  onAddWidget: () => void;
}

export const DashboardGrid: React.FC<DashboardGridProps> = ({ onAddWidget }) => {
  const { widgets, layouts, updateLayouts, resetLayout } = useDashboardStore();
  const { width, containerRef, mounted } = useContainerWidth();
  const visibleWidgets = widgets.filter((w) => w.isVisible);
  const visibleDefinitions: Array<{ widget: WidgetInstance; definition: WidgetDefinition }> = visibleWidgets
    .map((widget) => ({ widget, definition: getWidgetDefinition(widget.type) }))
    .filter((entry): entry is { widget: WidgetInstance; definition: WidgetDefinition } => Boolean(entry.definition));

  if (visibleWidgets.length === 0 || visibleDefinitions.length === 0) {
    return (
      <Paper sx={{ p: 4, textAlign: 'center', bgcolor: 'background.default', borderStyle: 'dashed' }}>
        <Typography variant="h6" color="text.secondary" gutterBottom>
          No widgets visible
        </Typography>
        <Box sx={{ display: 'flex', justifyContent: 'center', gap: 1 }}>
          <Button startIcon={<AddIcon />} onClick={onAddWidget} variant="contained">
            Add Your First Widget
          </Button>
          <Button onClick={resetLayout} variant="outlined">
            Reset Layout
          </Button>
        </Box>
      </Paper>
    );
  }

  return (
    <Box
      sx={{
        flexGrow: 1,
        '& .react-resizable-handle': {
          width: 22,
          height: 22,
          padding: 0,
          backgroundImage: 'none',
          color: 'action.active',
          opacity: 0.4,
          cursor: 'se-resize',
          overflow: 'hidden',
          '&:hover': { opacity: 0.9 },
        },
        '& .react-resizable-handle::after': {
          content: '""',
          position: 'absolute',
          right: 4,
          bottom: 4,
          width: 8,
          height: 8,
          borderRight: '2px solid currentColor',
          borderBottom: '2px solid currentColor',
        },
      }}
      ref={containerRef}
      className="dashboard-grid-layout"
    >
      {mounted && (
        <Responsive
          className="layout"
          layouts={layouts}
          breakpoints={BREAKPOINTS}
          cols={COLS}
          rowHeight={100}
          width={width}
          dragConfig={{
            enabled: true,
            handle: '.drag-handle',
            threshold: 3,
          }}
          resizeConfig={{
            enabled: true,
            handles: ['se'],
          }}
          onLayoutChange={(_layout: Layout, allLayouts: ResponsiveLayouts) => {
            updateLayouts(allLayouts as { [key: string]: Layout });
          }}
          margin={[24, 24]}
          containerPadding={[0, 0]}
        >
          {visibleDefinitions.map(({ widget, definition }) => {
            const WidgetComponent = definition.component;

            return (
              <div key={widget.id}>
                <WidgetComponent widget={widget} />
              </div>
            );
          })}
        </Responsive>
      )}
    </Box>
  );
};
