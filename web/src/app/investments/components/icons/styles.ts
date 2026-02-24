import { alpha, Theme } from "@mui/material";

// Common styles for the glass effect used in animated icons
export const glassStyle = (theme: Theme) => ({
  backdropFilter: "blur(8px)",
  backgroundColor: alpha(theme.palette.background.paper, 0.8),
  border: `1px solid ${alpha(theme.palette.divider, 0.2)}`,
  boxShadow: theme.shadows[4],
});
