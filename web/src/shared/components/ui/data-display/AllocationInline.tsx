import * as React from 'react';
import { Box, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import type { SxProps, Theme } from '@mui/material/styles';

export interface AllocationInlineProps {
  value?: number | null;
  label: React.ReactNode;
  sx?: SxProps<Theme>;
  barWidth?: number | string;
  barHeight?: number | string;
  barSx?: SxProps<Theme>;
  trackSx?: SxProps<Theme>;
  textVariant?: React.ComponentProps<typeof Typography>['variant'];
  textSx?: SxProps<Theme>;
}

export function AllocationInline({
  value,
  label,
  sx,
  barWidth = 28,
  barHeight = 6,
  barSx,
  trackSx,
  textVariant = 'caption',
  textSx,
}: AllocationInlineProps) {
  const theme = useTheme();
  const clampedValue = typeof value === 'number' && Number.isFinite(value) ? Math.min(1, Math.max(0, value)) : null;

  if (clampedValue == null) {
    return (
      <Typography variant={textVariant} color="text.secondary" sx={textSx}>
        {label}
      </Typography>
    );
  }

  return (
    <Box component="span" sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.75, ...(sx as any) }}>
      <Box
        component="span"
        sx={{
          width: barWidth,
          height: barHeight,
          borderRadius: 999,
          bgcolor: alpha(theme.palette.text.primary, 0.10),
          overflow: 'hidden',
          flex: '0 0 auto',
          ...(trackSx as any),
        }}
        aria-hidden
      >
        <Box
          component="span"
          sx={{
            display: 'block',
            width: `${clampedValue * 100}%`,
            height: '100%',
            borderRadius: 999,
            bgcolor: theme.palette.primary.main,
            ...(barSx as any),
          }}
        />
      </Box>
      <Typography variant={textVariant} color="text.secondary" sx={{ ...(textSx as any) }}>
        {label}
      </Typography>
    </Box>
  );
}

