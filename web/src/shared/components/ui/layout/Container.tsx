import React from 'react';
import { Container as MuiContainer, ContainerProps as MuiContainerProps } from '@mui/material';
import { getTestIdProps } from '@/shared/utils/testid';

export interface ContainerProps extends Omit<MuiContainerProps, 'maxWidth' | 'sx'> {
  /** 
   * When true, removes max-width constraints and sets height to 100%.
   * Overrides maxWidth prop when set to true.
   */
  fullSize?: boolean;
  /** Max width constraint. Ignored when fullSize is true. */
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
 * Container component wrapper that extends MUI Container with fullSize prop
 * for removing max-width constraints and setting height to 100%.
 * 
 * When fullSize is true, it also:
 * - Automatically disables gutters (removes default padding)
 * - Sets up flexbox column layout (display: flex, flexDirection: column)
 * - Prevents overflow (overflow: hidden)
 * 
 * This makes it easy to create full-height pages where children can use flex: 1
 * to fill remaining vertical space.
 * 
 * @example
 * ```tsx
 * <Container fullSize>
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
  fullSize = false,
  maxWidth,
  sx, 
  name,
  'data-testid': dataTestId,
  disableGutters,
  ...props 
}: ContainerProps) {
  // Generate data-testid from component name and optional name prop
  const testIdProps = getTestIdProps('Container', name, dataTestId);
  
  // When fullSize is true, override maxWidth, disable gutters, and set up flexbox layout
  const effectiveMaxWidth = fullSize ? false : maxWidth;
  const effectiveDisableGutters = fullSize ? true : (disableGutters ?? false);
  
  return (
    <MuiContainer
      maxWidth={effectiveMaxWidth}
      disableGutters={effectiveDisableGutters}
      sx={fullSize ? {
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        overflow: 'hidden',
        ...sx,
      } : sx}
      {...testIdProps}
      {...props}
    />
  );
}

export default Container;

