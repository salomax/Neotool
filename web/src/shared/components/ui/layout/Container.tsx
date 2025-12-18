import React from 'react';
import { Container as MuiContainer, ContainerProps as MuiContainerProps } from '@mui/material';
import { getTestIdProps } from '@/shared/utils/testid';

export interface ContainerProps extends Omit<MuiContainerProps, 'maxWidth' | 'sx'> {
  /** 
   * When true, applies full-height flex column layout with overflow hidden.
   * Applies: flex: 1, display: 'flex', flexDirection: 'column', minHeight: 0, height: '100%', overflow: 'hidden'
   * Does not affect maxWidth prop.
   */
  fullHeight?: boolean;
  /** Max width constraint. */
  maxWidth?: MuiContainerProps['maxWidth'];
  /** Custom styles to apply to the Container component. */
  sx?: MuiContainerProps['sx'];
  /** 
   * Optional name used to generate data-testid. 
   * If provided, data-testid will be "container-{name}".
   * If both name and data-testid are provided, data-testid takes precedence.
   */
  name?: string;
  /** Custom data-testid attribute. Takes precedence over generated testid from name prop. */
  'data-testid'?: string;
}

/**
 * Container component wrapper that extends MUI Container with fullHeight prop
 * for creating full-height layouts.
 * 
 * This makes it easy to create full-height pages where children can use flex: 1
 * to fill remaining vertical space.
 * 
 * @example
 * ```tsx
 * <Container fullHeight>
 *   <Typography variant="h4">Title</Typography>
 *   <Box sx={{ flex: 1, overflow: 'auto' }}>
 *     Content that fills remaining space
 *   </Box>
 * </Container>
 * ```
 * 
 * @example
 * ```tsx
 * <Container maxWidth="lg" sx={{ py: 3 }}>
 *   Content with max width constraint (default gutters enabled)
 * </Container>
 * ```
 */
export function Container({ 
  fullHeight = false,
  maxWidth,
  sx, 
  name,
  'data-testid': dataTestId,
  disableGutters,
  ...props 
}: ContainerProps) {
  // Generate data-testid from component name and optional name prop
  const testIdProps = getTestIdProps('Container', name, dataTestId);
  
  // When fullHeight is true, apply full-height flex column layout
  const fullHeightStyles = fullHeight ? {
    flex: 1,
    display: 'flex',
    flexDirection: 'column',
    minHeight: 0,
    height: '100%',
    overflow: 'hidden',
  } : {};
  
  return (
    <MuiContainer
      maxWidth={maxWidth}
      disableGutters={disableGutters ?? false}
      sx={[
        ...(fullHeight ? [fullHeightStyles] : []),
        ...(Array.isArray(sx) ? sx : sx ? [sx] : []),
      ]}
      {...testIdProps}
      {...props}
    />
  );
}

export default Container;

