"use client";

import * as React from "react";
import { IconButton, Tooltip } from "@mui/material";
import LightModeIcon from "@mui/icons-material/LightMode";
import DarkModeIcon from "@mui/icons-material/DarkMode";
import { useThemeMode } from "./AppThemeProvider";

export const ThemeToggle: React.FC = () => {
  const { mode, toggle } = useThemeMode();
  const isDark = mode === "dark";
  return (
    <Tooltip title={isDark ? "Switch to light" : "Switch to dark"}>
      <IconButton onClick={toggle} aria-label="toggle theme" size="small">
        {isDark ? <LightModeIcon /> : <DarkModeIcon />}
      </IconButton>
    </Tooltip>
  );
};
