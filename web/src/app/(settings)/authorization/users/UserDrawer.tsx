"use client";

import React from "react";
import {
  Box,
  Typography,
  Chip,
  Stack,
  TextField,
  Button,
} from "@mui/material";
import { WarningAlert, LoadingState, ErrorAlert } from "@/shared/components/ui/feedback";
import { Drawer } from "@/shared/components/ui/layout/Drawer";
import { Avatar } from "@/shared/components/ui/primitives/Avatar";
import { useTranslation } from "@/shared/i18n";
import { authorizationManagementTranslations } from "@/app/(settings)/settings/i18n";
import { UserGroupAssignment } from "./UserGroupAssignment";
import { UserRoleAssignment } from "./UserRoleAssignment";
import { useUserDrawer } from "@/shared/hooks/authorization/useUserDrawer";
import { PermissionGate } from "@/shared/components/authorization";

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
  
  // Use the custom hook for all drawer logic
  const {
    user,
    loading,
    error,
    displayName,
    email,
    selectedGroups,
    selectedRoles,
    hasChanges,
    saving,
    updateDisplayName,
    updateEmail,
    updateSelectedGroups,
    updateSelectedRoles,
    handleSave,
    resetChanges,
    refetch,
  } = useUserDrawer(userId, open);

  // Footer with action buttons
  const footer = (
    <PermissionGate require="security:user:save">
      <Box sx={{ display: "flex", justifyContent: "flex-end", gap: 2, p: 2 }}>
        <Button
          variant="outlined"
          onClick={() => {
            resetChanges();
            onClose();
          }}
          disabled={saving}
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
    </PermissionGate>
  );

  return (
    <Drawer
      open={open}
      onClose={onClose}
      anchor="right"
      size="md"
      variant="temporary"
    >
      <Drawer.Header title={t("userManagement.drawer.title")} />
      <Drawer.Body>
        <LoadingState isLoading={loading} />

        <ErrorAlert
          error={error || undefined}
          onRetry={() => refetch()}
          fallbackMessage={t("userManagement.drawer.errorLoading")}
        />

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
              <Avatar
                name={user.displayName || user.email}
                size="large"
                sx={{
                  width: 80,
                  height: 80,
                  fontSize: "2rem",
                }}
              />
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
                  onChange={(e) => updateDisplayName(e.target.value)}
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
                  onChange={(e) => updateEmail(e.target.value)}
                  placeholder={t("userManagement.drawer.emailPlaceholder")}
                />
              </Box>

              {/* Groups */}
              <PermissionGate require="security:user:save">
                <Box>
                  <Typography variant="body2" color="text.secondary" gutterBottom>
                    {t("userManagement.drawer.groups")}
                  </Typography>
                  <UserGroupAssignment
                    userId={userId}
                    assignedGroups={selectedGroups}
                    onChange={updateSelectedGroups}
                  />
                </Box>
              </PermissionGate>

              {/* Roles */}
              <PermissionGate require="security:user:save">
                <Box>
                  <Typography variant="body2" color="text.secondary" gutterBottom>
                    {t("userManagement.drawer.roles")}
                  </Typography>
                  <UserRoleAssignment
                    userId={userId}
                    assignedRoles={selectedRoles}
                    onChange={updateSelectedRoles}
                  />
                </Box>
              </PermissionGate>
            </Stack>
          </Stack>
        )}

        {!loading && !error && !user && userId && (
          <WarningAlert message={t("userManagement.drawer.userNotFound")} />
        )}
      </Drawer.Body>
      <Drawer.Footer>{footer}</Drawer.Footer>
    </Drawer>
  );
};
