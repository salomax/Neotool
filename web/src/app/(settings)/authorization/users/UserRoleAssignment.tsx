"use client";

import React, { useMemo } from "react";
import { Box, Typography, Chip } from "@mui/material";
import { useTranslation } from "@/shared/i18n";
import { authorizationManagementTranslations } from "@/app/(settings)/settings/i18n";

export interface Role {
  id: string;
  name: string;
}

export interface UserRoleAssignmentProps {
  userId: string | null;
  assignedRoles: Role[];
  readonly?: boolean;
}

/**
 * UserRoleAssignment component for managing user role assignments
 * Uses a multi-select Autocomplete to assign and remove roles
 * When readonly, displays roles as chips only (no editing capability)
 */
export const UserRoleAssignment: React.FC<UserRoleAssignmentProps> = ({
  assignedRoles,
  readonly = true,
}) => {
  const { t } = useTranslation(authorizationManagementTranslations);
  const chipVariant = readonly ? "outlined" : "filled";

  const selectedRoles = useMemo(() => {
    return assignedRoles.map((role) => ({
      id: role.id,
      label: role.name,
      name: role.name,
    }));
  }, [assignedRoles]);

  return (
    <Box data-testid="user-role-assignment">
      {selectedRoles.length === 0 ? (
        <Typography variant="body2" color="text.secondary" data-testid="user-role-assignment-empty">
          {t("userManagement.roles.noRoles")}
        </Typography>
      ) : (
        <Box sx={{ display: "flex", flexWrap: "wrap", gap: 1 }}>
          {selectedRoles.map((role) => (
            <Chip
              key={role.id}
              variant={chipVariant}
              label={role.label}
              data-testid={`user-role-chip-${role.id}`}
            />
          ))}
        </Box>
      )}
    </Box>
  );
};

