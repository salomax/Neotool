'use client';

import * as React from 'react';
import { Stack, Typography } from '@mui/material';
import type { SxProps, Theme } from '@mui/material/styles';
import { Label } from '@/shared/components/ui/primitives/Label';

export interface InfoFieldProps {
  label: React.ReactNode;
  value: React.ReactNode;
  footer?: React.ReactNode;
  valueColor?: 'text.primary' | 'text.secondary';
  sx?: SxProps<Theme>;
}

export const InfoField: React.FC<InfoFieldProps> = ({
  label,
  value,
  footer,
  valueColor = 'text.primary',
  sx,
}) => {
  return (
    <Stack spacing={0.25} sx={sx}>
      <Label sx={{ mb: 0 }}>{label}</Label>
      <Typography variant="body2" color={valueColor} sx={{ fontWeight: 600 }}>
        {value}
      </Typography>
      {footer ?? null}
    </Stack>
  );
};

