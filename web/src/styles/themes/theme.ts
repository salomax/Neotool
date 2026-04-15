import { createTheme, ThemeOptions, alpha, Shadows } from "@mui/material/styles";
import { fixedIncomeTypeColorsByMode } from "./fixedIncomeTypeColors";
import { tokens, Mode, DesignTokens } from "./tokens";

type CustomPaletteTokens = Pick<
  DesignTokens["palette"],
  | "inputBorder"
  | "tabBorder"
  | "errorLightBg"
  | "successLightBg"
  | "infoLightBg"
  | "sidebarBg"
  | "sidebarIcon"
  | "labelIcon"
  | "currencyPositive"
  | "currencyNegative"
  | "currencyNeutral"
  | "thresholdBad"
  | "thresholdRegular"
  | "thresholdGood"
  | "tax"
>;

declare module "@mui/material/styles" {
  interface Theme {
    custom: {
      layout: DesignTokens["layout"];
      radius: DesignTokens["radius"];
      border: DesignTokens["border"];
      palette: CustomPaletteTokens;
      /** Background colors for fixed-income type avatars (`AssetType` → hex). Includes `DEFAULT`. */
      fixedIncomeTypeColors: Record<string, string>;
      shadow: (level: 0 | 1 | 2 | 3 | 4, color?: string) => string;
    };
  }
  interface ThemeOptions {
    custom?: {
      layout?: DesignTokens["layout"];
      radius?: DesignTokens["radius"];
      border?: DesignTokens["border"];
      palette?: CustomPaletteTokens;
      fixedIncomeTypeColors?: Record<string, string>;
      shadow?: (level: 0 | 1 | 2 | 3 | 4, color?: string) => string;
    };
  }
  interface ZIndex {
    bottomNav: number;
  }
  interface ZIndexOptions {
    bottomNav?: number;
  }
}

