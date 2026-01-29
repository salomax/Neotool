"use client";

import React, { useMemo, useCallback } from "react";
import { Box, Typography, Chip } from "@mui/material";
import { useQuery } from "@apollo/client/react";
import {
  GetUsersDocument,
  type GetUsersQuery,
  type GetUsersQueryVariables,
} from "@/lib/graphql/operations/authorization-management/queries.generated";
import { useTranslation } from "@/shared/i18n";
import { authorizationManagementTranslations } from "@/app/(settings)/settings/i18n";
import { useToast } from "@/shared/providers";
import { extractErrorMessage } from "@/shared/utils/error";
import { useAuth } from "@/shared/providers/AuthProvider";
import { SearchableAutocomplete } from "@/shared/components/ui/forms/SearchableAutocomplete";

// Custom hook wrapper to satisfy ESLint rules-of-hooks
function useUsersQuery(options?: {
  variables?: GetUsersQueryVariables;
  skip?: boolean;
  fetchPolicy?: "cache-first" | "network-only" | "cache-only" | "no-cache" | "standby";
  notifyOnNetworkStatusChange?: boolean;
}) {
  return useQuery<GetUsersQuery, GetUsersQueryVariables>(GetUsersDocument, options);
}

export interface User {
  id: string;
  email: string;
  displayName: string | null;
  enabled: boolean;
}

export interface RoleUserAssignmentProps {
  roleId: string | null;
  assignedUsers: User[];
  onAssignUser?: (userId: string) => Promise<void>;
  onRemoveUser?: (userId: string) => Promise<void>;
  assignLoading?: boolean;
  removeLoading?: boolean;
  onUsersChange?: () => void;
  /**
   * When false, skips loading user options and renders nothing.
   */
  active?: boolean;
  /**
   * For edit mode with deferred mutations: callback when users change (updates local state only)
   */
  onChange?: (users: User[]) => void;
  /**
   * When true, displays users as readonly chips only (no editing capability)
   */
  readonly?: boolean;
}

type UserOption = {
  id: string;
  label: string;
  email: string;
  displayName: string | null;
  enabled: boolean;
};

/**
 * RoleUserAssignment component for managing role user assignments
 * Uses a multi-select Autocomplete to assign and remove users
 * When readonly, displays users as chips only (no editing capability)
 */
export const RoleUserAssignment: React.FC<RoleUserAssignmentProps> = ({
  roleId,
  assignedUsers,
  onAssignUser,
  onRemoveUser,
  assignLoading = false,
  removeLoading = false,
  onUsersChange,
  active = true,
  onChange,
  readonly = false,
}) => {
  const { t } = useTranslation(authorizationManagementTranslations);
  const toast = useToast();
  const { isAuthenticated } = useAuth();

  // Map assigned users to option format
  const selectedUsers = useMemo(() => {
    return assignedUsers.map((user) => ({
      id: user.id,
      label: user.displayName || user.email,
      email: user.email,
      displayName: user.displayName,
      enabled: user.enabled,
    }));
  }, [assignedUsers]);

  const handleChange = useCallback(
    async (newValue: UserOption[]) => {
      // Prevent duplicates - filter out any duplicates in newValue
      // Autocomplete should handle this, but we ensure uniqueness
      const uniqueNewValue = Array.from(
        new Map(newValue.map((user) => [user.id, user])).values()
      );

      const newUsers: User[] = uniqueNewValue.map((user) => ({
        id: user.id,
        email: user.email,
        displayName: user.displayName,
        enabled: user.enabled,
      }));

      // If onChange is provided (edit mode with deferred mutations), use it
      if (onChange) {
        onChange(newUsers);
        return;
      }

      // Legacy mode: immediate mutations
      if (!onAssignUser || !onRemoveUser) return;

      const currentIds = new Set(assignedUsers.map((u) => u.id));
      const newIds = new Set(uniqueNewValue.map((u) => u.id));

      // Find added users (in uniqueNewValue but not in assignedUsers)
      const addedUsers = uniqueNewValue.filter((user) => !currentIds.has(user.id));
      
      // Find removed users (in assignedUsers but not in uniqueNewValue)
      const removedUsers = assignedUsers.filter((user) => !newIds.has(user.id));

      try {
        // Assign new users
        for (const user of addedUsers) {
          await onAssignUser(user.id);
        }

        // Remove users
        for (const user of removedUsers) {
          await onRemoveUser(user.id);
        }

        onUsersChange?.();
      } catch (err) {
        const errorMessage = extractErrorMessage(
          err,
          t("roleManagement.users.assignError")
        );
        toast.error(errorMessage);
      }
    },
    [assignedUsers, onAssignUser, onRemoveUser, onChange, toast, t, onUsersChange]
  );

  if (!active) {
    return null;
  }

  // Readonly mode: just display users as chips
  if (readonly) {
    return (
      <Box>
        <Typography variant="subtitle1" gutterBottom>
          {t("roleManagement.users.assigned")}
        </Typography>
        {selectedUsers.length === 0 ? (
          <Typography variant="body2" color="text.secondary">
            {t("roleManagement.users.noUsers")}
          </Typography>
        ) : (
          <Box sx={{ display: "flex", flexWrap: "wrap", gap: 1 }}>
            {selectedUsers.map((user) => (
              <Chip
                key={user.id}
                variant="outlined"
                label={user.label}
              />
            ))}
          </Box>
        )}
      </Box>
    );
  }

  return (
    <Box>
      <Typography variant="subtitle1" gutterBottom>
        {t("roleManagement.users.assigned")}
      </Typography>
      <SearchableAutocomplete<
        UserOption,
        UserOption,
        GetUsersQuery,
        GetUsersQueryVariables
      >
        useQuery={useUsersQuery}
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
          enabled: user.enabled,
        })}
        selectedItems={selectedUsers}
        onChange={handleChange}
        getOptionId={(option) => option.id}
        getOptionLabel={(option) => option.label}
        isOptionEqualToValue={(option, value) => option.id === value.id}
        multiple
        label={t("roleManagement.users.assigned")}
        placeholder={t("roleManagement.users.searchPlaceholder")}
        disabled={assignLoading || removeLoading || (!!roleId && !onChange && (!onAssignUser || !onRemoveUser))}
        loading={assignLoading || removeLoading}
        skip={!active || !isAuthenticated}
        errorMessage={t("roleManagement.users.loadError")}
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
