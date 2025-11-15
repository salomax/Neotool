"use client";

import { useForm, type UseFormProps, type FieldValues } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import type { ZodType, ZodTypeDef } from "zod";

export function useZodForm<
  TSchema extends ZodType<any, ZodTypeDef, any>,
  TFieldValues extends FieldValues = any,
>(schema: TSchema, props?: UseFormProps<TFieldValues>) {
  return useForm<TFieldValues>({
    ...props,
    resolver: zodResolver(schema as any),
    mode: props?.mode ?? "onBlur",
    reValidateMode: props?.reValidateMode ?? "onChange",
  });
}
