// web/src/components/form/FormLayout.tsx
"use client";

import * as React from "react";
import { Stack, Box } from "@mui/material";

export type FormLayoutProps = {
  children: React.ReactNode;
  gap?: number;
  maxWidth?: number | string;
};

export function FormLayout({
  children,
  gap = 2,
  maxWidth = 960,
}: FormLayoutProps) {
  return (
    <Box sx={{ width: "100%", display: "flex", justifyContent: "center" }}>
      <Stack spacing={gap} sx={{ width: "100%", maxWidth, px: 2 }}>
        {children}
      </Stack>
    </Box>
  );
}

export default FormLayout;
