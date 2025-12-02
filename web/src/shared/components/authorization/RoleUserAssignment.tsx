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
import { useGetUsersQuery } from "@/lib/graphql/operations/authorization-management/queries.generated";
import { useTranslation } from "@/shared/i18n";
import { authorizationManagementTranslations } from "@/app/(neotool)/settings/i18n";
import { useToast } from "@/shared/providers";
import { extractErrorMessage } from "@/shared/utils/error";
import { SearchField } from "@/shared/components/ui/forms/SearchField";

export interface User {
  id: string;
  email: string;
  displayName: string | null;
  enabled: boolean;
}

export interface RoleUserAssignmentProps {
  roleId: string | null;
  assignedUsers: User[];
  onAssignUser: (userId: string) => Promise<void>;
  onRemoveUser: (userId: string) => Promise<void>;
  assignLoading?: boolean;
  removeLoading?: boolean;
  onUsersChange?: () => void;
}

/**
 * RoleUserAssignment component for managing role user assignments
 * Shows assigned users with remove action and available users with assign action
 */
export const RoleUserAssignment: React.FC<RoleUserAssignmentProps> = ({
  roleId,
  assignedUsers,
  onAssignUser,
  onRemoveUser,
  assignLoading = false,
  removeLoading = false,
  onUsersChange,
}) => {
  const { t } = useTranslation(authorizationManagementTranslations);
  const toast = useToast();
  const [searchQuery, setSearchQuery] = useState("");

  // Fetch all users for selection
  const { data, loading: usersLoading, error: usersError } = useGetUsersQuery({
    variables: {
      first: 1000, // Fetch a large number of users for selection
      query: undefined,
    },
    skip: false,
  });

  // Transform users data
  const allUsers = useMemo(() => {
    return (data?.users?.nodes || []).map((user) => ({
      id: user.id,
      email: user.email,
      displayName: user.displayName,
      enabled: user.enabled,
    }));
  }, [data?.users?.nodes]);

  // Filter available users (not already assigned and matching search)
  const availableUsers = useMemo(() => {
    if (!allUsers) return [];
    const assignedIds = new Set(assignedUsers.map((u) => u.id));
    const searchLower = searchQuery.toLowerCase();
    return allUsers.filter(
      (user) =>
        !assignedIds.has(user.id) &&
        (user.email.toLowerCase().includes(searchLower) ||
          (user.displayName?.toLowerCase().includes(searchLower) ?? false))
    );
  }, [allUsers, assignedUsers, searchQuery]);

  const handleAssignUser = useCallback(
    async (userId: string) => {
      if (!roleId) return;
      try {
        await onAssignUser(userId);
        const user = allUsers.find((u) => u.id === userId);
        toast.success(
          t("roleManagement.users.userAssigned", {
            user: user?.displayName || user?.email || "",
          })
        );
        onUsersChange?.();
      } catch (err) {
        console.error("Error assigning user:", err);
        const errorMessage = extractErrorMessage(
          err,
          t("roleManagement.users.assignError")
        );
        toast.error(errorMessage);
      }
    },
    [roleId, onAssignUser, toast, t, allUsers, onUsersChange]
  );

  const handleRemoveUser = useCallback(
    async (userId: string) => {
      if (!roleId) return;
      try {
        await onRemoveUser(userId);
        const user = assignedUsers.find((u) => u.id === userId);
        toast.success(
          t("roleManagement.users.userRemoved", {
            user: user?.displayName || user?.email || "",
          })
        );
        onUsersChange?.();
      } catch (err) {
        console.error("Error removing user:", err);
        const errorMessage = extractErrorMessage(
          err,
          t("roleManagement.users.removeError")
        );
        toast.error(errorMessage);
      }
    },
    [roleId, onRemoveUser, toast, t, assignedUsers, onUsersChange]
  );

  const getUserDisplayName = (user: User) => {
    return user.displayName || user.email;
  };

  if (usersLoading) {
    return (
      <Box sx={{ display: "flex", justifyContent: "center", p: 4 }}>
        <CircularProgress />
      </Box>
    );
  }

  if (usersError) {
    return (
      <Alert severity="error" sx={{ mb: 2 }}>
        {t("roleManagement.users.loadError")}
      </Alert>
    );
  }

  return (
    <Stack spacing={3}>
      {/* Assigned Users */}
      <Box>
        <Typography variant="h6" gutterBottom>
          {t("roleManagement.users.assigned")}
        </Typography>
        <Divider sx={{ mb: 2 }} />
        {assignedUsers.length === 0 ? (
          <Typography variant="body2" color="text.secondary">
            {t("roleManagement.users.noUsers")}
          </Typography>
        ) : (
          <Box>
            <Typography variant="caption" color="text.secondary" sx={{ mb: 1, display: "block" }}>
              {t("roleManagement.users.assignedCount", { count: assignedUsers.length })}
            </Typography>
            <Box sx={{ display: "flex", flexWrap: "wrap", gap: 1 }}>
              {assignedUsers.map((user) => (
                <Chip
                  key={user.id}
                  label={getUserDisplayName(user)}
                  size="small"
                  color="primary"
                  variant="outlined"
                  onDelete={
                    !removeLoading
                      ? () => handleRemoveUser(user.id)
                      : undefined
                  }
                  disabled={removeLoading}
                  aria-label={t("roleManagement.users.removeUser", {
                    name: getUserDisplayName(user),
                  })}
                />
              ))}
            </Box>
          </Box>
        )}
      </Box>

      {/* Available Users */}
      <Box>
        <Typography variant="h6" gutterBottom>
          {t("roleManagement.users.available")}
        </Typography>
        <Divider sx={{ mb: 2 }} />
        <SearchField
          value={searchQuery}
          onChange={setSearchQuery}
          placeholder={t("roleManagement.users.searchPlaceholder")}
          fullWidth
          debounceMs={300}
          name="user-search"
          data-testid="user-search"
        />
        {availableUsers.length === 0 ? (
          <Typography variant="body2" color="text.secondary">
            {searchQuery
              ? t("roleManagement.users.noAvailableMatching")
              : t("roleManagement.users.allAssigned")}
          </Typography>
        ) : (
          <Box sx={{ display: "flex", flexWrap: "wrap", gap: 1, mt: 2 }}>
            {availableUsers.map((user) => (
              <Button
                key={user.id}
                variant="outlined"
                size="small"
                onClick={() => handleAssignUser(user.id)}
                disabled={assignLoading || removeLoading}
                sx={{ textTransform: "none" }}
              >
                {getUserDisplayName(user)}
              </Button>
            ))}
          </Box>
        )}
      </Box>
    </Stack>
  );
};

