'use client';

import * as React from 'react';
import { Box, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import type { SxProps, Theme } from '@mui/material/styles';

export interface PercentageGainProps {
  value?: number | null;
  locale?: string;
  showIcon?: boolean;
  variant?: React.ComponentProps<typeof Typography>['variant'];
  fontWeight?: React.ComponentProps<typeof Typography>['fontWeight'];
  sx?: SxProps<Theme>;
  textSx?: SxProps<Theme>;
  iconSize?: number | string;
  maximumFractionDigits?: number;
}

export const PercentageGain: React.FC<PercentageGainProps> = ({
  value,
  locale,
  showIcon = true,
  variant = 'caption',
  fontWeight = 'medium',
  sx,
  textSx,
  iconSize,
  maximumFractionDigits = 2,
}) => {
  const theme = useTheme();
  const hasValue = typeof value === 'number' && Number.isFinite(value);
  if (!hasValue) return null;

  const color =
    (value as number) > 0
      ? theme.palette.success.main
      : (value as number) < 0
      ? theme.palette.error.main
      : theme.palette.text.secondary;

  const Icon =
    showIcon && (value as number) !== 0
      ? (value as number) > 0
        ? TrendingUpIcon
        : TrendingDownIcon
      : null;

  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, color, ...(sx as any) }}>
      {Icon ? <Icon sx={{ fontSize: iconSize ?? 12, color: 'inherit' }} /> : null}
      <Typography
        variant={variant}
        fontWeight={fontWeight}
        color="inherit"
        sx={{ fontVariantNumeric: 'tabular-nums', ...(textSx as any) }}
      >
        {new Intl.NumberFormat(locale, {
          style: 'percent',
          maximumFractionDigits,
        }).format(value as number)}
      </Typography>
    </Box>
  );
};

export default PercentageGain;
