"use client";

import React, { useMemo, useCallback } from "react";
import { Box, Typography, Chip } from "@mui/material";
import { useGetGroupsQuery, type GetGroupsQuery, type GetGroupsQueryVariables } from "@/lib/graphql/operations/authorization-management/queries.generated";
import { useTranslation } from "@/shared/i18n";
import { authorizationManagementTranslations } from "@/app/(settings)/settings/i18n";
import { useToast } from "@/shared/providers";
import { extractErrorMessage } from "@/shared/utils/error";
import { SearchableAutocomplete } from "@/shared/components/ui/forms/SearchableAutocomplete";

export interface Group {
  id: string;
  name: string;
  description: string | null;
}

export interface RoleGroupAssignmentProps {
  roleId: string | null;
  assignedGroups: Group[];
  onAssignGroup?: (groupId: string) => Promise<void>;
  onRemoveGroup?: (groupId: string) => Promise<void>;
  assignLoading?: boolean;
  removeLoading?: boolean;
  onGroupsChange?: () => void;
  /**
   * When false, skips loading group options and renders nothing.
   */
  active?: boolean;
  /**
   * For edit mode with deferred mutations: callback when groups change (updates local state only)
   */
  onChange?: (groups: Group[]) => void;
}

type GroupOption = {
  id: string;
  label: string;
  name: string;
  description: string | null;
};

/**
 * RoleGroupAssignment component for managing role group assignments
 * Uses a multi-select Autocomplete to assign and remove groups
 */
export const RoleGroupAssignment: React.FC<RoleGroupAssignmentProps> = ({
  roleId,
  assignedGroups,
  onAssignGroup,
  onRemoveGroup,
  assignLoading = false,
  removeLoading = false,
  onGroupsChange,
  active = true,
  onChange,
}) => {
  const { t } = useTranslation(authorizationManagementTranslations);
  const toast = useToast();

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
    async (newValue: GroupOption[]) => {
      // Prevent duplicates - filter out any duplicates in newValue
      // Autocomplete should handle this, but we ensure uniqueness
      const uniqueNewValue = Array.from(
        new Map(newValue.map((group) => [group.id, group])).values()
      );

      const newGroups: Group[] = uniqueNewValue.map((group) => ({
        id: group.id,
        name: group.name,
        description: group.description,
      }));

      // If onChange is provided (edit mode with deferred mutations), use it
      if (onChange) {
        onChange(newGroups);
        return;
      }

      // Legacy mode: immediate mutations
      if (!onAssignGroup || !onRemoveGroup) return;

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
        const errorMessage = extractErrorMessage(
          err,
          t("roleManagement.groups.assignError")
        );
        toast.error(errorMessage);
      }
    },
    [assignedGroups, onAssignGroup, onRemoveGroup, onChange, toast, t, onGroupsChange]
  );

  if (!active) {
    return null;
  }

  return (
    <Box>
      <Typography variant="subtitle1" gutterBottom>
        {t("roleManagement.groups.assigned")}
      </Typography>
      <SearchableAutocomplete<
        GroupOption,
        GroupOption,
        GetGroupsQuery,
        GetGroupsQueryVariables
      >
        useQuery={useGetGroupsQuery}
        getQueryVariables={(searchQuery) => ({
          first: 100,
          query: searchQuery || undefined,
        })}
        extractData={(data) => data?.groups?.edges?.map((e) => e.node) || []}
        transformOption={(group) => ({
          id: group.id,
          label: group.name,
          name: group.name,
          description: group.description,
        })}
        selectedItems={selectedGroups}
        onChange={handleChange}
        getOptionId={(option) => option.id}
        getOptionLabel={(option) => option.label}
        isOptionEqualToValue={(option, value) => option.id === value.id}
        multiple
        placeholder={t("roleManagement.groups.searchPlaceholder")}
        disabled={assignLoading || removeLoading || (!!roleId && !onChange && (!onAssignGroup || !onRemoveGroup))}
        loading={assignLoading || removeLoading}
        skip={!active}
        errorMessage={t("roleManagement.groups.loadError")}
        variant="outlined"
        renderTags={(value, getTagProps) =>
          value.map((option, index) => {
            const { key, ...tagProps } = getTagProps({ index });
            return (
              <Chip
                key={key || option.id}
                variant="outlined"
                color="primary"
                label={option.label}
                {...tagProps}
              />
            );
          })
        }
      />
    </Box>
  );
};
