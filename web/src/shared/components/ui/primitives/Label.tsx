import type { ElementType, FC, ReactNode } from 'react';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import type { TypographyProps } from '@mui/material/Typography';
import type { SxProps, Theme } from '@mui/material/styles';

export const LABEL_FONT_SIZE = '0.7rem';
export const LABEL_LINE_HEIGHT = 1.35;

/** Typography aligned with `<Label />` for reuse (e.g. MUI date picker month header and month grid). */
export function labelTypographySx(theme: Theme): SxProps<Theme> {
  return {
    ...theme.typography.subtitle2,
    color: theme.palette.text.secondary,
    textTransform: 'uppercase',
    fontSize: LABEL_FONT_SIZE,
    fontWeight: 400,
    lineHeight: LABEL_LINE_HEIGHT,
    letterSpacing: 0.5,
  };
}

export interface LabelProps extends TypographyProps {
  children?: ReactNode;
  component?: ElementType;
  icon?: ReactNode;
}

export const Label: FC<LabelProps> = ({ children, icon, sx, ...props }) => {
  const sxMerged: SxProps<Theme> = [
    (theme) => ({
      ...labelTypographySx(theme),
      mb: 0.5,
      ...(icon
        ? {
            display: 'flex',
            alignItems: 'center',
            gap: 1,
          }
        : null),
    }),
    ...(sx == null ? [] : Array.isArray(sx) ? sx : [sx]),
  ];
  return (
    <Typography variant="subtitle2" color="text.secondary" sx={sxMerged} {...props}>
      {icon ? (
        <Box
          component="span"
          sx={(theme) => ({
            display: 'inline-flex',
            alignItems: 'center',
            color: theme.custom.palette.labelIcon ?? theme.palette.text.disabled,
          })}
        >
          {icon}
        </Box>
      ) : null}
      {children}
    </Typography>
  );
};
