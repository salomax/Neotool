"use client";

import React from "react";
import { Box, Tooltip, CircularProgress } from "@mui/material";
import { Switch } from "@/shared/components/ui/primitives/Switch";
import { useOptimisticUpdate } from "@/shared/hooks/mutations";

export interface StatusToggleProps {
  /**
   * Current status value from server
   */
  value: boolean;
  /**
   * Callback when toggle is changed
   */
  onChange: (value: boolean) => Promise<void>;
  /**
   * Tooltip text when enabled
   */
  enabledTooltip?: string;
  /**
   * Tooltip text when disabled
   */
  disabledTooltip?: string;
  /**
   * Tooltip text when enabling (loading state)
   */
  enablingTooltip?: string;
  /**
   * Tooltip text when disabling (loading state)
   */
  disablingTooltip?: string;
  /**
   * Size of the switch
   */
  size?: "small" | "medium" | "large";
  /**
   * Test identifier
   */
  "data-testid"?: string;
  /**
   * Name for generating test ID
   */
  name?: string;
  /**
   * Additional CSS class name
   */
  className?: string;
}

/**
 * Generic StatusToggle component with optimistic updates
 * 
 * Provides a reusable toggle component that:
 * - Immediately reflects user actions (optimistic updates)
 * - Shows loading state during mutations
 * - Reverts on error
 * - Prevents UI blinking
 * 
 * @example
 * ```tsx
 * <StatusToggle
 *   value={user.enabled}
 *   onChange={(enabled) => updateUser(user.id, enabled)}
 *   enabledTooltip="Disable user"
 *   disabledTooltip="Enable user"
 *   name={`user-${user.id}`}
 * />
 * ```
 */
export const StatusToggle: React.FC<StatusToggleProps> = ({
  value,
  onChange,
  enabledTooltip = "Disable",
  disabledTooltip = "Enable",
  enablingTooltip = "Enabling...",
  disablingTooltip = "Disabling...",
  size = "small",
  "data-testid": dataTestId,
  name,
  className,
}) => {
  const {
    optimisticValue,
    isUpdating,
    executeUpdate,
  } = useOptimisticUpdate({
    value,
  });

  const handleToggle = async (checked: boolean) => {
    await executeUpdate(checked, () => onChange(checked));
  };

  const tooltipTitle = isUpdating
    ? optimisticValue
      ? disablingTooltip
      : enablingTooltip
    : optimisticValue
    ? enabledTooltip
    : disabledTooltip;

  return (
    <Box
      className={className}
      sx={{
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        gap: 1,
        minHeight: 40, // Prevent layout shift when spinner appears/disappears
      }}
    >
      <Tooltip title={tooltipTitle} placement="top">
        <Box
          sx={{
            display: "flex",
            alignItems: "center",
            gap: 1,
            opacity: isUpdating ? 0.8 : 1,
            transition: "opacity 0.15s ease-in-out",
          }}
        >
          {isUpdating && (
            <CircularProgress
              size={16}
              sx={{
                color: "text.secondary",
              }}
            />
          )}
          <Switch
            checked={optimisticValue}
            onChange={handleToggle}
            disabled={isUpdating}
            size={size}
            showStatus={false}
            name={name}
            data-testid={dataTestId}
          />
        </Box>
      </Tooltip>
    </Box>
  );
};

