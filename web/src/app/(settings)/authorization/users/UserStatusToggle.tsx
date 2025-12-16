"use client";

import React from "react";
import { Box, Tooltip, CircularProgress } from "@mui/material";
import { Switch } from "@/shared/components/ui/primitives/Switch";
import { useOptimisticUpdate } from "@/shared/hooks/mutations";
import { useTranslation } from "@/shared/i18n";
import { authorizationManagementTranslations } from "@/app/(settings)/settings/i18n";
import type { User } from "@/shared/hooks/authorization/useUserManagement";

export interface UserStatusToggleProps {
  user: User;
  enabled: boolean;
  onToggle: (userId: string, enabled: boolean) => Promise<void>;
  /**
   * @deprecated This prop is no longer used. Each toggle manages its own state independently.
   */
  loading?: boolean;
}

/**
 * UserStatusToggle component for enabling/disabling users
 * 
 * Uses optimistic updates to prevent UI blinking during mutations.
 * The toggle immediately reflects the new state while the mutation is in progress,
 * and reverts if the mutation fails.
 * 
 * @example
 * ```tsx
 * <UserStatusToggle
 *   user={user}
 *   enabled={user.enabled}
 *   onToggle={handleToggleStatus}
 * />
 * ```
 */
export const UserStatusToggle: React.FC<UserStatusToggleProps> = ({
  user,
  enabled,
  onToggle,
}) => {
  const { t } = useTranslation(authorizationManagementTranslations);
  
  const {
    optimisticValue: optimisticEnabled,
    isUpdating: isToggling,
    executeUpdate,
  } = useOptimisticUpdate({
    value: enabled,
  });

  const handleToggle = async (checked: boolean) => {
    await executeUpdate(checked, () => onToggle(user.id, checked));
  };

  const tooltipTitle = isToggling
    ? optimisticEnabled
      ? t("userManagement.status.disabling")
      : t("userManagement.status.enabling")
    : optimisticEnabled
    ? t("userManagement.status.disable")
    : t("userManagement.status.enable");

  return (
    <Box
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
            opacity: isToggling ? 0.8 : 1,
            transition: "opacity 0.15s ease-in-out",
          }}
        >
          {isToggling && (
            <CircularProgress
              size={16}
              sx={{
                color: "text.secondary",
              }}
            />
          )}
          <Switch
            checked={optimisticEnabled}
            onChange={handleToggle}
            disabled={isToggling}
            size="small"
            showStatus={false}
            name={`user-status-${user.id}`}
            data-testid={`user-status-toggle-${user.id}`}
          />
        </Box>
      </Tooltip>
    </Box>
  );
};

