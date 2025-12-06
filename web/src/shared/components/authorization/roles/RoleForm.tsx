"use client";

import React from "react";
import { Controller, useFormContext } from "react-hook-form";
import { Stack } from "@mui/material";
import { TextField } from "@/shared/components/ui/forms/form/TextField";
import { useTranslation } from "@/shared/i18n";
import { authorizationManagementTranslations } from "@/app/(neotool)/settings/i18n";

export interface RoleFormData {
  name: string;
}

export interface RoleFormProps {
  initialValues?: Partial<RoleFormData>;
}

/**
 * RoleForm component for creating/editing roles
 * Must be used within a FormProvider from react-hook-form
 */
export const RoleForm: React.FC<RoleFormProps> = ({ initialValues }) => {
  const { t } = useTranslation(authorizationManagementTranslations);
  const { control } = useFormContext<RoleFormData>();

  return (
    <Stack spacing={3}>
      <Controller
        name="name"
        control={control}
        rules={{
          required: t("roleManagement.form.validation.nameRequired"),
          minLength: {
            value: 1,
            message: t("roleManagement.form.validation.nameMinLength"),
          },
        }}
        render={({ field, fieldState }) => (
          <TextField
            {...field}
            label={t("roleManagement.form.name")}
            error={!!fieldState.error}
            helperText={fieldState.error?.message}
            fullWidth
            required
            autoFocus
            data-testid="role-form-name"
          />
        )}
      />

    </Stack>
  );
};

