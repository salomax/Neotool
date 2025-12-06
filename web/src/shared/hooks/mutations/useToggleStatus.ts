"use client";

import { useCallback } from "react";
import { useToast } from "@/shared/providers";
import { extractErrorMessage } from "@/shared/utils/error";

export interface UseToggleStatusOptions {
  /**
   * Function to enable the item (called when enabled is true)
   */
  enableFn: (id: string) => Promise<void>;
  /**
   * Function to disable the item (called when enabled is false)
   */
  disableFn: (id: string) => Promise<void>;
  /**
   * Translation key for success message when enabling
   */
  enableSuccessMessage: string;
  /**
   * Translation key for success message when disabling
   */
  disableSuccessMessage: string;
  /**
   * Translation key for error message when enabling fails
   */
  enableErrorMessage: string;
  /**
   * Translation key for error message when disabling fails
   */
  disableErrorMessage: string;
  /**
   * Translation function (from useTranslation hook)
   */
  t: (key: string, params?: Record<string, string>) => string;
}

/**
 * Hook for handling enable/disable toggle operations with toast notifications
 * 
 * Provides a reusable pattern for toggling item status (enabled/disabled) with
 * consistent error handling and user feedback through toast notifications.
 * 
 * @param options - Configuration options including enable/disable functions and translation keys
 * @returns Callback function that takes (id: string, enabled: boolean) and handles the toggle
 * 
 * @example
 * ```tsx
 * const handleToggleStatus = useToggleStatus({
 *   enableFn: enableUser,
 *   disableFn: disableUser,
 *   enableSuccessMessage: "userManagement.toast.userEnabled",
 *   disableSuccessMessage: "userManagement.toast.userDisabled",
 *   enableErrorMessage: "userManagement.toast.userEnableError",
 *   disableErrorMessage: "userManagement.toast.userDisableError",
 *   t,
 * });
 * 
 * // Usage
 * await handleToggleStatus(userId, true); // Enable
 * await handleToggleStatus(userId, false); // Disable
 * ```
 */
export function useToggleStatus({
  enableFn,
  disableFn,
  enableSuccessMessage,
  disableSuccessMessage,
  enableErrorMessage,
  disableErrorMessage,
  t,
}: UseToggleStatusOptions) {
  const toast = useToast();

  return useCallback(
    async (id: string, enabled: boolean) => {
      try {
        if (enabled) {
          await enableFn(id);
          toast.success(t(enableSuccessMessage));
        } else {
          await disableFn(id);
          toast.success(t(disableSuccessMessage));
        }
      } catch (err) {
        console.error("Error toggling status:", err);
        const errorMessage = extractErrorMessage(
          err,
          enabled ? t(enableErrorMessage) : t(disableErrorMessage)
        );
        toast.error(errorMessage);
      }
    },
    [enableFn, disableFn, enableSuccessMessage, disableSuccessMessage, enableErrorMessage, disableErrorMessage, toast, t]
  );
}
