"use client";

import React, { useMemo } from "react";
import { useFormContext, Controller } from "react-hook-form";
import { Box, CircularProgress, Alert, Autocomplete, TextField, Chip } from "@mui/material";
import { useGetUsersQuery } from "@/lib/graphql/operations/authorization-management/queries.generated";
import { useTranslation } from "@/shared/i18n";
import { authorizationManagementTranslations } from "@/app/(neotool)/settings/i18n";
import type { GroupFormData } from "./GroupForm";

export interface GroupUserAssignmentProps {
  initialUserIds?: string[];
}

/**
 * GroupUserAssignment component for selecting users to assign to a group
 * Must be used within a FormProvider from react-hook-form
 */
export const GroupUserAssignment: React.FC<GroupUserAssignmentProps> = ({
  initialUserIds,
}) => {
  const { t } = useTranslation(authorizationManagementTranslations);
  const { watch, control } = useFormContext<GroupFormData>();

  // Fetch all users for selection
  const { data, loading, error } = useGetUsersQuery({
    variables: {
      first: 1000, // Fetch a large number of users for selection
      query: undefined,
    },
    skip: false,
  });

  // Transform users data for AutocompleteField
  const userOptions = useMemo(() => {
    return (data?.users?.nodes || []).map((user) => ({
      id: user.id,
      label: user.displayName || user.email,
      email: user.email,
      displayName: user.displayName,
    }));
  }, [data?.users?.nodes]);

  // Get current userIds from form (should be string IDs)
  const currentUserIds = watch("userIds") || [];

  // Map current userIds (strings) to user objects for Autocomplete display
  const selectedUsers = useMemo(() => {
    if (!currentUserIds.length) return [];
    // Handle both string IDs and objects (for backward compatibility)
    const ids = currentUserIds.map((item) => 
      typeof item === "string" ? item : (item as any)?.id
    ).filter(Boolean);
    return userOptions.filter((user) => ids.includes(user.id));
  }, [currentUserIds, userOptions]);

  if (loading) {
    return (
      <Box sx={{ display: "flex", justifyContent: "center", p: 2 }}>
        <CircularProgress size={24} />
      </Box>
    );
  }

  if (error) {
    return (
      <Alert severity="error" sx={{ mb: 2 }}>
        {t("groupManagement.form.errors.loadUsersFailed")}
      </Alert>
    );
  }

  return (
    <Controller
      name="userIds"
      control={control}
      render={({ field, fieldState }) => (
        <Autocomplete
          multiple
          options={userOptions}
          getOptionLabel={(option) => option.label}
          value={selectedUsers}
          onChange={(_event, newValue) => {
            // Extract only the IDs from the selected user objects
            const userIds = newValue.map((user) => user.id);
            field.onChange(userIds);
          }}
          renderTags={(value: typeof userOptions, getTagProps) =>
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
              label={t("groupManagement.form.users")}
              error={!!fieldState.error}
              helperText={fieldState.error?.message ?? t("groupManagement.form.usersHelper")}
              fullWidth
            />
          )}
        />
      )}
    />
  );
};

