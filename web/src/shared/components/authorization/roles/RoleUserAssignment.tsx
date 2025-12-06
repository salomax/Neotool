"use client";

import React, { useMemo, useCallback, useEffect } from "react";
import {
  Box,
  Typography,
  CircularProgress,
  Alert,
  Autocomplete,
  TextField,
  Chip,
} from "@mui/material";
import { useGetUsersQuery } from "@/lib/graphql/operations/authorization-management/queries.generated";
import { useTranslation } from "@/shared/i18n";
import { authorizationManagementTranslations } from "@/app/(neotool)/settings/i18n";
import { useToast } from "@/shared/providers";
import { extractErrorMessage } from "@/shared/utils/error";

export interface User {
  id: string;
  email: string;
  displayName: string | null;
  enabled: boolean;
}

export interface RoleUserAssignmentProps {
  roleId: string | null;
  assignedUsers: User[];
  onAssignUser: (userId: string) => Promise<void>;
  onRemoveUser: (userId: string) => Promise<void>;
  assignLoading?: boolean;
  removeLoading?: boolean;
  onUsersChange?: () => void;
}

type UserOption = {
  id: string;
  label: string;
  email: string;
  displayName: string | null;
  enabled: boolean;
};

/**
 * RoleUserAssignment component for managing role user assignments
 * Uses a multi-select Autocomplete to assign and remove users
 */
export const RoleUserAssignment: React.FC<RoleUserAssignmentProps> = ({
  roleId,
  assignedUsers,
  onAssignUser,
  onRemoveUser,
  assignLoading = false,
  removeLoading = false,
  onUsersChange,
}) => {
  const { t } = useTranslation(authorizationManagementTranslations);
  const toast = useToast();

  // Fetch all users for selection
  const { data, loading: usersLoading, error: usersError } = useGetUsersQuery({
    variables: {
      first: 1000, // Fetch a large number of users for selection
      query: undefined,
    },
    skip: false,
  });

  // Transform users data for Autocomplete
  const userOptions = useMemo(() => {
    return (data?.users?.edges?.map(e => e.node) || []).map((user) => ({
      id: user.id,
      label: user.displayName || user.email,
      email: user.email,
      displayName: user.displayName,
      enabled: user.enabled,
    }));
  }, [data?.users?.edges]);

  // Map assigned users to option format
  const selectedUsers = useMemo(() => {
    return assignedUsers.map((user) => ({
      id: user.id,
      label: user.displayName || user.email,
      email: user.email,
      displayName: user.displayName,
      enabled: user.enabled,
    }));
  }, [assignedUsers]);

  const handleChange = useCallback(
    async (_event: any, newValue: UserOption[]) => {
      // Prevent duplicates - filter out any duplicates in newValue
      // Autocomplete should handle this, but we ensure uniqueness
      const uniqueNewValue = Array.from(
        new Map(newValue.map((user) => [user.id, user])).values()
      );

      const currentIds = new Set(assignedUsers.map((u) => u.id));
      const newIds = new Set(uniqueNewValue.map((u) => u.id));

      // Find added users (in uniqueNewValue but not in assignedUsers)
      const addedUsers = uniqueNewValue.filter((user) => !currentIds.has(user.id));
      
      // Find removed users (in assignedUsers but not in uniqueNewValue)
      const removedUsers = assignedUsers.filter((user) => !newIds.has(user.id));

      try {
        // Assign new users
        for (const user of addedUsers) {
          await onAssignUser(user.id);
        }

        // Remove users
        for (const user of removedUsers) {
          await onRemoveUser(user.id);
        }

        onUsersChange?.();
      } catch (err) {
        console.error("Error updating user assignments:", err);
        const errorMessage = extractErrorMessage(
          err,
          t("roleManagement.users.assignError")
        );
        toast.error(errorMessage);
      }
    },
    [roleId, assignedUsers, onAssignUser, onRemoveUser, toast, t, onUsersChange]
  );

  if (usersLoading) {
    return (
      <Box sx={{ display: "flex", justifyContent: "center", p: 2 }}>
        <CircularProgress size={24} />
      </Box>
    );
  }

  if (usersError) {
    return (
      <Alert severity="error" sx={{ mb: 2 }}>
        {t("roleManagement.users.loadError")}
      </Alert>
    );
  }

  return (
    <Box>
      <Typography variant="subtitle1" gutterBottom>
        {t("roleManagement.users.assigned")}
      </Typography>
      <Autocomplete
        multiple
        options={userOptions}
        getOptionLabel={(option) => option.label}
        value={selectedUsers}
        onChange={handleChange}
        loading={assignLoading || removeLoading}
        disabled={assignLoading || removeLoading}
        isOptionEqualToValue={(option, value) => option.id === value.id}
        filterOptions={(options, { inputValue }) => {
          const searchLower = inputValue.toLowerCase();
          return options.filter(
            (option) =>
              option.email.toLowerCase().includes(searchLower) ||
              option.label.toLowerCase().includes(searchLower)
          );
        }}
        renderTags={(value: UserOption[], getTagProps) =>
          value.map((option, index) => {
            const { key, ...tagProps } = getTagProps({ index });
            return (
              <Chip
                key={key}
                variant="outlined"
                label={option.label}
                {...tagProps}
              />
            );
          })
        }
        renderInput={(params) => (
          <TextField
            {...params}
            label={t("roleManagement.users.assigned")}
            placeholder={t("roleManagement.users.searchPlaceholder")}
            fullWidth
          />
        )}
      />
    </Box>
  );
};

