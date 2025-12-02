"use client";

import React, { useState, useMemo, useCallback } from "react";
import {
  Box,
  Typography,
  Divider,
  CircularProgress,
  Alert,
  Button,
  Stack,
} from "@mui/material";
import { usePermissionManagement, type Permission } from "@/shared/hooks/authorization/usePermissionManagement";
import { PermissionList } from "./PermissionList";
import { PermissionSearch } from "./PermissionSearch";
import { useTranslation } from "@/shared/i18n";
import { authorizationManagementTranslations } from "@/app/(neotool)/settings/i18n";
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
}

/**
 * RolePermissionAssignment component for managing role permissions
 * Shows assigned permissions with remove action and available permissions with assign action
 */
export const RolePermissionAssignment: React.FC<RolePermissionAssignmentProps> = ({
  roleId,
  assignedPermissions,
  onAssignPermission,
  onRemovePermission,
  assignLoading = false,
  removeLoading = false,
  onPermissionsChange,
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
  } = usePermissionManagement({
    initialFirst: 1000, // Fetch all permissions for selection
  });

  // Filter available permissions (not already assigned and matching search)
  const availablePermissions = useMemo(() => {
    if (!allPermissions) return [];
    const assignedIds = new Set(assignedPermissions.map((p) => p.id));
    return allPermissions.filter(
      (permission) =>
        !assignedIds.has(permission.id) &&
        permission.name.toLowerCase().includes(searchQuery.toLowerCase())
    );
  }, [allPermissions, assignedPermissions, searchQuery]);

  const handleAssignPermission = useCallback(
    async (permissionId: string) => {
      if (!roleId) return;
      try {
        await onAssignPermission(permissionId);
        toast.success(
          t("roleManagement.permissions.permissionAssigned", {
            permission: allPermissions.find((p) => p.id === permissionId)?.name || "",
          })
        );
        onPermissionsChange?.();
      } catch (err) {
        console.error("Error assigning permission:", err);
        const errorMessage = extractErrorMessage(
          err,
          t("roleManagement.permissions.assignError")
        );
        toast.error(errorMessage);
      }
    },
    [roleId, onAssignPermission, toast, t, allPermissions, onPermissionsChange]
  );

  const handleRemovePermission = useCallback(
    async (permissionId: string) => {
      if (!roleId) return;
      try {
        await onRemovePermission(permissionId);
        toast.success(
          t("roleManagement.permissions.permissionRemoved", {
            permission: assignedPermissions.find((p) => p.id === permissionId)?.name || "",
          })
        );
        onPermissionsChange?.();
      } catch (err) {
        console.error("Error removing permission:", err);
        const errorMessage = extractErrorMessage(
          err,
          t("roleManagement.permissions.removeError")
        );
        toast.error(errorMessage);
      }
    },
    [roleId, onRemovePermission, toast, t, assignedPermissions, onPermissionsChange]
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
      <Alert severity="error" sx={{ mb: 2 }}>
        {t("roleManagement.permissions.loadError")}
      </Alert>
    );
  }

  return (
    <Stack spacing={3}>
      {/* Assigned Permissions */}
      <Box>
        <Typography variant="h6" gutterBottom>
          {t("roleManagement.permissions.assigned")}
        </Typography>
        <Divider sx={{ mb: 2 }} />
        <PermissionList
          permissions={assignedPermissions}
          onRemove={handleRemovePermission}
          loading={removeLoading}
          showRemoveButton={true}
        />
      </Box>

      {/* Available Permissions */}
      <Box>
        <Typography variant="h6" gutterBottom>
          {t("roleManagement.permissions.available")}
        </Typography>
        <Divider sx={{ mb: 2 }} />
        <PermissionSearch
          value={searchQuery}
          onChange={handleSearchChange}
          placeholder={t("roleManagement.permissions.searchPlaceholder")}
        />
        {availablePermissions.length === 0 ? (
          <Typography variant="body2" color="text.secondary">
            {searchQuery
              ? t("roleManagement.permissions.noAvailableMatching")
              : t("roleManagement.permissions.allAssigned")}
          </Typography>
        ) : (
          <Box sx={{ display: "flex", flexWrap: "wrap", gap: 1, mt: 2 }}>
            {availablePermissions.map((permission) => (
              <Button
                key={permission.id}
                variant="outlined"
                size="small"
                onClick={() => handleAssignPermission(permission.id)}
                disabled={assignLoading || removeLoading}
                sx={{ textTransform: "none" }}
              >
                {permission.name}
              </Button>
            ))}
          </Box>
        )}
      </Box>
    </Stack>
  );
};

