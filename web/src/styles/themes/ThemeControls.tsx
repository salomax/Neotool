"use client";

import * as React from "react";
import { useThemeMode } from "@/styles/themes/AppThemeProvider";
import {
  Stack,
  ToggleButtonGroup,
  ToggleButton,
} from "@mui/material";
import LightModeIcon from "@mui/icons-material/LightMode";
import DarkModeIcon from "@mui/icons-material/DarkMode";

export const ThemeControls: React.FC = () => {
  const { mode, setMode, toggle } = useThemeMode();
  return (
    <Stack direction="row" spacing={2} alignItems="center" flexWrap="wrap">
      <ToggleButtonGroup
        exclusive
        size="small"
        value={mode}
        onChange={(_, v) => v && setMode(v)}
      >
        <ToggleButton value="light" aria-label="light">
          <LightModeIcon fontSize="small" />
        </ToggleButton>
        <ToggleButton value="dark" aria-label="dark">
          <DarkModeIcon fontSize="small" />
        </ToggleButton>
      </ToggleButtonGroup>
    </Stack>
  );
};
