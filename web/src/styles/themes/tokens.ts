// Design tokens independent from MUI.

export type Mode = "light" | "dark";

export interface DesignTokens {
  spacing: {
    xs: number;
    sm: number;
    md: number;
    lg: number;
    xl: number;
  };
  radius: {
    sm: number;
    md: number;
    lg: number;
    xl: number;
    table: number;
  };
  layout: {
    paper: {
      padding: number;
      flexShrink: number;
    };
    pageLayout: {
      padding: number;
      gap: number;
      fullHeight: boolean;
    };
    stack: {
      gap: number;
    };
  };
  typography: {
    fontFamily: string;
    h1: number;
    h2: number;
    h3: number;
    h4: number;
    h5: number;
    h6: number;
    body: number;
    small: number;
    monoFamily: string;
  };
  palette: {
    primary: string;
    primaryContrast: string;
    secondary: string;
    secondaryContrast: string;
    success: string;
    warning: string;
    error: string;
    errorLightBg: string;
    info: string;
    bg: string;
    bgPaper: string;
    text: string;
    textMuted: string;
    divider: string;
    inputBorder: string;
    tabBorder: string;
  };
}

export const tokens: Record<Mode, DesignTokens> = {
  light: {
    spacing: { xs: 4, sm: 8, md: 12, lg: 16, xl: 24 },
    radius: { sm: 4, md: 8, lg: 12, xl: 16, table: 8 },
    layout: {
      paper: {
        padding: 2,
        flexShrink: 0,
      },
      pageLayout: {
        padding: 4,
        gap: 12,
        fullHeight: true,
      },
      stack: {
        gap: 2,
      },
    },
    typography: {
      fontFamily: `'Inter', ui-sans-serif, system-ui, -apple-system, Segoe UI, Roboto, 'Helvetica Neue', Arial`,
      monoFamily: `'JetBrains Mono', ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace`,
      h1: 40,
      h2: 32,
      h3: 28,
      h4: 24,
      h5: 20,
      h6: 18,
      body: 16,
      small: 14,
    },
    palette: {
      primary: "#2563eb",
      primaryContrast: "#ffffff",
      secondary: "#7c3aed",
      secondaryContrast: "#ffffff",
      success: "#16a34a",
      warning: "#f59e0b",
      error: "#dc2626",
      errorLightBg: "rgba(220, 38, 38, 0.1)",
      info: "#0284c7",
      bg: "#f8fafc",
      bgPaper: "#ffffff",
      text: "#0f172a",
      textMuted: "#475569",
      divider: "#e2e8f0",
      inputBorder: "rgba(0, 0, 0, 0.23)",
      tabBorder: "rgba(0, 0, 0, 0.23)",
    },
  },
  dark: {
    spacing: { xs: 4, sm: 8, md: 12, lg: 16, xl: 24 },
    radius: { sm: 4, md: 8, lg: 12, xl: 16, table: 8 },
    layout: {
      paper: {
        padding: 2,
        flexShrink: 0,
      },
      pageLayout: {
        padding: 4,
        gap: 12,
        fullHeight: true,
      },
      stack: {
        gap: 2,
      },
    },
    typography: {
      fontFamily: `'Inter', ui-sans-serif, system-ui, -apple-system, Segoe UI, Roboto, 'Helvetica Neue', Arial`,
      monoFamily: `'JetBrains Mono', ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace`,
      h1: 40,
      h2: 32,
      h3: 28,
      h4: 24,
      h5: 20,
      h6: 18,
      body: 16,
      small: 14,
    },
    palette: {
      primary: "#60a5fa",
      primaryContrast: "#0b1220",
      secondary: "#c084fc",
      secondaryContrast: "#0b1220",
      success: "#22c55e",
      warning: "#fbbf24",
      error: "#ef4444",
      errorLightBg: "rgba(239, 68, 68, 0.15)",
      info: "#38bdf8",
      bg: "#0b1220",
      bgPaper: "#0f172a",
      text: "#e5e7eb",
      textMuted: "#94a3b8",
      divider: "#1f2937",
      inputBorder: "rgba(255, 255, 255, 0.23)",
      tabBorder: "rgba(255, 255, 255, 0.23)",
    },
  },
};
