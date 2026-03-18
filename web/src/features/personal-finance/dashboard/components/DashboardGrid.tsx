import React from 'react';
import { Box, Typography, Button, Paper } from '@mui/material';
import { Responsive, useContainerWidth } from 'react-grid-layout';
import type { Layout, ResponsiveLayouts } from 'react-grid-layout';
import { useDashboardStore } from '../store';
import { getWidgetDefinition } from '../registry';
import AddIcon from '@mui/icons-material/Add';
import 'react-grid-layout/css/styles.css';
import './DashboardGrid.css';

const BREAKPOINTS = { lg: 1200, md: 996, sm: 768, xs: 480, xxs: 0 };
const COLS = { lg: 12, md: 12, sm: 12, xs: 12, xxs: 12 };

interface DashboardGridProps {
  onAddWidget: () => void;
}

export const DashboardGrid: React.FC<DashboardGridProps> = ({ onAddWidget }) => {
  const { widgets, layouts, isEditMode, updateLayouts } = useDashboardStore();
  const { width, containerRef, mounted } = useContainerWidth();
  const visibleWidgets = widgets.filter((w) => w.isVisible || isEditMode);

  if (visibleWidgets.length === 0 && !isEditMode) {
    return (
      <Paper sx={{ p: 4, textAlign: 'center', bgcolor: 'background.default', borderStyle: 'dashed' }}>
        <Typography variant="h6" color="text.secondary" gutterBottom>
          No widgets visible
        </Typography>
        <Button startIcon={<AddIcon />} onClick={onAddWidget} variant="contained">
          Add Your First Widget
        </Button>
      </Paper>
    );
  }

  return (
    <Box sx={{ flexGrow: 1 }} ref={containerRef} className="dashboard-grid-layout">
      {mounted && (
        <Responsive
          className="layout"
          layouts={layouts}
          breakpoints={BREAKPOINTS}
          cols={COLS}
          rowHeight={100}
          width={width}
          dragConfig={{
            enabled: isEditMode,
            handle: '.drag-handle',
            threshold: 3,
          }}
          resizeConfig={{
            enabled: isEditMode,
          }}
          onLayoutChange={(_layout: Layout, allLayouts: ResponsiveLayouts) => {
            updateLayouts(allLayouts as { [key: string]: Layout });
          }}
          margin={[24, 24]}
          containerPadding={[0, 0]}
        >
          {visibleWidgets.map((widget) => {
            const definition = getWidgetDefinition(widget.type);

            if (!definition) {
              return null;
            }

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
