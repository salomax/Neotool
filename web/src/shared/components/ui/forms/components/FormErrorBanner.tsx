// web/src/components/form/FormErrorBanner.tsx
"use client";

import * as React from "react";
import { ErrorAlert } from "@/shared/components/ui/feedback";

export type FormErrorBannerProps = {
  message?: string;
  onRetry?: () => void;
};

export function FormErrorBanner({ message, onRetry }: FormErrorBannerProps) {
  if (!message) return null;
  return (
    <ErrorAlert
      error={message}
      onRetry={onRetry}
    />
  );
}

export default FormErrorBanner;
