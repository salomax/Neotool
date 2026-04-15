import type { Theme } from '@mui/material';
import type React from 'react';

/**
 * Returns the sx object for an interactive (clickable) summary row.
 * All three row variants (SummaryMetricRow, CategoryProgressRow, TagProgressRow) share this styling.
 */
export function interactiveRowSx(theme: Theme, interactive: boolean, dimmed: boolean) {
  return {
    borderRadius: 1,
    px: 1,
    py: 0.75,
    opacity: dimmed ? 0.55 : 1,
    filter: dimmed ? 'grayscale(1)' : 'none',
    transition: theme.transitions.create(['opacity', 'filter', 'background-color'], {
      duration: theme.transitions.duration.shorter,
    }),
    cursor: interactive ? 'pointer' : 'default',
    pointerEvents: dimmed ? 'none' : 'auto',
    ...(interactive
      ? {
          '&:hover': { bgcolor: theme.palette.action.hover },
          '&:focus-visible': { outline: `2px solid ${theme.palette.primary.main}`, outlineOffset: 2 },
        }
      : {}),
  } as const;
}

/**
 * Returns the shared keyboard handler for Enter/Space activation.
 */
export function makeKeyDownHandler(onActivate: () => void) {
  return (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      onActivate();
    }
  };
}
