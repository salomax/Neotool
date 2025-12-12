"use client";

import React, { useMemo, useCallback } from "react";
import { Box, Typography, Chip } from "@mui/material";
import { useGetRolesQuery, type GetRolesQuery, type GetRolesQueryVariables } from "@/lib/graphql/operations/authorization-management/queries.generated";
import { useTranslation } from "@/shared/i18n";
import { authorizationManagementTranslations } from "@/app/(settings)/settings/i18n";
import { useToast } from "@/shared/providers";
import { extractErrorMessage } from "@/shared/utils/error";
import { SearchableAutocomplete } from "@/shared/components/ui/forms/SearchableAutocomplete";

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
}) => {
  const { t } = useTranslation(authorizationManagementTranslations);
  const toast = useToast();

  // Map assigned roles to option format
  const selectedRoles = useMemo(() => {
    return assignedRoles.map((role) => ({
      id: role.id,
      label: role.name,
      name: role.name,
    }));
  }, [assignedRoles]);

  // Handle role selection changes
  const handleRoleChange = useCallback(
    async (newValue: RoleOption[]) => {
      // If in create mode (no groupId), just update pending roles
      if (!groupId) {
        const pendingRoles: Role[] = newValue.map((option) => ({
          id: option.id,
          name: option.name,
        }));
        onPendingRolesChange?.(pendingRoles);
        return;
      }

      // Edit mode: assign/remove roles via mutations
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
        useQuery={useGetRolesQuery}
        getQueryVariables={(searchQuery) => ({
          first: 100,
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
        disabled={assignLoading || removeLoading || (!!groupId && (!onAssignRole || !onRemoveRole))}
        loading={assignLoading || removeLoading}
        errorMessage={t("groupManagement.roles.loadError")}
        renderTags={(value, getTagProps) =>
          value.map((option, index) => {
            const { key, ...tagProps } = getTagProps({ index });
            return (
              <Chip
                {...tagProps}
                key={key || option.id}
                label={option.label}
                disabled={assignLoading || removeLoading}
              />
            );
          })
        }
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

