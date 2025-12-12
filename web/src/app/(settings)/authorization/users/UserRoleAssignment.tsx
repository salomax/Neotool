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

export interface Role {
  id: string;
  name: string;
}

export interface UserRoleAssignmentProps {
  userId: string | null;
  assignedRoles: Role[];
  onChange?: (selectedRoles: Role[]) => void;
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
  onChange,
}) => {
  const { t } = useTranslation(authorizationManagementTranslations);

  // Fetch all roles for selection
  const { data, loading: rolesLoading, error: rolesError, refetch } = useGetRolesQuery({
    variables: {
      first: 100,
      query: undefined,
    },
    skip: false,
  });

  // Transform roles data for Autocomplete
  const roleOptions = useMemo(() => {
    return (data?.roles?.edges?.map(e => e.node) || []).map((role) => ({
      id: role.id,
      label: role.name,
      name: role.name,
    }));
  }, [data?.roles?.edges]);

  // Map assigned roles to option format
  const selectedRoles = useMemo(() => {
    return assignedRoles.map((role) => ({
      id: role.id,
      label: role.name,
      name: role.name,
    }));
  }, [assignedRoles]);

  const handleChange = useCallback(
    (_event: any, newValue: RoleOption[]) => {
      // Prevent duplicates - filter out any duplicates in newValue
      // Autocomplete should handle this, but we ensure uniqueness
      const uniqueNewValue = Array.from(
        new Map(newValue.map((role) => [role.id, role])).values()
      );

      // Convert to Role format and notify parent
      const selectedRoles: Role[] = uniqueNewValue.map((option) => ({
        id: option.id,
        name: option.name,
      }));
      onChange?.(selectedRoles);
    },
    [onChange]
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
      <ErrorAlert
        error={rolesError}
        onRetry={() => refetch()}
        fallbackMessage={t("userManagement.roles.loadError")}
      />
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
        isOptionEqualToValue={(option, value) => option.id === value.id}
        filterOptions={(options, { inputValue }) => {
          const searchLower = inputValue.toLowerCase();
          return options.filter((option) =>
            option.name.toLowerCase().includes(searchLower)
          );
        }}
        renderTags={(value: RoleOption[], getTagProps) =>
          value.map((option, index) => {
            const { key, onDelete, ...tagProps } = getTagProps({ index });
            return (
              <Chip
                key={key}
                variant="outlined"
                label={option.label}
                onDelete={onDelete}
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

