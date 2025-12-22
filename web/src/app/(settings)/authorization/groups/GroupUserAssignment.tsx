"use client";

import React, { useMemo } from "react";
import { ListItem, Typography, Box } from "@mui/material";
import { useGetUsersQuery, type GetUsersQuery, type GetUsersQueryVariables } from "@/lib/graphql/operations/authorization-management/queries.generated";
import { useTranslation } from "@/shared/i18n";
import { authorizationManagementTranslations } from "@/app/(settings)/settings/i18n";
import { useAuth } from "@/shared/providers/AuthProvider";
import { SearchableAutocomplete } from "@/shared/components/ui/forms/SearchableAutocomplete";
import { useFormContext } from "react-hook-form";

export type User = {
  id: string;
  email: string;
  displayName: string | null;
  enabled: boolean;
};

export interface GroupUserAssignmentProps {
  assignedUsers: User[];
  onChange?: (users: User[]) => void;
  /**
   * Optional field name for react-hook-form integration
   */
  name?: string;
}

type UserOption = {
  id: string;
  label: string;
  email: string;
  displayName: string | null;
};

/**
 * GroupUserAssignment component for selecting users to assign to a group
 * Works like GroupRoleAssignment - uses onChange callback instead of form
 */
export const GroupUserAssignment: React.FC<GroupUserAssignmentProps> = ({
  assignedUsers,
  onChange,
  name = "userIds",
}) => {
  const { t } = useTranslation(authorizationManagementTranslations);
  const { isAuthenticated } = useAuth();
  
  // Optional react-hook-form integration
  // Access formState from useFormContext - it's reactive and triggers re-renders
  let formContext: ReturnType<typeof useFormContext> | null = null;
  try {
    formContext = useFormContext();
  } catch {
    // Not in a form context, that's okay
  }
  
  // formState from useFormContext is reactive and will trigger re-renders when errors change
  const formState = formContext?.formState;
  const fieldState = formState?.errors[name];
  const fieldError = !!fieldState;
  const fieldHelperText = fieldState?.message as string | undefined;

  // Map assigned users to option format, deduplicating by ID
  const selectedUsers = useMemo(() => {
    const userMap = new Map<string, UserOption>();
    for (const user of assignedUsers) {
      if (!userMap.has(user.id)) {
        userMap.set(user.id, {
          id: user.id,
          label: user.displayName || user.email,
          email: user.email,
          displayName: user.displayName,
        });
      }
    }
    return Array.from(userMap.values());
  }, [assignedUsers]);

  const handleChange = (newSelected: UserOption[]) => {
    if (!onChange) return;
    
    const newUsers: User[] = newSelected.map((option) => ({
      id: option.id,
      email: option.email,
      displayName: option.displayName,
      enabled: true, // We don't have enabled from the option, but it's fine
    }));
    
    onChange(newUsers);
  };

  return (
    <SearchableAutocomplete<
      UserOption,
      UserOption,
      GetUsersQuery,
      GetUsersQueryVariables
    >
      useQuery={useGetUsersQuery}
      getQueryVariables={(searchQuery) => ({
        first: 5,
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
      onChange={handleChange}
      getOptionId={(option) => option.id}
      getOptionLabel={(option) => option.label}
      isOptionEqualToValue={(option, value) => option.id === value.id}
      multiple
      label={t("groupManagement.form.users")}
      placeholder={t("groupManagement.form.usersHelper")}
      helperText={fieldHelperText || t("groupManagement.form.usersHelper")}
      fieldError={fieldError}
      skip={!isAuthenticated}
      errorMessage={t("groupManagement.form.errors.loadUsersFailed")}
      variant="outlined"
      loadMode="eager"
      renderOption={(props, option) => {
        return (
          <ListItem {...props}>
            <Box sx={{ display: "flex", alignItems: "center", gap: 1, width: "100%" }}>
              <Typography component="span">
                {option.displayName || option.email}
              </Typography>
              {option.displayName && (
                <Typography
                  component="span"
                  variant="body2"
                  sx={{ color: "text.secondary" }}
                >
                  {option.email}
                </Typography>
              )}
            </Box>
          </ListItem>
        );
      }}
    />
  );
};

