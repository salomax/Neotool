/**
 * Shared semantic color tokens.
 *
 * This file is the translation layer between the shared product palette
 * and the mobile MD3 color roles consumed by React Native Paper.
 */

const semanticPalette = {
  light: {
    brand: {
      primary: '#1976d2',
      primaryMuted: '#bbdefb',
      secondary: '#dc004e',
      secondaryMuted: '#fce4ec',
      tertiary: '#7c4dff',
      tertiaryMuted: '#ede7f6',
    },
    feedback: {
      error: '#f44336',
      errorMuted: '#ffebee',
      success: '#4caf50',
      warning: '#ff9800',
      info: '#2196f3',
    },
    surface: {
      background: '#ffffff',
      surface: '#f5f5f5',
      surfaceVariant: '#e0e0e0',
      onBackground: '#1a1c1e',
      onSurface: '#1a1c1e',
      onSurfaceVariant: '#44474e',
      outline: '#74777f',
      outlineVariant: '#c4c6d0',
    },
    contrast: {
      onPrimary: '#ffffff',
      onPrimaryMuted: '#004881',
      onSecondary: '#ffffff',
      onSecondaryMuted: '#31111d',
      onTertiary: '#ffffff',
      onTertiaryMuted: '#23036a',
      onError: '#ffffff',
      onErrorMuted: '#410002',
    },
  },
  dark: {
    brand: {
      primary: '#90caf9',
      primaryMuted: '#004881',
      secondary: '#f48fb1',
      secondaryMuted: '#8b002e',
      tertiary: '#b085ff',
      tertiaryMuted: '#23036a',
    },
    feedback: {
      error: '#cf6679',
      errorMuted: '#93000a',
      success: '#81c784',
      warning: '#ffb74d',
      info: '#64b5f6',
    },
    surface: {
      background: '#121212',
      surface: '#1e1e1e',
      surfaceVariant: '#2c2c2c',
      onBackground: '#e1e2e5',
      onSurface: '#e1e2e5',
      onSurfaceVariant: '#c4c6d0',
      outline: '#8e9099',
      outlineVariant: '#44474e',
    },
    contrast: {
      onPrimary: '#003258',
      onPrimaryMuted: '#bbdefb',
      onSecondary: '#5e0021',
      onSecondaryMuted: '#fce4ec',
      onTertiary: '#23036a',
      onTertiaryMuted: '#ede7f6',
      onError: '#690005',
      onErrorMuted: '#ffebee',
    },
  },
} as const;

function mapSemanticPaletteToMd3Colors(
  palette: (typeof semanticPalette)[keyof typeof semanticPalette]
) {
  return {
    primary: palette.brand.primary,
    primaryContainer: palette.brand.primaryMuted,
    onPrimary: palette.contrast.onPrimary,
    onPrimaryContainer: palette.contrast.onPrimaryMuted,

    secondary: palette.brand.secondary,
    secondaryContainer: palette.brand.secondaryMuted,
    onSecondary: palette.contrast.onSecondary,
    onSecondaryContainer: palette.contrast.onSecondaryMuted,

    tertiary: palette.brand.tertiary,
    tertiaryContainer: palette.brand.tertiaryMuted,
    onTertiary: palette.contrast.onTertiary,
    onTertiaryContainer: palette.contrast.onTertiaryMuted,

    error: palette.feedback.error,
    errorContainer: palette.feedback.errorMuted,
    onError: palette.contrast.onError,
    onErrorContainer: palette.contrast.onErrorMuted,

    background: palette.surface.background,
    onBackground: palette.surface.onBackground,

    surface: palette.surface.surface,
    surfaceVariant: palette.surface.surfaceVariant,
    onSurface: palette.surface.onSurface,
    onSurfaceVariant: palette.surface.onSurfaceVariant,

    outline: palette.surface.outline,
    outlineVariant: palette.surface.outlineVariant,

    success: palette.feedback.success,
    warning: palette.feedback.warning,
    info: palette.feedback.info,
  };
}

export const webToMd3TokenMap = {
  brand: ['primary', 'secondary', 'tertiary'],
  feedback: ['error', 'success', 'warning', 'info'],
  surface: ['background', 'surface', 'surfaceVariant', 'outline'],
} as const;

export const lightColors = mapSemanticPaletteToMd3Colors(semanticPalette.light);
export const darkColors = mapSemanticPaletteToMd3Colors(semanticPalette.dark);

export type Colors = typeof lightColors;
