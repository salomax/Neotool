"use client";

import React, { useState, useMemo, useCallback, memo } from "react";
import {
  Box,
  Typography,
  CircularProgress,
  Checkbox,
  FormControlLabel,
  Stack,
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
  onAssignPermission?: (permissionId: string) => Promise<void>;
  onRemovePermission?: (permissionId: string) => Promise<void>;
  assignLoading?: boolean;
  removeLoading?: boolean;
  onPermissionsChange?: () => void;
  /**
   * When false, skips loading data and renders nothing (used when drawer is closed).
   */
  active?: boolean;
  /**
   * For edit mode with deferred mutations: callback when permissions change (updates local state only)
   */
  onChange?: (permissions: Permission[]) => void;
}

/**
 * RolePermissionAssignment component for managing role permissions
 * Shows all permissions as a checkbox list where users can select/unselect
 */
// Memoized permission item component to prevent unnecessary re-renders
const PermissionItem = memo<{
  permission: Permission;
  isAssigned: boolean;
  disabled: boolean;
  onToggle: (permissionId: string, isChecked: boolean) => void;
}>(({ permission, isAssigned, disabled, onToggle }) => {
  const handleChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      onToggle(permission.id, e.target.checked);
    },
    [permission.id, onToggle]
  );

  return (
    <FormControlLabel
      control={
        <Checkbox
          checked={isAssigned}
          onChange={handleChange}
          disabled={disabled}
        />
      }
      label={permission.name}
    />
  );
});

PermissionItem.displayName = "PermissionItem";

// Permissions list component - handles filtering internally
interface PermissionsListProps {
  allPermissions: Permission[] | null | undefined;  // Changed from filteredPermissions
  assignedPermissionIds: string[];
  disabled: boolean;
  onToggle: (permissionId: string, isChecked: boolean) => void;
  searchQuery: string;
  emptyMessageNoQuery: string;
  emptyMessageWithQuery: string;
}

const PermissionsList = memo<PermissionsListProps>(({ 
  allPermissions,  // Changed from filteredPermissions
  assignedPermissionIds, 
  disabled, 
  onToggle,
  searchQuery,
  emptyMessageNoQuery,
  emptyMessageWithQuery,
}) => {
  // Filter INSIDE this component - parent doesn't recalculate
  const filteredPermissions = useMemo(() => {
    if (!allPermissions) return [];
    if (!searchQuery.trim()) return allPermissions;
    const searchLower = searchQuery.toLowerCase();
    return allPermissions.filter((permission) =>
      permission.name.toLowerCase().includes(searchLower)
    );
  }, [allPermissions, searchQuery]);

  const assignedSet = useMemo(() => new Set(assignedPermissionIds), [assignedPermissionIds]);
  
  return (
    <Box
      sx={{
        minHeight: 400,
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
          {searchQuery ? emptyMessageWithQuery : emptyMessageNoQuery}
        </Typography>
      ) : (
        <Stack spacing={0.5}>
          {filteredPermissions.map((permission) => {
            const isAssigned = assignedSet.has(permission.id);
            return (
              <PermissionItem
                key={permission.id}
                permission={permission}
                isAssigned={isAssigned}
                disabled={disabled}
                onToggle={onToggle}
              />
            );
          })}
        </Stack>
      )}
    </Box>
  );
}, (prevProps, nextProps) => {
  // Compare allPermissions by reference (stable from hook)
  const allPermissionsEqual = prevProps.allPermissions === nextProps.allPermissions;
  const assignedIdsEqual = 
    prevProps.assignedPermissionIds.length === nextProps.assignedPermissionIds.length &&
    prevProps.assignedPermissionIds.every((id, i) => id === nextProps.assignedPermissionIds[i]);
  
  return (
    allPermissionsEqual &&
    assignedIdsEqual &&
    prevProps.disabled === nextProps.disabled &&
    prevProps.searchQuery === nextProps.searchQuery &&
    prevProps.emptyMessageNoQuery === nextProps.emptyMessageNoQuery &&
    prevProps.emptyMessageWithQuery === nextProps.emptyMessageWithQuery
  );
});

const RolePermissionAssignmentComponent: React.FC<RolePermissionAssignmentProps> = ({
  roleId,
  assignedPermissions,
  onAssignPermission,
  onRemovePermission,
  assignLoading = false,
  removeLoading = false,
  onPermissionsChange,
  active = true,
  onChange,
}) => {
  const { t } = useTranslation(authorizationManagementTranslations);
  const toast = useToast();
  
  // Separate input state (immediate) from filter state (debounced)
  const [searchInput, setSearchInput] = useState("");
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
    initialFirst: 100, // Fetch up to 100 permissions for selection (max allowed)
    skip: !active,
  });

  // Create a set of assigned permission IDs for quick lookup
  const assignedPermissionIds = useMemo(() => {
    return new Set(assignedPermissions.map((p) => p.id));
  }, [assignedPermissions]);

  const handlePermissionToggle = useCallback(
    async (permissionId: string, isChecked: boolean) => {
      // Edit mode with deferred mutations: use onChange callback to update local state
      if (onChange) {
        const currentPermissionIds = new Set(assignedPermissions.map(p => p.id));
        let newPermissions: Permission[];
        
        if (isChecked) {
          // Add permission
          const permission = allPermissions?.find((p) => p.id === permissionId);
          if (permission && !currentPermissionIds.has(permissionId)) {
            newPermissions = [...assignedPermissions, { id: permission.id, name: permission.name }];
            onChange(newPermissions);
          }
        } else {
          // Remove permission
          newPermissions = assignedPermissions.filter((p) => p.id !== permissionId);
          onChange(newPermissions);
        }
        return;
      }

      // Legacy edit mode: assign/remove permissions via immediate mutations
      if (!onAssignPermission || !onRemovePermission) return;

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
      onChange,
      toast,
      t,
      allPermissions,
      assignedPermissions,
      onPermissionsChange,
    ]
  );

  // Handle immediate input change (for responsive typing)
  const handleInputChange = useCallback((value: string) => {
    setSearchInput(value);
  }, []);

  // Handle debounced search change (for actual filtering)
  const handleSearchChange = useCallback(
    (value: string) => {
      setSearchQuery(value);
      setPermissionSearchQuery(value);
    },
    [setPermissionSearchQuery]
  );

  if (!active) {
    return null;
  }

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
        value={searchInput}
        onChange={handleInputChange}
        onSearch={handleSearchChange}
        placeholder={t("roleManagement.permissions.searchPlaceholder")}
      />
      <PermissionsList
        allPermissions={allPermissions}  // Changed from filteredPermissions
        assignedPermissionIds={assignedPermissionIds}
        disabled={assignLoading || removeLoading || (!!roleId && !onChange && (!onAssignPermission || !onRemovePermission))}
        onToggle={handlePermissionToggle}
        searchQuery={searchQuery}
        emptyMessageNoQuery={t("roleManagement.permissions.noPermissions")}
        emptyMessageWithQuery={t("roleManagement.permissions.noAvailableMatching")}
      />
    </Box>
  );
};

// Memoize the component to prevent unnecessary re-renders
// The default shallow comparison is sufficient since we're already memoizing individual items
export const RolePermissionAssignment = memo(RolePermissionAssignmentComponent);
