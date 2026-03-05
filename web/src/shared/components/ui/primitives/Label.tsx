import React from 'react';
import { Typography, TypographyProps } from '@mui/material';

export interface LabelProps extends TypographyProps {
  component?: React.ElementType;
}

export const Label: React.FC<LabelProps> = ({ 
  children, 
  sx, 
  ...props 
}) => {
  return (
    <Typography
      variant="subtitle2"
      color="text.secondary"
      sx={{
        textTransform: 'uppercase',
        fontSize: '0.7rem',
        fontWeight: 600,
        letterSpacing: 0.5,
        mb: 0.5,
        ...sx,
      }}
      {...props}
    >
      {children}
    </Typography>
  );
};