"use client";

import React from "react";
import { Alert } from "@mui/material";

export interface WarningAlertProps {
  /**
   * Warning message to display. If null, undefined, or empty, the alert is not rendered.
   */
  message: string | null | undefined;
  /**
   * Optional callback function invoked when the user clicks the close button.
   */
  onClose?: () => void;
  /**
   * Fallback message to display if message is not available.
   * @default "Warning"
   */
  fallbackMessage?: string;
}

/**
 * WarningAlert component - Generic warning alert
 * 
 * Displays a warning message in an Alert component with an optional close button.
 * Returns null if no message is provided.
 * 
 * @example
 * ```tsx
 * <WarningAlert 
 *   message={warningMessage} 
 *   onClose={() => setWarningMessage(null)} 
 *   fallbackMessage="Something went wrong"
 * />
 * ```
 */
export const WarningAlert: React.FC<WarningAlertProps> = ({
  message,
  onClose,
  fallbackMessage = "Warning",
}) => {
  if (!message) {
    return null;
  }

  return (
    <Alert severity="warning" sx={{ mb: 3 }} onClose={onClose}>
      {message || fallbackMessage}
    </Alert>
  );
};

export default WarningAlert;
