'use client';

import * as React from 'react';
import { Box, Skeleton, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import type { SxProps, Theme } from '@mui/material/styles';

function typographyStyleFromTheme(
  theme: Theme,
  variant: React.ComponentProps<typeof Typography>['variant'],
): { fontSize?: string | number; lineHeight?: string | number } {
  const key = (variant ?? 'body2') as keyof typeof theme.typography;
  const raw = theme.typography[key];
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return {};
  const o = raw as Record<string, unknown>;
  const fontSize = o.fontSize;
  const lineHeight = o.lineHeight;
  return {
    fontSize:
      typeof fontSize === 'string' || typeof fontSize === 'number'
        ? fontSize
        : undefined,
    lineHeight:
      typeof lineHeight === 'string' || typeof lineHeight === 'number'
        ? lineHeight
        : undefined,
  };
}

function skeletonLineBoxHeight(
  theme: Theme,
  variant: React.ComponentProps<typeof Typography>['variant'],
  textSx?: SxProps<Theme>,
): string {
  const ty = typographyStyleFromTheme(theme, variant);
  let fontSize =
    typeof ty.fontSize === 'string'
      ? ty.fontSize
      : typeof ty.fontSize === 'number'
        ? `${ty.fontSize}px`
        : '0.75rem';
  if (
    textSx &&
    typeof textSx === 'object' &&
    !Array.isArray(textSx) &&
    textSx !== null &&
    'fontSize' in textSx
  ) {
    const fs = (textSx as { fontSize?: string | number }).fontSize;
    if (typeof fs === 'string') fontSize = fs;
    else if (typeof fs === 'number') fontSize = `${fs}px`;
  }
  const lh = ty.lineHeight;
  if (typeof lh === 'number') return `calc(${fontSize} * ${lh})`;
  if (typeof lh === 'string') return lh;
  return `calc(${fontSize} * 1.25)`;
}

function iconSizePx(iconSize?: number | string): number {
  if (typeof iconSize === 'number' && Number.isFinite(iconSize)) return iconSize;
  if (typeof iconSize === 'string') {
    const n = parseFloat(iconSize);
    if (Number.isFinite(n)) return n;
  }
  return 12;
}

export interface PercentageGainProps {
  value?: number | null;
  /** When true and there is no numeric value yet, shows a layout-matched skeleton. */
  loading?: boolean;
  /** Width of the text skeleton bar (default: 62 without icon, 52 with icon). */
  skeletonTextWidth?: number;
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
  loading = false,
  skeletonTextWidth,
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
  const lineBox = skeletonLineBoxHeight(theme, variant, textSx);
  const defaultTextBarWidth = showIcon ? 52 : 62;
  const textBarWidth = skeletonTextWidth ?? defaultTextBarWidth;

  if (loading && !hasValue) {
    const ipx = iconSizePx(iconSize);
    return (
      <Box
        sx={{ display: 'flex', alignItems: 'center', gap: 0.5, ...(sx as any) }}
        aria-busy
      >
        {showIcon ? (
          <Skeleton
            variant="circular"
            width={ipx}
            height={ipx}
            sx={{ flexShrink: 0, transform: 'none' }}
          />
        ) : null}
        <Skeleton
          variant="rounded"
          width={textBarWidth}
          sx={{
            height: lineBox,
            transform: 'none',
            borderRadius: 1,
          }}
        />
      </Box>
    );
  }

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
