import React from 'react';
import { Box, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import type { SxProps, Theme } from '@mui/material/styles';

export interface GainPercentProps {
  value?: number | null;
  locale?: string;
  sx?: SxProps<Theme>;
  variant?: React.ComponentProps<typeof Typography>['variant'];
  textSx?: SxProps<Theme>;
  iconSize?: number | string;
}

export const GainPercent: React.FC<GainPercentProps> = ({ value, locale, sx, variant, textSx, iconSize }) => {
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
    (value as number) > 0
      ? TrendingUpIcon
      : (value as number) < 0
        ? TrendingDownIcon
        : null;

  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, color, ...(sx as any) }}>
      {Icon ? <Icon sx={{ fontSize: iconSize ?? 14, color: 'inherit' }} /> : null}
      <Typography
        variant={variant ?? 'caption'}
        color="inherit"
        sx={{ fontVariantNumeric: 'tabular-nums', ...(textSx as any) }}
      >
        {new Intl.NumberFormat(locale, {
          style: 'percent',
          maximumFractionDigits: 2,
        }).format(value as number)}
      </Typography>
    </Box>
  );
};
