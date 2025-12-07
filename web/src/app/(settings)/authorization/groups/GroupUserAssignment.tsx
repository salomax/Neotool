"use client";

import React, { useMemo } from "react";
import { useFormContext, Controller } from "react-hook-form";
import { Box, CircularProgress, Autocomplete, TextField, Chip, ListItem } from "@mui/material";
import { ErrorAlert } from "@/shared/components/ui/feedback";
import { useGetUsersQuery } from "@/lib/graphql/operations/authorization-management/queries.generated";
import { useTranslation } from "@/shared/i18n";
import { authorizationManagementTranslations } from "@/app/(settings)/settings/i18n";
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
  const { data, loading, error, refetch } = useGetUsersQuery({
    variables: {
      first: 1000, // Fetch a large number of users for selection
      query: undefined,
    },
    skip: false,
  });

  // Transform users data for AutocompleteField and deduplicate by ID
  const userOptions = useMemo(() => {
    const users = (data?.users?.edges?.map(e => e.node) || []).map((user) => ({
      id: user.id,
      label: user.displayName || user.email,
      email: user.email,
      displayName: user.displayName,
    }));
    // Deduplicate by ID to ensure uniqueness
    const uniqueUsers = Array.from(
      new Map(users.map((user) => [user.id, user])).values()
    );
    return uniqueUsers;
  }, [data?.users?.edges]);

  // Get current userIds from form (should be string IDs)
  const currentUserIds = watch("userIds") || [];

  // Map current userIds (strings) to user objects for Autocomplete display
  // Deduplicate to prevent duplicate selections
  const selectedUsers = useMemo(() => {
    if (!currentUserIds.length) return [];
    // Handle both string IDs and objects (for backward compatibility)
    const ids = Array.from(
      new Set(
        currentUserIds
          .map((item) => (typeof item === "string" ? item : (item as any)?.id))
          .filter(Boolean)
      )
    );
    const selected = userOptions.filter((user) => ids.includes(user.id));
    // Deduplicate by ID to ensure uniqueness
    return Array.from(
      new Map(selected.map((user) => [user.id, user])).values()
    );
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
      <ErrorAlert
        error={error}
        onRetry={() => refetch()}
        fallbackMessage={t("groupManagement.form.errors.loadUsersFailed")}
      />
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
          isOptionEqualToValue={(option, value) => option.id === value.id}
          value={selectedUsers}
          onChange={(_event, newValue) => {
            // Deduplicate by ID to prevent duplicates
            const uniqueUsers = Array.from(
              new Map(newValue.map((user) => [user.id, user])).values()
            );
            // Extract only the IDs from the selected user objects
            const userIds = uniqueUsers.map((user) => user.id);
            field.onChange(userIds);
          }}
          renderOption={(props, option) => {
            const { key, ...otherProps } = props;
            return (
              <ListItem {...otherProps} key={option.id}>
                {option.label}
              </ListItem>
            );
          }}
          renderTags={(value: typeof userOptions, getTagProps) =>
            value.map((option, index) => {
              const { key, ...tagProps } = getTagProps({ index });
              return (
                <Chip
                  key={option.id}
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

