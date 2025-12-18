"use client";

import React from "react";
import {
  Box,
  Typography,
  Chip,
} from "@mui/material";
import { useTranslation } from "@/shared/i18n";
import { authorizationManagementTranslations } from "@/app/(settings)/settings/i18n";

export interface Permission {
  id: string;
  name: string;
}

export interface PermissionListProps {
  permissions: Permission[];
  onRemove?: (permissionId: string) => void;
  loading?: boolean;
  showRemoveButton?: boolean;
}

/**
 * PermissionList component for displaying a list of permissions
 * Shows permissions as chips with optional remove functionality
 */
export const PermissionList: React.FC<PermissionListProps> = ({
  permissions,
  onRemove,
  loading = false,
  showRemoveButton = false,
}) => {
  const { t } = useTranslation(authorizationManagementTranslations);

  if (permissions.length === 0) {
    return (
      <Typography variant="body2" color="text.secondary">
        {t("roleManagement.permissions.noPermissions")}
      </Typography>
    );
  }

  return (
    <Box>
      <Typography variant="caption" color="text.secondary" sx={{ mb: 1, display: "block" }}>
        {t("roleManagement.permissions.assignedCount", { count: permissions.length })}
      </Typography>
      <Box sx={{ display: "flex", flexWrap: "wrap", gap: 1 }}>
        {permissions.map((permission) => (
          <Chip
            key={permission.id}
            label={permission.name}
            size="small"
            color="primary"
            variant="outlined"
            onDelete={
              showRemoveButton && onRemove && !loading
                ? () => onRemove(permission.id)
                : undefined
            }
            disabled={loading}
            aria-label={
              showRemoveButton && onRemove
                ? t("roleManagement.permissions.removePermission", {
                    name: permission.name,
                  })
                : permission.name
            }
          />
        ))}
      </Box>
    </Box>
  );
};