export const createAppTheme = (mode: Mode) => {
  const t = tokens[mode];
  const defaultShadows = createTheme().shadows;
  const shadows = [...defaultShadows] as Shadows;
  const buttonRadiusPx = `${t.radius.button}px`;
  const inputHoverBorderColor = t.palette.primary;

  const buildShadow = (level: 0 | 1 | 2 | 3 | 4, color?: string) => {
    if (level === 0) return "none";

    const c = color ?? "#000";

    if (level === 1) {
      return `0px 1px 2px 0px ${alpha(c, 0.12)}, 0px 1px 3px 0px ${alpha(c, 0.06)}`;
    }

    if (level === 2) {
      return `0px 2px 4px -1px ${alpha(c, 0.14)}, 0px 4px 6px -1px ${alpha(c, 0.08)}, 0px 1px 10px 0px ${alpha(c, 0.06)}`;
    }

    if (level === 3) {
      return `0px 3px 6px -2px ${alpha(c, 0.16)}, 0px 6px 10px -2px ${alpha(c, 0.10)}, 0px 2px 18px 0px ${alpha(c, 0.08)}`;
    }

    return `0px 2px 4px -1px ${alpha(c, 0.20)}, 0px 4px 5px 0px ${alpha(c, 0.14)}, 0px 1px 10px 0px ${alpha(c, 0.12)}`;
  };

  shadows[1] = buildShadow(1);
  shadows[2] = buildShadow(2);
  shadows[3] = buildShadow(3);
  shadows[4] = buildShadow(4);

  const options: ThemeOptions = {
    shadows,
    // Mobile Breakpoints (2026 Standard)
    // xs: 0px    - Mobile Portrait
    // sm: 600px  - Mobile Landscape / Small Tablet
    // md: 960px  - Tablet Landscape / Desktop Threshold (Primary Mobile Breakpoint)
    // lg: 1280px - Desktop
    // xl: 1920px - Large Desktop
    breakpoints: {
      values: {
        xs: 0,
        sm: 600,
        md: 960, 
        lg: 1280,
        xl: 1920,
      },
    },
    zIndex: {
      bottomNav: 1101, // Above AppBar (1100)
    },
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
      action: {
        hover: t.palette.infoLightBg,
      },
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
      button: { textTransform: "none", fontWeight: 600, fontSize: t.typography.small },
      fontFamilyMonospace: t.typography.monoFamily as any,
    } as any,
    components: ({
      MuiButton: {
        defaultProps: { disableElevation: true, variant: "contained" },
        styleOverrides: {
          root: { 
            borderRadius: buttonRadiusPx,
            minWidth: '90px',
          },
          sizeMedium: {
            height: '48px',
            minHeight: '48px',
          },
        },
      },
      MuiLink: {
        defaultProps: { variant: "inherit" as any },
      },
      MuiFormLabel: {
        styleOverrides: {
          root: {
            fontSize: t.typography.small,
          },
        },
      },
      MuiInputLabel: {
        styleOverrides: {
          root: {
            fontSize: t.typography.small,
          },
          outlined: {
            transform: "translate(14px, 14px) scale(1)",
            "&.MuiInputLabel-shrink": {
              transform: "translate(14px, -9px) scale(0.75)",
            },
          },
        },
      },
      MuiInputBase: {
        styleOverrides: {
          root: {
            fontSize: t.typography.small,
          },
        },
      },
      MuiOutlinedInput: {
        styleOverrides: {
          root: {
            "&:hover:not(.Mui-disabled):not(.Mui-focused):not(.Mui-error) .MuiOutlinedInput-notchedOutline": {
              borderColor: inputHoverBorderColor,
            },
          },
          input: {
            padding: "14.5px 14px",
          },
        },
      },
      MuiAutocomplete: {
        styleOverrides: {
          inputRoot: ({ ownerState }: any) => {
            if (ownerState?.multiple) return {};

            const inputPadding = ownerState?.size === "small" ? "10.5px 14px" : "14.5px 14px";

            return {
              paddingTop: 0,
              paddingBottom: 0,
              "& .MuiAutocomplete-input": {
                padding: inputPadding,
              },
              "& .MuiOutlinedInput-inputAdornedStart.MuiAutocomplete-input": {
                paddingLeft: 0,
              },
            };
          },
        },
      },
      MuiPickersOutlinedInput: {
        styleOverrides: {
          root: {
            "&:hover:not(.Mui-disabled):not(.Mui-focused):not(.Mui-error) .MuiPickersOutlinedInput-notchedOutline": {
              borderColor: inputHoverBorderColor,
            },
          },
          input: {
            padding: "14.5px 14px",
          },
        },
      },
      MuiMenuItem: {
        styleOverrides: {
          root: {
            fontSize: t.typography.small,
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
    } as any),
    // Custom theme properties for layout and design tokens
    custom: {
      layout: t.layout,
      radius: t.radius,
      border: t.border,
      shadow: buildShadow,
      fixedIncomeTypeColors: fixedIncomeTypeColorsByMode[mode],
      palette: {
        inputBorder: t.palette.inputBorder,
        tabBorder: t.palette.tabBorder,
        errorLightBg: t.palette.errorLightBg,
        successLightBg: t.palette.successLightBg,
        infoLightBg: t.palette.infoLightBg,
        sidebarBg: t.palette.sidebarBg,
        sidebarIcon: t.palette.sidebarIcon,
        labelIcon: t.palette.labelIcon,
        // Currency-specific colors
        currencyPositive: t.palette.currencyPositive,
        currencyNegative: t.palette.currencyNegative,
        currencyNeutral: t.palette.currencyNeutral,
        // Threshold colors for percentage indicators
        thresholdBad: t.palette.thresholdBad,
        thresholdRegular: t.palette.thresholdRegular,
        thresholdGood: t.palette.thresholdGood,
        tax: t.palette.tax,
      },
    },
  };
  return createTheme(options);
};
