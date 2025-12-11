"use client";

import React, { useState, useMemo, useCallback } from "react";
import {
  Box,
  Typography,
  CircularProgress,
  Checkbox,
  FormControlLabel,
  Stack,
  TextField,
} from "@mui/material";
import { ErrorAlert } from "@/shared/components/ui/feedback";
import { usePermissionManagement, type Permission } from "@/shared/hooks/authorization/usePermissionManagement";
import { PermissionSearch } from "../permissions/PermissionSearch";
import { useTranslation } from "@/shared/i18n";
import { authorizationManagementTranslations } from "@/app/(settings)/settings/i18n";
import { useToast } from "@/shared/providers";
import { extractErrorMessage } from "@/shared/utils/error";

export interface RolePermissionAssignmentProps {
  roleId: string | null;
  assignedPermissions: Permission[];
  onAssignPermission: (permissionId: string) => Promise<void>;
  onRemovePermission: (permissionId: string) => Promise<void>;
  assignLoading?: boolean;
  removeLoading?: boolean;
  onPermissionsChange?: () => void;
  /**
   * When false, skips loading data and renders nothing (used when drawer is closed).
   */
  active?: boolean;
}

/**
 * RolePermissionAssignment component for managing role permissions
 * Shows all permissions as a checkbox list where users can select/unselect
 */
export const RolePermissionAssignment: React.FC<RolePermissionAssignmentProps> = ({
  roleId,
  assignedPermissions,
  onAssignPermission,
  onRemovePermission,
  assignLoading = false,
  removeLoading = false,
  onPermissionsChange,
  active = true,
}) => {
  const { t } = useTranslation(authorizationManagementTranslations);
  const toast = useToast();
  const [searchQuery, setSearchQuery] = useState("");

  // Fetch all available permissions
  const {
    permissions: allPermissions,
    searchQuery: permissionSearchQuery,
    setSearchQuery: setPermissionSearchQuery,
    loading: permissionsLoading,
    error: permissionsError,
    refetch: refetchPermissions,
  } = usePermissionManagement({
    initialFirst: 1000, // Fetch all permissions for selection
    skip: !active,
  });

  if (!active) {
    return null;
  }

  // Create a set of assigned permission IDs for quick lookup
  const assignedPermissionIds = useMemo(() => {
    return new Set(assignedPermissions.map((p) => p.id));
  }, [assignedPermissions]);

  // Filter permissions based on search query
  const filteredPermissions = useMemo(() => {
    if (!allPermissions) return [];
    const searchLower = searchQuery.toLowerCase();
    return allPermissions.filter((permission) =>
      permission.name.toLowerCase().includes(searchLower)
    );
  }, [allPermissions, searchQuery]);

  const handlePermissionToggle = useCallback(
    async (permissionId: string, isChecked: boolean) => {
      try {
        if (isChecked) {
          await onAssignPermission(permissionId);
          // Only show toast in edit mode (when roleId exists)
          if (roleId) {
            const permission = allPermissions?.find((p) => p.id === permissionId);
            toast.success(
              t("roleManagement.permissions.permissionAssigned", {
                permission: permission?.name || "",
              })
            );
          }
        } else {
          await onRemovePermission(permissionId);
          // Only show toast in edit mode (when roleId exists)
          if (roleId) {
            const permission = assignedPermissions.find((p) => p.id === permissionId);
            toast.success(
              t("roleManagement.permissions.permissionRemoved", {
                permission: permission?.name || "",
              })
            );
          }
        }
        onPermissionsChange?.();
      } catch (err) {
        const errorMessage = extractErrorMessage(
          err,
          isChecked
            ? t("roleManagement.permissions.assignError")
            : t("roleManagement.permissions.removeError")
        );
        toast.error(errorMessage);
      }
    },
    [
      roleId,
      onAssignPermission,
      onRemovePermission,
      toast,
      t,
      allPermissions,
      assignedPermissions,
      onPermissionsChange,
    ]
  );

  const handleSearchChange = useCallback(
    (value: string) => {
      setSearchQuery(value);
      setPermissionSearchQuery(value);
    },
    [setPermissionSearchQuery]
  );

  if (permissionsLoading) {
    return (
      <Box sx={{ display: "flex", justifyContent: "center", p: 4 }}>
        <CircularProgress />
      </Box>
    );
  }

  if (permissionsError) {
    return (
      <ErrorAlert
        error={permissionsError}
        onRetry={() => refetchPermissions()}
        fallbackMessage={t("roleManagement.permissions.loadError")}
      />
    );
  }

  const totalPermissions = allPermissions?.length || 0;
  const assignedCount = assignedPermissions.length;

  return (
    <Box>
      <Typography variant="subtitle1" gutterBottom>
        {t("roleManagement.permissions.assigned")} ({assignedCount})
      </Typography>
      <PermissionSearch
        value={searchQuery}
        onChange={handleSearchChange}
        placeholder={t("roleManagement.permissions.searchPlaceholder")}
      />
      <Box
        sx={{
          maxHeight: 400,
          overflowY: "auto",
          border: "1px solid",
          borderColor: "divider",
          borderRadius: 1,
          p: 1,
          mt: 2,
        }}
      >
        {filteredPermissions.length === 0 ? (
          <Typography variant="body2" color="text.secondary" sx={{ p: 2 }}>
            {searchQuery
              ? t("roleManagement.permissions.noAvailableMatching")
              : t("roleManagement.permissions.noPermissions")}
          </Typography>
        ) : (
          <Stack spacing={0.5}>
            {filteredPermissions.map((permission) => {
              const isAssigned = assignedPermissionIds.has(permission.id);
              return (
                <FormControlLabel
                  key={permission.id}
                  control={
                    <Checkbox
                      checked={isAssigned}
                      onChange={(e) =>
                        handlePermissionToggle(permission.id, e.target.checked)
                      }
                      disabled={assignLoading || removeLoading || !roleId}
                    />
                  }
                  label={permission.name}
                />
              );
            })}
          </Stack>
        )}
      </Box>
    </Box>
  );
};
