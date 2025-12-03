"use client";

import React, { useMemo, useCallback } from "react";
import {
  Box,
  Typography,
  CircularProgress,
  Alert,
  Autocomplete,
  TextField,
  Chip,
} from "@mui/material";
import { useGetRolesQuery } from "@/lib/graphql/operations/authorization-management/queries.generated";
import { useTranslation } from "@/shared/i18n";
import { authorizationManagementTranslations } from "@/app/(neotool)/settings/i18n";
import { useToast } from "@/shared/providers";
import { extractErrorMessage } from "@/shared/utils/error";

export interface Role {
  id: string;
  name: string;
}

export interface UserRoleAssignmentProps {
  userId: string | null;
  assignedRoles: Role[];
  onAssignRole: (roleId: string) => Promise<void>;
  onRemoveRole: (roleId: string) => Promise<void>;
  assignLoading?: boolean;
  removeLoading?: boolean;
  onRolesChange?: () => void;
}

type RoleOption = {
  id: string;
  label: string;
  name: string;
};

/**
 * UserRoleAssignment component for managing user role assignments
 * Uses a multi-select Autocomplete to assign and remove roles
 */
export const UserRoleAssignment: React.FC<UserRoleAssignmentProps> = ({
  userId,
  assignedRoles,
  onAssignRole,
  onRemoveRole,
  assignLoading = false,
  removeLoading = false,
  onRolesChange,
}) => {
  const { t } = useTranslation(authorizationManagementTranslations);
  const toast = useToast();

  // Fetch all roles for selection
  const { data, loading: rolesLoading, error: rolesError } = useGetRolesQuery({
    variables: {
      first: 1000, // Fetch a large number of roles for selection
      query: undefined,
    },
    skip: false,
  });

  // Transform roles data for Autocomplete
  const roleOptions = useMemo(() => {
    return (data?.roles?.nodes || []).map((role) => ({
      id: role.id,
      label: role.name,
      name: role.name,
    }));
  }, [data?.roles?.nodes]);

  // Map assigned roles to option format
  const selectedRoles = useMemo(() => {
    return assignedRoles.map((role) => ({
      id: role.id,
      label: role.name,
      name: role.name,
    }));
  }, [assignedRoles]);

  const handleChange = useCallback(
    async (_event: any, newValue: RoleOption[]) => {
      // Prevent duplicates - filter out any duplicates in newValue
      // Autocomplete should handle this, but we ensure uniqueness
      const uniqueNewValue = Array.from(
        new Map(newValue.map((role) => [role.id, role])).values()
      );

      const currentIds = new Set(assignedRoles.map((r) => r.id));
      const newIds = new Set(uniqueNewValue.map((r) => r.id));

      // Find added roles (in uniqueNewValue but not in assignedRoles)
      const addedRoles = uniqueNewValue.filter((role) => !currentIds.has(role.id));
      
      // Find removed roles (in assignedRoles but not in uniqueNewValue)
      const removedRoles = assignedRoles.filter((role) => !newIds.has(role.id));

      try {
        // Assign new roles
        for (const role of addedRoles) {
          await onAssignRole(role.id);
        }

        // Remove roles
        for (const role of removedRoles) {
          await onRemoveRole(role.id);
        }

        onRolesChange?.();
      } catch (err) {
        console.error("Error updating role assignments:", err);
        const errorMessage = extractErrorMessage(
          err,
          t("userManagement.roles.assignError")
        );
        toast.error(errorMessage);
      }
    },
    [userId, assignedRoles, onAssignRole, onRemoveRole, toast, t, onRolesChange]
  );

  if (rolesLoading) {
    return (
      <Box sx={{ display: "flex", justifyContent: "center", p: 2 }}>
        <CircularProgress size={24} />
      </Box>
    );
  }

  if (rolesError) {
    return (
      <Alert severity="error" sx={{ mb: 2 }}>
        {t("userManagement.roles.loadError")}
      </Alert>
    );
  }

  return (
    <Box>
      <Autocomplete
        multiple
        options={roleOptions}
        getOptionLabel={(option) => option.label}
        value={selectedRoles}
        onChange={handleChange}
        loading={assignLoading || removeLoading}
        disabled={assignLoading || removeLoading}
        isOptionEqualToValue={(option, value) => option.id === value.id}
        filterOptions={(options, { inputValue }) => {
          const searchLower = inputValue.toLowerCase();
          return options.filter((option) =>
            option.name.toLowerCase().includes(searchLower)
          );
        }}
        renderTags={(value: RoleOption[], getTagProps) =>
          value.map((option, index) => {
            const { key, ...tagProps } = getTagProps({ index });
            return (
              <Chip
                key={key}
                variant="outlined"
                label={option.label}
                onDelete={tagProps.onDelete}
                {...tagProps}
              />
            );
          })
        }
        renderInput={(params) => (
          <TextField
            {...params}
            label={t("userManagement.drawer.roles")}
            placeholder={t("userManagement.roles.searchPlaceholder")}
            fullWidth
          />
        )}
      />
    </Box>
  );
};

