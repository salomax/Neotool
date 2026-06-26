/** Breakpoint key used for drawer mobile presentation (matches MUI `theme.breakpoints.down`). */
export const DRAWER_MOBILE_BREAKPOINT = 'md' as const;

export type DrawerMobilePresentation = 'bottom-sheet-full';

export const DRAWER_LAYOUT = {
  desktop: {
    header: {
      py: 1.5,
      px: 2,
      minHeight: 54,
    },
    body: {
      p: 3,
    },
    footer: {
      p: 2,
    },
  },
  mobileSheet: {
    header: {
      py: 1.25,
      px: 2,
      minHeight: 48,
    },
    body: {
      px: 2,
      py: 2,
    },
    footer: {
      px: 2,
      py: 1.5,
      pb: 'calc(12px + env(safe-area-inset-bottom, 0px))',
    },
    paper: {
      height: '100dvh',
      maxHeight: '100dvh',
      borderTopLeftRadius: 12,
      borderTopRightRadius: 12,
    },
    actionButtons: {
      stackGap: 1.5,
      buttonMinHeight: 44,
    },
    uploadZone: {
      p: 2.5,
      iconSize: 40,
    },
  },
} as const;
