'use client';

import * as React from 'react';
import { Box, Typography } from '@mui/material';
import type { SxProps, Theme } from '@mui/material/styles';
import { Label } from '@/shared/components/ui/primitives/Label';

export interface TimeProgressItem {
  label: React.ReactNode;
  value: React.ReactNode;
}

export interface TimeProgressProps {
  percent: number | null | undefined;
  leftTitle: React.ReactNode;
  leftValue: React.ReactNode;
  rightTitle: React.ReactNode;
  rightValue: React.ReactNode;
  items?: readonly TimeProgressItem[];
  sx?: SxProps<Theme>;
}

export const TimeProgress: React.FC<TimeProgressProps> = ({
  percent,
  leftTitle,
  leftValue,
  rightTitle,
  rightValue,
  items = [],
  sx,
}) => {
  const clamped =
    typeof percent === 'number' && Number.isFinite(percent) ? Math.max(0, Math.min(1, percent)) : 0;

  return (
    <Box sx={{ display: 'grid', gap: 2, px: 2, ...sx }}>
      <Box
        sx={{
          display: 'grid',
          gridTemplateColumns: '1fr auto',
          alignItems: 'end',
          gap: 2,
        }}
      >
        <Box>
          <Label sx={{ mb: 0.5 }}>{leftTitle}</Label>
          <Typography variant="body2" fontWeight={700} color="primary.main">
            {leftValue}
          </Typography>
        </Box>
        <Box sx={{ justifySelf: 'end', textAlign: 'right' }}>
          <Label sx={{ mb: 0.5 }}>{rightTitle}</Label>
          <Typography variant="body2" fontWeight={700}>
            {rightValue}
          </Typography>
        </Box>
      </Box>

      <Box sx={{ position: 'relative', height: 6, borderRadius: 999, bgcolor: 'action.disabledBackground' }}>
        <Box
          sx={{
            position: 'absolute',
            left: 0,
            top: 0,
            bottom: 0,
            width: `${clamped * 100}%`,
            borderRadius: 999,
            bgcolor: 'primary.main',
            transition: (theme) => theme.transitions.create(['width'], { duration: theme.transitions.duration.shorter }),
          }}
        />
      </Box>

      {items.length > 0 ? (
        <Box
          sx={{
            display: 'grid',
            gridTemplateColumns: { xs: '1fr', sm: `repeat(${Math.min(items.length, 3)}, minmax(0, 1fr))` },
            gap: 2,
            alignItems: 'start',
          }}
        >
          {items.map((it, idx) => (
            <Box key={idx} sx={{ textAlign: 'center' }}>
              <Label sx={{ mb: 0.5 }}>{it.label}</Label>
              <Typography variant="body2" fontWeight={700}>
                {it.value}
              </Typography>
            </Box>
          ))}
        </Box>
      ) : null}
    </Box>
  );
};

export default TimeProgress;
