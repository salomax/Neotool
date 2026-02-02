"use client";

import React, { useMemo, useCallback } from "react";
import { Box, Typography, Chip } from "@mui/material";
import { useQuery } from "@apollo/client/react";
import {
  GetRolesDocument,
  type GetRolesQuery,
  type GetRolesQueryVariables,
} from "@/lib/graphql/operations/authorization-management/queries.generated";
import { useTranslation } from "@/shared/i18n";
import { authorizationManagementTranslations } from "@/app/(settings)/settings/i18n";
import { useToast } from "@/shared/providers";
import { extractErrorMessage } from "@/shared/utils/error";
import { SearchableAutocomplete } from "@/shared/components/ui/forms/SearchableAutocomplete";

// Custom hook wrapper to satisfy ESLint rules-of-hooks
function useRolesQuery(options?: {
  variables?: GetRolesQueryVariables;
  skip?: boolean;
  fetchPolicy?: "cache-first" | "network-only" | "cache-only" | "no-cache" | "standby";
  notifyOnNetworkStatusChange?: boolean;
}) {
  return useQuery<GetRolesQuery, GetRolesQueryVariables>(GetRolesDocument, options);
}

export interface Role {
  id: string;
  name: string;
}

export interface GroupRoleAssignmentProps {
  groupId: string | null;
  assignedRoles: Role[];
  onAssignRole?: (roleId: string) => Promise<void>;
  onRemoveRole?: (roleId: string) => Promise<void>;
  assignLoading?: boolean;
  removeLoading?: boolean;
  onRolesChange?: () => void;
  // For create mode: callback when roles are selected (before group is created)
  onPendingRolesChange?: (roles: Role[]) => void;
  // For edit mode with deferred mutations: callback when roles change (updates local state only)
  onChange?: (roles: Role[]) => void;
}

type RoleOption = {
  id: string;
  label: string;
  name: string;
};

/**
 * GroupRoleAssignment component for managing group role assignments
 * Uses a multi-select Autocomplete to assign and remove roles
 */
export const GroupRoleAssignment: React.FC<GroupRoleAssignmentProps> = ({
  groupId,
  assignedRoles,
  onAssignRole,
  onRemoveRole,
  assignLoading = false,
  removeLoading = false,
  onRolesChange,
  onPendingRolesChange,
  onChange,
}) => {
  const { t } = useTranslation(authorizationManagementTranslations);
  const toast = useToast();

  // Map assigned roles to option format, deduplicating by ID
  const selectedRoles = useMemo(() => {
    const roleMap = new Map<string, RoleOption>();
    for (const role of assignedRoles) {
      if (!roleMap.has(role.id)) {
        roleMap.set(role.id, {
          id: role.id,
          label: role.name,
          name: role.name,
        });
      }
    }
    return Array.from(roleMap.values());
  }, [assignedRoles]);

  // Handle role selection changes
  const handleRoleChange = useCallback(
    async (newValue: RoleOption[]) => {
      const newRoles: Role[] = newValue.map((option) => ({
        id: option.id,
        name: option.name,
      }));

      // If in create mode (no groupId), just update pending roles
      if (!groupId) {
        onPendingRolesChange?.(newRoles);
        return;
      }

      // Edit mode with deferred mutations: use onChange callback to update local state
      if (onChange) {
        onChange(newRoles);
        return;
      }

      // Legacy edit mode: assign/remove roles via immediate mutations
      if (!onAssignRole || !onRemoveRole) return;

      const currentRoleIds = new Set(assignedRoles.map((r) => r.id));
      const newRoleIds = new Set(newValue.map((r) => r.id));

      // Find roles to add
      const rolesToAdd = newValue.filter(
        (role) => !currentRoleIds.has(role.id)
      );

      // Find roles to remove
      const rolesToRemove = assignedRoles.filter(
        (role) => !newRoleIds.has(role.id)
      );

      try {
        // Add new roles
        for (const role of rolesToAdd) {
          await onAssignRole(role.id);
        }

        // Remove roles
        for (const role of rolesToRemove) {
          await onRemoveRole(role.id);
        }

        onRolesChange?.();
      } catch (err) {
        const errorMessage = extractErrorMessage(
          err,
          t("groupManagement.roles.assignError")
        );
        toast.error(errorMessage);
      }
    },
    [
      groupId,
      assignedRoles,
      onAssignRole,
      onRemoveRole,
      onRolesChange,
      onPendingRolesChange,
      onChange,
      toast,
      t,
    ]
  );

  return (
    <Box>
      <SearchableAutocomplete<
        RoleOption,
        RoleOption,
        GetRolesQuery,
        GetRolesQueryVariables
      >
        useQuery={useRolesQuery}
        getQueryVariables={(searchQuery) => ({
          first: 5,
          query: searchQuery || undefined,
        })}
        extractData={(data) => data?.roles?.edges?.map((e) => e.node) || []}
        transformOption={(role) => ({
          id: role.id,
          label: role.name,
          name: role.name,
        })}
        selectedItems={selectedRoles}
        onChange={handleRoleChange}
        getOptionId={(option) => option.id}
        getOptionLabel={(option) => option.label}
        isOptionEqualToValue={(option, value) => option.id === value.id}
        multiple
        placeholder={t("groupManagement.roles.selectRoles")}
        disabled={assignLoading || removeLoading || (!!groupId && !onChange && (!onAssignRole || !onRemoveRole))}
        loading={assignLoading || removeLoading}
        errorMessage={t("groupManagement.roles.loadError")}
        loadMode="eager"
        renderTags={(value, getTagProps) => {
          // Deduplicate by ID to ensure unique keys
          const seenIds = new Set<string>();
          return value
            .filter((option) => {
              const id = option.id;
              if (seenIds.has(id)) {
                return false;
              }
              seenIds.add(id);
              return true;
            })
            .map((option, index) => {
              const { key: _key, ...tagProps } = getTagProps({ index });
              return (
                <Chip
                  {...tagProps}
                  key={option.id}
                  variant="outlined"
                  color="primary"
                  label={option.label}
                  disabled={assignLoading || removeLoading}
                />
              );
            });
        }}
      />
      {selectedRoles.length === 0 && (
        <Typography
          variant="body2"
          color="text.secondary"
          sx={{ mt: 1, fontStyle: "italic" }}
        >
          {t("groupManagement.roles.noRolesAssigned")}
        </Typography>
      )}
    </Box>
  );
};
