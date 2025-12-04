import React from 'react';
import { Box as MuiBox, BoxProps as MuiBoxProps } from '@mui/material';
import { getTestIdProps } from '@/shared/utils/testid';

export interface BoxProps extends Omit<MuiBoxProps, 'sx'> {
  /** 
   * When true, removes max-width constraints and sets height to 100%.
   */
  fullSize?: boolean;
  /** 
   * When true, automatically fills remaining space in a flex container.
   * Applies: flex: 1, overflow: 'auto', minHeight: 0
   * Useful for content areas that should fill remaining vertical space.
   */
  autoFill?: boolean;
  /** 
   * When true, applies full-height flex column layout with overflow hidden.
   * Applies: flex: 1, display: 'flex', flexDirection: 'column', minHeight: 0, height: '100%', overflow: 'hidden'
   */
  fullHeight?: boolean;
  /** Custom styles to apply to the Box component. */
  sx?: MuiBoxProps['sx'];
  /** 
   * Optional name used to generate data-testid. 
   * If provided, data-testid will be "box-{name}".
   * If both name and data-testid are provided, data-testid takes precedence.
   */
  name?: string;
  /** Custom data-testid attribute. Takes precedence over generated testid from name prop. */
  'data-testid'?: string;
}

/**
 * Box component wrapper that extends MUI Box with fullSize and autoFill props.
 * 
 * @example
 * ```tsx
 * <Container fullHeight>
 *   <Typography>Title</Typography>
 *   <Box autoFill>
 *     Content that fills remaining space
 *   </Box>
 * </Container>
 * ```
 * 
 * @example
 * ```tsx
 * <Box fullSize sx={{ display: 'flex' }}>
 *   Content here
 * </Box>
 * ```
 * 
 * @example
 * ```tsx
 * <Box sx={{ maxWidth: 400 }}>
 *   Content with max width constraint
 * </Box>
 * ```
 */
export const Box = React.forwardRef<HTMLDivElement, BoxProps>(function Box( 
{ 
  fullSize = false,
  autoFill = false,
  fullHeight = false,
  sx, 
  name,
  'data-testid': dataTestId,
  ...props 
}: BoxProps, ref) {
  // Generate data-testid from component name and optional name prop
  const testIdProps = getTestIdProps('Box', name, dataTestId);
  
  // When fullSize is true, remove max-width constraints and add height: 100%
  const fullSizeStyles = fullSize ? {
    maxWidth: 'none',
    height: '100%',
  } : {};
  
  // When autoFill is true, fill remaining space in flex container
  const autoFillStyles = autoFill ? {
    flex: 1,
    overflow: 'auto',
    minHeight: 0,
  } : {};
  
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
    <MuiBox
      ref={ref}
      sx={fullSize || autoFill || fullHeight ? {
        ...fullSizeStyles,
        ...autoFillStyles,
        ...fullHeightStyles,
        ...sx,
      } : sx}
      {...testIdProps}
      {...props}
    />
  );
});

export default Box;
