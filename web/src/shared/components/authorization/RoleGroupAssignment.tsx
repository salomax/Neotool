"use client";

import React, { useState, useMemo, useCallback } from "react";
import {
  Box,
  Typography,
  Divider,
  CircularProgress,
  Alert,
  Button,
  Stack,
  Chip,
} from "@mui/material";
import { useGetGroupsQuery } from "@/lib/graphql/operations/authorization-management/queries.generated";
import { useTranslation } from "@/shared/i18n";
import { authorizationManagementTranslations } from "@/app/(neotool)/settings/i18n";
import { useToast } from "@/shared/providers";
import { extractErrorMessage } from "@/shared/utils/error";
import { SearchField } from "@/shared/components/ui/forms/SearchField";

export interface Group {
  id: string;
  name: string;
  description: string | null;
}

export interface RoleGroupAssignmentProps {
  roleId: string | null;
  assignedGroups: Group[];
  onAssignGroup: (groupId: string) => Promise<void>;
  onRemoveGroup: (groupId: string) => Promise<void>;
  assignLoading?: boolean;
  removeLoading?: boolean;
  onGroupsChange?: () => void;
}

/**
 * RoleGroupAssignment component for managing role group assignments
 * Shows assigned groups with remove action and available groups with assign action
 */
export const RoleGroupAssignment: React.FC<RoleGroupAssignmentProps> = ({
  roleId,
  assignedGroups,
  onAssignGroup,
  onRemoveGroup,
  assignLoading = false,
  removeLoading = false,
  onGroupsChange,
}) => {
  const { t } = useTranslation(authorizationManagementTranslations);
  const toast = useToast();
  const [searchQuery, setSearchQuery] = useState("");

  // Fetch all groups for selection
  const { data, loading: groupsLoading, error: groupsError } = useGetGroupsQuery({
    variables: {
      first: 1000, // Fetch a large number of groups for selection
      query: undefined,
    },
    skip: false,
  });

  // Transform groups data
  const allGroups = useMemo(() => {
    return (data?.groups?.nodes || []).map((group) => ({
      id: group.id,
      name: group.name,
      description: group.description,
    }));
  }, [data?.groups?.nodes]);

  // Filter available groups (not already assigned and matching search)
  const availableGroups = useMemo(() => {
    if (!allGroups) return [];
    const assignedIds = new Set(assignedGroups.map((g) => g.id));
    const searchLower = searchQuery.toLowerCase();
    return allGroups.filter(
      (group) =>
        !assignedIds.has(group.id) &&
        group.name.toLowerCase().includes(searchLower)
    );
  }, [allGroups, assignedGroups, searchQuery]);

  const handleAssignGroup = useCallback(
    async (groupId: string) => {
      if (!roleId) return;
      try {
        await onAssignGroup(groupId);
        const group = allGroups.find((g) => g.id === groupId);
        toast.success(
          t("roleManagement.groups.groupAssigned", {
            group: group?.name || "",
          })
        );
        onGroupsChange?.();
      } catch (err) {
        console.error("Error assigning group:", err);
        const errorMessage = extractErrorMessage(
          err,
          t("roleManagement.groups.assignError")
        );
        toast.error(errorMessage);
      }
    },
    [roleId, onAssignGroup, toast, t, allGroups, onGroupsChange]
  );

  const handleRemoveGroup = useCallback(
    async (groupId: string) => {
      if (!roleId) return;
      try {
        await onRemoveGroup(groupId);
        const group = assignedGroups.find((g) => g.id === groupId);
        toast.success(
          t("roleManagement.groups.groupRemoved", {
            group: group?.name || "",
          })
        );
        onGroupsChange?.();
      } catch (err) {
        console.error("Error removing group:", err);
        const errorMessage = extractErrorMessage(
          err,
          t("roleManagement.groups.removeError")
        );
        toast.error(errorMessage);
      }
    },
    [roleId, onRemoveGroup, toast, t, assignedGroups, onGroupsChange]
  );

  if (groupsLoading) {
    return (
      <Box sx={{ display: "flex", justifyContent: "center", p: 4 }}>
        <CircularProgress />
      </Box>
    );
  }

  if (groupsError) {
    return (
      <Alert severity="error" sx={{ mb: 2 }}>
        {t("roleManagement.groups.loadError")}
      </Alert>
    );
  }

  return (
    <Stack spacing={3}>
      {/* Assigned Groups */}
      <Box>
        <Typography variant="h6" gutterBottom>
          {t("roleManagement.groups.assigned")}
        </Typography>
        <Divider sx={{ mb: 2 }} />
        {assignedGroups.length === 0 ? (
          <Typography variant="body2" color="text.secondary">
            {t("roleManagement.groups.noGroups")}
          </Typography>
        ) : (
          <Box>
            <Typography variant="caption" color="text.secondary" sx={{ mb: 1, display: "block" }}>
              {t("roleManagement.groups.assignedCount", { count: assignedGroups.length })}
            </Typography>
            <Box sx={{ display: "flex", flexWrap: "wrap", gap: 1 }}>
              {assignedGroups.map((group) => (
                <Chip
                  key={group.id}
                  label={group.name}
                  size="small"
                  color="primary"
                  variant="outlined"
                  onDelete={
                    !removeLoading
                      ? () => handleRemoveGroup(group.id)
                      : undefined
                  }
                  disabled={removeLoading}
                  aria-label={t("roleManagement.groups.removeGroup", {
                    name: group.name,
                  })}
                />
              ))}
            </Box>
          </Box>
        )}
      </Box>

      {/* Available Groups */}
      <Box>
        <Typography variant="h6" gutterBottom>
          {t("roleManagement.groups.available")}
        </Typography>
        <Divider sx={{ mb: 2 }} />
        <SearchField
          value={searchQuery}
          onChange={setSearchQuery}
          placeholder={t("roleManagement.groups.searchPlaceholder")}
          fullWidth
          debounceMs={300}
          name="group-search"
          data-testid="group-search"
        />
        {availableGroups.length === 0 ? (
          <Typography variant="body2" color="text.secondary">
            {searchQuery
              ? t("roleManagement.groups.noAvailableMatching")
              : t("roleManagement.groups.allAssigned")}
          </Typography>
        ) : (
          <Box sx={{ display: "flex", flexWrap: "wrap", gap: 1, mt: 2 }}>
            {availableGroups.map((group) => (
              <Button
                key={group.id}
                variant="outlined"
                size="small"
                onClick={() => handleAssignGroup(group.id)}
                disabled={assignLoading || removeLoading}
                sx={{ textTransform: "none" }}
              >
                {group.name}
              </Button>
            ))}
          </Box>
        )}
      </Box>
    </Stack>
  );
};

