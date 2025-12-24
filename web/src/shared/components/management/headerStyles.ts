import { SxProps, Theme } from "@mui/material";

/**
 * Standard styling for header action buttons used alongside search fields.
 * Uses theme token radius but outputs px to avoid MUI's radius multiplier.
 */
export const managementHeaderActionButtonSx: SxProps<Theme> = {
  alignSelf: "stretch",
  height: "51px",
  minHeight: "unset",
  borderRadius: (theme) =>
    `${(theme as any).custom?.radius?.md ?? theme.shape.borderRadius}px`,
};
