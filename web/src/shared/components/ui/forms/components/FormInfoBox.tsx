"use client";

import * as React from "react";
import Paper, { PaperProps } from "@mui/material/Paper";
import { alpha, useTheme } from "@mui/material/styles";

export type FormInfoBoxProps = Omit<PaperProps, "variant">;

export const FormInfoBox = React.forwardRef<HTMLDivElement, FormInfoBoxProps>(function FormInfoBox(
  { sx, children, ...props },
  ref,
) {
  const theme = useTheme();

  return (
    <Paper
      ref={ref}
      variant="outlined"
      sx={{
        p: 2,
        borderRadius: 1,
        bgcolor: alpha(theme.palette.primary.main, 0.04),
        borderColor: alpha(theme.palette.primary.main, 0.12),
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: 2,
        ...sx,
      }}
      {...props}
    >
      {children}
    </Paper>
  );
});

export default FormInfoBox;
