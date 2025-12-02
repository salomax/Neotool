"use client";

import React, { useMemo, useEffect } from "react";
import {
  Box,
  Typography,
  List,
  ListItem,
  ListItemText,
  Divider,
  CircularProgress,
  Alert,
  Chip,
  Stack,
} from "@mui/material";
import { Drawer } from "@/shared/components/ui/layout/Drawer";
import { useGetUserWithRelationshipsQuery } from "@/lib/graphql/operations/authorization-management/queries.generated";
import { useTranslation } from "@/shared/i18n";
import { authorizationManagementTranslations } from "@/app/(neotool)/settings/i18n";

export interface UserDrawerProps {
  open: boolean;
  onClose: () => void;
  userId: string | null;
}

/**
 * UserDrawer component for viewing user details including groups, roles, and permissions
 */
export const UserDrawer: React.FC<UserDrawerProps> = ({
  open,
  onClose,
  userId,
}) => {
  const { t } = useTranslation(authorizationManagementTranslations);

  // Query user with relationships
  // Use fetchPolicy: 'network-only' to always get fresh data when drawer opens
  // This ensures group assignments are reflected immediately
  const { data, loading, error, refetch } = useGetUserWithRelationshipsQuery({
    skip: !open || !userId,
    fetchPolicy: 'network-only',
    notifyOnNetworkStatusChange: true,
  });

  // Refetch when drawer opens to ensure fresh data
  useEffect(() => {
    if (open && userId) {
      refetch();
    }
  }, [open, userId, refetch]);

  // Find the specific user from the query results
  const user = useMemo(() => {
    if (!data?.users?.nodes || !userId) return null;
    return data.users.nodes.find((u) => u.id === userId) || null;
  }, [data?.users?.nodes, userId]);

  // Extract relationships
  const groups = useMemo(() => user?.groups || [], [user?.groups]);
  const roles = useMemo(() => user?.roles || [], [user?.roles]);
  const permissions = useMemo(() => user?.permissions || [], [user?.permissions]);

  return (
    <Drawer
      open={open}
      onClose={onClose}
      title={t("userManagement.drawer.viewUser")}
      anchor="right"
      width={600}
      variant="temporary"
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
          <Stack spacing={3}>
            {/* User Information */}
            <Box>
              <Stack spacing={1}>
                <Box>
                  <Typography variant="caption" color="text.secondary">
                    {t("userManagement.drawer.email")}
                  </Typography>
                  <Typography variant="body1">{user.email}</Typography>
                </Box>
                {user.displayName && (
                  <Box>
                    <Typography variant="caption" color="text.secondary">
                      {t("userManagement.drawer.displayName")}
                    </Typography>
                    <Typography variant="body1">{user.displayName}</Typography>
                  </Box>
                )}
                <Box>
                  <Typography variant="caption" color="text.secondary">
                    {t("userManagement.drawer.status")}
                  </Typography>
                  <Typography variant="body1" component="div" sx={{ mt: 0.5 }}>
                    <Chip
                      label={
                        user.enabled
                          ? t("userManagement.status.enabled")
                          : t("userManagement.status.disabled")
                      }
                      color={user.enabled ? "success" : "default"}
                      size="small"
                    />
                  </Typography>
                </Box>
              </Stack>
            </Box>

            {/* Groups */}
            <Box>
              <Typography variant="h6" gutterBottom>
                {t("userManagement.drawer.groups")} ({groups.length})
              </Typography>
              <Divider sx={{ mb: 2 }} />
              {groups.length === 0 ? (
                <Typography variant="body2" color="text.secondary">
                  {t("userManagement.drawer.noGroups")}
                </Typography>
              ) : (
                <List dense>
                  {groups.map((group) => (
                    <ListItem key={group.id} sx={{ px: 0 }}>
                      <ListItemText
                        primary={group.name}
                        secondary={group.description || undefined}
                      />
                    </ListItem>
                  ))}
                </List>
              )}
            </Box>

            {/* Roles */}
            <Box>
              <Typography variant="h6" gutterBottom>
                {t("userManagement.drawer.roles")} ({roles.length})
              </Typography>
              <Divider sx={{ mb: 2 }} />
              {roles.length === 0 ? (
                <Typography variant="body2" color="text.secondary">
                  {t("userManagement.drawer.noRoles")}
                </Typography>
              ) : (
                <Box sx={{ display: "flex", flexWrap: "wrap", gap: 1 }}>
                  {roles.map((role) => (
                    <Chip key={role.id} label={role.name} size="small" />
                  ))}
                </Box>
              )}
            </Box>

            {/* Permissions */}
            <Box>
              <Typography variant="h6" gutterBottom>
                {t("userManagement.drawer.permissions")} ({permissions.length})
              </Typography>
              <Divider sx={{ mb: 2 }} />
              {permissions.length === 0 ? (
                <Typography variant="body2" color="text.secondary">
                  {t("userManagement.drawer.noPermissions")}
                </Typography>
              ) : (
                <Box sx={{ display: "flex", flexWrap: "wrap", gap: 1 }}>
                  {permissions.map((permission) => (
                    <Chip
                      key={permission.id}
                      label={permission.name}
                      size="small"
                      color="primary"
                      variant="outlined"
                    />
                  ))}
                </Box>
              )}
            </Box>
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

