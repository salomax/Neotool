"use client";

import React from "react";
import { Controller, useFormContext } from "react-hook-form";
import { Stack } from "@mui/material";
import { TextField } from "@/shared/components/ui/forms/form/TextField";
import { useTranslation } from "@/shared/i18n";
import { authorizationManagementTranslations } from "@/app/(settings)/settings/i18n";

export interface GroupFormData {
  name: string;
  description?: string | null;
  userIds?: string[];
}

export interface GroupFormProps {
  initialValues?: Partial<GroupFormData>;
}

/**
 * GroupForm component for creating/editing groups
 * Must be used within a FormProvider from react-hook-form
 */
export const GroupForm: React.FC<GroupFormProps> = ({ initialValues }) => {
  const { t } = useTranslation(authorizationManagementTranslations);
  const { control } = useFormContext<GroupFormData>();

  return (
    <Stack spacing={3}>
      <Controller
        name="name"
        control={control}
        rules={{
          required: t("groupManagement.form.validation.nameRequired"),
          minLength: {
            value: 1,
            message: t("groupManagement.form.validation.nameMinLength"),
          },
        }}
        render={({ field, fieldState }) => (
          <TextField
            {...field}
            label={t("groupManagement.form.name")}
            error={!!fieldState.error}
            helperText={fieldState.error?.message}
            fullWidth
            required
            autoFocus
            data-testid="group-form-name"
          />
        )}
      />

      <Controller
        name="description"
        control={control}
        render={({ field, fieldState }) => (
          <TextField
            {...field}
            value={field.value ?? ""}
            label={t("groupManagement.form.description")}
            error={!!fieldState.error}
            helperText={fieldState.error?.message}
            fullWidth
            multiline
            rows={3}
            data-testid="group-form-description"
          />
        )}
      />
    </Stack>
  );
};

