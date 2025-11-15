"use client";

import React from "react";
import CssBaseline from '@mui/material/CssBaseline';
import { ThemeProvider } from '@mui/material/styles';
import { createAppTheme } from "./theme";
import type { Mode } from "./tokens";

type ThemeCtx = { mode: Mode; setMode: (_m: Mode) => void; toggle: () => void };

const ThemeModeContext = React.createContext<ThemeCtx | null>(null);

export const useThemeMode = () => {
  const ctx = React.useContext(ThemeModeContext);
  if (!ctx)
    throw new Error("useThemeMode must be used within AppThemeProvider");
  return ctx;
};

const STORAGE_KEY = "app:theme-mode";

export const AppThemeProvider: React.FC<
  React.PropsWithChildren<{ defaultMode?: Mode }>
> = ({ children, defaultMode = "light" }) => {
  const [mode, setMode] = React.useState<Mode>(defaultMode);
  // Avoid hydration mismatch
  const [mounted, setMounted] = React.useState(false);

  React.useEffect(() => {
    const saved =
      (typeof window !== "undefined" &&
        (localStorage.getItem(STORAGE_KEY) as Mode)) ||
      null;
    if (saved === "light" || saved === "dark") setMode(saved);
    setMounted(true);
  }, []);

  const change = (m: Mode) => {
    setMode(m);
    if (typeof window !== "undefined") localStorage.setItem(STORAGE_KEY, m);
  };

  const toggle = () => change(mode === "light" ? "dark" : "light");

  const theme = React.useMemo(() => createAppTheme(mode), [mode]);

  return (
    <ThemeModeContext.Provider value={{ mode, setMode: change, toggle }}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        {mounted ? children : null}
      </ThemeProvider>
    </ThemeModeContext.Provider>
  );
};
