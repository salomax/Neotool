"use client";

import React, { useRef } from "react";
import {
  Box,
  Typography,
  Chip,
  Stack,
  Button,
  IconButton,
} from "@mui/material";
import { TextField } from "@/shared/components/ui/primitives";
import PersonIcon from "@mui/icons-material/Person";
import { WarningAlert, LoadingState, ErrorAlert } from "@/shared/components/ui/feedback";
import { Drawer } from "@/shared/components/ui/layout/Drawer";
import { Avatar } from "@/shared/components/ui/primitives/Avatar";
import { useTranslation } from "@/shared/i18n";
import { authorizationManagementTranslations } from "@/app/(settings)/settings/i18n";
import { UserGroupAssignment } from "./UserGroupAssignment";
import { UserRoleAssignment } from "./UserRoleAssignment";
import { useUserDrawer } from "@/shared/hooks/authorization/useUserDrawer";
import { PermissionGate } from "@/shared/components/authorization";
import { CloseIcon } from "@/shared/ui/mui-imports";
import { useKeyboardFormSubmit, useDrawerAutoFocus } from "@/shared/hooks/forms";

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

  // Ref for drawer body to scope keyboard handling
  const bodyRef = useRef<HTMLDivElement>(null);

  // Auto-focus first input when drawer opens
  useDrawerAutoFocus({
    containerRef: bodyRef,
    open: open,
    enabled: true,
  });

  // Enable keyboard form submission
  // Uses custom hook's handleSave directly since this drawer doesn't use react-hook-form
  useKeyboardFormSubmit({
    onSubmit: async () => {
      try {
        await handleSave();
        onClose();
      } catch {
        // Error handling (toast) is already performed in handleSave
      }
    },
    isSubmitEnabled: () => !saving && hasChanges,
    containerRef: bodyRef,
    enabled: open,
  });

  // Footer with action buttons
  const footer = (
    <PermissionGate require="security:user:save">
      <Box sx={{ display: "flex", justifyContent: "flex-end", gap: 2, p: 2 }} data-testid="drawer-footer">
        <Button
          variant="outlined"
          onClick={() => {
            resetChanges();
            onClose();
          }}
          disabled={saving || !hasChanges}
          data-testid="user-drawer-cancel-button"
        >
          {t("common.cancel")}
        </Button>
        <Button
          variant="contained"
          onClick={async () => {
            try {
              await handleSave();
              onClose();
            } catch {
              // Error handling (toast) is already performed in handleSave
            }
          }}
          disabled={saving || !hasChanges}
          color="primary"
          data-testid="user-drawer-save-button"
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
      id="user-drawer"
      data-testid="user-drawer"
      open={open}
      onClose={onClose}
      anchor="right"
      size="md"
      variant="temporary"
    >
      <Drawer.Header>
        <Box sx={{ display: "flex", alignItems: "center", gap: 1, flex: 1 }}>
          <PersonIcon sx={{ color: "text.secondary" }} />
          <Typography variant="h6" component="h2" sx={{ flex: 1 }} data-testid="drawer-title">
            {t("userManagement.drawer.title")}
          </Typography>
        </Box>
        <IconButton
          onClick={onClose}
          size="small"
          aria-label={`Close ${t("userManagement.drawer.title")}`}
          data-testid="drawer-close-button"
        >
          <CloseIcon />
        </IconButton>
      </Drawer.Header>
      <Drawer.Body ref={bodyRef} data-testid="drawer-body">
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
                src={user.avatarUrl || undefined}
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
                  data-testid="user-drawer-display-name-input"
                />
              </Box>

              {/* Email - Readonly */}
              <Box>
                <Typography variant="body2" color="text.secondary" gutterBottom>
                  {t("userManagement.drawer.email")}
                </Typography>
                <TextField
                  fullWidth
                  value={email}
                  readOnly
                  data-testid="user-drawer-email-input"
                />
              </Box>

              {/* Groups */}
              <PermissionGate require="security:user:save">
                <Box data-testid="user-drawer-groups-section">
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

              {/* Roles - Readonly (roles are assigned through groups only) */}
              <Box data-testid="user-drawer-roles-section">
                <Typography variant="body2" color="text.secondary" gutterBottom>
                  {t("userManagement.drawer.roles")}
                </Typography>
                <UserRoleAssignment
                  userId={userId}
                  assignedRoles={selectedRoles}
                  readonly={true}
                />
              </Box>

              {/* Timestamps - at the bottom in 2 columns */}
              {user.createdAt && user.updatedAt && (
                <Box sx={{ display: "flex", gap: 2 }}>
                  <Box sx={{ flex: 1 }}>
                    <Typography variant="body2" color="text.secondary" gutterBottom>
                      {t("userManagement.drawer.createdAt")}
                    </Typography>
                    <TextField
                      fullWidth
                      value={new Date(user.createdAt).toLocaleString()}
                      readOnly
                      data-testid="user-drawer-created-at"
                    />
                  </Box>
                  <Box sx={{ flex: 1 }}>
                    <Typography variant="body2" color="text.secondary" gutterBottom>
                      {t("userManagement.drawer.updatedAt")}
                    </Typography>
                    <TextField
                      fullWidth
                      value={new Date(user.updatedAt).toLocaleString()}
                      readOnly
                      data-testid="user-drawer-updated-at"
                    />
                  </Box>
                </Box>
              )}
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
