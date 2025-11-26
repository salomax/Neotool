"use client";

import { useForm, type UseFormProps, type FieldValues } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import type { ZodType, ZodTypeDef } from "zod";

export function useZodForm<
  TSchema extends ZodType<any, ZodTypeDef, any>,
  TFieldValues extends FieldValues = any,
>(schema: TSchema, props?: UseFormProps<TFieldValues>) {
  const mode = props?.mode ?? "onBlur";
  const reValidateMode = props?.reValidateMode ?? "onChange";
  const form = useForm<TFieldValues>({
    ...props,
    resolver: zodResolver(schema as any),
    mode,
    reValidateMode,
  });
  return {
    ...form,
    formState: {
      ...form.formState,
      mode,
      reValidateMode,
    },
  };
}
