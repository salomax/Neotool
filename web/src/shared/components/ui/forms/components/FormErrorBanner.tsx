// web/src/components/form/FormErrorBanner.tsx
"use client";

import * as React from "react";
import { Alert } from "@mui/material";

export type FormErrorBannerProps = {
  message?: string;
};

export function FormErrorBanner({ message }: FormErrorBannerProps) {
  if (!message) return null;
  return <Alert severity="error">{message}</Alert>;
}

export default FormErrorBanner;
