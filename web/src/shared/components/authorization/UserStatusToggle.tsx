"use client";

import React, { useState } from "react";
import { Box, Tooltip, CircularProgress } from "@mui/material";
import { Switch } from "@/shared/components/ui/primitives/Switch";
import type { User } from "@/shared/hooks/authorization/useUserManagement";

export interface UserStatusToggleProps {
  user: User;
  enabled: boolean;
  onToggle: (userId: string, enabled: boolean) => Promise<void>;
  loading?: boolean;
}

/**
 * UserStatusToggle component for enabling/disabling users
 */
export const UserStatusToggle: React.FC<UserStatusToggleProps> = ({
  user,
  enabled,
  onToggle,
  loading = false,
}) => {
  const [isToggling, setIsToggling] = useState(false);

  const handleToggle = async (checked: boolean) => {
    if (isToggling || loading) return;

    setIsToggling(true);
    try {
      await onToggle(user.id, checked);
    } catch (error) {
      console.error("Error toggling user status:", error);
      // Error handling is done at the parent level
    } finally {
      setIsToggling(false);
    }
  };

  const isDisabled = isToggling || loading;

  return (
    <Box sx={{ display: "flex", alignItems: "center", justifyContent: "center", gap: 1 }}>
      {isToggling && (
        <CircularProgress size={16} sx={{ color: "text.secondary" }} />
      )}
      <Tooltip
        title={enabled ? "Disable user" : "Enable user"}
        placement="top"
      >
        <Box>
          <Switch
            checked={enabled}
            onChange={handleToggle}
            disabled={isDisabled}
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

