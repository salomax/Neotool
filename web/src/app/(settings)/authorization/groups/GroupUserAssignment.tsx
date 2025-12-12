"use client";

import React, { useMemo } from "react";
import { useFormContext, Controller } from "react-hook-form";
import { ListItem } from "@mui/material";
import { useGetUsersQuery, type GetUsersQuery, type GetUsersQueryVariables } from "@/lib/graphql/operations/authorization-management/queries.generated";
import { useTranslation } from "@/shared/i18n";
import { authorizationManagementTranslations } from "@/app/(settings)/settings/i18n";
import { useAuth } from "@/shared/providers/AuthProvider";
import { SearchableAutocomplete } from "@/shared/components/ui/forms/SearchableAutocomplete";
import type { GroupFormData } from "./GroupForm";

export interface GroupUserAssignmentProps {
  initialUserIds?: string[];
}

type UserOption = {
  id: string;
  label: string;
  email: string;
  displayName: string | null;
};

/**
 * GroupUserAssignment component for selecting users to assign to a group
 * Must be used within a FormProvider from react-hook-form
 */
export const GroupUserAssignment: React.FC<GroupUserAssignmentProps> = ({
  initialUserIds,
}) => {
  const { t } = useTranslation(authorizationManagementTranslations);
  const { watch, control } = useFormContext<GroupFormData>();
  const { isAuthenticated } = useAuth();

  // Get current userIds from form (should be string IDs)
  const currentUserIds = watch("userIds") || [];
  
  // Map current userIds to user option format for selected items
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
    // Create placeholder options for selected items (they'll be merged with search results)
    return ids.map((id) => ({
      id: id as string,
      label: id as string, // Temporary, will be replaced by actual data
      email: "",
      displayName: null,
    }));
  }, [currentUserIds]);

  return (
    <Controller
      name="userIds"
      control={control}
      render={({ field, fieldState }) => (
        <SearchableAutocomplete<
          UserOption,
          UserOption,
          GetUsersQuery,
          GetUsersQueryVariables
        >
          useQuery={useGetUsersQuery}
          getQueryVariables={(searchQuery) => ({
            first: 100,
            query: searchQuery || undefined,
          })}
          extractData={(data) => data?.users?.edges?.map((e) => e.node) || []}
          transformOption={(user) => ({
            id: user.id,
            label: user.displayName || user.email,
            email: user.email,
            displayName: user.displayName,
          })}
          selectedItems={selectedUsers}
          onChange={(newSelected) => {
            // Extract only the IDs from the selected user objects
            const userIds = newSelected.map((user) => user.id);
            field.onChange(userIds);
          }}
          getOptionId={(option) => option.id}
          getOptionLabel={(option) => option.label}
          isOptionEqualToValue={(option, value) => option.id === value.id}
          multiple
          label={t("groupManagement.form.users")}
          placeholder={t("groupManagement.form.usersHelper")}
          skip={!isAuthenticated}
          errorMessage={t("groupManagement.form.errors.loadUsersFailed")}
          fieldError={!!fieldState.error}
          helperText={fieldState.error?.message ?? t("groupManagement.form.usersHelper")}
          renderOption={(props, option) => {
            const { key, ...otherProps } = props;
            return (
              <ListItem {...otherProps} key={option.id}>
                {option.label}
              </ListItem>
            );
          }}
        />
      )}
    />
  );
};

