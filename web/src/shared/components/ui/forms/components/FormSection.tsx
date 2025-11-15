// web/src/components/form/FormSection.tsx
"use client";

import * as React from "react";
import { Stack, Typography, Paper } from "@mui/material";

export type FormSectionProps = {
  title?: string;
  description?: string;
  children: React.ReactNode;
};

export function FormSection({
  title,
  description,
  children,
}: FormSectionProps) {
  return (
    <Paper elevation={0} variant="outlined" sx={{ p: 2, borderRadius: 2 }}>
      {(title || description) && (
        <Stack spacing={0.5} sx={{ mb: 2 }}>
          {title && <Typography variant="h6">{title}</Typography>}
          {description && (
            <Typography variant="body2" color="text.secondary">
              {description}
            </Typography>
          )}
        </Stack>
      )}
      <Stack spacing={2}>{children}</Stack>
    </Paper>
  );
}

export default FormSection;
