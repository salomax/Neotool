import { createTheme, ThemeOptions } from "@mui/material/styles";
import { tokens, Mode } from "./tokens";

export const createAppTheme = (mode: Mode) => {
  const t = tokens[mode];
  const options: ThemeOptions = {
    palette: {
      mode,
      primary: {
        main: t.palette.primary,
        contrastText: t.palette.primaryContrast,
      },
      secondary: {
        main: t.palette.secondary,
        contrastText: t.palette.secondaryContrast,
      },
      success: { main: t.palette.success },
      warning: { main: t.palette.warning },
      error: { main: t.palette.error },
      info: { main: t.palette.info },
      background: { default: t.palette.bg, paper: t.palette.bgPaper },
      divider: t.palette.divider,
      text: { primary: t.palette.text, secondary: t.palette.textMuted },
    },
    shape: { borderRadius: t.radius.md },
    spacing: (factor: number) => `${factor * t.spacing.sm}px`,
    typography: {
      fontFamily: t.typography.fontFamily,
      h1: { fontSize: t.typography.h1, fontWeight: 700, lineHeight: 1.1 },
      h2: { fontSize: t.typography.h2, fontWeight: 700, lineHeight: 1.15 },
      h3: { fontSize: t.typography.h3, fontWeight: 700, lineHeight: 1.2 },
      h4: { fontSize: t.typography.h4, fontWeight: 600, lineHeight: 1.2 },
      h5: { fontSize: t.typography.h5, fontWeight: 600, lineHeight: 1.25 },
      h6: { fontSize: t.typography.h6, fontWeight: 600, lineHeight: 1.25 },
      body1: { fontSize: t.typography.body },
      body2: { fontSize: t.typography.small },
      button: { textTransform: "none", fontWeight: 600 },
      fontFamilyMonospace: t.typography.monoFamily as any,
    } as any,
    components: {
      MuiButton: {
        defaultProps: { disableElevation: true, variant: "contained" },
        styleOverrides: {
          root: { 
            borderRadius: t.radius.lg,
            minWidth: '90px',
          },
          sizeMedium: {
            height: '56px',
            minHeight: '56px',
          },
        },
      },
      MuiPaper: {
        defaultProps: {
          elevation: 1,
        },
        styleOverrides: {
          root: { 
            borderRadius: t.radius.lg,
            flexShrink: t.layout.paper.flexShrink,
          },
        },
      },
    },
    // Custom theme properties for layout and design tokens
    custom: {
      layout: t.layout,
      radius: t.radius,
      palette: {
        inputBorder: t.palette.inputBorder,
        tabBorder: t.palette.tabBorder,
      },
    },
  } as ThemeOptions & { custom?: { layout: typeof t.layout } };
  return createTheme(options);
};
