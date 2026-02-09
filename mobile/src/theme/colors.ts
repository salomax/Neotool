/**
 * Design tokens - Colors
 * Can be shared with web application
 */

export const lightColors = {
  primary: '#1976d2',
  primaryContainer: '#bbdefb',
  onPrimary: '#ffffff',
  onPrimaryContainer: '#004881',

  secondary: '#dc004e',
  secondaryContainer: '#fce4ec',
  onSecondary: '#ffffff',
  onSecondaryContainer: '#31111d',

  tertiary: '#7c4dff',
  tertiaryContainer: '#ede7f6',
  onTertiary: '#ffffff',
  onTertiaryContainer: '#23036a',

  error: '#f44336',
  errorContainer: '#ffebee',
  onError: '#ffffff',
  onErrorContainer: '#410002',

  background: '#ffffff',
  onBackground: '#1a1c1e',

  surface: '#f5f5f5',
  surfaceVariant: '#e0e0e0',
  onSurface: '#1a1c1e',
  onSurfaceVariant: '#44474e',

  outline: '#74777f',
  outlineVariant: '#c4c6d0',

  success: '#4caf50',
  warning: '#ff9800',
  info: '#2196f3',
};

export const darkColors = {
  primary: '#90caf9',
  primaryContainer: '#004881',
  onPrimary: '#003258',
  onPrimaryContainer: '#bbdefb',

  secondary: '#f48fb1',
  secondaryContainer: '#8b002e',
  onSecondary: '#5e0021',
  onSecondaryContainer: '#fce4ec',

  tertiary: '#b085ff',
  tertiaryContainer: '#23036a',
  onTertiary: '#23036a',
  onTertiaryContainer: '#ede7f6',

  error: '#cf6679',
  errorContainer: '#93000a',
  onError: '#690005',
  onErrorContainer: '#ffebee',

  background: '#121212',
  onBackground: '#e1e2e5',

  surface: '#1e1e1e',
  surfaceVariant: '#2c2c2c',
  onSurface: '#e1e2e5',
  onSurfaceVariant: '#c4c6d0',

  outline: '#8e9099',
  outlineVariant: '#44474e',

  success: '#81c784',
  warning: '#ffb74d',
  info: '#64b5f6',
};

export type Colors = typeof lightColors;
