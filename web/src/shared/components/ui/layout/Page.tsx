import React from 'react';
import { Box, BoxProps } from '@mui/material';

export interface PageProps extends BoxProps {}

export const Page = React.forwardRef<HTMLDivElement, PageProps>((props, ref) => {
  return (
    <Box
      ref={ref}
      component="div"
      {...props}
      sx={{
        p: 3,
        ...props.sx,
      }}
    />
  );
});

Page.displayName = 'Page';