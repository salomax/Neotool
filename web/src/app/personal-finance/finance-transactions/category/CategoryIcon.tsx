'use client';

import React from 'react';
import type { SvgIconProps } from '@mui/material/SvgIcon';
import type { SxProps, Theme } from '@mui/material/styles';
import HelpOutlineIcon from '@mui/icons-material/HelpOutline';
import { categoryIconMap } from './categoryIconMap';

export interface CategoryIconProps {
  iconName: string | null | undefined;
  color?: string | null | undefined;
  size?: number;
  sx?: SxProps<Theme>;
}

/**
 * Renders a category icon by MUI icon name (snake_case from backend).
 * Falls back to HelpOutlineIcon for unknown names.
 */
export function CategoryIcon({
  iconName,
  color,
  size = 24,
  sx,
}: CategoryIconProps) {
  const IconComponent = (iconName && categoryIconMap[iconName]) ?? HelpOutlineIcon;
  const Component = IconComponent as React.ComponentType<SvgIconProps>;
  return (
    <Component
      sx={{
        color: color ?? 'text.secondary',
        fontSize: size,
        ...sx,
      }}
    />
  );
}
