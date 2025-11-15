import React from 'react';
import { Paper as MuiPaper, PaperProps as MuiPaperProps } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { getTestIdProps } from '@/shared/utils/testid';

export interface PaperProps extends Omit<MuiPaperProps, 'elevation' | 'sx'> {
  /** Shadow depth (0-24). Defaults to 1. */
  elevation?: number;
  /** Custom styles to apply to the Paper component. */
  sx?: MuiPaperProps['sx'];
  /** 
   * Optional name used to generate data-testid. 
   * If provided, data-testid will be "paper-{name}".
   * If both name and data-testid are provided, data-testid takes precedence.
   */
  name?: string;
  /** Custom data-testid attribute. Takes precedence over generated testid from name prop. */
  'data-testid'?: string;
}

/**
 * Paper component wrapper that extends MUI Paper with consistent styling
 * and test ID generation. Automatically applies padding from theme.
 * 
 * @example
 * ```tsx
 * <Paper elevation={2} name="search-filters">
 *   Content here
 * </Paper>
 * ```
 */
export function Paper({ 
  elevation = 1, 
  sx, 
  name,
  'data-testid': dataTestId,
  ...props 
}: PaperProps) {
  const theme = useTheme();
  
  // Get the paper padding from the theme's custom layout tokens
  // Note: custom layout properties are added to theme in createAppTheme
  const paperPadding = (theme as any).custom?.layout?.paper?.padding ?? 2;
  
  // Generate data-testid from component name and optional name prop
  const testIdProps = getTestIdProps('Paper', name, dataTestId);
  
  return (
    <MuiPaper
      elevation={elevation}
      sx={{
        p: paperPadding,
        ...sx,
      }}
      {...testIdProps}
      {...props}
    />
  );
}

export default Paper;
