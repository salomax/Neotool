"use client";

import React, { useMemo, useCallback } from "react";
import {
  Box,
  Typography,
  CircularProgress,
  Autocomplete,
  Chip,
} from "@mui/material";
import { TextField } from "@/shared/components/ui/primitives";
import { ErrorAlert } from "@/shared/components/ui/feedback";
import { useGetGroupsQuery } from "@/lib/graphql/operations/authorization-management/queries.generated";
import { useTranslation } from "@/shared/i18n";
import { authorizationManagementTranslations } from "@/app/(settings)/settings/i18n";

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
  label: string;
  name: string;
  description: string | null;
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

  // Fetch all groups for selection
  const { data, loading: groupsLoading, error: groupsError, refetch } = useGetGroupsQuery({
    variables: {
      first: 100, // API limit is 100 items per request
      query: undefined,
    },
    skip: false,
  });

  // Transform groups data for Autocomplete
  const groupOptions = useMemo(() => {
    return (data?.groups?.edges?.map(e => e.node) || []).map((group) => ({
      id: group.id,
      label: group.name,
      name: group.name,
      description: group.description,
    }));
  }, [data?.groups?.edges]);

  // Map assigned groups to option format
  const selectedGroups = useMemo(() => {
    return assignedGroups.map((group) => ({
      id: group.id,
      label: group.name,
      name: group.name,
      description: group.description,
    }));
  }, [assignedGroups]);

  const handleChange = useCallback(
    (_event: any, newValue: GroupOption[]) => {
      // Prevent duplicates - filter out any duplicates in newValue
      // Autocomplete should handle this, but we ensure uniqueness
      const uniqueNewValue = Array.from(
        new Map(newValue.map((group) => [group.id, group])).values()
      );

      // Convert to Group format and notify parent
      const selectedGroups: Group[] = uniqueNewValue.map((option) => ({
        id: option.id,
        name: option.name,
        description: option.description,
      }));
      onChange?.(selectedGroups);
    },
    [onChange]
  );

  if (groupsLoading) {
    return (
      <Box sx={{ display: "flex", justifyContent: "center", p: 2 }}>
        <CircularProgress size={24} />
      </Box>
    );
  }

  if (groupsError) {
    return (
      <ErrorAlert
        error={groupsError}
        onRetry={() => refetch()}
        fallbackMessage={t("userManagement.groups.loadError")}
      />
    );
  }

  return (
    <Box data-testid="user-group-assignment">
      <Autocomplete
        multiple
        options={groupOptions}
        getOptionLabel={(option) => option.label}
        value={selectedGroups}
        onChange={handleChange}
        isOptionEqualToValue={(option, value) => option.id === value.id}
        filterOptions={(options, { inputValue }) => {
          const searchLower = inputValue.toLowerCase();
          return options.filter((option) =>
            option.name.toLowerCase().includes(searchLower)
          );
        }}
        renderTags={(value: GroupOption[], getTagProps) =>
          value.map((option, index) => {
            const { key, onDelete, ...tagProps } = getTagProps({ index });
            return (
              <Chip
                key={key}
                variant="outlined"
                color="primary"
                label={option.label}
                onDelete={onDelete}
                data-testid={`user-group-chip-${option.id}`}
                {...tagProps}
              />
            );
          })
        }
        renderInput={(params) => (
          <TextField
            {...params}
            placeholder={t("userManagement.groups.searchPlaceholder")}
            fullWidth
            variant="outlined"
            data-testid="user-group-assignment-input"
            sx={{
              '& .MuiOutlinedInput-root': {
                '& .MuiOutlinedInput-notchedOutline': {
                  borderColor: 'primary.main',
                },
                '&:hover .MuiOutlinedInput-notchedOutline': {
                  borderColor: 'primary.main',
                },
                '&.Mui-focused .MuiOutlinedInput-notchedOutline': {
                  borderColor: 'primary.main',
                },
              },
            }}
          />
        )}
      />
    </Box>
  );
};

