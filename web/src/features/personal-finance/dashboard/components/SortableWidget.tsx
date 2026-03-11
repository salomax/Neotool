import React from 'react';
import { useSortable } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import { Grid } from '@mui/material';
import { WidgetInstance } from '../types';

interface SortableWidgetProps {
  widget: WidgetInstance;
  children: React.ReactElement;
}

export const SortableWidget: React.FC<SortableWidgetProps> = ({ widget, children }) => {
  const {
    attributes,
    listeners,
    setNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({ id: widget.id });

  const style = {
    transform: CSS.Translate.toString(transform),
    transition,
    opacity: isDragging ? 0 : 1,
    height: '100%',
    zIndex: isDragging ? 1 : 'auto',
  };

  const gridSize = widget.grid?.w ?? { xs: 12, md: 12, lg: 12 };
  return (
    <Grid
      ref={setNodeRef}
      style={style}
      size={{
        xs: gridSize.xs ?? 12,
        md: gridSize.md ?? gridSize.xs ?? 12,
        lg: gridSize.lg ?? gridSize.md ?? 12
      }}
    >
      {/* Pass drag listeners to the child component (WidgetWrapper) */}
      {React.cloneElement(children, { 
        dragHandleProps: { ...attributes, ...listeners } 
      })}
    </Grid>
  );
};
