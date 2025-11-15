import React from 'react';
import { IconButton as MuiIconButton, IconButtonProps as MuiIconButtonProps } from '@mui/material';
import { getTestIdProps } from '@/shared/utils/testid';

export interface IconButtonProps extends Omit<MuiIconButtonProps, 'size'> {
  size?: 'small' | 'medium' | 'large';
  name?: string;
  'data-testid'?: string;
}

export const IconButton: React.FC<IconButtonProps> = ({
  name,
  size = 'medium',
  'data-testid': dataTestId,
  sx,
  ...props
}) => {
  // Generate data-testid from component name and optional name prop
  const testIdProps = getTestIdProps('IconButton', name, dataTestId);
  
  // Map our size to MUI size (MUI only supports 'small' and 'medium')
  const muiSize = size === 'large' ? 'medium' : size;
  
  // Custom styling for all sizes to ensure clear differences
  const sizeSx = {
    small: {
      padding: '5px',
      '& svg': {
        fontSize: '1.25rem', // 20px
      },
    },
    medium: {
      padding: '12px',
      '& svg': {
        fontSize: '1.5rem', // 24px
      },
    },
    large: {
      padding: '16px',
      '& svg': {
        fontSize: '2rem', // 32px
      },
    },
  };
  
  return (
    <MuiIconButton
      size={muiSize}
      sx={{
        ...sizeSx[size],
        ...sx,
      }}
      {...testIdProps}
      {...props}
    />
  );
};

export default IconButton;
