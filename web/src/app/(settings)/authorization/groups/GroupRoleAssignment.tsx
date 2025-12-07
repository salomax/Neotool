"use client";

import React, { useMemo, useCallback } from "react";
import {
  Box,
  Typography,
  CircularProgress,
  Autocomplete,
  TextField,
  Chip,
} from "@mui/material";
import { ErrorAlert } from "@/shared/components/ui/feedback";
import { useGetRolesQuery } from "@/lib/graphql/operations/authorization-management/queries.generated";
import { useTranslation } from "@/shared/i18n";
import { authorizationManagementTranslations } from "@/app/(settings)/settings/i18n";
import { useToast } from "@/shared/providers";
import { extractErrorMessage } from "@/shared/utils/error";

export interface Role {
  id: string;
  name: string;
}

export interface GroupRoleAssignmentProps {
  groupId: string | null;
  assignedRoles: Role[];
  onAssignRole?: (roleId: string) => Promise<void>;
  onRemoveRole?: (roleId: string) => Promise<void>;
  assignLoading?: boolean;
  removeLoading?: boolean;
  onRolesChange?: () => void;
  // For create mode: callback when roles are selected (before group is created)
  onPendingRolesChange?: (roles: Role[]) => void;
}

type RoleOption = {
  id: string;
  label: string;
  name: string;
};

/**
 * GroupRoleAssignment component for managing group role assignments
 * Uses a multi-select Autocomplete to assign and remove roles
 */
export const GroupRoleAssignment: React.FC<GroupRoleAssignmentProps> = ({
  groupId,
  assignedRoles,
  onAssignRole,
  onRemoveRole,
  assignLoading = false,
  removeLoading = false,
  onRolesChange,
  onPendingRolesChange,
}) => {
  const { t } = useTranslation(authorizationManagementTranslations);
  const toast = useToast();

  // Query all available roles
  const { data, loading, error, refetch } = useGetRolesQuery({
    variables: { first: 1000 },
  });

  // Get available roles
  const availableRoles = useMemo(() => {
    return data?.roles?.edges?.map(e => e.node) || [];
  }, [data?.roles?.edges]);

  // Create role options for Autocomplete
  const roleOptions: RoleOption[] = useMemo(() => {
    return availableRoles.map((role) => ({
      id: role.id,
      label: role.name,
      name: role.name,
    }));
  }, [availableRoles]);

  // Get currently selected roles
  const selectedRoles = useMemo(() => {
    return roleOptions.filter((option) =>
      assignedRoles.some((assigned) => assigned.id === option.id)
    );
  }, [roleOptions, assignedRoles]);

  // Handle role selection changes
  const handleRoleChange = useCallback(
    async (_event: React.SyntheticEvent, newValue: RoleOption[]) => {
      // If in create mode (no groupId), just update pending roles
      if (!groupId) {
        const pendingRoles: Role[] = newValue.map((option) => ({
          id: option.id,
          name: option.name,
        }));
        onPendingRolesChange?.(pendingRoles);
        return;
      }

      // Edit mode: assign/remove roles via mutations
      if (!onAssignRole || !onRemoveRole) return;

      const currentRoleIds = new Set(assignedRoles.map((r) => r.id));
      const newRoleIds = new Set(newValue.map((r) => r.id));

      // Find roles to add
      const rolesToAdd = newValue.filter(
        (role) => !currentRoleIds.has(role.id)
      );

      // Find roles to remove
      const rolesToRemove = assignedRoles.filter(
        (role) => !newRoleIds.has(role.id)
      );

      try {
        // Add new roles
        for (const role of rolesToAdd) {
          await onAssignRole(role.id);
        }

        // Remove roles
        for (const role of rolesToRemove) {
          await onRemoveRole(role.id);
        }

        onRolesChange?.();
      } catch (err) {
        const errorMessage = extractErrorMessage(
          err,
          t("groupManagement.roles.assignError")
        );
        toast.error(errorMessage);
      }
    },
    [
      groupId,
      assignedRoles,
      onAssignRole,
      onRemoveRole,
      onRolesChange,
      onPendingRolesChange,
      toast,
      t,
    ]
  );

  if (loading) {
    return (
      <Box sx={{ display: "flex", justifyContent: "center", p: 2 }}>
        <CircularProgress size={24} />
      </Box>
    );
  }

  if (error) {
    return (
      <ErrorAlert
        error={error}
        onRetry={() => refetch()}
        fallbackMessage={t("groupManagement.roles.loadError")}
      />
    );
  }

  return (
    <Box>
      <Autocomplete
        multiple
        options={roleOptions}
        value={selectedRoles}
        onChange={handleRoleChange}
        getOptionLabel={(option) => option.label}
        isOptionEqualToValue={(option, value) => option.id === value.id}
        renderInput={(params) => (
          <TextField
            {...params}
            placeholder={t("groupManagement.roles.selectRoles")}
            disabled={assignLoading || removeLoading}
          />
        )}
        renderTags={(value, getTagProps) =>
          value.map((option, index) => (
            <Chip
              {...getTagProps({ index })}
              key={option.id}
              label={option.label}
              disabled={assignLoading || removeLoading}
            />
          ))
        }
        disabled={assignLoading || removeLoading || (!!groupId && (!onAssignRole || !onRemoveRole))}
        loading={loading}
      />
      {selectedRoles.length === 0 && (
        <Typography
          variant="body2"
          color="text.secondary"
          sx={{ mt: 1, fontStyle: "italic" }}
        >
          {t("groupManagement.roles.noRolesAssigned")}
        </Typography>
      )}
    </Box>
  );
};

