"use client";

import React from "react";
import { Alert } from "@mui/material";

export interface WarningAlertProps {
  /**
   * Warning message to display.
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
  /**
   * Controls the visibility of the alert. If false, the alert is not rendered.
   * If undefined, visibility is determined by the presence of a message.
   * @default undefined (auto-determined from message)
   */
  visible?: boolean;
}

/**
 * WarningAlert component - Generic warning alert
 * 
 * Displays a warning message in an Alert component with an optional close button.
 * 
 * @example
 * ```tsx
 * // Auto-hide when no message (default behavior)
 * <WarningAlert message={warningMessage} />
 * 
 * // Explicit visibility control
 * <WarningAlert message="test" visible={showWarning} />
 * 
 * // Always show (even if message is null)
 * <WarningAlert message={message} visible={true} />
 * 
 * // Force hide (even if message exists)
 * <WarningAlert message={message} visible={false} />
 * 
 * // With close handler
 * <WarningAlert 
 *   message={warningMessage} 
 *   onClose={() => setWarningMessage(null)} 
 * />
 * ```
 */
export const WarningAlert: React.FC<WarningAlertProps> = ({
  message,
  onClose,
  fallbackMessage = "Warning",
  visible,
}) => {
  // If visible is explicitly false, don't render
  if (visible === false) {
    return null;
  }

  // If visible is not explicitly set, auto-determine from message
  // If visible is true, still need message to show content
  const hasMessage = message && message.trim() !== "";
  if (visible === undefined && !hasMessage) {
    return null;
  }

  // If visible is true but no message, show fallback or nothing
  if (visible === true && !hasMessage) {
    return null;
  }

  return (
    <Alert severity="warning" sx={{ mb: 3 }} onClose={onClose || undefined}>
      {message || fallbackMessage}
    </Alert>
  );
};

export default WarningAlert;
