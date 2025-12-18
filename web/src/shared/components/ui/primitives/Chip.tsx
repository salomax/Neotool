import React from 'react';
import { Chip as MuiChip, ChipProps as MuiChipProps } from '@mui/material';
import { getTestIdProps } from '@/shared/utils/testid';

export interface ChipProps extends Omit<MuiChipProps, 'size'> {
  name?: string;
  'data-testid'?: string;
  size?: 'small' | 'medium' | 'large';
}

export const Chip: React.FC<ChipProps> = ({
  name,
  'data-testid': dataTestId,
  size = 'medium',
  sx,
  ...props
}) => {
  // Generate data-testid from component name and optional name prop
  const testIdProps = getTestIdProps('Chip', name, dataTestId);
  
  // Handle custom 'large' size with styling
  const chipSize = size === 'large' ? 'medium' : size;
  const largeSx = size === 'large' ? {
    height: 40,
    fontSize: '1rem',
    paddingX: 2,
    '& .MuiChip-label': {
      paddingLeft: 2,
      paddingRight: 2,
    },
    ...sx,
  } : sx;
  
  return (
    <MuiChip
      {...testIdProps}
      size={chipSize}
      sx={largeSx}
      {...props}
    />
  );
};

export default Chip;
