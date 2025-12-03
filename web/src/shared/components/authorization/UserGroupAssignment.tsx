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
import { useGetGroupsQuery } from "@/lib/graphql/operations/authorization-management/queries.generated";
import { useTranslation } from "@/shared/i18n";
import { authorizationManagementTranslations } from "@/app/(neotool)/settings/i18n";
import { useToast } from "@/shared/providers";
import { extractErrorMessage } from "@/shared/utils/error";

export interface Group {
  id: string;
  name: string;
  description: string | null;
}

export interface UserGroupAssignmentProps {
  userId: string | null;
  assignedGroups: Group[];
  onAssignGroup: (groupId: string) => Promise<void>;
  onRemoveGroup: (groupId: string) => Promise<void>;
  assignLoading?: boolean;
  removeLoading?: boolean;
  onGroupsChange?: () => void;
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
  onAssignGroup,
  onRemoveGroup,
  assignLoading = false,
  removeLoading = false,
  onGroupsChange,
}) => {
  const { t } = useTranslation(authorizationManagementTranslations);
  const toast = useToast();

  // Fetch all groups for selection
  const { data, loading: groupsLoading, error: groupsError } = useGetGroupsQuery({
    variables: {
      first: 1000, // Fetch a large number of groups for selection
      query: undefined,
    },
    skip: false,
  });

  // Transform groups data for Autocomplete
  const groupOptions = useMemo(() => {
    return (data?.groups?.nodes || []).map((group) => ({
      id: group.id,
      label: group.name,
      name: group.name,
      description: group.description,
    }));
  }, [data?.groups?.nodes]);

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
    async (_event: any, newValue: GroupOption[]) => {
      // Prevent duplicates - filter out any duplicates in newValue
      // Autocomplete should handle this, but we ensure uniqueness
      const uniqueNewValue = Array.from(
        new Map(newValue.map((group) => [group.id, group])).values()
      );

      const currentIds = new Set(assignedGroups.map((g) => g.id));
      const newIds = new Set(uniqueNewValue.map((g) => g.id));

      // Find added groups (in uniqueNewValue but not in assignedGroups)
      const addedGroups = uniqueNewValue.filter((group) => !currentIds.has(group.id));
      
      // Find removed groups (in assignedGroups but not in uniqueNewValue)
      const removedGroups = assignedGroups.filter((group) => !newIds.has(group.id));

      try {
        // Assign new groups
        for (const group of addedGroups) {
          await onAssignGroup(group.id);
        }

        // Remove groups
        for (const group of removedGroups) {
          await onRemoveGroup(group.id);
        }

        onGroupsChange?.();
      } catch (err) {
        console.error("Error updating group assignments:", err);
        const errorMessage = extractErrorMessage(
          err,
          t("userManagement.groups.assignError")
        );
        toast.error(errorMessage);
      }
    },
    [userId, assignedGroups, onAssignGroup, onRemoveGroup, toast, t, onGroupsChange]
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
      <Alert severity="error" sx={{ mb: 2 }}>
        {t("userManagement.groups.loadError")}
      </Alert>
    );
  }

  return (
    <Box>
      <Autocomplete
        multiple
        options={groupOptions}
        getOptionLabel={(option) => option.label}
        value={selectedGroups}
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
        renderTags={(value: GroupOption[], getTagProps) =>
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
            label={t("userManagement.drawer.groups")}
            placeholder={t("userManagement.groups.searchPlaceholder")}
            fullWidth
          />
        )}
      />
    </Box>
  );
};

