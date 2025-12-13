"use client";

import React, { useMemo } from "react";
import { ListItem } from "@mui/material";
import { useGetUsersQuery, type GetUsersQuery, type GetUsersQueryVariables } from "@/lib/graphql/operations/authorization-management/queries.generated";
import { useTranslation } from "@/shared/i18n";
import { authorizationManagementTranslations } from "@/app/(settings)/settings/i18n";
import { useAuth } from "@/shared/providers/AuthProvider";
import { SearchableAutocomplete } from "@/shared/components/ui/forms/SearchableAutocomplete";

export type User = {
  id: string;
  email: string;
  displayName: string | null;
  enabled: boolean;
};

export interface GroupUserAssignmentProps {
  assignedUsers: User[];
  onChange?: (users: User[]) => void;
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
}) => {
  const { t } = useTranslation(authorizationManagementTranslations);
  const { isAuthenticated } = useAuth();

  // Map assigned users to option format
  const selectedUsers = useMemo(() => {
    return assignedUsers.map((user) => ({
      id: user.id,
      label: user.displayName || user.email,
      email: user.email,
      displayName: user.displayName,
    }));
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
      onChange={handleChange}
      getOptionId={(option) => option.id}
      getOptionLabel={(option) => option.label}
      isOptionEqualToValue={(option, value) => option.id === value.id}
        multiple
        placeholder={t("groupManagement.form.usersHelper")}
        skip={!isAuthenticated}
        errorMessage={t("groupManagement.form.errors.loadUsersFailed")}
        variant="outlined"
      renderOption={(props, option) => {
        const { key, ...otherProps } = props;
        return (
          <ListItem {...otherProps} key={option.id}>
            {option.label}
          </ListItem>
        );
      }}
    />
  );
};

