"use client";

import React, { useMemo, useEffect, useState, useCallback } from "react";
import {
  Box,
  Typography,
  CircularProgress,
  Alert,
  Chip,
  Stack,
  TextField,
  Button,
  Badge,
} from "@mui/material";
import { Drawer } from "@/shared/components/ui/layout/Drawer";
import { Avatar } from "@/shared/components/ui/primitives/Avatar";
import { useGetUserWithRelationshipsQuery } from "@/lib/graphql/operations/authorization-management/queries.generated";
import { useTranslation } from "@/shared/i18n";
import { authorizationManagementTranslations } from "@/app/(neotool)/settings/i18n";
import { useUserManagement } from "@/shared/hooks/authorization/useUserManagement";
import { UserGroupAssignment } from "./UserGroupAssignment";
import { UserRoleAssignment } from "./UserRoleAssignment";
import { useToast } from "@/shared/providers";
import { extractErrorMessage } from "@/shared/utils/error";

export interface UserDrawerProps {
  open: boolean;
  onClose: () => void;
  userId: string | null;
}

/**
 * UserDrawer component for viewing and editing user details including groups and roles
 */
export const UserDrawer: React.FC<UserDrawerProps> = ({
  open,
  onClose,
  userId,
}) => {
  const { t } = useTranslation(authorizationManagementTranslations);
  const toast = useToast();
  const {
    assignGroupToUser,
    removeGroupFromUser,
    assignRoleToUser,
    removeRoleFromUser,
    assignGroupLoading,
    removeGroupLoading,
    assignRoleLoading,
    removeRoleLoading,
  } = useUserManagement();

  // Form state
  const [displayName, setDisplayName] = useState("");
  const [email, setEmail] = useState("");
  const [hasChanges, setHasChanges] = useState(false);
  const [saving, setSaving] = useState(false);

  // Query user with relationships
  const { data, loading, error, refetch } = useGetUserWithRelationshipsQuery({
    skip: !open || !userId,
    variables: { id: userId! },
    fetchPolicy: 'network-only',
    notifyOnNetworkStatusChange: true,
  });

  // Get the user directly from the query result
  const user = useMemo(() => {
    if (!data?.user || !userId) return null;
    return data.user;
  }, [data?.user, userId]);

  // Extract relationships
  const groups = useMemo(() => user?.groups || [], [user?.groups]);
  const roles = useMemo(() => user?.roles || [], [user?.roles]);

  // Initialize form when user data loads
  useEffect(() => {
    if (user) {
      setDisplayName(user.displayName || "");
      setEmail(user.email || "");
      setHasChanges(false);
    }
  }, [user]);

  // Track form changes
  useEffect(() => {
    if (user) {
      const changed =
        displayName !== (user.displayName || "") ||
        email !== user.email;
      setHasChanges(changed);
    }
  }, [displayName, email, user]);

  // Refetch when drawer opens to ensure fresh data
  useEffect(() => {
    if (open && userId) {
      refetch();
    }
  }, [open, userId, refetch]);

  const handleAssignGroup = useCallback(
    async (groupId: string) => {
      if (!userId) return;
      try {
        await assignGroupToUser(userId, groupId);
        await refetch();
      } catch (err) {
        const errorMessage = extractErrorMessage(
          err,
          t("userManagement.groups.assignError")
        );
        toast.error(errorMessage);
        throw err;
      }
    },
    [userId, assignGroupToUser, refetch, toast, t]
  );

  const handleRemoveGroup = useCallback(
    async (groupId: string) => {
      if (!userId) return;
      try {
        await removeGroupFromUser(userId, groupId);
        await refetch();
      } catch (err) {
        const errorMessage = extractErrorMessage(
          err,
          t("userManagement.groups.removeError")
        );
        toast.error(errorMessage);
        throw err;
      }
    },
    [userId, removeGroupFromUser, refetch, toast, t]
  );

  const handleAssignRole = useCallback(
    async (roleId: string) => {
      if (!userId) return;
      try {
        await assignRoleToUser(userId, roleId);
        await refetch();
      } catch (err) {
        const errorMessage = extractErrorMessage(
          err,
          t("userManagement.roles.assignError")
        );
        toast.error(errorMessage);
        throw err;
      }
    },
    [userId, assignRoleToUser, refetch, toast, t]
  );

  const handleRemoveRole = useCallback(
    async (roleId: string) => {
      if (!userId) return;
      try {
        await removeRoleFromUser(userId, roleId);
        await refetch();
      } catch (err) {
        const errorMessage = extractErrorMessage(
          err,
          t("userManagement.roles.removeError")
        );
        toast.error(errorMessage);
        throw err;
      }
    },
    [userId, removeRoleFromUser, refetch, toast, t]
  );

  const handleSave = useCallback(async () => {
    if (!user || !userId) return;
    setSaving(true);
    try {
      // TODO: Add update user mutation when available
      // For now, just show a message
      toast.success(t("userManagement.drawer.saveSuccess"));
      setHasChanges(false);
      await refetch();
    } catch (err) {
      const errorMessage = extractErrorMessage(
        err,
        t("userManagement.drawer.saveError")
      );
      toast.error(errorMessage);
    } finally {
      setSaving(false);
    }
  }, [user, userId, refetch, toast, t]);

  const handleCancel = useCallback(() => {
    if (user) {
      setDisplayName(user.displayName || "");
      setEmail(user.email || "");
      setHasChanges(false);
    }
  }, [user]);

  const handleGroupsChange = useCallback(() => {
    refetch();
  }, [refetch]);

  const handleRolesChange = useCallback(() => {
    refetch();
  }, [refetch]);

  // Footer with action buttons
  const footer = (
    <Box sx={{ display: "flex", justifyContent: "flex-end", gap: 2, p: 2 }}>
      <Button
        variant="outlined"
        onClick={handleCancel}
        disabled={saving || !hasChanges}
      >
        {t("common.cancel")}
      </Button>
      <Button
        variant="contained"
        onClick={handleSave}
        disabled={saving || !hasChanges}
        color="primary"
      >
        {saving
          ? t("common.saving")
          : t("userManagement.drawer.saveChanges")}
      </Button>
    </Box>
  );

  return (
    <Drawer
      open={open}
      onClose={onClose}
      title={t("userManagement.drawer.title")}
      anchor="right"
      width={600}
      variant="temporary"
      footer={footer}
    >
      <Box sx={{ p: 3 }}>
        {loading && (
          <Box sx={{ display: "flex", justifyContent: "center", p: 4 }}>
            <CircularProgress />
          </Box>
        )}

        {error && (
          <Alert severity="error" sx={{ mb: 3 }}>
            {t("userManagement.drawer.errorLoading")}
          </Alert>
        )}

        {!loading && !error && user && (
          <Stack spacing={4}>
            {/* Profile Section - Centered */}
            <Box
              sx={{
                display: "flex",
                flexDirection: "column",
                alignItems: "center",
                py: 3,
              }}
            >
              <Badge
                overlap="circular"
                anchorOrigin={{ vertical: "bottom", horizontal: "right" }}
                badgeContent={
                  <Box
                    sx={{
                      width: 12,
                      height: 12,
                      borderRadius: "50%",
                      bgcolor: user.enabled ? "success.main" : "grey.500",
                      border: "2px solid",
                      borderColor: "background.paper",
                    }}
                  />
                }
              >
                <Avatar
                  name={user.displayName || user.email}
                  size="large"
                  sx={{
                    width: 80,
                    height: 80,
                    fontSize: "2rem",
                  }}
                />
              </Badge>
              <Typography
                variant="h5"
                sx={{ mt: 2, fontWeight: "bold", textAlign: "center" }}
              >
                {user.displayName || user.email}
              </Typography>
              <Typography
                variant="body2"
                color="text.secondary"
                sx={{ mt: 0.5, textAlign: "center" }}
              >
                {user.email}
              </Typography>
              <Chip
                label={
                  user.enabled
                    ? t("userManagement.status.enabled")
                    : t("userManagement.status.disabled")
                }
                color={user.enabled ? "success" : "default"}
                size="small"
                sx={{ mt: 1 }}
              />
            </Box>

            {/* Form Section */}
            <Stack spacing={3}>
              {/* Display Name */}
              <Box>
                <Typography variant="body2" color="text.secondary" gutterBottom>
                  {t("userManagement.drawer.displayName")}
                </Typography>
                <TextField
                  fullWidth
                  value={displayName}
                  onChange={(e) => setDisplayName(e.target.value)}
                  placeholder={t("userManagement.drawer.displayNamePlaceholder")}
                />
              </Box>

              {/* Email */}
              <Box>
                <Typography variant="body2" color="text.secondary" gutterBottom>
                  {t("userManagement.drawer.email")}
                </Typography>
                <TextField
                  fullWidth
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder={t("userManagement.drawer.emailPlaceholder")}
                />
              </Box>

              {/* Groups */}
              <Box>
                <Typography variant="body2" color="text.secondary" gutterBottom>
                  {t("userManagement.drawer.groups")}
                </Typography>
                <UserGroupAssignment
                  userId={userId}
                  assignedGroups={groups.map((g) => ({
                    id: g.id,
                    name: g.name,
                    description: g.description,
                  }))}
                  onAssignGroup={handleAssignGroup}
                  onRemoveGroup={handleRemoveGroup}
                  assignLoading={assignGroupLoading}
                  removeLoading={removeGroupLoading}
                  onGroupsChange={handleGroupsChange}
                />
              </Box>

              {/* Roles */}
              <Box>
                <Typography variant="body2" color="text.secondary" gutterBottom>
                  {t("userManagement.drawer.roles")}
                </Typography>
                <UserRoleAssignment
                  userId={userId}
                  assignedRoles={roles.map((r) => ({
                    id: r.id,
                    name: r.name,
                  }))}
                  onAssignRole={handleAssignRole}
                  onRemoveRole={handleRemoveRole}
                  assignLoading={assignRoleLoading}
                  removeLoading={removeRoleLoading}
                  onRolesChange={handleRolesChange}
                />
              </Box>
            </Stack>
          </Stack>
        )}

        {!loading && !error && !user && userId && (
          <Alert severity="warning">
            {t("userManagement.drawer.userNotFound")}
          </Alert>
        )}
      </Box>
    </Drawer>
  );
};
