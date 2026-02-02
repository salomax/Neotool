"use client";

import React, { useMemo, useCallback } from "react";
import { Box, Typography, Chip } from "@mui/material";
import {
  GetGroupsDocument,
  type GetGroupsQuery,
  type GetGroupsQueryVariables,
} from "@/lib/graphql/operations/authorization-management/queries.generated";
import { useQuery } from "@apollo/client/react";
import { useTranslation } from "@/shared/i18n";
import { authorizationManagementTranslations } from "@/app/(settings)/settings/i18n";
import { SearchableAutocomplete } from "@/shared/components/ui/forms/SearchableAutocomplete";

// Custom hook wrapper to satisfy ESLint rules-of-hooks
function useGroupsQuery(options?: {
  variables?: GetGroupsQueryVariables;
  skip?: boolean;
  fetchPolicy?: "cache-first" | "network-only" | "cache-only" | "no-cache" | "standby";
  notifyOnNetworkStatusChange?: boolean;
}) {
  return useQuery<GetGroupsQuery, GetGroupsQueryVariables>(GetGroupsDocument, options);
}

export interface Group {
  id: string;
  name: string;
  description: string | null;
}

export interface UserGroupAssignmentProps {
  userId: string | null;
  assignedGroups: Group[];
  onChange?: (selectedGroups: Group[]) => void;
}

type GroupOption = {
  id: string;
  name: string;
  description: string | null;
  label: string;
};

/**
 * UserGroupAssignment component for managing user group assignments
 * Uses a multi-select Autocomplete to assign and remove groups
 */
export const UserGroupAssignment: React.FC<UserGroupAssignmentProps> = ({
  userId,
  assignedGroups,
  onChange,
}) => {
  const { t } = useTranslation(authorizationManagementTranslations);

  // Map assigned groups to option format
  const selectedGroups = useMemo<GroupOption[]>(() => {
    return assignedGroups.map((group) => ({
      id: group.id,
      label: group.name,
      name: group.name,
      description: group.description,
    }));
  }, [assignedGroups]);

  const handleChange = useCallback(
    (selectedOptions: GroupOption[]) => {
      const selectedGroups: Group[] = selectedOptions.map((option) => ({
        id: option.id,
        name: option.name,
        description: option.description,
      }));
      onChange?.(selectedGroups);
    },
    [onChange]
  );

  return (
    <Box data-testid="user-group-assignment">
      <SearchableAutocomplete<GroupOption, GroupOption, GetGroupsQuery, GetGroupsQueryVariables>
        multiple
        useQuery={useGroupsQuery}
        getQueryVariables={(search) => ({
          first: 5,
          query: search || undefined,
        })}
        extractData={(queryData) => queryData?.groups?.edges?.map((e) => e.node) || []}
        transformOption={(group) => ({
          id: group.id,
          name: group.name,
          description: group.description,
          label: group.name,
        })}
        selectedItems={selectedGroups}
        onChange={handleChange}
        getOptionId={(option) => option.id}
        getOptionLabel={(option) => option.label}
        isOptionEqualToValue={(option, value) => option.id === value.id}
        placeholder={t("userManagement.groups.searchPlaceholder")}
        errorMessage={t("userManagement.groups.loadError")}
        renderTags={(value: GroupOption[], getTagProps) =>
          value.map((option, index) => {
            const { key, ...tagProps } = getTagProps({ index });
            return (
              <Chip
                key={key || option.id}
                variant="outlined"
                color="primary"
                label={option.label}
                data-testid={`user-group-chip-${option.id}`}
                {...tagProps}
              />
            );
          })
        }
        fetchPolicy="network-only"
        notifyOnNetworkStatusChange
        skip={!userId}
        loadMode="eager"
      />
    </Box>
  );
};
