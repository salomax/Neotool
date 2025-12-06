"use client";

import React from "react";
import { Alert } from "@mui/material";

export interface ErrorAlertProps {
  /**
   * Error object to display. If null or undefined, the alert is not rendered.
   */
  error: Error | null | undefined;
  /**
   * Callback function invoked when the user clicks the close button to retry.
   */
  onRetry: () => void;
  /**
   * Fallback message to display if error.message is not available.
   * @default "An error occurred"
   */
  fallbackMessage?: string;
}

/**
 * ErrorAlert component - Generic error alert with retry functionality
 * 
 * Displays an error message in an Alert component with a close button that triggers
 * a retry callback. Returns null if no error is provided.
 * 
 * @example
 * ```tsx
 * <ErrorAlert 
 *   error={error} 
 *   onRetry={() => refetch()} 
 *   fallbackMessage="Failed to load data"
 * />
 * ```
 */
export const ErrorAlert: React.FC<ErrorAlertProps> = ({
  error,
  onRetry,
  fallbackMessage = "An error occurred",
}) => {
  if (!error) {
    return null;
  }

  return (
    <Alert severity="error" sx={{ mb: 3 }} onClose={onRetry}>
      {error.message || fallbackMessage}
    </Alert>
  );
};

export default ErrorAlert;
