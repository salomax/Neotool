"use client";

import React from "react";
import { Alert } from "@mui/material";

export interface ErrorAlertProps {
  /**
   * Error object or error message string to display.
   */
  error: Error | string | null | undefined;
  /**
   * Optional callback function invoked when the user clicks the close button to retry.
   * If not provided, the alert will not have a close button.
   */
  onRetry?: () => void;
  /**
   * Fallback message to display if error message is not available.
   * @default "An error occurred"
   */
  fallbackMessage?: string;
  /**
   * Controls the visibility of the alert. If false, the alert is not rendered.
   * If undefined, visibility is determined by the presence of an error.
   * @default undefined (auto-determined from error)
   */
  visible?: boolean;
  /**
   * Test ID for testing purposes.
   */
  "data-testid"?: string;
}

/**
 * ErrorAlert component - Generic error alert with optional retry functionality
 * 
 * Displays an error message in an Alert component. If onRetry is provided, a close button
 * will be shown that triggers the retry callback.
 * 
 * @example
 * ```tsx
 * // Auto-hide when no error (default behavior)
 * <ErrorAlert error={error} />
 * 
 * // Explicit visibility control
 * <ErrorAlert error="test" visible={showError} />
 * 
 * // Always show (even if error is null)
 * <ErrorAlert error={error} visible={true} />
 * 
 * // Force hide (even if error exists)
 * <ErrorAlert error={error} visible={false} />
 * 
 * // With retry
 * <ErrorAlert error={error} onRetry={() => refetch()} />
 * ```
 */
export const ErrorAlert: React.FC<ErrorAlertProps> = ({
  error,
  onRetry,
  fallbackMessage = "An error occurred",
  visible,
  "data-testid": dataTestId,
}) => {
  // If visible is explicitly false, don't render
  if (visible === false) {
    return null;
  }

  // If visible is not explicitly set, auto-determine from error
  // If visible is true, still need error to show message
  const hasError = error && (typeof error !== "string" || error.trim() !== "");
  if (visible === undefined && !hasError) {
    return null;
  }

  // If visible is true but no error, show fallback or nothing
  if (visible === true && !hasError) {
    return null;
  }

  // Extract message from Error object or use string directly
  const message = typeof error === "string" ? error : error?.message || fallbackMessage;

  return (
    <Alert 
      severity="error" 
      sx={{ mb: 3 }} 
      onClose={onRetry || undefined}
      data-testid={dataTestId}
    >
      {message}
    </Alert>
  );
};

export default ErrorAlert;
