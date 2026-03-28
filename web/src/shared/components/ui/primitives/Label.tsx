import React from 'react';
import { Typography, TypographyProps } from '@mui/material';
import type { SxProps, Theme } from '@mui/material/styles';

/** Typography aligned with `<Label />` for reuse (e.g. MUI date picker month header and month grid). */
export function labelTypographySx(theme: Theme): SxProps<Theme> {
  return {
    ...theme.typography.subtitle2,
    color: theme.palette.text.secondary,
    textTransform: 'uppercase',
    fontSize: '0.7rem',
    fontWeight: 400,
    letterSpacing: 0.5,
  };
}

export interface LabelProps extends TypographyProps {
  component?: React.ElementType;
}

export const Label: React.FC<LabelProps> = ({
  children,
  sx,
  ...props
}) => {
  const sxMerged: SxProps<Theme> = [
    (theme) => ({
      ...labelTypographySx(theme),
      mb: 0.5,
    }),
    ...(sx == null ? [] : Array.isArray(sx) ? sx : [sx]),
  ];
  return (
    <Typography variant="subtitle2" color="text.secondary" sx={sxMerged} {...props}>
      {children}
    </Typography>
  );
};