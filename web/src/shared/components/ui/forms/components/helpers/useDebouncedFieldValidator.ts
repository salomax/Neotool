"use client";

import * as React from "react";
import type {
  UseFormSetError,
  UseFormClearErrors,
  FieldValues,
  Path,
} from "react-hook-form";

export function useDebouncedFieldValidator<T extends FieldValues>(
  name: Path<T>,
  value: any,
  validate: (_value: any) => Promise<true | string>,
  setError: UseFormSetError<T>,
  clearErrors: UseFormClearErrors<T>,
  delay = 500,
) {
  const timer = React.useRef<number | null>(null);

  React.useEffect(() => {
    // limpar erro enquanto digita
    clearErrors(name);

    if (timer.current) window.clearTimeout(timer.current);
    timer.current = window.setTimeout(async () => {
      const res = await validate(value);
      if (res !== true) setError(name, { type: "validate", message: res });
    }, delay) as unknown as number;

    return () => {
      if (timer.current) window.clearTimeout(timer.current);
    };
  }, [name, value, delay, validate, setError, clearErrors]);
}
