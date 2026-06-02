"use client";

import React from "react";
import { Controller, useFormContext } from "react-hook-form";
import { FormStack } from "@/shared/components/ui/forms/components";
import { TextField } from "@/shared/components/ui/primitives";
import { useTranslation } from "@/shared/i18n";
import { authorizationManagementTranslations } from "@/app/(settings)/settings/i18n";

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
    <FormStack>
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

    </FormStack>
  );
};

