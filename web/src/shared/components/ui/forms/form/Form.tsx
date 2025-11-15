"use client";

import React from "react";
import { useForm, FormProvider, SubmitHandler, FieldValues } from "react-hook-form";

export interface FormProps<T extends FieldValues> {
  defaultValues?: T;
  onSubmit: SubmitHandler<T>;
  children: React.ReactNode;
}
export function Form<T extends FieldValues>({
  defaultValues,
  onSubmit,
  children,
}: FormProps<T>) {
  const methods = useForm<T>({ defaultValues: defaultValues as any });
  return (
    <FormProvider {...methods}>
      <form onSubmit={methods.handleSubmit(onSubmit as any)} noValidate>
        {children}
      </form>
    </FormProvider>
  );
}
