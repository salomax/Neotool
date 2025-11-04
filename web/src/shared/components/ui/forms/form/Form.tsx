"use client";

import React from "react";
import { useForm, FormProvider, SubmitHandler, FieldValues } from "react-hook-form";

export interface RHFFormProps<T extends FieldValues> {
  defaultValues?: T;
  onSubmit: SubmitHandler<T>;
  children: React.ReactNode;
}
export function RHFForm<T extends FieldValues>({
  defaultValues,
  onSubmit,
  children,
}: RHFFormProps<T>) {
  const methods = useForm<T>({ defaultValues: defaultValues as any });
  return (
    <FormProvider {...methods}>
      <form onSubmit={methods.handleSubmit(onSubmit as any)} noValidate>
        {children}
      </form>
    </FormProvider>
  );
}
